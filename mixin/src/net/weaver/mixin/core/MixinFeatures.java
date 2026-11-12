package net.weaver.mixin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Static feature census of one config's mixin classes, read from bytecode without loading
 * anything.
 *
 * <p>Two jobs:
 * <ul>
 *   <li>evidence — every config emits one {@code MIXIN FEATURES} marker stating exactly which
 *       Mixin / MixinExtras facilities it uses, so coverage claims are measured, not asserted;</li>
 *   <li>fail-fast — a mixin that uses a facility this runtime cannot provide (MixinExtras injectors
 *       when the extras bootstrap failed, an interface-mixin shape the engine rejects, an
 *       injector config resource that does not exist) is refused at arm time with a precise reason
 *       instead of failing later on a random target class.</li>
 * </ul>
 */
public final class MixinFeatures {

    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
    private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    private static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
    private static final String FINAL = "Lorg/spongepowered/asm/mixin/Final;";
    private static final String MUTABLE = "Lorg/spongepowered/asm/mixin/Mutable;";
    private static final String WRAP_OPERATION =
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    // MixinExtras 0.5 ships WrapWithCondition as injector.v2 (current) and injector (v1, deprecated).
    private static final String WRAP_WITH_CONDITION =
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;";
    private static final String WRAP_WITH_CONDITION_V1 =
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;";
    private static final String MODIFY_EXPRESSION_VALUE =
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
    private static final String MODIFY_RECEIVER =
            "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;";
    private static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";
    private static final String SHARE = "Lcom/llamalad7/mixinextras/sugar/Share;";
    private static final String EXTRAS_PREFIX = "Lcom/llamalad7/mixinextras/";

    /**
     * Counts for one config. All values are numbers of annotations/classes, so a marker can state
     * them without a second scan.
     */
    public record Census(
            int mixins,
            int accessors,
            int invokers,
            int injects,
            int constructorInjects,
            int redirects,
            int modifyArgs,
            int modifyArgsMultiple,
            int modifyVariables,
            int modifyConstants,
            int overwrites,
            int shadows,
            int wrapOperations,
            int wrapWithConditions,
            int modifyExpressionValues,
            int modifyReceivers,
            int locals,
            int shares,
            int extrasAnnotations,
            int interfaceMixins,
            int innerMixins,
            int unique,
            int mutable,
            int finalFields,
            boolean declaredPlugin,
            boolean extrasUsed) {

        /** Marker-safe {@code key=value} list (stable order, no spaces). */
        public String fields() {
            return "mixins=" + this.mixins
                    + " accessors=" + this.accessors
                    + " invokers=" + this.invokers
                    + " inject=" + this.injects + " ctor_inject=" + this.constructorInjects
                    + " redirect=" + this.redirects
                    + " modifyarg=" + this.modifyArgs
                    + " modifyargs=" + this.modifyArgsMultiple
                    + " modifyvariable=" + this.modifyVariables
                    + " modifyconstant=" + this.modifyConstants
                    + " overwrite=" + this.overwrites
                    + " shadow=" + this.shadows
                    + " wrapoperation=" + this.wrapOperations
                    + " wrapwithcondition=" + this.wrapWithConditions
                    + " modifyexpressionvalue=" + this.modifyExpressionValues
                    + " modifyreceiver=" + this.modifyReceivers
                    + " local=" + this.locals + " share=" + this.shares
                    + " extras=" + this.extrasAnnotations
                    + " interface_mixins=" + this.interfaceMixins
                    + " inner_mixins=" + this.innerMixins
                    + " unique=" + this.unique + " mutable=" + this.mutable
                    + " final=" + this.finalFields
                    + " plugin=" + this.declaredPlugin;
        }
    }

    private MixinFeatures() {
    }

    /**
     * One injection handler declared by a mixin class, with the {@code require} it declares
     * ({@code -1} = inherit the config's {@code injectors.defaultRequire}).
     */
    public record Handler(String name, int require) {
    }

    /**
     * Every injection handler declared by one mixin class ({@code @Inject}, {@code @Redirect},
     * {@code @Modify*}, MixinExtras wrappers), with its declared {@code require}.
     *
     * <p>A handler that applies leaves a {@code handler$…$<name>} method in the woven target, so the
     * names are the expectation an audit compares the woven class against — Mixin itself reports a
     * tolerated ({@code require = 0}) miss to nobody, and it is the engine that makes a
     * {@code require = 1} handler tolerant, which is exactly when a silent miss becomes possible.
     */
    public static List<Handler> handlerList(String mixinClass, ClassLoader loader) {
        ClassNode node = ConfigParser.readNode(mixinClass, loader);
        if (node == null || node.methods == null) {
            return Collections.emptyList();
        }
        List<Handler> handlers = new ArrayList<>();
        for (MethodNode method : node.methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (method.visibleAnnotations != null) {
                annotations.addAll(method.visibleAnnotations);
            }
            if (method.invisibleAnnotations != null) {
                annotations.addAll(method.invisibleAnnotations);
            }
            for (AnnotationNode annotation : annotations) {
                if (isInjection(annotation.desc)) {
                    handlers.add(new Handler(method.name, requireOf(annotation)));
                    break;
                }
            }
        }
        return handlers;
    }

    /** {@code require} from an injection annotation; {@code -1} when it is not declared. */
    private static int requireOf(AnnotationNode annotation) {
        if (annotation.values == null) {
            return -1;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if ("require".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof Integer) {
                return (Integer) annotation.values.get(i + 1);
            }
        }
        return -1;
    }

    /** True for every annotation that becomes a woven callback method. */
    private static boolean isInjection(String desc) {
        return INJECT.equals(desc)
                || REDIRECT.equals(desc)
                || MODIFY_ARG.equals(desc)
                || MODIFY_ARGS.equals(desc)
                || MODIFY_VARIABLE.equals(desc)
                || MODIFY_CONSTANT.equals(desc)
                || WRAP_OPERATION.equals(desc)
                || WRAP_WITH_CONDITION.equals(desc)
                || WRAP_WITH_CONDITION_V1.equals(desc)
                || MODIFY_EXPRESSION_VALUE.equals(desc)
                || MODIFY_RECEIVER.equals(desc);
    }

    /** Scans every armed mixin class of one config. Never throws: unreadable classes are skipped. */
    public static Census scan(List<String> mixinClasses, ClassLoader loader, boolean declaredPlugin) {
        int accessors = 0;
        int invokers = 0;
        int injects = 0;
        int ctorInjects = 0;
        int redirects = 0;
        int modifyArgs = 0;
        int modifyArgsMultiple = 0;
        int modifyVariables = 0;
        int modifyConstants = 0;
        int overwrites = 0;
        int shadows = 0;
        int wrapOperations = 0;
        int wrapWithConditions = 0;
        int modifyExpressionValues = 0;
        int modifyReceivers = 0;
        int locals = 0;
        int shares = 0;
        int extras = 0;
        int interfaceMixins = 0;
        int innerMixins = 0;
        int unique = 0;
        int mutable = 0;
        int finalFields = 0;
        Set<String> scanned = new LinkedHashSet<>();
        for (String mixin : mixinClasses) {
            ClassNode node = ConfigParser.readNode(mixin, loader);
            if (node == null || !scanned.add(node.name)) {
                continue;
            }
            if ((node.access & Opcodes.ACC_INTERFACE) != 0) {
                interfaceMixins++;
            }
            if (node.name.indexOf('$') > 0) {
                innerMixins++;
            }
            if (hasAnnotation(node.visibleAnnotations, UNIQUE)
                    || hasAnnotation(node.invisibleAnnotations, UNIQUE)) {
                unique++;
            }
            if (hasAnnotation(node.visibleAnnotations, MUTABLE)
                    || hasAnnotation(node.invisibleAnnotations, MUTABLE)) {
                mutable++;
            }
            List<AnnotationNode> classAnnotations = new ArrayList<>();
            if (node.visibleAnnotations != null) {
                classAnnotations.addAll(node.visibleAnnotations);
            }
            if (node.invisibleAnnotations != null) {
                classAnnotations.addAll(node.invisibleAnnotations);
            }
            extras += countExtras(classAnnotations);
            if (node.fields != null) {
                for (FieldNode field : node.fields) {
                    if (hasAnnotation(field.visibleAnnotations, FINAL)
                            || hasAnnotation(field.invisibleAnnotations, FINAL)) {
                        finalFields++;
                    }
                    if (hasAnnotation(field.visibleAnnotations, SHADOW)
                            || hasAnnotation(field.invisibleAnnotations, SHADOW)) {
                        shadows++;
                    }
                }
            }
            if (node.methods == null) {
                continue;
            }
            for (MethodNode method : node.methods) {
                List<AnnotationNode> annotations = new ArrayList<>();
                if (method.visibleAnnotations != null) {
                    annotations.addAll(method.visibleAnnotations);
                }
                if (method.invisibleAnnotations != null) {
                    annotations.addAll(method.invisibleAnnotations);
                }
                for (AnnotationNode annotation : annotations) {
                    String desc = annotation.desc;
                    if (ACCESSOR.equals(desc)) {
                        accessors++;
                    } else if (INVOKER.equals(desc)) {
                        invokers++;
                    } else if (INJECT.equals(desc)) {
                        if (targetsConstructor(annotation)) {
                            ctorInjects++;
                        } else {
                            injects++;
                        }
                    } else if (REDIRECT.equals(desc)) {
                        redirects++;
                    } else if (MODIFY_ARG.equals(desc)) {
                        modifyArgs++;
                    } else if (MODIFY_ARGS.equals(desc)) {
                        modifyArgsMultiple++;
                    } else if (MODIFY_VARIABLE.equals(desc)) {
                        modifyVariables++;
                    } else if (MODIFY_CONSTANT.equals(desc)) {
                        modifyConstants++;
                    } else if (OVERWRITE.equals(desc)) {
                        overwrites++;
                    } else if (SHADOW.equals(desc)) {
                        shadows++;
                    } else if (WRAP_OPERATION.equals(desc)) {
                        wrapOperations++;
                    } else if (WRAP_WITH_CONDITION.equals(desc) || WRAP_WITH_CONDITION_V1.equals(desc)) {
                        wrapWithConditions++;
                    } else if (MODIFY_EXPRESSION_VALUE.equals(desc)) {
                        modifyExpressionValues++;
                    } else if (MODIFY_RECEIVER.equals(desc)) {
                        modifyReceivers++;
                    } else if (LOCAL.equals(desc)) {
                        locals++;
                    } else if (SHARE.equals(desc)) {
                        shares++;
                    }
                }
                extras += countExtras(annotations);
            }
        }
        return new Census(mixinClasses.size(), accessors, invokers, injects, ctorInjects, redirects,
                modifyArgs, modifyArgsMultiple, modifyVariables, modifyConstants, overwrites,
                shadows, wrapOperations, wrapWithConditions, modifyExpressionValues,
                modifyReceivers, locals, shares, extras, interfaceMixins, innerMixins, unique,
                mutable, finalFields, declaredPlugin, extras > 0);
    }

    private static int countExtras(List<AnnotationNode> annotations) {
        int count = 0;
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc != null && annotation.desc.startsWith(EXTRAS_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    /**
     * True when an injection annotation targets a constructor: the handler method is named after
     * the mod's handler, so the {@code method} attribute is the only reliable signal.
     */
    private static boolean targetsConstructor(AnnotationNode annotation) {
        if (annotation.values == null) {
            return false;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (!"method".equals(String.valueOf(annotation.values.get(i)))) {
                continue;
            }
            Object value = annotation.values.get(i + 1);
            if (value instanceof String text) {
                return text.startsWith("<init>");
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String text && text.startsWith("<init>")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (desc.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }
}
