package net.weaver.core.enums;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.weaver.api.EnumExtensions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An enum gains a mod constant: values, valueOf, ordinal, field, and constructor arguments. */
class EnumExtenderTest {
    /** The enum being extended, compiled like vanilla enums (with $values()). */
    public enum SampleEnum {
        ALPHA(1, "a"),
        BETA(2, "b");

        public final int id;
        public final String label;

        SampleEnum(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** The mod side: arguments for the new constant, from its ordinal. */
    public static Object[] gamma(int ordinal) {
        return new Object[] {ordinal + 1, "g"};
    }

    private static final String ENUM = SampleEnum.class.getName().replace('.', '/');

    @Test
    void addsConstantAfterVanillaOnes() throws Exception {
        List<String> problems = new ArrayList<>();
        String json = "{\"entries\":[{\"enum\":\"" + ENUM + "\",\"name\":\"TESTMOD_GAMMA\","
                + "\"constructor\":\"(ILjava/lang/String;)V\",\"parameters\":{\"class\":\""
                + EnumExtenderTest.class.getName() + "\",\"method\":\"gamma\"}},"
                + "{\"enum\":\"" + ENUM + "\",\"name\":\"OTHER_NAME\",\"constructor\":\"()V\","
                + "\"parameters\":{\"class\":\"x\",\"method\":\"y\"}}]}";
        List<EnumExtender.Addition> additions = EnumExtender.parse("testmod",
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8), problems);
        assertEquals(1, additions.size(), "the constant without the mod prefix is rejected");
        assertEquals(1, problems.size());

        EnumExtensions.install(List.of(new EnumExtensions.Entry("testmod", ENUM, "TESTMOD_GAMMA",
                EnumExtenderTest.class.getName(), "gamma", EnumExtenderTest.class.getClassLoader())));
        byte[] transformed = new EnumExtender(additions).apply(bytes(SampleEnum.class));

        Class<?> extended = new ChildFirst(SampleEnum.class.getName(), transformed).loadClass(SampleEnum.class.getName());
        Object[] values = (Object[]) extended.getMethod("values").invoke(null);
        assertEquals(3, values.length);
        Enum<?> gamma = (Enum<?>) values[2];
        assertEquals("TESTMOD_GAMMA", gamma.name());
        assertEquals(2, gamma.ordinal());
        assertEquals(3, extended.getField("id").getInt(gamma));
        assertEquals("g", extended.getField("label").get(gamma));
        assertSame(gamma, extended.getField("TESTMOD_GAMMA").get(null));
        Method valueOf = extended.getMethod("valueOf", String.class);
        assertSame(gamma, valueOf.invoke(null, "TESTMOD_GAMMA"));
        assertTrue(extended.getField("TESTMOD_GAMMA").isEnumConstant());
    }

    private static byte[] bytes(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (in == null) {
                throw new IOException("no class file for " + type);
            }
            return in.readAllBytes();
        }
    }

    /** Defines one class from the given bytes and leaves the rest to the parent. */
    private static final class ChildFirst extends ClassLoader {
        private final String name;
        private final byte[] bytes;

        ChildFirst(String name, byte[] bytes) {
            super(EnumExtenderTest.class.getClassLoader());
            this.name = name;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
            if (className.equals(name)) {
                synchronized (getClassLoadingLock(className)) {
                    Class<?> loaded = findLoadedClass(className);
                    return loaded != null ? loaded : defineClass(className, bytes, 0, bytes.length);
                }
            }
            return super.loadClass(className, resolve);
        }
    }

    @Test
    void hyphenatedModIdsUseUnderscoresInThePrefix() {
        assertEquals("MY_MOD_", EnumExtender.prefix("my-mod"));
        List<String> problems = new ArrayList<>();
        String json = "{\"entries\":[{\"enum\":\"" + ENUM + "\",\"name\":\"MY_MOD_DELTA\","
                + "\"constructor\":\"(ILjava/lang/String;)V\",\"parameters\":{\"class\":\"x\",\"method\":\"y\"}}]}";
        assertEquals(1, EnumExtender.parse("my-mod", json.getBytes(java.nio.charset.StandardCharsets.UTF_8), problems).size());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void aSecondConstantWithTheSameNameIsSkippedNotFatal() throws Exception {
        EnumExtender.Addition gamma = new EnumExtender.Addition("testmod", ENUM, "TESTMOD_GAMMA",
                "(ILjava/lang/String;)V", EnumExtenderTest.class.getName(), "gamma");
        EnumExtensions.install(List.of(new EnumExtensions.Entry("testmod", ENUM, "TESTMOD_GAMMA",
                EnumExtenderTest.class.getName(), "gamma", EnumExtenderTest.class.getClassLoader())));
        byte[] transformed = new EnumExtender(List.of(gamma, gamma)).apply(bytes(SampleEnum.class));
        Class<?> extended = new ChildFirst(SampleEnum.class.getName(), transformed).loadClass(SampleEnum.class.getName());
        Object[] values = (Object[]) extended.getMethod("values").invoke(null);
        assertEquals(3, values.length, "the duplicate is left out and the first one is added");
    }
}
