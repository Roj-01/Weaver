package diag;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * نقطة الدخول: محلل crash-report.
 * يحدد «المذنبين المحتملين» ويطبع ملخصاً عربياً و/أو JSON منظماً.
 */
public final class Main {

    public static final String VERSION = "0.1.0";

    private Main() {}

    public static void main(String[] args) {
        PrintStream out = utf8(FileDescriptor.out);
        PrintStream err = utf8(FileDescriptor.err);
        int code;
        try {
            code = run(args, out);
        } catch (UsageException e) {
            err.println("خطأ: " + e.getMessage());
            err.println();
            err.println(usage());
            code = 2;
        } catch (IOException e) {
            err.println("خطأ في قراءة/كتابة الملفات: " + e.getMessage());
            code = 3;
        } catch (RuntimeException e) {
            err.println("خطأ غير متوقع: " + e);
            code = 1;
        }
        out.flush();
        err.flush();
        System.exit(code);
    }

    static int run(String[] args, PrintStream out) throws IOException {
        Path report = null;
        Path modsFile = null;
        Path outFile = null;
        String format = "text";
        int top = 3;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help" -> {
                    out.println(usage());
                    return 0;
                }
                case "--mods" -> modsFile = Path.of(value(args, ++i, "--mods"));
                case "--format" -> {
                    format = value(args, ++i, "--format");
                    if (!Set.of("text", "json", "top1").contains(format)) {
                        throw new UsageException("صيغة غير معروفة: " + format + " (المتاح: text, json, top1)");
                    }
                }
                case "--top" -> {
                    String v = value(args, ++i, "--top");
                    try {
                        top = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        throw new UsageException("--top لازم يكون رقماً: " + v);
                    }
                    if (top < 1) throw new UsageException("--top لازم يكون 1 أو أكثر");
                }
                case "--out" -> outFile = Path.of(value(args, ++i, "--out"));
                default -> {
                    if (a.startsWith("-")) throw new UsageException("خيار غير معروف: " + a);
                    if (report != null) throw new UsageException("ملف تقرير إضافي غير متوقع: " + a);
                    report = Path.of(a);
                }
            }
        }

        if (report == null) throw new UsageException("لازم تمرير مسار ملف crash-report.txt");
        if (!Files.isRegularFile(report)) throw new UsageException("الملف غير موجود: " + report);
        if (modsFile != null && !Files.isRegularFile(modsFile)) throw new UsageException("ملف قائمة المودات غير موجود: " + modsFile);

        CrashReport rep = CrashReport.parse(report);
        Set<String> modNames = modsFile == null ? Set.of() : readMods(modsFile);
        Analyzer.Result res = Analyzer.analyze(rep, modNames);

        List<String> summary = HumanSummary.build(res, top, report.toString());
        String json = JsonReport.build(res, report.toString(),
                modsFile == null ? null : modsFile.toString(), summary);

        switch (format) {
            case "json" -> out.println(json);
            case "top1" -> out.println(res.candidates().isEmpty() ? "-" : res.candidates().get(0).id);
            default -> summary.forEach(out::println);
        }
        if (outFile != null) {
            Files.writeString(outFile, json + "\n", StandardCharsets.UTF_8);
            if (!"json".equals(format)) out.println("(كُتب JSON إلى " + outFile + ")");
        }
        return 0;
    }

    private static String value(String[] args, int i, String opt) {
        if (i >= args.length) throw new UsageException("ناقص قيمة بعد " + opt);
        return args[i];
    }

    /** قائمة المودات: سطر لكل معرّف/اسم، «#» تعليق، وأول كلمة من السطر هي المعرّف. */
    static LinkedHashSet<String> readMods(Path file) throws IOException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') raw = raw.substring(1);
            String t = raw.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            names.add(t.split("\\s+")[0]);
        }
        return names;
    }

    private static PrintStream utf8(FileDescriptor fd) {
        return new PrintStream(new FileOutputStream(fd), true, StandardCharsets.UTF_8);
    }

    static String usage() {
        return """
                محلل crash-report — Weaver (الورشة 7)

                الاستخدام:
                  java -jar out/diagnostics.jar <crash-report.txt> [خيارات]

                خيارات:
                  --mods <ملف>      قائمة أسماء/معرّفات المودات (سطر لكل مود، # للتعليق) — اختياري
                  --format <صيغة>   text (افتراضي) | json | top1
                  --top <N>         عدد المرشحين في الملخص النصي (افتراضي 3)
                  --out <ملف>       كتابة تقرير JSON إلى ملف
                  -h, --help        هذه المساعدة

                أمثلة:
                  java -jar out/diagnostics.jar crash-report.txt
                  java -jar out/diagnostics.jar crash-report.txt --mods mods.txt --format json
                  java -jar out/diagnostics.jar crash-report.txt --format top1""";
    }

    static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }
}
