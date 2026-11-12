package net.weaver.core.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which changes redefine fine but would never run: static initializers and a mod's startup methods. */
class MethodPrintsTest {
    @TempDir
    Path dir;

    private byte[] compile(String name, String source) throws Exception {
        Path src = dir.resolve(System.nanoTime() + "").resolve(name + ".java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        String cp = System.getProperty("java.class.path");
        int code = javac.run(null, null, null, "-cp", cp, "-d", src.getParent().toString(), src.toString());
        assertEquals(0, code);
        return Files.readAllBytes(src.getParent().resolve(name + ".class"));
    }

    @Test
    void aBodyChangeNeedsNoRestart() throws Exception {
        byte[] a = compile("A", "class A { static int x = 1; int f() { return 1; } }");
        byte[] b = compile("A", "class A { static int x = 1; int f() { return 2; } }");
        assertEquals(Set.of("f"), MethodPrints.changed(a, b));
        assertNull(MethodPrints.restartReason(a, b));
    }

    @Test
    void lineNumbersAloneAreNoChange() throws Exception {
        byte[] a = compile("A", "class A { int f() { return 1; } }");
        byte[] b = compile("A", "class A {\n\n\n int f() {\n return 1;\n }\n}");
        assertTrue(MethodPrints.changed(a, b).isEmpty());
    }

    @Test
    void aStaticInitializerChangeNeedsARestart() throws Exception {
        byte[] a = compile("A", "class A { static int x = 1; }");
        byte[] b = compile("A", "class A { static int x = 2; }");
        assertTrue(MethodPrints.restartReason(a, b).startsWith("static initializer changed"));
    }

    @Test
    void aModStartupMethodChangeNeedsARestart() throws Exception {
        String head = "public class M implements net.weaver.api.WeaverMod { public void onInitialize(net.weaver.api.ModContext c) { ";
        byte[] a = compile("M", head + "c.log(\"a\"); } public int other() { return 1; } }");
        byte[] b = compile("M", head + "c.log(\"b\"); } public int other() { return 1; } }");
        assertTrue(MethodPrints.restartReason(a, b).startsWith("onInitialize changed"));
        byte[] c = compile("M", head + "c.log(\"a\"); } public int other() { return 2; } }");
        assertNull(MethodPrints.restartReason(a, c), "other methods of the mod class swap live");
    }
}
