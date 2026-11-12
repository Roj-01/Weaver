package net.weaver.core.at;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which members a rule opens: a bare name, a name with a descriptor, a field. */
class AccessTransformerMemberTest
{
    private static byte[] sample()
    {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "demo/Sample", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "name", "Ljava/lang/String;", null, null).visitEnd();
        for (String descriptor : new String[] {"()V", "(I)V"})
        {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PRIVATE, "tick", descriptor, null, null);
            method.visitCode();
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 2);
            method.visitEnd();
        }
        MethodVisitor other = writer.visitMethod(Opcodes.ACC_PRIVATE, "other", "()V", null, null);
        other.visitCode();
        other.visitInsn(Opcodes.RETURN);
        other.visitMaxs(0, 1);
        other.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static Map<String, Integer> access(byte[] bytes)
    {
        Map<String, Integer> out = new HashMap<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9)
        {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value)
            {
                out.put(name, access);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions)
            {
                out.put(name + descriptor, access);
                return null;
            }
        }, 0);
        return out;
    }

    @Test
    void bareNameOpensEveryMethodWithThatName()
    {
        Map<String, Integer> result = access(AccessTransformer.parse("public demo.Sample tick").apply(sample()));
        assertEquals(Opcodes.ACC_PUBLIC, result.get("tick()V"));
        assertEquals(Opcodes.ACC_PUBLIC, result.get("tick(I)V"));
        assertEquals(Opcodes.ACC_PRIVATE, result.get("other()V"));
    }

    @Test
    void descriptorOpensOnlyThatMethod()
    {
        Map<String, Integer> result = access(AccessTransformer.parse("public demo.Sample tick(I)V").apply(sample()));
        assertEquals(Opcodes.ACC_PRIVATE, result.get("tick()V"));
        assertEquals(Opcodes.ACC_PUBLIC, result.get("tick(I)V"));
    }

    @Test
    void aFieldRuleNeverChangesAMethodWithTheSameName()
    {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "demo/Holder", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "value", "I", null, null).visitEnd();
        MethodVisitor accessor = writer.visitMethod(Opcodes.ACC_PUBLIC, "value", "()I", null, null);
        accessor.visitCode();
        accessor.visitInsn(Opcodes.ICONST_0);
        accessor.visitInsn(Opcodes.IRETURN);
        accessor.visitMaxs(1, 1);
        accessor.visitEnd();
        writer.visitEnd();
        Map<String, Integer> result = access(AccessTransformer.parse("protected-f demo.Holder value")
                .apply(writer.toByteArray()));
        assertEquals(Opcodes.ACC_PROTECTED, result.get("value"));
        assertEquals(Opcodes.ACC_PUBLIC, result.get("value()I"));
    }

    @Test
    void bareNameStillOpensFieldsAndRemovesFinal()
    {
        Map<String, Integer> result = access(AccessTransformer.parse(
                "public demo.Sample count\npublic-f demo.Sample name").apply(sample()));
        assertEquals(Opcodes.ACC_PUBLIC, result.get("count"));
        assertEquals(Opcodes.ACC_PUBLIC, result.get("name"));
    }
}
