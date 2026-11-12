package diag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** حساب ترتيب «المذنبين المحتملين» بأوزان موثقة في README.md · قسم المنهجية. */
public final class Analyzer {

    private Analyzer() {}

    // ── الأوزان ──────────────────────────────────────────────────────
    /** قرب الفريم من رأس كتلته: 2.0 / (1 + i) */
    static final double PROX_BASE = 2.0;
    /** أول فريم داخل كتلة Caused by = مصدر الخطأ المباشر لتلك الطبقة */
    static final double CAUSED_TOP_BONUS = 1.0;
    /** مضاعفة العمق السببي: كل كتلة Caused by أعمق أثقل (الجذر عادة في الأعمق) */
    static final double CAUSED_AMP = 0.5;
    /** التكرار عبر كامل التقرير: 0.75 * log2(1 + عدد الفريمات) */
    static final double REP_FACTOR = 0.75;
    static final double REP_CAP = 3.0;
    /** لكل كتلة Caused by يظهر فيها المود */
    static final double CAUSED_PRESENCE = 1.25;
    /** لكل فريم ذي بصمة Mixin */
    static final double MIXIN_FRAME = 0.5;
    static final double MIXIN_FRAME_CAP = 2.0;
    /** ذُكر معرّف المود في *.mixins.json أو *-refmap.json */
    static final double MIXIN_CONFIG = 2.5;
    /** والذكر كان وسط فشل صريح (fail/error/critical) */
    static final double MIXIN_FAIL_BONUS = 1.5;
    /** موجود في قائمة المودات الممررة */
    static final double MODS_LIST = 1.5;
    /** "Mixin apply for mod X failed" أو mod "X" نصاً */
    static final double TEXT_CLAIM = 1.0;
    /** الاستثناء الرئيسي نفسه من عائلة Mixin والمرشح مرتبط بـMixin */
    static final double MIXIN_HEAD = 1.0;

    public record Result(CrashReport report, List<Candidate> candidates,
                         Map<Category, Integer> byCategory, int framesTotal, boolean headIsMixin) {}

    /** تراكم خام لمرشح واحد قبل الدمج والترتيب. */
    private static final class Raw {
        String displayId;
        String attribution; // mods-list | mixin-config | text
        double prox;
        int frames;
        final Set<Integer> causedBlocks = new LinkedHashSet<>();
        boolean topPrimary;
        boolean causedTop;
        int mixinFrames;
        boolean mixinConfigHit;
        boolean mixinFailLinked;
        boolean modsListHit;
        boolean textHit;
        final List<String> samples = new ArrayList<>();
        final Set<String> normTokens = new LinkedHashSet<>();
        final Map<String, Integer> rootFrames = new LinkedHashMap<>();
    }

    public static Result analyze(CrashReport r, Collection<String> modNames) {
        boolean headIsMixin = r.headType.toLowerCase(Locale.ROOT).contains("mixin");
        Map<Category, Integer> byCategory = new EnumMap<>(Category.class);
        int framesTotal = 0;
        Map<String, Raw> raws = new TreeMap<>();

        // 1) جمع فريمات المودات + أوزان القرب/العمق/التكرار
        for (CrashReport.Block b : r.blocks) {
            for (int i = 0; i < b.frames.size(); i++) {
                Frame f = b.frames.get(i);
                framesTotal++;
                byCategory.merge(f.category, 1, Integer::sum);
                if (f.category != Category.MOD) continue;

                Raw raw = raws.computeIfAbsent(f.rootPackage, k -> new Raw());
                double occ = PROX_BASE / (1.0 + i) * (1.0 + CAUSED_AMP * b.depth);
                if (b.kind == CrashReport.Kind.CAUSED && i == 0) occ += CAUSED_TOP_BONUS;
                raw.prox += occ;
                raw.frames++;
                if (b.depth >= 1) raw.causedBlocks.add(b.seq);
                if (b.kind == CrashReport.Kind.PRIMARY && i == 0) raw.topPrimary = true;
                if (b.kind == CrashReport.Kind.CAUSED && i == 0) raw.causedTop = true;
                if (f.mixinish) raw.mixinFrames++;
                String s = f.shortForm();
                if (raw.samples.size() < 4 && !raw.samples.contains(s)) raw.samples.add(s);
                for (String seg : f.className.split("\\.")) {
                    String n = norm(seg);
                    if (!n.isEmpty()) raw.normTokens.add(n);
                }
                raw.normTokens.add(norm(f.rootPackage));
                raw.rootFrames.merge(f.rootPackage, 1, Integer::sum);
            }
        }

        // 2) النسبة: قائمة المودات > ملفات Mixin > نصوص الفشل
        LinkedHashMap<String, String> modsNorm = new LinkedHashMap<>(); // norm -> عرض
        for (String m : modNames) {
            String n = norm(m);
            if (n.length() >= 2) modsNorm.putIfAbsent(n, m);
        }
        LinkedHashMap<String, String> cfgNorm = normMap(r.mixinConfigIds);
        Set<String> cfgFailNorm = new LinkedHashSet<>(normMap(r.mixinFailLinkedIds).keySet());
        LinkedHashMap<String, String> claimNorm = normMap(r.textClaimIds);

        for (Raw raw : raws.values()) {
            String hit = firstHit(raw, modsNorm.keySet());
            if (hit != null) {
                raw.modsListHit = true;
                raw.attribution = "mods-list";
                raw.displayId = modsNorm.get(hit);
            }
            hit = firstHit(raw, cfgNorm.keySet());
            if (hit != null) {
                raw.mixinConfigHit = true;
                raw.mixinFailLinked = cfgFailNorm.contains(hit);
                if (raw.displayId == null) {
                    raw.attribution = "mixin-config";
                    raw.displayId = cfgNorm.get(hit);
                }
            }
            hit = firstHit(raw, claimNorm.keySet());
            if (hit != null) {
                raw.textHit = true;
                if (raw.displayId == null) {
                    raw.attribution = "text";
                    raw.displayId = claimNorm.get(hit);
                }
            }
        }

        // 3) مرشحون نصيون (من رسائل Mixin/الفشل) حتى لو ما ظهروا في الستاك أبداً
        List<Raw> textRaws = new ArrayList<>();
        for (Map.Entry<String, String> e : cfgNorm.entrySet()) {
            Raw raw = new Raw();
            raw.mixinConfigHit = true;
            raw.mixinFailLinked = cfgFailNorm.contains(e.getKey());
            raw.attribution = "mixin-config";
            raw.displayId = e.getValue();
            textRaws.add(raw);
        }
        for (Map.Entry<String, String> e : claimNorm.entrySet()) {
            Raw raw = new Raw();
            raw.textHit = true;
            raw.attribution = "text";
            raw.displayId = e.getValue();
            textRaws.add(raw);
        }

        // 4) دمج حسب الهوية: مود منسوب أو الحزمة الجذرية
        LinkedHashMap<String, Raw> merged = new LinkedHashMap<>();
        for (Raw src : raws.values()) mergeInto(merged, src);
        for (Raw src : textRaws) mergeInto(merged, src);

        // 5) النتيجة النهائية
        List<Candidate> candidates = new ArrayList<>();
        double sum = 0;
        for (Raw raw : merged.values()) {
            String pkg = argmax(raw.rootFrames);
            String id = raw.displayId != null ? raw.displayId : pkg;
            double score = raw.prox
                    + Math.min(REP_CAP, REP_FACTOR * log2(1 + raw.frames))
                    + CAUSED_PRESENCE * raw.causedBlocks.size()
                    + Math.min(MIXIN_FRAME_CAP, MIXIN_FRAME * raw.mixinFrames)
                    + (raw.mixinConfigHit ? MIXIN_CONFIG : 0)
                    + (raw.mixinFailLinked ? MIXIN_FAIL_BONUS : 0)
                    + (raw.modsListHit ? MODS_LIST : 0)
                    + (raw.textHit ? TEXT_CLAIM : 0)
                    + (headIsMixin && (raw.mixinConfigHit || raw.mixinFrames > 0) ? MIXIN_HEAD : 0);
            if (score < 1e-9) continue;
            Candidate c = new Candidate(id, pkg, raw.attribution);
            c.score = score;
            c.frames = raw.frames;
            c.causedByBlocks = raw.causedBlocks.size();
            c.mixinFrames = raw.mixinFrames;
            c.topFrame = raw.topPrimary;
            c.causedTop = raw.causedTop;
            c.mixinConfigHit = raw.mixinConfigHit;
            c.mixinFailLinked = raw.mixinFailLinked;
            c.modsListHit = raw.modsListHit;
            c.textHit = raw.textHit;
            c.samples.addAll(raw.samples);
            candidates.add(c);
            sum += score;
        }
        candidates.sort(null);
        int rank = 1;
        for (Candidate c : candidates) {
            c.rank = rank++;
            if (sum > 0) c.sharePct = 100.0 * c.score / sum;
        }
        return new Result(r, candidates, byCategory, framesTotal, headIsMixin);
    }

    private static void mergeInto(Map<String, Raw> merged, Raw src) {
        String key = src.displayId != null ? "id:" + norm(src.displayId) : "pkg:" + argmax(src.rootFrames);
        Raw dst = merged.get(key);
        if (dst == null) {
            dst = new Raw();
            dst.attribution = src.attribution;
            dst.displayId = src.displayId;
            merged.put(key, dst);
        }
        dst.prox += src.prox;
        dst.frames += src.frames;
        dst.mixinFrames += src.mixinFrames;
        dst.causedBlocks.addAll(src.causedBlocks);
        dst.topPrimary |= src.topPrimary;
        dst.causedTop |= src.causedTop;
        dst.mixinConfigHit |= src.mixinConfigHit;
        dst.mixinFailLinked |= src.mixinFailLinked;
        dst.modsListHit |= src.modsListHit;
        dst.textHit |= src.textHit;
        for (String s : src.samples) {
            if (dst.samples.size() < 4 && !dst.samples.contains(s)) dst.samples.add(s);
        }
        for (Map.Entry<String, Integer> e : src.rootFrames.entrySet()) {
            dst.rootFrames.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        if (dst.displayId == null) dst.displayId = src.displayId;
    }

    private static LinkedHashMap<String, String> normMap(Collection<String> ids) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (String id : ids) {
            String n = norm(id);
            if (n.length() >= 2) m.putIfAbsent(n, id);
        }
        return m;
    }

    private static String firstHit(Raw raw, Collection<String> normalizedNames) {
        for (String name : normalizedNames) {
            if (raw.normTokens.contains(name)) return name;
        }
        return null;
    }

    /** تطبيع للمقارنة: حروف لاتينية/أرقام فقط بأحرف صغيرة. */
    static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) b.append(c);
        }
        return b.toString();
    }

    private static String argmax(Map<String, Integer> m) {
        String best = null;
        int bestN = -1;
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (e.getValue() > bestN) {
                bestN = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2.0);
    }
}
