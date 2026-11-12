package net.weaver.api.event;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class NoReflectionTest
{
    private static final String METHOD_CLASS = "java/lang/reflect/Method";
    private static final String INVOKE = "invoke";
    private static final String INVOCATION_TARGET = "java/lang/reflect/InvocationTargetException";

    @Test
    void parserDetectsReflectiveInvocationInAControlClass() throws IOException
    {
        ConstantPool pool = read(ReflectiveControl.class);

        assertTrue(pool.hasMethodReference(METHOD_CLASS, INVOKE));
    }

    @Test
    void eventBusNeverUsesReflectiveMethodInvocation() throws IOException
    {
        List<Class<?>> classes = new ArrayList<>();
        collect(EventBus.class, classes);
        for (Class<?> type : classes)
        {
            ConstantPool pool = read(type);
            assertFalse(pool.hasMethodReference(METHOD_CLASS, INVOKE),
                    type.getName() + " references " + METHOD_CLASS + "." + INVOKE);
            assertFalse(pool.hasUtf8(INVOCATION_TARGET),
                    type.getName() + " references " + INVOCATION_TARGET);
        }
    }

    private static void collect(Class<?> type, List<Class<?>> classes)
    {
        classes.add(type);
        for (Class<?> nested : type.getDeclaredClasses())
        {
            collect(nested, classes);
        }
    }

    private static ConstantPool read(Class<?> type) throws IOException
    {
        String resource = type.getName().replace('.', '/') + ".class";
        ClassLoader loader = type.getClassLoader();
        assertNotNull(loader, "No class loader for " + type.getName());
        try (InputStream stream = loader.getResourceAsStream(resource))
        {
            assertNotNull(stream, "Missing class resource " + resource);
            return ConstantPool.parse(stream);
        }
    }

    private static final class ConstantPool
    {
        private final String[] utf8;
        private final int[] classNameIndex;
        private final int[] nameAndTypeNameIndex;
        private final int[] methodReferenceClassIndex;
        private final int[] methodReferenceNameAndTypeIndex;

        private ConstantPool(int size)
        {
            this.utf8 = new String[size];
            this.classNameIndex = new int[size];
            this.nameAndTypeNameIndex = new int[size];
            this.methodReferenceClassIndex = new int[size];
            this.methodReferenceNameAndTypeIndex = new int[size];
        }

        private static ConstantPool parse(InputStream stream) throws IOException
        {
            DataInputStream input = new DataInputStream(stream);
            int magic = input.readInt();
            if (magic != 0xCAFEBABE)
            {
                fail("Bad class magic 0x" + Integer.toHexString(magic));
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            int count = input.readUnsignedShort();
            ConstantPool pool = new ConstantPool(count);
            for (int index = 1; index < count; index++)
            {
                int tag = input.readUnsignedByte();
                switch (tag)
                {
                    case 1 ->
                    {
                        int length = input.readUnsignedShort();
                        byte[] bytes = new byte[length];
                        input.readFully(bytes);
                        pool.utf8[index] = new String(bytes, StandardCharsets.UTF_8);
                    }
                    case 3, 4 -> input.skipBytes(4);
                    case 5, 6 ->
                    {
                        input.skipBytes(8);
                        index++;
                    }
                    case 7 -> pool.classNameIndex[index] = input.readUnsignedShort();
                    case 8 -> input.skipBytes(2);
                    case 9 -> input.skipBytes(4);
                    case 10 ->
                    {
                        pool.methodReferenceClassIndex[index] = input.readUnsignedShort();
                        pool.methodReferenceNameAndTypeIndex[index] = input.readUnsignedShort();
                    }
                    case 11 -> input.skipBytes(4);
                    case 12 ->
                    {
                        pool.nameAndTypeNameIndex[index] = input.readUnsignedShort();
                        input.skipBytes(2);
                    }
                    case 15 -> input.skipBytes(3);
                    case 16, 19, 20 -> input.skipBytes(2);
                    case 17, 18 -> input.skipBytes(4);
                    default -> fail("Unknown constant pool tag " + tag + " at index " + index);
                }
            }
            return pool;
        }

        private boolean hasMethodReference(String owner, String name)
        {
            for (int index = 1; index < methodReferenceClassIndex.length; index++)
            {
                int classIndex = methodReferenceClassIndex[index];
                if (classIndex == 0)
                {
                    continue;
                }
                String resolvedOwner = utf8[classNameIndex[classIndex]];
                int nameAndTypeIndex = methodReferenceNameAndTypeIndex[index];
                String resolvedName = nameAndTypeIndex == 0 ? null
                        : utf8[nameAndTypeNameIndex[nameAndTypeIndex]];
                if (owner.equals(resolvedOwner) && name.equals(resolvedName))
                {
                    return true;
                }
            }
            return false;
        }

        private boolean hasUtf8(String value)
        {
            for (String candidate : utf8)
            {
                if (candidate != null && candidate.contains(value))
                {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ReflectiveControl
    {
        @SuppressWarnings("unused")
        private void call(Method method, Object target, Object argument) throws Exception
        {
            method.invoke(target, argument);
        }
    }
}
