package net.weaver.core.at;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small access-transformer parser and class visitor.
 *
 * <p>Supported lines:
 * <pre>
 *   public net.minecraft.world.food.FoodData
 *   public net.minecraft.world.food.FoodData fieldName
 *   public net.minecraft.world.food.FoodData methodName
 *   public net.minecraft.world.food.FoodData methodName(I)V
 *   public-f net.minecraft.world.food.FoodData fieldName
 * </pre>
 * Access tokens: {@code public}, {@code protected}, {@code private}, and their
 * {@code -f} variants (also written {@code +f}).
 *
 * <p>A member with a descriptor ({@code methodName(I)V}) is that one method. A bare
 * member name is the field with that name; when the class has no field with that
 * name, it is every method with that name, so {@code methodName} opens all its
 * overloads. A field rule never changes a method that shares its name.</p>
 */
public final class AccessTransformer {

    public enum Access {
        PUBLIC,
        PROTECTED,
        PRIVATE,
        PUBLIC_F,
        PROTECTED_F,
        PRIVATE_F;

        int apply(int access) {
            int result = access;
            switch (this) {
                case PUBLIC, PUBLIC_F -> result = (result & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED))
                        | Opcodes.ACC_PUBLIC;
                case PROTECTED, PROTECTED_F -> result = (result & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE))
                        | Opcodes.ACC_PROTECTED;
                case PRIVATE, PRIVATE_F -> result = (result & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED))
                        | Opcodes.ACC_PRIVATE;
            }
            if (this == PUBLIC_F || this == PROTECTED_F || this == PRIVATE_F) {
                result &= ~Opcodes.ACC_FINAL;
            }
            return result;
        }
    }

    public record Rule(Access access, String member, String descriptor, boolean method) {
    }

    private final Map<String, List<Rule>> rules;

    private AccessTransformer(Map<String, List<Rule>> rules) {
        this.rules = rules;
    }

    public static AccessTransformer parse(String text) {
        Map<String, List<Rule>> rules = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return new AccessTransformer(rules);
        }
        for (String raw : text.split("\\R")) {
            String line = raw;
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            Access access = access(parts[0]);
            if (access == null) {
                continue;
            }
            String className = parts[1].replace('/', '.');
            List<Rule> list = rules.computeIfAbsent(className, key -> new ArrayList<>());
            if (parts.length == 2) {
                list.add(new Rule(access, null, null, false));
                continue;
            }
            String member = parts[2];
            if (member.indexOf('(') >= 0) {
                int open = member.indexOf('(');
                list.add(new Rule(access, member.substring(0, open), member.substring(open), true));
            } else {
                list.add(new Rule(access, member, null, false));
            }
        }
        return new AccessTransformer(rules);
    }

    private static Access access(String token) {
        String value = token.toLowerCase(Locale.ROOT).replace("+f", "-f");
        return switch (value) {
            case "public" -> Access.PUBLIC;
            case "protected" -> Access.PROTECTED;
            case "private" -> Access.PRIVATE;
            case "public-f" -> Access.PUBLIC_F;
            case "protected-f" -> Access.PROTECTED_F;
            case "private-f" -> Access.PRIVATE_F;
            default -> null;
        };
    }

    public boolean hasRules() {
        return !rules.isEmpty();
    }

    public boolean hasRulesFor(String dottedClassName) {
        return rules.containsKey(dottedClassName);
    }

    public int size() {
        int count = 0;
        for (List<Rule> list : rules.values()) {
            count += list.size();
        }
        return count;
    }

    public byte[] apply(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            private String className;
            // ASM visits every field before any method, so this is complete when methods come.
            private final java.util.Set<String> fieldNames = new java.util.HashSet<>();

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                className = name.replace('/', '.');
                super.visit(version, applyClass(className, access), name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                fieldNames.add(name);
                return super.visitField(applyMember(className, name, descriptor, false, false, access),
                        name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return super.visitMethod(applyMember(className, name, descriptor, true, fieldNames.contains(name), access),
                        name, descriptor, signature, exceptions);
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private int applyClass(String className, int access) {
        int result = access;
        for (Rule rule : rules.getOrDefault(className, List.of())) {
            if (rule.member() == null) {
                result = rule.access().apply(result);
            }
        }
        return result;
    }

    private int applyMember(String className, String name, String descriptor,
                            boolean method, boolean fieldWithSameName, int access) {
        int result = access;
        for (Rule rule : rules.getOrDefault(className, List.of())) {
            if (rule.member() == null || !rule.member().equals(name)) {
                continue;
            }
            // A name with a descriptor is one method. A bare name is the field with that
            // name; when the class has no such field, it is every method with that name
            // (all overloads). A bare-name rule written for a field (other loaders' files)
            // therefore never changes a method that shares the name, such as a record
            // accessor.
            if (rule.descriptor() != null && (!method || !rule.descriptor().equals(descriptor))) {
                continue;
            }
            if (rule.descriptor() == null && method && fieldWithSameName) {
                continue;
            }
            result = rule.access().apply(result);
        }
        return result;
    }
}
