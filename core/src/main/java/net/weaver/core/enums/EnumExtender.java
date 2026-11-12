package net.weaver.core.enums;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds mod constants to enums when they load (see {@code net.weaver.api.EnumExtensions}
 * for the file format). For each constant it adds the field, builds the constant in
 * the static initializer right before {@code $VALUES} is filled, and makes
 * {@code $values()} return the vanilla constants followed by the added ones.
 */
public final class EnumExtender {
    /** One constant to add. Names are internal ({@code net/minecraft/world/item/Rarity}). */
    public record Addition(String modId, String enumName, String constant, String constructor,
                           String factoryClass, String factoryMethod) {
    }

    private static final String HELPER = "net/weaver/api/EnumExtensions";

    private final Map<String, List<Addition>> byEnum;

    public EnumExtender(List<Addition> additions) {
        Map<String, List<Addition>> map = new LinkedHashMap<>();
        for (Addition addition : additions) {
            map.computeIfAbsent(addition.enumName(), ignored -> new ArrayList<>()).add(addition);
        }
        this.byEnum = map;
    }

    public boolean isEmpty() {
        return byEnum.isEmpty();
    }

    public int size() {
        return byEnum.values().stream().mapToInt(List::size).sum();
    }

    public boolean targets(String internalName) {
        return byEnum.containsKey(internalName);
    }

    /**
     * Reads one mod's {@code enumextensions.json}. Entries that are malformed or whose
     * name lacks the mod id prefix are reported through {@code problems} and left out.
     */
    public static List<Addition> parse(String modId, byte[] json, List<String> problems) {
        List<Addition> additions = new ArrayList<>();
        JsonElement root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
        if (!root.isJsonObject() || !root.getAsJsonObject().has("entries")
                || !root.getAsJsonObject().get("entries").isJsonArray()) {
            problems.add(modId + ": enumextensions.json needs an \"entries\" array");
            return additions;
        }
        String prefix = prefix(modId);
        for (JsonElement element : root.getAsJsonObject().getAsJsonArray("entries")) {
            if (!element.isJsonObject()) {
                problems.add(modId + ": an entry is not an object");
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String enumName = text(entry, "enum");
            String constant = text(entry, "name");
            String constructor = text(entry, "constructor");
            JsonObject parameters = entry.has("parameters") && entry.get("parameters").isJsonObject()
                    ? entry.getAsJsonObject("parameters") : null;
            String factoryClass = parameters == null ? null : text(parameters, "class");
            String factoryMethod = parameters == null ? null : text(parameters, "method");
            if (enumName == null || constant == null || constructor == null || factoryClass == null || factoryMethod == null) {
                problems.add(modId + ": an entry needs enum, name, constructor, and parameters.class/method");
                continue;
            }
            if (!constant.startsWith(prefix) || !isIdentifier(constant)) {
                problems.add(modId + ": constant " + constant + " must be a Java name starting with " + prefix);
                continue;
            }
            try {
                Type type = Type.getMethodType(constructor);
                if (type.getReturnType() != Type.VOID_TYPE) {
                    problems.add(modId + ": constructor " + constructor + " must return V");
                    continue;
                }
            } catch (RuntimeException bad) {
                problems.add(modId + ": bad constructor descriptor " + constructor);
                continue;
            }
            additions.add(new Addition(modId, enumName.replace('.', '/'), constant, constructor,
                    factoryClass.replace('.', '/'), factoryMethod));
        }
        return additions;
    }

    /**
     * The prefix every constant of {@code modId} starts with: the id in upper case,
     * with the hyphens mod ids may have (and Java names may not) as underscores.
     */
    public static String prefix(String modId) {
        return modId.toUpperCase(Locale.ROOT).replace('-', '_') + "_";
    }

    /**
     * Adds the constants meant for this class.
     *
     * @return the new class bytes
     * @throws IllegalStateException when the class is not an enum this can extend
     */
    public byte[] apply(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        List<Addition> additions = byEnum.get(node.name);
        if (additions == null || additions.isEmpty()) {
            return bytes;
        }
        if ((node.access & Opcodes.ACC_ENUM) == 0) {
            throw new IllegalStateException(node.name + " is not an enum");
        }
        if ((node.access & Opcodes.ACC_ABSTRACT) != 0) {
            throw new IllegalStateException(node.name + " is abstract (constants with bodies) and cannot be extended");
        }
        String self = "L" + node.name + ";";
        MethodNode clinit = method(node, "<clinit>", "()V");
        MethodNode values = method(node, "$values", "()[" + self);
        if (clinit == null || values == null) {
            throw new IllegalStateException(node.name + " has no $values() initializer");
        }
        MethodInsnNode valuesCall = null;
        for (AbstractInsnNode insn : clinit.instructions) {
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                    && call.owner.equals(node.name) && call.name.equals("$values")) {
                valuesCall = call;
                break;
            }
        }
        if (valuesCall == null) {
            throw new IllegalStateException(node.name + " does not call $values() in its initializer");
        }
        int existing = 0;
        for (FieldNode field : node.fields) {
            if ((field.access & Opcodes.ACC_ENUM) != 0) {
                existing++;
            }
        }
        List<Addition> added = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Addition addition : additions) {
            if (field(node, addition.constant()) != null || !names.add(addition.constant())) {
                System.out.println("[weaver] ENUM EXTENSION SKIPPED " + node.name + "." + addition.constant()
                        + " reason=name-taken mod=" + addition.modId());
                continue;
            }
            String fullConstructor = "(Ljava/lang/String;I" + addition.constructor().substring(1);
            if (method(node, "<init>", fullConstructor) == null) {
                System.out.println("[weaver] ENUM EXTENSION SKIPPED " + node.name + "." + addition.constant()
                        + " reason=no-constructor " + addition.constructor() + " mod=" + addition.modId());
                continue;
            }
            added.add(addition);
        }
        if (added.isEmpty()) {
            return bytes;
        }

        int argumentsLocal = clinit.maxLocals;
        clinit.maxLocals += 1;
        InsnList build = new InsnList();
        for (int i = 0; i < added.size(); i++) {
            Addition addition = added.get(i);
            int ordinal = existing + i;
            node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                    addition.constant(), self, null, null));
            build.add(new LdcInsnNode(node.name));
            build.add(new LdcInsnNode(addition.constant()));
            build.add(pushInt(ordinal));
            build.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "arguments",
                    "(Ljava/lang/String;Ljava/lang/String;I)[Ljava/lang/Object;", false));
            build.add(new VarInsnNode(Opcodes.ASTORE, argumentsLocal));
            build.add(new TypeInsnNode(Opcodes.NEW, node.name));
            build.add(new InsnNode(Opcodes.DUP));
            build.add(new LdcInsnNode(addition.constant()));
            build.add(pushInt(ordinal));
            Type[] parameters = Type.getArgumentTypes(addition.constructor());
            for (int p = 0; p < parameters.length; p++) {
                build.add(new VarInsnNode(Opcodes.ALOAD, argumentsLocal));
                build.add(pushInt(p));
                build.add(new InsnNode(Opcodes.AALOAD));
                unbox(build, parameters[p]);
            }
            build.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, "<init>",
                    "(Ljava/lang/String;I" + addition.constructor().substring(1), false));
            build.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, addition.constant(), self));
        }
        clinit.instructions.insertBefore(valuesCall, build);

        for (AbstractInsnNode insn : values.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.ARETURN) {
                continue;
            }
            InsnList grow = new InsnList();
            grow.add(new InsnNode(Opcodes.DUP));
            grow.add(new InsnNode(Opcodes.ARRAYLENGTH));
            grow.add(pushInt(added.size()));
            grow.add(new InsnNode(Opcodes.IADD));
            grow.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf",
                    "([Ljava/lang/Object;I)[Ljava/lang/Object;", false));
            grow.add(new TypeInsnNode(Opcodes.CHECKCAST, "[" + self));
            for (int i = 0; i < added.size(); i++) {
                grow.add(new InsnNode(Opcodes.DUP));
                grow.add(pushInt(existing + i));
                grow.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, added.get(i).constant(), self));
                grow.add(new InsnNode(Opcodes.AASTORE));
            }
            values.instructions.insertBefore(insn, grow);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        for (Addition addition : added) {
            System.out.println("[weaver] ENUM EXTENSION " + node.name.replace('/', '.') + "." + addition.constant()
                    + " mod=" + addition.modId());
        }
        return writer.toByteArray();
    }

    private static void unbox(InsnList code, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> unboxAs(code, "java/lang/Boolean", "booleanValue", "()Z");
            case Type.BYTE -> unboxAs(code, "java/lang/Number", "byteValue", "()B");
            case Type.SHORT -> unboxAs(code, "java/lang/Number", "shortValue", "()S");
            case Type.CHAR -> unboxAs(code, "java/lang/Character", "charValue", "()C");
            case Type.INT -> unboxAs(code, "java/lang/Number", "intValue", "()I");
            case Type.LONG -> unboxAs(code, "java/lang/Number", "longValue", "()J");
            case Type.FLOAT -> unboxAs(code, "java/lang/Number", "floatValue", "()F");
            case Type.DOUBLE -> unboxAs(code, "java/lang/Number", "doubleValue", "()D");
            default -> code.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
        }
    }

    private static void unboxAs(InsnList code, String owner, String name, String descriptor) {
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, owner));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false));
    }

    private static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + value);
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, value);
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(Opcodes.SIPUSH, value);
        }
        return new LdcInsnNode(value);
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    private static FieldNode field(ClassNode node, String name) {
        for (FieldNode field : node.fields) {
            if (field.name.equals(name)) {
                return field;
            }
        }
        return null;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static boolean isIdentifier(String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
