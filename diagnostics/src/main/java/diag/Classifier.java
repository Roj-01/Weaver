package diag;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * تصنيف الفريمات: فئة الحزمة + الحزمة الجذرية (هوية تقريبية للمود) + بصمات Mixin.
 * كل القواعد هنا موثقة في README.md · قسم «المنهجية».
 */
public final class Classifier {

    private Classifier() {}

    /** بادئات نطاقات شائعة: com.x.y.Class -> نأخذ 3 مقاطع كجذر. */
    private static final Set<String> GLOBAL_TOP = Set.of(
            "com", "org", "net", "io", "dev", "me", "gg", "xyz", "cc", "tv",
            "fr", "uk", "su", "top", "cool", "online", "site", "space", "info", "live");

    /** مقاطع حزم عامة تُقصّ من الجذر حتى لا يتجزأ المود الواحد لمرشحين متعددين. */
    private static final Set<String> GENERIC = Set.of(
            "core", "common", "client", "server", "util", "utils", "helper", "helpers",
            "api", "impl", "internal", "mixin", "mixins", "registry", "world", "entity",
            "entities", "block", "blocks", "item", "items", "gui", "render", "network",
            "event", "events", "data", "config", "command", "commands", "module", "modules",
            "feature", "features", "tweak", "tweaks", "base");

    /** مكتبات مرافقة معروفة — لا نحاسب المودات عليها. */
    private static final String[] KNOWN_LIBS = {
            "org.lwjgl", "org.slf4j", "org.apache", "com.google", "org.objectweb.asm",
            "it.unimi", "com.electronwill", "blue.endless", "org.joml", "io.netty",
            "org.jetbrains", "kotlin", "com.fasterxml", "org.yaml", "org.graalvm",
            "net.java.dev", "oshi", "net.sf.jopt", "org.intellij"};

    private static final String[] LOADER_PREFIXES = {
            "net.fabricmc", "net.minecraftforge", "net.neoforged", "net.minecraft.launchwrapper",
            "cpw.mods", "org.quiltmc", "net.legacyfabric"};

    public static Category categorize(String className) {
        if (className.startsWith("org.spongepowered.asm") || className.startsWith("org.spongepowered.mixin")) {
            return Category.MIXIN_INFRA;
        }
        for (String p : LOADER_PREFIXES) {
            if (className.startsWith(p)) return Category.LOADER;
        }
        if (className.startsWith("net.minecraft") || className.startsWith("com.mojang")) {
            return Category.VANILLA;
        }
        if (className.startsWith("java.") || className.startsWith("java.base")
                || className.startsWith("javax.") || className.startsWith("jdk.")
                || className.startsWith("sun.") || className.startsWith("com.sun.")) {
            return Category.PLATFORM;
        }
        for (String lib : KNOWN_LIBS) {
            if (className.startsWith(lib)) return Category.PLATFORM;
        }
        return Category.MOD;
    }

    /** هل الفريم من عائلة Mixin؟ (بنية Mixin، حزمة .mixin، صنف Mixin/Accessor، أو ميثود مولّدة x$y) */
    public static boolean isMixinish(String className, String method, Category category) {
        if (category == Category.MIXIN_INFRA) return true;
        String lower = className.toLowerCase(Locale.ROOT);
        if (lower.contains(".mixin.") || lower.contains(".mixins.")) return true;
        int lastDot = className.lastIndexOf('.');
        String simple = lastDot >= 0 ? className.substring(lastDot + 1) : className;
        if (simple.contains("Mixin") || simple.contains("Accessor") || simple.contains("Invoker")) return true;
        return method != null && method.contains("$") && !method.startsWith("lambda$") && !method.startsWith("<");
    }

    /** الحزمة الجذرية = الهوية التقريبية للمود. القواعد موثقة في README. */
    public static String rootPackage(String className) {
        String[] s = className.split("\\.");
        if (s.length <= 1) return className;
        int n = 0;
        while (n < s.length && s[n].matches("[a-z0-9_]+")) n++;
        if (n == 0) n = 1;
        if (GLOBAL_TOP.contains(s[0])) {
            if (n > 3) {
                // me.<org>.mods.<mod>.* -> نمدد مقطعاً بدل قصّ اسم المود
                n = (s[2].equals("mods") || s[2].equals("mod")) ? 4 : 3;
            }
        } else {
            while (n > 1 && GENERIC.contains(s[n - 1])) n--;
            if (n > 2) n = 2;
        }
        return String.join(".", Arrays.copyOfRange(s, 0, n));
    }
}
