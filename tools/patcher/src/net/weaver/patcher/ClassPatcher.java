package net.weaver.patcher;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Adds the Weaver interfaces and delegating methods to one existing class.
 *
 * <p>The transform is append-only: existing methods and their StackMapTable
 * frames are copied through untouched. New methods are straight-line code, so
 * they need no frames. ClassWriter is constructed with the original reader to
 * reuse the constant pool and only recompute max stack/locals.</p>
 */
final class ClassPatcher {
    private static final Set<String> ADDED_METHOD_NAMES = Set.of(
            PatchSet.GET,
            PatchSet.SET,
            PatchSet.REMOVE,
            PatchSet.HAS,
            PatchSet.PATCH_LEVEL_METHOD
    );

    private ClassPatcher() {
    }

    static PatchedClass patch(byte[] classBytes, PatchSet.Target target) throws PatchException {
        ClassReader reader;
        try {
            reader = new ClassReader(classBytes);
        } catch (RuntimeException e) {
            throw new PatchException("target " + target.className() + " is not a readable class file", e);
        }
        if (!reader.getClassName().equals(target.className())) {
            throw new PatchException("entry " + target.entryName() + " contains class "
                    + reader.getClassName() + " instead of " + target.className());
        }

        List<String> operations = new ArrayList<>();
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        PatchVisitor visitor = new PatchVisitor(writer, target, operations);
        try {
            reader.accept(visitor, 0);
        } catch (PatchFailure failure) {
            throw failure.patchException;
        } catch (RuntimeException e) {
            throw new PatchException("failed to transform " + target.className() + ": " + e.getMessage(), e);
        }
        if (!visitor.sawClass) {
            throw new PatchException("target class was not visited: " + target.className());
        }
        return new PatchedClass(writer.toByteArray(), List.copyOf(operations));
    }

    record PatchedClass(byte[] bytes, List<String> operations) {
    }

    private static final class PatchVisitor extends ClassVisitor {
        private final PatchSet.Target target;
        private final List<String> operations;
        private boolean sawClass;

        private PatchVisitor(ClassWriter writer, PatchSet.Target target, List<String> operations) {
            super(Opcodes.ASM9, writer);
            this.target = target;
            this.operations = operations;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            sawClass = true;
            if ((access & Opcodes.ACC_INTERFACE) != 0) {
                throw fail("target " + name + " is an interface; expected a class");
            }
            if (!name.equals(target.className())) {
                throw fail("visited class " + name + " does not match target " + target.className());
            }

            List<String> existing = interfaces == null ? List.of() : List.of(interfaces);
            if (existing.contains(PatchSet.HOLDER_INTERFACE)) {
                throw fail("target " + name + " already implements " + PatchSet.HOLDER_INTERFACE);
            }
            if (existing.contains(PatchSet.PATCHED_GAME_INTERFACE)) {
                throw fail("target " + name + " already implements " + PatchSet.PATCHED_GAME_INTERFACE);
            }

            List<String> updated = new ArrayList<>(existing);
            updated.add(PatchSet.HOLDER_INTERFACE);
            operations.add("add-interface " + name + " " + PatchSet.HOLDER_INTERFACE);

            if (target.patchLevel()) {
                updated.add(PatchSet.PATCHED_GAME_INTERFACE);
                operations.add("add-interface " + name + " " + PatchSet.PATCHED_GAME_INTERFACE);
            }

            String updatedSignature = appendToClassSignature(signature, updated.subList(existing.size(), updated.size()));
            if (!java.util.Objects.equals(updatedSignature, signature)) {
                operations.add("update-signature " + name);
            }

            super.visit(version, access, name, updatedSignature, superName, updated.toArray(String[]::new));
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor,
                                                         String signature, Object value) {
            if (ADDED_METHOD_NAMES.contains(name)) {
                throw fail("target " + target.className() + " already has a conflicting field named " + name);
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            if (ADDED_METHOD_NAMES.contains(name)) {
                throw fail("target " + target.className() + " already has a conflicting member "
                        + name + descriptor);
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            emitHolderMethods();
            if (target.patchLevel()) {
                emitPatchLevel();
            }
            super.visitEnd();
        }

        private void emitHolderMethods() {
            emitDelegating(PatchSet.GET, PatchSet.GET_DESC, PatchSet.STORE_GET,
                    PatchSet.STORE_GET_DESC, 1, false);
            emitDelegating(PatchSet.SET, PatchSet.SET_DESC, PatchSet.STORE_SET,
                    PatchSet.STORE_SET_DESC, 2, false);
            emitDelegating(PatchSet.REMOVE, PatchSet.REMOVE_DESC, PatchSet.STORE_REMOVE,
                    PatchSet.STORE_REMOVE_DESC, 1, false);
            emitDelegating(PatchSet.HAS, PatchSet.HAS_DESC, PatchSet.STORE_HAS,
                    PatchSet.STORE_HAS_DESC, 1, true);
        }

        private void emitDelegating(String name, String descriptor, String storeName,
                                    String storeDescriptor, int argumentCount, boolean returnsBoolean) {
            MethodVisitor method = super.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
            method.visitCode();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitVarInsn(Opcodes.ALOAD, 1);
            if (argumentCount == 2) {
                method.visitVarInsn(Opcodes.ALOAD, 2);
            }
            method.visitMethodInsn(Opcodes.INVOKESTATIC, PatchSet.STORE_CLASS, storeName, storeDescriptor, false);
            method.visitInsn(returnsBoolean ? Opcodes.IRETURN : Opcodes.ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            operations.add("add-method " + target.className() + " " + name + descriptor);
        }

        private void emitPatchLevel() {
            MethodVisitor method = super.visitMethod(Opcodes.ACC_PUBLIC, PatchSet.PATCH_LEVEL_METHOD,
                    PatchSet.PATCH_LEVEL_DESC, null, null);
            method.visitCode();
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IRETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            operations.add("add-method " + target.className() + " "
                    + PatchSet.PATCH_LEVEL_METHOD + PatchSet.PATCH_LEVEL_DESC);
        }

        private PatchFailure fail(String message) {
            return new PatchFailure(new PatchException(message));
        }
    }

    /**
     * A class with generic supertypes carries a Signature attribute. Adding a
     * raw interface to the interfaces table without adding it to the signature
     * makes javac ignore the new interface, so append valid ClassTypeSignature
     * entries for the non-generic Weaver interfaces.
     */
    private static String appendToClassSignature(String signature, List<String> addedInterfaces) {
        if (signature == null || addedInterfaces.isEmpty()) {
            return signature;
        }
        StringBuilder updated = new StringBuilder(signature);
        for (String added : addedInterfaces) {
            updated.append('L').append(added).append(';');
        }
        return updated.toString();
    }

    private static final class PatchFailure extends RuntimeException {
        private final PatchException patchException;

        private PatchFailure(PatchException patchException) {
            super(patchException.getMessage(), patchException, false, false);
            this.patchException = patchException;
        }
    }
}
