package net.weaver.core.hook;

import net.weaver.api.event.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Describes one method hook: which game method to instrument and which static calls to inject. */
public final class HookSpec
{
    /** The value contract of an action call injected at method entry. */
    public enum Action
    {
        /** The injected call returns nothing and the target method runs normally. */
        VOID,
        /** The injected call returns a boolean; a false value cancels the target method. */
        CANCEL_IF_FALSE,
        /**
         * The injected call returns the target method's value. A non-null
         * value replaces the target method's result and the original body
         * does not run; a null value means "no override" and the original
         * method body runs normally.
         */
        RETURN_VALUE
    }

    /** The JVM stack contract of one injected static call. */
    public enum Kind
    {
        /** The call returns {@code void}. */
        VOID,
        /** The call returns {@code boolean} and gates the target method. */
        CANCEL_IF_FALSE,
        /** The call returns the target method's return type. */
        RETURN_VALUE
    }

    public enum Arg
    {
        THIS,
        ARG0,
        ARG1,
        ARG2,
        ARG3,
        ARG4
    }

    public static final String ANY_DESCRIPTOR = "*";

    private final String className;
    private final String methodName;
    private final String descriptor;
    private final String id;
    private final List<HookCall> entryCalls;
    private final List<HookCall> exitCalls;
    private final List<HookCall> throwCalls;
    private final boolean required;
    private final Side side;
    private final int priority;
    private final Action action;

    private HookSpec(Builder builder)
    {
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.descriptor = builder.descriptor;
        this.id = builder.id == null ? defaultId() : builder.id;
        this.entryCalls = builder.finalizeEntries();
        this.exitCalls = List.copyOf(builder.exitCalls);
        this.throwCalls = List.copyOf(builder.throwCalls);
        this.required = builder.required;
        this.side = builder.side;
        this.priority = builder.priority;
        this.action = builder.action;
    }

    public static Builder method(String className, String methodName, String descriptor)
    {
        return new Builder(className, methodName, descriptor);
    }

    public static Builder of(String className, String methodName, String descriptor)
    {
        return method(className, methodName, descriptor);
    }

    /**
     * Creates a spec without builder validation. This package-private factory
     * exists only for transformer guardrail tests that must represent legacy
     * or deserialized specs the builder would reject. Production code must use
     * {@link #method(String, String, String)}.
     *
     * @param className  the target class name
     * @param methodName the target method name
     * @param descriptor the target descriptor
     * @param action     the entry action contract
     * @param entryCalls the entry calls
     * @param exitCalls  the exit calls
     * @param throwCalls the throw calls
     * @return an unvalidated spec
     */
    static HookSpec testUnsafe(String className, String methodName, String descriptor, Action action,
                               List<HookCall> entryCalls, List<HookCall> exitCalls,
                               List<HookCall> throwCalls)
    {
        Builder builder = new Builder(className, methodName, descriptor);
        builder.action = Objects.requireNonNull(action, "action");
        builder.entryCalls.addAll(Objects.requireNonNull(entryCalls, "entryCalls"));
        builder.exitCalls.addAll(Objects.requireNonNull(exitCalls, "exitCalls"));
        builder.throwCalls.addAll(Objects.requireNonNull(throwCalls, "throwCalls"));
        if (builder.action != Action.VOID && builder.entryCalls.size() != 1)
        {
            throw new IllegalArgumentException(
                    "testUnsafe non-void actions require exactly one entry call");
        }
        return new HookSpec(builder);
    }

    public String className()
    {
        return className;
    }

    public String methodName()
    {
        return methodName;
    }

    public String descriptor()
    {
        return descriptor;
    }

    public List<HookCall> entryCalls()
    {
        return entryCalls;
    }

    public List<HookCall> exitCalls()
    {
        return exitCalls;
    }

    /**
     * Calls injected immediately before an {@code ATHROW} in the target method.
     * The throwable stays on the operand stack; the call arguments are pushed
     * above it and popped by the invocation, so the original exception
     * propagates unchanged.
     *
     * @return the immutable throw-call list
     */
    public List<HookCall> throwCalls()
    {
        return throwCalls;
    }

    /** Whether a missing target is a required failure or an expected absence. */
    public boolean required()
    {
        return required;
    }

    /** The side that must be hosted for this spec to be installed. */
    public Side side()
    {
        return side;
    }

    /** Lower values run first when several specs target the same method. */
    public int priority()
    {
        return priority;
    }

    /** What the transformer does with the entry call's value. */
    public Action action()
    {
        return action;
    }

    public String id()
    {
        return id;
    }

    public boolean anyDescriptor()
    {
        return ANY_DESCRIPTOR.equals(descriptor);
    }

    public boolean matches(String name, String methodDescriptor)
    {
        if (!methodName.equals(name))
        {
            return false;
        }
        return anyDescriptor() || descriptor.equals(methodDescriptor);
    }

    /**
     * One injected call. The descriptor is derived from the call kind: a void
     * call ends in {@code V}, a cancelable action call ends in {@code Z}, and a
     * value-returning action call ends in the target method's return
     * descriptor.
     *
     * @param name             the public static hook method name
     * @param args             the arguments to load before the invocation
     * @param kind             the stack contract of the call
     * @param returnDescriptor the target method's return descriptor for
     *                         {@link Kind#RETURN_VALUE}; ignored otherwise
     */
    public record HookCall(String name, List<Arg> args, Kind kind, String returnDescriptor)
    {
        public HookCall
        {
            Objects.requireNonNull(name, "name");
            args = List.copyOf(args);
            kind = kind == null ? Kind.VOID : kind;
            returnDescriptor = returnDescriptor == null ? "V" : returnDescriptor;
        }

        /** Creates a void call, kept for source compatibility. */
        public HookCall(String name, List<Arg> args)
        {
            this(name, args, Kind.VOID, "V");
        }

        /** The emitted JVM descriptor for this call. */
        public String descriptor()
        {
            String parameters = "(" + "Ljava/lang/Object;".repeat(args.size()) + ")";
            return switch (kind)
            {
                case VOID -> parameters + "V";
                case CANCEL_IF_FALSE -> parameters + "Z";
                case RETURN_VALUE -> parameters + returnDescriptor;
            };
        }
    }

    public static final class Builder
    {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final List<HookCall> entryCalls = new ArrayList<>();
        private final List<HookCall> exitCalls = new ArrayList<>();
        private final List<HookCall> throwCalls = new ArrayList<>();
        private boolean required = true;
        private Side side = Side.BOTH;
        private int priority;
        private Action action = Action.VOID;
        private String id;

        private Builder(String className, String methodName, String descriptor)
        {
            this.className = normalize(className);
            this.methodName = requireText(methodName, "methodName");
            this.descriptor = requireText(descriptor, "descriptor");
            if (ANY_DESCRIPTOR.equals(this.descriptor) && !"<init>".equals(this.methodName))
            {
                throw new IllegalArgumentException("A wildcard descriptor is only valid for <init>");
            }
        }

        public Builder entry(String hookMethod, Arg... args)
        {
            entryCalls.add(call(hookMethod, args));
            return this;
        }

        public Builder exit(String hookMethod, Arg... args)
        {
            exitCalls.add(call(hookMethod, args));
            return this;
        }

        /**
         * Adds a call injected immediately before an {@code ATHROW} in the
         * target method. The call receives only its declared arguments; the
         * throwable remains on the stack and is never passed to the hook.
         *
         * @param hookMethod the public static hook method name
         * @param args       the arguments to load before the invocation
         * @return this builder
         */
        public Builder throwHook(String hookMethod, Arg... args)
        {
            throwCalls.add(call(hookMethod, args));
            return this;
        }

        /**
         * Marks whether the target is required. Required targets that are absent or invalid are
         * reported in the hook audit; optional targets are reported as skipped. Defaults to true.
         */
        public Builder required(boolean required)
        {
            this.required = required;
            return this;
        }

        /**
         * Restricts the spec to one hosted side. Defaults to {@link Side#BOTH}.
         *
         * @param side the side that must be hosted
         * @return this builder
         */
        public Builder side(Side side)
        {
            this.side = Objects.requireNonNull(side, "side");
            return this;
        }

        /**
         * Sets the ordering priority for specs that target the same method.
         * Lower values run first. Defaults to zero.
         *
         * @param priority the ordering value
         * @return this builder
         */
        public Builder priority(int priority)
        {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the action performed with the entry call's value. A non-void
         * action requires exactly one entry call, which becomes the action
         * call. Exit and throw observation calls are still allowed.
         *
         * <p>{@link Action#RETURN_VALUE} replaces the target's result only
         * when the hook call returns a non-null value; a null return means
         * "no override" and the original method body runs.
         *
         * @param action the action contract
         * @return this builder
         */
        public Builder action(Action action)
        {
            this.action = Objects.requireNonNull(action, "action");
            return this;
        }

        /**
         * Overrides the diagnostic spec id. Defaults to
         * {@code className#methodName+descriptor}.
         *
         * @param id the explicit id
         * @return this builder
         */
        public Builder id(String id)
        {
            this.id = id;
            return this;
        }

        /**
         * Builds the immutable spec after validating the call and action
         * contract.
         *
         * <p>Constructors ({@code <init>}) accept only {@link Action#VOID}
         * entry and exit calls. Non-void actions and throw hooks are rejected
         * because {@code this} is uninitialized and returning or observing a
         * throw before the super call is unsafe.
         *
         * <p>{@link Action#RETURN_VALUE} replaces the target's result only
         * when the hook call returns a non-null value; a null return means
         * "no override" and the original method body runs.
         *
         * @return the validated spec
         * @throws IllegalStateException when the spec has no calls, a non-void
         *         action targets a constructor, a throw hook targets a
         *         constructor, an action does not have exactly one entry call,
         *         or {@link Action#RETURN_VALUE} targets a void, primitive, or
         *         wildcard descriptor
         * @throws IllegalArgumentException when the id is blank
         */
        public HookSpec build()
        {
            if (entryCalls.isEmpty() && exitCalls.isEmpty() && throwCalls.isEmpty())
            {
                throw new IllegalStateException("Hook has no calls: " + className + "#" + methodName);
            }
            if ("<init>".equals(methodName))
            {
                if (action == Action.RETURN_VALUE)
                {
                    throw new IllegalStateException(
                            "RETURN_VALUE is not supported on a constructor: "
                                    + className + "#" + methodName);
                }
                if (action != Action.VOID)
                {
                    throw new IllegalStateException("Action " + action
                            + " is not supported on a constructor: " + className + "#" + methodName);
                }
                if (!throwCalls.isEmpty())
                {
                    throw new IllegalStateException(
                            "Constructor throw hooks are not supported: "
                                    + className + "#" + methodName);
                }
            }
            if (action != Action.VOID && entryCalls.size() != 1)
            {
                throw new IllegalStateException("Action " + action + " requires exactly one entry call: "
                        + className + "#" + methodName);
            }
            if (action == Action.RETURN_VALUE)
            {
                if (ANY_DESCRIPTOR.equals(descriptor))
                {
                    throw new IllegalStateException(
                            "RETURN_VALUE requires a concrete descriptor: "
                                    + className + "#" + methodName);
                }
                String returnDescriptor = returnDescriptor();
                if (!returnDescriptor.startsWith("L") && !returnDescriptor.startsWith("["))
                {
                    throw new IllegalStateException(
                            "RETURN_VALUE requires a reference-returning target: "
                                    + className + "#" + methodName + descriptor);
                }
            }
            if (id != null && id.isBlank())
            {
                throw new IllegalArgumentException("id must not be blank");
            }
            return new HookSpec(this);
        }

        private String returnDescriptor()
        {
            int close = descriptor.lastIndexOf(')');
            if (close < 0 || close + 1 >= descriptor.length())
            {
                throw new IllegalStateException("Invalid method descriptor: " + descriptor);
            }
            return descriptor.substring(close + 1);
        }

        private List<HookCall> finalizeEntries()
        {
            if (action == Action.VOID)
            {
                return List.copyOf(entryCalls);
            }
            HookCall entry = entryCalls.get(0);
            Kind kind = action == Action.CANCEL_IF_FALSE ? Kind.CANCEL_IF_FALSE : Kind.RETURN_VALUE;
            String returnDescriptor = action == Action.RETURN_VALUE ? returnDescriptor() : "V";
            return List.of(new HookCall(entry.name(), entry.args(), kind, returnDescriptor));
        }

        private static HookCall call(String hookMethod, Arg... args)
        {
            Objects.requireNonNull(hookMethod, "hookMethod");
            Objects.requireNonNull(args, "args");
            return new HookCall(hookMethod, List.of(args));
        }
    }

    private String defaultId()
    {
        return className + "#" + methodName + (anyDescriptor() ? ANY_DESCRIPTOR : descriptor);
    }

    private static String normalize(String className)
    {
        String value = requireText(className, "className");
        return value.indexOf('/') >= 0 ? value : value.replace('.', '/');
    }

    private static String requireText(String value, String name)
    {
        Objects.requireNonNull(value, name);
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
