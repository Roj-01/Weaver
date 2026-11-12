package diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** الملخص البشري العربي: أول N مرشحين + جملة توجيه. */
public final class HumanSummary {

    private HumanSummary() {}

    public static List<String> build(Analyzer.Result res, int top, String reportPath) {
        CrashReport r = res.report();
        List<String> out = new ArrayList<>();
        out.add("════════ Weaver · تشخيص الانهيار ════════");
        out.add("التقرير: " + reportPath);
        if (r.blocks.isEmpty()) {
            out.add("تحذير: ما لقينا أي استثناء في الملف — تأكد أنه crash-report.txt الأصلي (مو سجل موسوم بالوقت).");
            return out;
        }
        out.add("الوصف: " + (r.description.isEmpty() ? "غير مذكور" : r.description));
        out.add("الاستثناء الرئيسي: " + clip(r.headType + (r.headMessage.isEmpty() ? "" : ": " + r.headMessage), 150));
        out.add("إحصائيات: فريمات " + res.framesTotal()
                + " — مود " + res.byCategory().getOrDefault(Category.MOD, 0)
                + " · لعبة " + res.byCategory().getOrDefault(Category.VANILLA, 0)
                + " · محمّل " + (res.byCategory().getOrDefault(Category.LOADER, 0)
                        + res.byCategory().getOrDefault(Category.MIXIN_INFRA, 0))
                + " · منصة " + res.byCategory().getOrDefault(Category.PLATFORM, 0)
                + " — كتل Caused by: " + r.causedBlocksCount()
                + " — إشارة Mixin: " + (r.mixinPresent ? "نعم" : "لا"));

        List<Candidate> cs = res.candidates();
        if (cs.isEmpty()) {
            out.add("");
            out.add("المذنبون المحتملون: ما في أي فريم مود في الستاك — الانهيار يبدو من اللعبة/المحمّل، أو ملف التقرير ناقص.");
        } else {
            out.add("");
            out.add("المذنبون المحتملون (الأول = الأرجح):");
            int n = Math.min(top, cs.size());
            for (int i = 0; i < n; i++) {
                Candidate c = cs.get(i);
                StringBuilder sb = new StringBuilder("  ").append(c.rank).append(") ").append(c.id);
                if (c.pkg != null && !c.pkg.equals(c.id)) sb.append(" [").append(c.pkg).append("]");
                sb.append(" — ").append(fmt(c.score, 2)).append(" نقطة · ").append(fmt(c.sharePct, 1)).append("%");
                if (c.attribution != null) sb.append(" · النسبة: ").append(attrAr(c.attribution));
                sb.append(" · فريمات ").append(c.frames);
                if (c.causedByBlocks > 0) sb.append(" · كتل سببية ").append(c.causedByBlocks);
                if (c.mixinFrames > 0) sb.append(" · بصمة Mixin ×").append(c.mixinFrames);
                if (c.topFrame) sb.append(" · يتصدّر الستاك");
                if (c.frames == 0) sb.append(" · دليل نصي فقط");
                out.add(sb.toString());
            }
            if (cs.size() > n) {
                out.add("  … وإجمالاً " + cs.size() + " مرشحاً (القائمة الكاملة عبر --format json).");
            }
        }
        out.add("");
        out.add("التوجيه: " + guidance(cs));
        if (r.mixinPresent) {
            out.add("ملاحظة: التقرير يمر بمرحلة Mixin — تأكد من توافق نسخة المود مع نسخة اللعبة/المحمّل قبل ما تحكم عليه.");
        }
        return out;
    }

    private static String guidance(List<Candidate> cs) {
        if (cs.isEmpty()) {
            return "أعد إنتاج الانهيار مع لوجات كاملة، وإذا تكرر بلا مودات فارفع التقرير لفريق اللعبة/المحمّل.";
        }
        Candidate c1 = cs.get(0);
        StringBuilder g = new StringBuilder();
        g.append("عطّل \"").append(c1.id).append("\" (أو حدّثه لآخر نسخة) وأعد التشغيل؛ إذا اختفى الانهيار فالمشكلة مؤكدة فيه.");
        if (cs.size() >= 2) {
            g.append(" إذا استمر، جرّب \"").append(cs.get(1).id).append("\" بعدها.");
        }
        if (c1.frames == 0) {
            g.append(" (دليل هذا المرشح نصي من رسالة الانهيار — ابدأ بتحديثه لنسخة متوافقة.)");
        }
        return g.toString();
    }

    private static String attrAr(String a) {
        return switch (a) {
            case "mods-list" -> "قائمة المودات";
            case "mixin-config" -> "ملف Mixin";
            case "text" -> "نص الفشل";
            default -> a;
        };
    }

    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }
}
