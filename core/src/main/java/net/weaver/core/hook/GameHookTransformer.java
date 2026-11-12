package net.weaver.core.hook;

import net.weaver.api.event.Side;
import net.weaver.core.LifecycleHooks;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Instruments the game with the hook targets registered in {@link #SPECS}. */
public final class GameHookTransformer implements ClassFileTransformer
{
    private static final String HOOK_OWNER = "net/weaver/core/hook/GameHooks";
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SUMMARY_LOGGED = new AtomicBoolean();
    private static final Set<String> NOTES = ConcurrentHashMap.newKeySet();
    private static final Map<HookSpec, Status> STATUS = new ConcurrentHashMap<>();
    private static final Set<HookSpec> ABSENT_AT_INSTALL = ConcurrentHashMap.newKeySet();
    private static final Set<String> LATE_REQUIRED_MISSES = ConcurrentHashMap.newKeySet();
    private static final HookSpec.Arg THIS = HookSpec.Arg.THIS;
    private static final HookSpec.Arg ARG0 = HookSpec.Arg.ARG0;
    private static final HookSpec.Arg ARG1 = HookSpec.Arg.ARG1;
    private static final HookSpec.Arg ARG2 = HookSpec.Arg.ARG2;
    private static final HookSpec.Arg ARG3 = HookSpec.Arg.ARG3;
    private static final HookSpec.Arg ARG4 = HookSpec.Arg.ARG4;
    private static final List<HookSpec> SPECS = buildSpecs();

    private static volatile boolean clientHosted;
    private static volatile boolean serverHosted;
    private static volatile Map<String, List<HookSpec>> byClass = index(SPECS);

    private enum Status
    {
        APPLIED,
        SKIPPED,
        MISSING,
        INVALID
    }

    private final ClassLoader gameLoader;

    private GameHookTransformer(ClassLoader gameLoader)
    {
        this.gameLoader = gameLoader;
    }

    public static void install(Instrumentation instrumentation, ClassLoader gameLoader)
    {
        try
        {
            for (String problem : verifySpecs())
            {
                LifecycleHooks.logLine("[weaver] GAME HOOK SPEC INVALID " + problem);
            }
        }
        catch (Throwable failure)
        {
            LifecycleHooks.logLine("[weaver] GAME HOOK SPEC CHECK FAILED reason=" + failure);
        }
        configureSides(LifecycleHooks.clientAvailable(), LifecycleHooks.serverAvailable());
        try
        {
            markAbsentTargets(SPECS, gameLoader);
        }
        catch (Throwable failure)
        {
            LifecycleHooks.logLine("[weaver] GAME HOOK TARGET CHECK FAILED reason=" + failure);
        }
        instrumentation.addTransformer(new GameHookTransformer(gameLoader), false);
    }

    /**
     * Marks hosted specs whose target class cannot exist in this process.
     *
     * <p>A required spec whose class resource is absent from the game loader
     * would otherwise stay pending forever and never fail strict mode, so it
     * is recorded as {@code MISSING} up front. An optional spec is recorded as
     * {@code SKIPPED}. Classes under {@code net/minecraft/client/} are not
     * shipped in the dedicated server jar, so they are recorded as skipped on
     * a process that hosts no client. A later successful transform still
     * upgrades the status to {@code APPLIED}.</p>
     *
     * @param specs      the specs to check
     * @param gameLoader the loader that hosts the game classes
     */
    static void markAbsentTargets(List<HookSpec> specs, ClassLoader gameLoader)
    {
        if (gameLoader == null)
        {
            return;
        }
        Map<String, List<HookSpec>> hostedByClass = byClass;
        for (HookSpec spec : specs)
        {
            if (STATUS.containsKey(spec))
            {
                continue;
            }
            List<HookSpec> hosted = hostedByClass.get(spec.className());
            if (SPECS.contains(spec) && (hosted == null || !hosted.contains(spec)))
            {
                continue;
            }
            if (gameLoader.getResource(spec.className() + ".class") != null)
            {
                continue;
            }
            ABSENT_AT_INSTALL.add(spec);
            if (spec.className().startsWith("net/minecraft/client/") && !clientHosted)
            {
                STATUS.put(spec, Status.SKIPPED);
                note("SKIPPED", spec, "reason=client-class-absent-on-server");
            }
            else if (spec.required())
            {
                STATUS.put(spec, Status.MISSING);
                note("MISSING", spec, "reason=class-absent");
            }
            else
            {
                STATUS.put(spec, Status.SKIPPED);
                note("SKIPPED", spec, "reason=class-absent");
            }
        }
    }

    /**
     * Applies side gating to the default spec list without an instrumentation
     * instance. {@link #install} calls this with the sides reported by
     * {@link LifecycleHooks}. A spec whose side is not hosted is recorded as
     * skipped and removed from the transform index.
     *
     * <p>When neither side is hosted, {@link Side#BOTH} specs are still
     * considered hosted so side-agnostic unit-test processes keep working;
     * client-only and server-only specs are skipped.
     *
     * @param client whether a logical client is hosted
     * @param server whether a logical server is hosted
     */
    static void configureSides(boolean client, boolean server)
    {
        clientHosted = client;
        serverHosted = server;
        Map<String, List<HookSpec>> grouped = new LinkedHashMap<>();
        for (HookSpec spec : SPECS)
        {
            if (ABSENT_AT_INSTALL.remove(spec))
            {
                STATUS.computeIfPresent(spec, (key, status) ->
                        status == Status.APPLIED ? status : null);
            }
            if (hosted(spec.side()))
            {
                STATUS.remove(spec, Status.SKIPPED);
                grouped.computeIfAbsent(spec.className(), key -> new ArrayList<>()).add(spec);
            }
            else
            {
                STATUS.put(spec, Status.SKIPPED);
                note("SKIPPED", spec, "reason=side=" + spec.side());
            }
        }
        byClass = freeze(grouped);
    }

    private static boolean hosted(Side side)
    {
        return switch (side)
        {
            case CLIENT -> clientHosted;
            case SERVER -> serverHosted;
            case BOTH -> clientHosted || serverHosted || (!clientHosted && !serverHosted);
        };
    }

    static List<HookSpec> specs()
    {
        return SPECS;
    }

    /**
     * The status recorded for a spec. Returns {@code PENDING} when the spec has
     * not been applied, skipped, marked missing, or marked invalid yet.
     *
     * @param spec the spec to inspect
     * @return the status name
     */
    static String statusOf(HookSpec spec)
    {
        Status status = STATUS.get(spec);
        return status == null ? "PENDING" : status.name();
    }

    static List<String> verifySpecs()
    {
        return verifySpecs(SPECS);
    }

    static List<String> verifySpecs(List<HookSpec> specs)
    {
        List<String> problems = new ArrayList<>();
        for (HookSpec spec : specs)
        {
            verifyCalls(spec, spec.entryCalls(), "entry", problems);
            verifyCalls(spec, spec.exitCalls(), "exit", problems);
            verifyCalls(spec, spec.throwCalls(), "throw", problems);
        }
        return List.copyOf(problems);
    }

    private static void verifyCalls(HookSpec spec, List<HookSpec.HookCall> calls, String phase,
                                    List<String> problems)
    {
        for (HookSpec.HookCall call : calls)
        {
            Class<?>[] parameters = new Class<?>[call.args().size()];
            Arrays.fill(parameters, Object.class);
            try
            {
                Method method = GameHooks.class.getDeclaredMethod(call.name(), parameters);
                int modifiers = method.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers))
                {
                    problems.add(spec.id() + " " + phase + " call=" + call.name()
                            + " is not public static");
                    continue;
                }
                String actual = Type.getMethodDescriptor(method);
                String expectedParameters = "(" + "Ljava/lang/Object;".repeat(call.args().size()) + ")";
                if (!actual.startsWith(expectedParameters))
                {
                    problems.add(spec.id() + " " + phase + " call=" + call.name()
                            + " descriptor expected=" + call.descriptor()
                            + " actual=" + actual);
                    continue;
                }
                String problem = verifyReturn(call, method);
                if (problem != null)
                {
                    problems.add(spec.id() + " " + phase + " call=" + call.name() + " " + problem);
                }
            }
            catch (NoSuchMethodException missing)
            {
                problems.add(spec.id() + " " + phase + " call=" + call.name()
                        + " missing method " + call.descriptor());
            }
        }
    }

    private static String verifyReturn(HookSpec.HookCall call, Method method)
    {
        Class<?> declared = method.getReturnType();
        return switch (call.kind())
        {
            case VOID -> declared == void.class
                    ? null
                    : "returns " + declared.getName() + " but the call kind is VOID";
            case CANCEL_IF_FALSE -> declared == boolean.class
                    ? null
                    : "returns " + declared.getName() + " but the call kind is CANCEL_IF_FALSE";
            case RETURN_VALUE ->
            {
                if (declared == void.class || declared.isPrimitive())
                {
                    yield "returns " + declared.getName() + " but the call kind is RETURN_VALUE";
                }
                String declaredDescriptor = Type.getDescriptor(declared);
                if ("Ljava/lang/Object;".equals(declaredDescriptor))
                {
                    yield null;
                }
                yield declaredDescriptor.equals(call.returnDescriptor())
                        ? null
                        : "returns " + declared.getName() + " but the target returns "
                                + call.returnDescriptor();
            }
        };
    }

    /**
     * Logs one audit line with per-spec hook status on the first client or
     * server tick, and enforces strict mode for that snapshot.
     *
     * <p>Every later tick calls this again; it is then cheap unless a required
     * spec turned {@code MISSING} or {@code INVALID} after the summary (a
     * target class that loaded late), in which case strict mode is enforced
     * for those late misses.</p>
     */
    static void logSummaryOnce()
    {
        if (!SUMMARY_LOGGED.compareAndSet(false, true))
        {
            enforceLateMisses();
            return;
        }
        Map<HookSpec, String> statuses = snapshotStatuses();
        try
        {
            LifecycleHooks.logLine(summaryLine(SPECS, statuses));
            // Everything the loader worked around, in one place, once the game runs.
            if (!net.weaver.api.WeaverProblems.isEmpty())
            {
                LifecycleHooks.logLine("[weaver] PROBLEMS SUMMARY mods="
                        + net.weaver.api.WeaverProblems.all().size()
                        + " (each mod below is not fully working)\n"
                        + net.weaver.api.WeaverProblems.summary());
            }
        }
        catch (Throwable failure)
        {
            LifecycleHooks.logLine("[weaver] GAME HOOK SUMMARY FAILED reason=" + failure);
        }
        enforceStrict(SPECS, statuses);
    }

    /**
     * Builds the summary line. Specs that have not been transformed yet are
     * counted as {@code pending}, not as {@code skipped}.
     *
     * @param specs    the specs to count
     * @param statuses status names keyed by spec; absent means pending
     * @return the summary line
     */
    static String summaryLine(List<HookSpec> specs, Map<HookSpec, String> statuses)
    {
        int applied = 0;
        int skipped = 0;
        int missing = 0;
        int invalid = 0;
        int pending = 0;
        List<String> requiredMisses = new ArrayList<>();
        for (HookSpec spec : specs)
        {
            String status = statuses.getOrDefault(spec, "PENDING");
            switch (status)
            {
                case "APPLIED" -> applied++;
                case "MISSING" -> missing++;
                case "INVALID" -> invalid++;
                case "PENDING" -> pending++;
                default -> skipped++;
            }
            if (spec.required() && ("MISSING".equals(status) || "INVALID".equals(status)))
            {
                requiredMisses.add(spec.id());
            }
        }
        String misses = requiredMisses.isEmpty() ? "none" : String.join(",", requiredMisses);
        return "[weaver] GAME HOOK SUMMARY applied=" + applied
                + " skipped=" + skipped + " missing=" + missing + " invalid=" + invalid
                + " required_misses=" + misses + " pending=" + pending;
    }

    /**
     * Applies strict mode to required specs that became {@code MISSING} or
     * {@code INVALID} after the summary was logged. Each late miss is enforced
     * once.
     *
     * @throws IllegalStateException when strict mode is on and a late required miss exists
     */
    static void enforceLateMisses()
    {
        if (LATE_REQUIRED_MISSES.isEmpty() || !Boolean.getBoolean("weaver.hooks.strict"))
        {
            return;
        }
        List<String> ids = new ArrayList<>(LATE_REQUIRED_MISSES);
        LATE_REQUIRED_MISSES.removeAll(ids);
        ids.sort(String::compareTo);
        String joined = String.join(",", ids);
        LifecycleHooks.logLine("[weaver] GAME HOOK STRICT FAILURE required_misses=" + joined
                + " phase=after-summary");
        throw new IllegalStateException("Required Weaver hooks are missing: " + joined);
    }

    /**
     * Enforces strict mode from an explicit status snapshot. When the
     * {@code weaver.hooks.strict} system property is true and a required spec
     * is missing or invalid, this logs the strict failure and throws.
     *
     * @param specs    the specs to check
     * @param statuses status names keyed by spec
     * @throws IllegalStateException when strict mode is on and a required spec is missing
     */
    static void enforceStrict(List<HookSpec> specs, Map<HookSpec, String> statuses)
    {
        List<String> requiredMisses = new ArrayList<>();
        for (HookSpec spec : specs)
        {
            if (!spec.required())
            {
                continue;
            }
            String status = statuses.get(spec);
            if ("MISSING".equals(status) || "INVALID".equals(status))
            {
                requiredMisses.add(spec.id());
            }
        }
        if (requiredMisses.isEmpty() || !Boolean.getBoolean("weaver.hooks.strict"))
        {
            return;
        }
        String ids = String.join(",", requiredMisses);
        LifecycleHooks.logLine("[weaver] GAME HOOK STRICT FAILURE required_misses=" + ids);
        throw new IllegalStateException("Required Weaver hooks are missing: " + ids);
    }

    private static Map<HookSpec, String> snapshotStatuses()
    {
        Map<HookSpec, String> snapshot = new LinkedHashMap<>();
        for (HookSpec spec : SPECS)
        {
            snapshot.put(spec, statusOf(spec));
        }
        return snapshot;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer)
    {
        if (className == null || !isHostLoader(loader))
        {
            return null;
        }
        List<HookSpec> classSpecs = byClass.get(className);
        if (classSpecs == null)
        {
            return null;
        }
        try
        {
            return apply(className, classSpecs, classfileBuffer, HOOK_OWNER, gameLoader);
        }
        catch (Throwable failure)
        {
            for (HookSpec spec : classSpecs)
            {
                record(spec, Status.INVALID);
                note("INVALID", spec, "reason=" + failure);
            }
            if (FAILURE_LOGGED.compareAndSet(false, true))
            {
                LifecycleHooks.logLine("[weaver] GAME HOOK TRANSFORM FAILED class=" + className
                        + " reason=" + failure);
            }
            return null;
        }
    }

    private boolean isHostLoader(ClassLoader loader)
    {
        return loader == null || loader == gameLoader;
    }

    static byte[] apply(String className, List<HookSpec> classSpecs, byte[] classfileBuffer)
    {
        return apply(className, classSpecs, classfileBuffer, HOOK_OWNER,
                GameHookTransformer.class.getClassLoader());
    }

    /**
     * Applies specs to a class using an explicit hook owner and loader. The
     * loader resolves the hook-owner class for value-returning calls and
     * supplies the class hierarchy for frame computation.
     *
     * @param className      the internal class name being transformed
     * @param classSpecs     the specs that target this class
     * @param classfileBuffer the original class bytes
     * @param hookOwner      the internal name of the static hook owner
     * @param loader         the loader that resolves game and hook classes
     * @return the transformed bytes
     */
    static byte[] apply(String className, List<HookSpec> classSpecs, byte[] classfileBuffer,
                        String hookOwner, ClassLoader loader)
    {
        ClassReader reader = new ClassReader(classfileBuffer);
        boolean computeFrames = classSpecs.stream()
                .anyMatch(spec -> spec.action() != HookSpec.Action.VOID);
        ClassWriter writer = new SafeClassWriter(
                computeFrames ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS,
                loader, className, classfileBuffer);
        Set<HookSpec> applied = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<HookSpec> invalid = Collections.newSetFromMap(new IdentityHashMap<>());
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer)
        {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions)
            {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (method == null)
                {
                    return null;
                }
                List<HookSpec> matching = new ArrayList<>();
                for (HookSpec spec : classSpecs)
                {
                    if (!spec.matches(name, descriptor))
                    {
                        continue;
                    }
                    String reason = validate(spec, access, descriptor);
                    if (reason != null)
                    {
                        invalid.add(spec);
                        note("INVALID", spec, "reason=" + reason);
                        continue;
                    }
                    matching.add(spec);
                }
                if (matching.isEmpty())
                {
                    return method;
                }
                matching.sort(Comparator.comparingInt(HookSpec::priority));
                applied.addAll(matching);
                return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor)
                {
                    private final boolean staticMethod = (access & Opcodes.ACC_STATIC) != 0;
                    private final Type[] argumentTypes = Type.getArgumentTypes(descriptor);
                    private final Type returnType = Type.getReturnType(descriptor);

                    @Override
                    protected void onMethodEnter()
                    {
                        for (HookSpec spec : matching)
                        {
                            switch (spec.action())
                            {
                                case VOID ->
                                {
                                    for (HookSpec.HookCall call : spec.entryCalls())
                                    {
                                        emit(call);
                                    }
                                }
                                case CANCEL_IF_FALSE ->
                                {
                                    emit(spec.entryCalls().get(0));
                                    Label proceed = new Label();
                                    visitJumpInsn(Opcodes.IFNE, proceed);
                                    emitDefaultReturn();
                                    visitLabel(proceed);
                                }
                                case RETURN_VALUE ->
                                {
                                    emit(spec.entryCalls().get(0));
                                    visitInsn(Opcodes.DUP);
                                    Label proceed = new Label();
                                    visitJumpInsn(Opcodes.IFNULL, proceed);
                                    if (returnType.getSort() == Type.OBJECT
                                            || returnType.getSort() == Type.ARRAY)
                                    {
                                        visitTypeInsn(Opcodes.CHECKCAST,
                                                returnType.getInternalName());
                                    }
                                    visitInsn(Opcodes.ARETURN);
                                    visitLabel(proceed);
                                    visitInsn(Opcodes.POP);
                                }
                            }
                        }
                    }

                    @Override
                    protected void onMethodExit(int opcode)
                    {
                        if (opcode == Opcodes.ATHROW)
                        {
                            for (HookSpec spec : matching)
                            {
                                for (HookSpec.HookCall call : spec.throwCalls())
                                {
                                    emit(call);
                                }
                            }
                            return;
                        }
                        boolean preserve = opcode != Opcodes.RETURN;
                        int width = opcode == Opcodes.LRETURN || opcode == Opcodes.DRETURN ? 2 : 1;
                        if (preserve)
                        {
                            visitInsn(width == 2 ? Opcodes.DUP2 : Opcodes.DUP);
                        }
                        for (HookSpec spec : matching)
                        {
                            for (HookSpec.HookCall call : spec.exitCalls())
                            {
                                emit(call);
                            }
                        }
                        if (preserve)
                        {
                            visitInsn(width == 2 ? Opcodes.POP2 : Opcodes.POP);
                        }
                    }

                    private void emitDefaultReturn()
                    {
                        switch (returnType.getSort())
                        {
                            case Type.VOID -> visitInsn(Opcodes.RETURN);
                            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                            {
                                visitInsn(Opcodes.ICONST_0);
                                visitInsn(Opcodes.IRETURN);
                            }
                            case Type.LONG ->
                            {
                                visitInsn(Opcodes.LCONST_0);
                                visitInsn(Opcodes.LRETURN);
                            }
                            case Type.FLOAT ->
                            {
                                visitInsn(Opcodes.FCONST_0);
                                visitInsn(Opcodes.FRETURN);
                            }
                            case Type.DOUBLE ->
                            {
                                visitInsn(Opcodes.DCONST_0);
                                visitInsn(Opcodes.DRETURN);
                            }
                            default ->
                            {
                                visitInsn(Opcodes.ACONST_NULL);
                                visitInsn(Opcodes.ARETURN);
                            }
                        }
                    }

                    private void emit(HookSpec.HookCall call)
                    {
                        for (HookSpec.Arg arg : call.args())
                        {
                            visitVarInsn(Opcodes.ALOAD, localIndex(arg, staticMethod, argumentTypes));
                        }
                        visitMethodInsn(Opcodes.INVOKESTATIC, hookOwner, call.name(),
                                invocationDescriptor(call, hookOwner, loader), false);
                    }
                };
            }
        };
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        // Serialize before recording any status: toByteArray can still fail (for example a
        // method or class that grew past the class-file limits), and then the JVM keeps the
        // untransformed class, so no spec may be reported as APPLIED.
        byte[] transformed;
        try
        {
            transformed = writer.toByteArray();
        }
        catch (RuntimeException failure)
        {
            for (HookSpec spec : classSpecs)
            {
                record(spec, Status.INVALID);
                note("INVALID", spec, "reason=" + failure);
            }
            throw failure;
        }
        for (HookSpec spec : classSpecs)
        {
            if (applied.contains(spec))
            {
                record(spec, Status.APPLIED);
                note("APPLIED", spec, null);
            }
            else if (invalid.contains(spec))
            {
                record(spec, Status.INVALID);
            }
            else if (spec.required())
            {
                record(spec, Status.MISSING);
                note("MISSING", spec, null);
            }
            else
            {
                record(spec, Status.SKIPPED);
                note("SKIPPED", spec, "reason=not-found");
            }
        }
        return transformed;
    }

    private static String invocationDescriptor(HookSpec.HookCall call, String hookOwner,
                                               ClassLoader loader)
    {
        if (call.kind() != HookSpec.Kind.RETURN_VALUE)
        {
            return call.descriptor();
        }
        ClassLoader effective = loader == null ? GameHookTransformer.class.getClassLoader() : loader;
        try
        {
            Class<?> owner = Class.forName(hookOwner.replace('/', '.'), false, effective);
            Class<?>[] parameters = new Class<?>[call.args().size()];
            Arrays.fill(parameters, Object.class);
            Method method = owner.getDeclaredMethod(call.name(), parameters);
            return Type.getMethodDescriptor(method);
        }
        catch (Throwable failure)
        {
            return call.descriptor();
        }
    }

    /**
     * Runs the transformer-side validation for a spec against the target
     * method access flags and descriptor. This is package-private so tests can
     * exercise the defense-in-depth guards for legacy or deserialized specs
     * that the builder already rejects.
     *
     * @param spec       the spec to validate
     * @param access     the target method access flags
     * @param descriptor the target method descriptor
     * @return a rejection reason, or {@code null} when the spec is valid
     */
    static String validateForTest(HookSpec spec, int access, String descriptor)
    {
        return validate(spec, access, descriptor);
    }

    private static String validate(HookSpec spec, int access, String descriptor)
    {
        boolean constructor = "<init>".equals(spec.methodName());
        if (constructor && spec.action() != HookSpec.Action.VOID)
        {
            return "action " + spec.action() + " on a constructor";
        }
        if (constructor && !spec.throwCalls().isEmpty())
        {
            return "constructor throw hooks are not supported";
        }
        boolean staticMethod = (access & Opcodes.ACC_STATIC) != 0;
        if (staticMethod && spec.action() != HookSpec.Action.VOID)
        {
            return "action " + spec.action() + " on a static target";
        }
        Type[] argumentTypes = Type.getArgumentTypes(descriptor);
        for (HookSpec.HookCall call : spec.entryCalls())
        {
            String reason = validate(call, staticMethod, argumentTypes);
            if (reason != null)
            {
                return "entry " + call.name() + " " + reason;
            }
        }
        for (HookSpec.HookCall call : spec.exitCalls())
        {
            String reason = validate(call, staticMethod, argumentTypes);
            if (reason != null)
            {
                return "exit " + call.name() + " " + reason;
            }
        }
        for (HookSpec.HookCall call : spec.throwCalls())
        {
            String reason = validate(call, staticMethod, argumentTypes);
            if (reason != null)
            {
                return "throw " + call.name() + " " + reason;
            }
        }
        return null;
    }

    private static String validate(HookSpec.HookCall call, boolean staticMethod,
                                   Type[] argumentTypes)
    {
        for (HookSpec.Arg arg : call.args())
        {
            if (arg == HookSpec.Arg.THIS)
            {
                if (staticMethod)
                {
                    return "arg=THIS on a static target";
                }
                continue;
            }
            int target = arg.ordinal() - 1;
            if (target >= argumentTypes.length)
            {
                return "arg=" + arg + " out of range";
            }
            int sort = argumentTypes[target].getSort();
            if (sort != Type.OBJECT && sort != Type.ARRAY)
            {
                return "arg=" + arg + " is not a reference";
            }
        }
        return null;
    }

    private static int localIndex(HookSpec.Arg arg, boolean staticMethod, Type[] argumentTypes)
    {
        if (arg == HookSpec.Arg.THIS)
        {
            return 0;
        }
        int index = staticMethod ? 0 : 1;
        int target = arg.ordinal() - 1;
        for (int i = 0; i < target; i++)
        {
            index += argumentTypes[i].getSize();
        }
        return index;
    }

    private static void record(HookSpec spec, Status status)
    {
        Status previous = STATUS.get(spec);
        Status result = STATUS.merge(spec, status, (current, update) ->
                current == Status.APPLIED || update == Status.APPLIED ? Status.APPLIED : update);
        boolean failed = result == Status.MISSING || result == Status.INVALID;
        if (failed && previous != result && SUMMARY_LOGGED.get())
        {
            LifecycleHooks.logLine("[weaver] GAME HOOK LATE " + result.name() + " id=" + spec.id()
                    + " required=" + spec.required() + " phase=after-summary");
            if (spec.required())
            {
                LATE_REQUIRED_MISSES.add(spec.id());
            }
        }
    }

    /** Clears the summary and late-miss state for an isolated test. */
    static void resetSummaryForTests()
    {
        SUMMARY_LOGGED.set(false);
        LATE_REQUIRED_MISSES.clear();
    }

    /** Marks the summary as logged so a test can exercise the after-summary path. */
    static void markSummaryLoggedForTests()
    {
        SUMMARY_LOGGED.set(true);
    }

    private static void note(String kind, HookSpec spec, String detail)
    {
        String key = kind + "#" + spec.id();
        if (!NOTES.add(key))
        {
            return;
        }
        String suffix = detail == null || detail.isBlank() ? "" : " " + detail;
        LifecycleHooks.logLine("[weaver] GAME HOOK " + kind + " id=" + spec.id() + suffix);
    }

    private static Map<String, List<HookSpec>> index(List<HookSpec> specs)
    {
        Map<String, List<HookSpec>> grouped = new LinkedHashMap<>();
        for (HookSpec spec : specs)
        {
            grouped.computeIfAbsent(spec.className(), key -> new ArrayList<>()).add(spec);
        }
        return freeze(grouped);
    }

    private static Map<String, List<HookSpec>> freeze(Map<String, List<HookSpec>> grouped)
    {
        Map<String, List<HookSpec>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<HookSpec>> entry : grouped.entrySet())
        {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<HookSpec> buildSpecs()
    {
        List<HookSpec> specs = new ArrayList<>();
        specs.add(HookSpec.method("net.minecraft.client.Minecraft", "tick", "()V")
                .side(Side.CLIENT)
                .entry("clientTickEnter")
                .exit("clientTickExit")
                .build());
        specs.add(HookSpec.method("net.minecraft.client.Minecraft", "setLevel",
                        "(Lnet/minecraft/client/multiplayer/ClientLevel;)V")
                .side(Side.CLIENT)
                .exit("clientLevelLoad", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.client.Minecraft", "clearClientLevel",
                        "(Lnet/minecraft/client/gui/screens/Screen;)V")
                .side(Side.CLIENT)
                .entry("clientLevelUnload", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.client.Minecraft", "reloadResourcePacks",
                        "()Ljava/util/concurrent/CompletableFuture;")
                .side(Side.CLIENT)
                .entry("clientResourceReloadStart", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.client.Minecraft", "onResourceLoadFinished",
                        "(Lnet/minecraft/client/GameLoadCookie;)V")
                .side(Side.CLIENT)
                .exit("clientResourceReloadFinished", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.client.Minecraft", "setScreenAndShow",
                        "(Lnet/minecraft/client/gui/screens/Screen;)V")
                .side(Side.CLIENT)
                .entry("screenChange", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.client.multiplayer.ClientLevel", "tick",
                        "(Ljava/util/function/BooleanSupplier;)V")
                .side(Side.CLIENT)
                .entry("clientLevelTickEnter", THIS)
                .exit("clientLevelTickExit", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.MinecraftServer", "tickServer",
                        "(Ljava/util/function/BooleanSupplier;)V")
                .side(Side.SERVER)
                .entry("serverTickEnter")
                .exit("serverTickExit")
                .build());
        specs.add(HookSpec.method("net.minecraft.server.MinecraftServer", "runServer", "()V")
                .side(Side.SERVER)
                .entry("serverStarting", THIS)
                .exit("serverStopped", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.MinecraftServer", "stopServer", "()V")
                .side(Side.SERVER)
                .entry("serverStopping", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.dedicated.DedicatedServer", "initServer", "()Z")
                .side(Side.SERVER)
                .exit("serverStarted", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.client.server.IntegratedServer", "initServer", "()Z")
                .side(Side.SERVER)
                .exit("serverStarted", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.players.PlayerList", "placeNewPlayer",
                        "(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
                                + "Lnet/minecraft/server/network/CommonListenerCookie;)V")
                .side(Side.SERVER)
                .entry("playerLogin", THIS, ARG1)
                .exit("playerJoin", THIS, ARG1)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.players.PlayerList", "remove",
                        "(Lnet/minecraft/server/level/ServerPlayer;)V")
                .side(Side.SERVER)
                .entry("playerLogout", THIS, ARG0)
                .exit("playerLeave", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerLevel", "<init>",
                        HookSpec.ANY_DESCRIPTOR)
                .side(Side.SERVER)
                .exit("levelLoad", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerLevel", "close", "()V")
                .side(Side.SERVER)
                .entry("levelUnload", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerLevel", "tick",
                        "(Ljava/util/function/BooleanSupplier;)V")
                .side(Side.SERVER)
                .entry("serverLevelTickEnter", THIS)
                .exit("serverLevelTickExit", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerLevel", "addFreshEntity",
                        "(Lnet/minecraft/world/entity/Entity;)Z")
                .side(Side.SERVER)
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("entityJoinLevel", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.LivingEntity", "hurtServer",
                        "(Lnet/minecraft/server/level/ServerLevel;"
                                + "Lnet/minecraft/world/damagesource/DamageSource;F)Z")
                .side(Side.SERVER)
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("entityHurt", THIS, ARG0, ARG1)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayerGameMode", "useItemOn",
                        "(Lnet/minecraft/server/level/ServerPlayer;"
                                + "Lnet/minecraft/world/level/Level;"
                                + "Lnet/minecraft/world/item/ItemStack;"
                                + "Lnet/minecraft/world/InteractionHand;"
                                + "Lnet/minecraft/world/phys/BlockHitResult;)"
                                + "Lnet/minecraft/world/InteractionResult;")
                .side(Side.SERVER)
                .entry("blockInteract", ARG0, ARG1, ARG2, ARG3, ARG4)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayerGameMode", "useItem",
                        "(Lnet/minecraft/server/level/ServerPlayer;"
                                + "Lnet/minecraft/world/level/Level;"
                                + "Lnet/minecraft/world/item/ItemStack;"
                                + "Lnet/minecraft/world/InteractionHand;)"
                                + "Lnet/minecraft/world/InteractionResult;")
                .side(Side.SERVER)
                .entry("itemUse", ARG0, ARG1, ARG2, ARG3)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.LivingEntity", "die",
                        "(Lnet/minecraft/world/damagesource/DamageSource;)V")
                .side(Side.SERVER)
                .entry("livingEntityDeath", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayer", "die",
                        "(Lnet/minecraft/world/damagesource/DamageSource;)V")
                .side(Side.SERVER)
                .entry("playerDeath", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayer", "openMenu",
                        "(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;")
                .side(Side.SERVER)
                .required(false)
                .entry("containerOpen", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayer",
                        "closeContainer", "()V")
                .side(Side.SERVER)
                .entry("containerClose", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.inventory.AnvilMenu", "createResult", "()V")
                .side(Side.SERVER)
                .entry("anvilUpdate", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.inventory.EnchantmentMenu",
                        "clickMenuButton",
                        "(Lnet/minecraft/world/entity/player/Player;I)Z")
                .side(Side.SERVER)
                .entry("enchant", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayer", "teleport",
                        "(Lnet/minecraft/world/level/portal/TeleportTransition;)"
                                + "Lnet/minecraft/server/level/ServerPlayer;")
                .side(Side.SERVER)
                .entry("playerTeleport", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayer",
                        "findRespawnPositionAndUseSpawnBlock",
                        "(ZLnet/minecraft/world/level/portal/"
                                + "TeleportTransition$PostTeleportTransition;)"
                                + "Lnet/minecraft/world/level/portal/TeleportTransition;")
                .side(Side.SERVER)
                .required(false)
                .entry("playerRespawn", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayer",
                        "findRespawnPositionAndUseSpawnBlock",
                        "(ZLnet/minecraft/world/level/portal/"
                                + "TeleportTransition$PostTeleportTransition;)"
                                + "Lnet/minecraft/world/level/portal/TeleportTransition;")
                .side(Side.SERVER)
                .required(false)
                .priority(10)
                .action(HookSpec.Action.RETURN_VALUE)
                .entry("playerRespawnTarget", THIS, ARG1)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.level.storage.loot.LootTable",
                        "getRandomItems",
                        "(Lnet/minecraft/world/level/storage/loot/LootParams;J)"
                                + "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;")
                .side(Side.SERVER)
                .required(false)
                .entry("lootTable", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.MinecraftServer", "reloadResources",
                        "(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;")
                .side(Side.SERVER)
                .entry("serverResourceReloadStart", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.players.PlayerList", "reloadResources", "()V")
                .side(Side.SERVER)
                .exit("serverResourceReloadFinished", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.commands.Commands", "<init>", HookSpec.ANY_DESCRIPTOR)
                .side(Side.BOTH)
                .exit("commandRegister", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayer", "tick", "()V")
                .side(Side.SERVER)
                .entry("playerTickEnter", THIS)
                .exit("playerTickExit", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.players.PlayerList", "broadcastChatMessage",
                        "(Lnet/minecraft/network/chat/PlayerChatMessage;"
                                + "Lnet/minecraft/server/level/ServerPlayer;"
                                + "Lnet/minecraft/network/chat/ChatType$Bound;)V")
                .side(Side.SERVER)
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("chat", THIS, ARG0, ARG1)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.MinecraftServer", "saveEverything",
                        "(ZZZ)Z")
                .side(Side.SERVER)
                .entry("serverSaveStart", THIS)
                .exit("serverSaveFinished", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerLevel", "save",
                        "(Lnet/minecraft/util/ProgressListener;ZZ)V")
                .side(Side.SERVER)
                .entry("levelSaveStart", THIS)
                .exit("levelSaveFinished", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.player.Player", "interactOn",
                        "(Lnet/minecraft/world/entity/Entity;"
                                + "Lnet/minecraft/world/InteractionHand;"
                                + "Lnet/minecraft/world/phys/Vec3;)"
                                + "Lnet/minecraft/world/InteractionResult;")
                .side(Side.SERVER)
                .entry("entityInteract", THIS, ARG0, ARG1, ARG2)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.player.Player", "attack",
                        "(Lnet/minecraft/world/entity/Entity;)V")
                .side(Side.SERVER)
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("entityAttack", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayerGameMode", "destroyBlock",
                        "(Lnet/minecraft/core/BlockPos;)Z")
                .side(Side.SERVER)
                .action(HookSpec.Action.CANCEL_IF_FALSE)
                .entry("blockBreak", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.item.BlockItem", "place",
                        "(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
                                + "Lnet/minecraft/world/InteractionResult;")
                .side(Side.SERVER)
                .action(HookSpec.Action.RETURN_VALUE)
                .entry("blockPlace", ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.LivingEntity", "completeUsingItem",
                        "()V")
                .side(Side.SERVER)
                .entry("itemUseFinish", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.LivingEntity", "releaseUsingItem",
                        "()V")
                .side(Side.SERVER)
                .entry("itemRelease", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.projectile.Projectile", "shoot",
                        "(DDDFF)V")
                .side(Side.SERVER)
                .entry("projectileLaunch", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.client.player.LocalPlayer", "tick", "()V")
                .side(Side.CLIENT)
                .entry("clientPlayerTickEnter", THIS)
                .exit("clientPlayerTickExit", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerLevel", "tickNonPassenger",
                        "(Lnet/minecraft/world/entity/Entity;)V")
                .side(Side.SERVER)
                .entry("serverEntityTick", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.client.multiplayer.ClientLevel", "tickNonPassenger",
                        "(Lnet/minecraft/world/entity/Entity;)V")
                .side(Side.CLIENT)
                .entry("clientEntityTick", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerLevel", "startTickingChunk",
                        "(Lnet/minecraft/world/level/chunk/LevelChunk;)V")
                .side(Side.SERVER)
                .entry("chunkLoad", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerLevel", "unload",
                        "(Lnet/minecraft/world/level/chunk/LevelChunk;)V")
                .side(Side.SERVER)
                .entry("chunkUnload", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.Entity", "remove",
                        "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V")
                .side(Side.SERVER)
                .entry("entityRemoval", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.level.ServerPlayer", "doCloseContainer",
                        "()V")
                .side(Side.SERVER)
                .entry("containerClosed", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.level.block.entity.BlockEntity",
                        "loadWithComponents",
                        "(Lnet/minecraft/world/level/storage/ValueInput;)V")
                .side(Side.SERVER)
                .entry("blockEntityLoad", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.level.block.entity.BlockEntity",
                        "saveWithFullMetadata",
                        "(Lnet/minecraft/world/level/storage/ValueOutput;)V")
                .side(Side.SERVER)
                .entry("blockEntitySave", THIS)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.level.block.entity.BlockEntity",
                        "loadWithComponents",
                        "(Lnet/minecraft/world/level/storage/ValueInput;)V")
                .side(Side.SERVER)
                .priority(-10)
                .id("net.minecraft.world.level.block.entity.BlockEntity"
                        + "#loadWithComponents(Lnet/minecraft/world/level/storage/"
                        + "ValueInput;)V#capabilities")
                .entry("blockEntityPersistenceLoad", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.level.block.entity.BlockEntity",
                        "saveWithFullMetadata",
                        "(Lnet/minecraft/world/level/storage/ValueOutput;)V")
                .side(Side.SERVER)
                .priority(10)
                .id("net.minecraft.world.level.block.entity.BlockEntity"
                        + "#saveWithFullMetadata(Lnet/minecraft/world/level/storage/"
                        + "ValueOutput;)V#capabilities")
                .entry("blockEntityPersistenceSave", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.Entity", "saveWithoutId",
                        "(Lnet/minecraft/world/level/storage/ValueOutput;)V")
                .side(Side.SERVER)
                .entry("entitySave", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.world.entity.Entity", "load",
                        "(Lnet/minecraft/world/level/storage/ValueInput;)V")
                .side(Side.SERVER)
                .entry("entityLoad", THIS, ARG0)
                .build());
        specs.add(HookSpec.method("net.minecraft.server.players.PlayerList", "respawn",
                        "(Lnet/minecraft/server/level/ServerPlayer;Z"
                                + "Lnet/minecraft/world/entity/Entity$RemovalReason;)"
                                + "Lnet/minecraft/server/level/ServerPlayer;")
                .side(Side.SERVER)
                .entry("playerRespawnAction", THIS, ARG0, ARG2)
                .build());
        return List.copyOf(specs);
    }

    /**
     * A class writer that computes frames without loading the class currently
     * being transformed. Loading the in-flight class from inside the
     * transformer causes the JVM to report a duplicate class definition for
     * that class, so hierarchy queries read class bytes when possible and
     * only fall back to reflection for other, safely loadable types.
     */
    private static final class SafeClassWriter extends ClassWriter
    {
        private static final ClassInfo MISSING = new ClassInfo(false, null, List.of());

        private final ClassLoader loader;
        private final String currentClass;
        private final byte[] currentBytes;
        private final Map<String, ClassInfo> hierarchy = new ConcurrentHashMap<>();

        private SafeClassWriter(int flags, ClassLoader loader, String currentClass,
                                byte[] currentBytes)
        {
            super(flags);
            this.loader = loader == null ? GameHookTransformer.class.getClassLoader() : loader;
            this.currentClass = currentClass;
            this.currentBytes = currentBytes;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2)
        {
            try
            {
                if (type1.equals(type2))
                {
                    return type1;
                }
                if (isAssignable(type1, type2))
                {
                    return type2;
                }
                if (isAssignable(type2, type1))
                {
                    return type1;
                }
                ClassInfo left = info(type1);
                ClassInfo right = info(type2);
                if (left == null || right == null || left.isInterface() || right.isInterface())
                {
                    return "java/lang/Object";
                }
                String current = left.superName();
                while (current != null && !"java/lang/Object".equals(current))
                {
                    if (isAssignable(type2, current))
                    {
                        return current;
                    }
                    ClassInfo parent = info(current);
                    current = parent == null ? null : parent.superName();
                }
                return "java/lang/Object";
            }
            catch (Throwable failure)
            {
                return "java/lang/Object";
            }
        }

        private boolean isAssignable(String from, String to)
        {
            return from.equals(to) || supertypes(from).contains(to);
        }

        private Set<String> supertypes(String name)
        {
            Set<String> seen = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(name);
            while (!queue.isEmpty())
            {
                String current = queue.removeFirst();
                if (!seen.add(current))
                {
                    continue;
                }
                ClassInfo info = info(current);
                if (info == null)
                {
                    continue;
                }
                if (info.superName() != null)
                {
                    queue.add(info.superName());
                }
                queue.addAll(info.interfaces());
            }
            return seen;
        }

        private ClassInfo info(String name)
        {
            if (name == null)
            {
                return null;
            }
            ClassInfo cached = hierarchy.get(name);
            if (cached != null)
            {
                return cached == MISSING ? null : cached;
            }
            ClassInfo resolved = loadInfo(name);
            hierarchy.put(name, resolved == null ? MISSING : resolved);
            return resolved;
        }

        private ClassInfo loadInfo(String name)
        {
            byte[] bytes = classBytes(name);
            if (bytes != null)
            {
                try
                {
                    ClassReader reader = new ClassReader(bytes);
                    return new ClassInfo((reader.getAccess() & Opcodes.ACC_INTERFACE) != 0,
                            reader.getSuperName(), List.of(reader.getInterfaces()));
                }
                catch (Throwable failure)
                {
                    return null;
                }
            }
            if (name.equals(currentClass))
            {
                return null;
            }
            try
            {
                Class<?> type = Class.forName(name.replace('/', '.'), false, loader);
                Class<?> superType = type.getSuperclass();
                List<String> interfaces = new ArrayList<>();
                for (Class<?> implemented : type.getInterfaces())
                {
                    interfaces.add(implemented.getName().replace('.', '/'));
                }
                return new ClassInfo(type.isInterface(),
                        superType == null ? null : superType.getName().replace('.', '/'),
                        List.copyOf(interfaces));
            }
            catch (Throwable failure)
            {
                return null;
            }
        }

        private byte[] classBytes(String name)
        {
            if (name.equals(currentClass) && currentBytes != null)
            {
                return currentBytes;
            }
            try (InputStream in = loader.getResourceAsStream(name + ".class"))
            {
                if (in != null)
                {
                    return in.readAllBytes();
                }
            }
            catch (Throwable failure)
            {
                // fall through to the system loader
            }
            try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class"))
            {
                if (in != null)
                {
                    return in.readAllBytes();
                }
            }
            catch (Throwable failure)
            {
                // fall through to reflection
            }
            return null;
        }

        private record ClassInfo(boolean isInterface, String superName, List<String> interfaces)
        {
        }
    }
}
