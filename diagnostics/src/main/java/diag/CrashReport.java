package diag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** محلل crash-report: كتل الاستثناءات (رئيسي / Caused by) + الفريمات + الأقسام + إشارات Mixin. */
public final class CrashReport {

    public enum Kind { PRIMARY, CAUSED, SUPPRESSED }

    public static final class Block {
        public final int seq;
        public final Kind kind;
        public final int depth;   // 0 = الاستثناء الرئيسي، 1 = أول Caused by، ...
        public final String type;
        public final String message;
        public final List<Frame> frames = new ArrayList<>();

        Block(int seq, Kind kind, int depth, String type, String message) {
            this.seq = seq;
            this.kind = kind;
            this.depth = depth;
            this.type = type;
            this.message = message;
        }
    }

    public String description = "";
    public String headType = "";
    public String headMessage = "";
    public final List<Block> blocks = new ArrayList<>();
    public final Map<String, List<String>> sections = new LinkedHashMap<>();
    /** معرّفات ظهرت في ملفات *.mixins.json أو *-refmap.json */
    public final Set<String> mixinConfigIds = new LinkedHashSet<>();
    /** نفس المعرّفات لكن وسط سياق فشل صريح */
    public final Set<String> mixinFailLinkedIds = new LinkedHashSet<>();
    /** معرّفات من رسائل فشل نصية مثل "Mixin apply for mod X failed" */
    public final Set<String> textClaimIds = new LinkedHashSet<>();
    public boolean mixinPresent = false;

    private static final Pattern SECTION = Pattern.compile("^--\\s+(.+?)\\s+--$");
    private static final Pattern CAUSED = Pattern.compile(
            "^\\s*(Caused by|Suppressed):\\s*([A-Za-z_$][\\w.$]*(?:Exception|Error|Throwable))\\s*(?::\\s*(.*))?$");
    private static final Pattern THROWABLE = Pattern.compile(
            "^([A-Za-z_$][\\w.$]*(?:Exception|Error|Throwable))\\s*(?::\\s*(.*))?$");
    private static final Pattern FRAME = Pattern.compile("^\\s+at\\s+([\\w.$/]+)(?:\\(([^()]*)\\))?\\s*$");
    private static final Pattern DESCR = Pattern.compile("(?i)^Description:\\s*(.+)$");
    private static final Pattern MIXIN_CFG = Pattern.compile("([a-z0-9_\\-]{2,})\\.mixins?\\.json");
    private static final Pattern MIXIN_CFG2 = Pattern.compile("mixins\\.([a-z0-9_\\-]{2,})\\.json");
    private static final Pattern REFMAP = Pattern.compile("([a-z0-9_\\-]{2,})-refmap\\.json");
    private static final Pattern CLAIM_FOR_MOD = Pattern.compile("(?i)(?:for|from)\\s+mod\\s+['\"]?([A-Za-z0-9_\\-]{2,64})['\"]?");
    private static final Pattern CLAIM_QUOTED_MOD = Pattern.compile("(?i)\\bmod\\s+['\"]([^'\"]{2,64})['\"]");

    public static CrashReport parse(Path file) throws IOException {
        return parseLines(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    public static CrashReport parseLines(List<String> lines) {
        CrashReport r = new CrashReport();
        Block cur = null;
        String curSection = null;
        int seq = 0;

        for (String raw : lines) {
            String line = raw;
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1); // BOM
            String t = line.strip();
            if (t.isEmpty()) continue;

            scanMixinText(r, t);

            Matcher sm = SECTION.matcher(t);
            if (sm.matches()) {
                curSection = sm.group(1);
                r.sections.computeIfAbsent(curSection, k -> new ArrayList<>());
                if (curSection.toLowerCase(Locale.ROOT).contains("mixin")) r.mixinPresent = true;
                cur = null; // فريمات الأقسام (مثل Head) لا تُحتسب — الستاك الرئيسي وسلاسل Caused by كافية
                continue;
            }
            if (curSection != null) r.sections.get(curSection).add(t);

            Matcher dm = DESCR.matcher(t);
            if (dm.matches()) {
                r.description = dm.group(1).strip();
                continue;
            }

            Matcher cm = CAUSED.matcher(t);
            if (cm.matches()) {
                Kind kind = cm.group(1).startsWith("Suppressed") ? Kind.SUPPRESSED : Kind.CAUSED;
                int depth = cur == null ? 1 : cur.depth + 1;
                cur = new Block(++seq, kind, depth, cm.group(2),
                        cm.group(3) == null ? "" : cm.group(3).strip());
                r.blocks.add(cur);
                continue;
            }

            Matcher fm = FRAME.matcher(line);
            if (fm.matches()) {
                if (cur != null) cur.frames.add(frameOf(fm.group(1), fm.group(2)));
                continue;
            }
            if (t.startsWith("...")) continue; // "... N more"

            Matcher tm = THROWABLE.matcher(line); // كتلة رئيسية جديدة: سطر بادئ من العمود 0
            if (tm.matches()) {
                cur = new Block(++seq, Kind.PRIMARY, 0, tm.group(1),
                        tm.group(2) == null ? "" : tm.group(2).strip());
                r.blocks.add(cur);
                if (r.headType.isEmpty()) {
                    r.headType = cur.type;
                    r.headMessage = cur.message;
                }
            }
        }

        for (Block b : r.blocks) {
            for (Frame f : b.frames) {
                if (f.category == Category.MIXIN_INFRA || f.mixinish) {
                    r.mixinPresent = true;
                    break;
                }
            }
        }
        if (r.headType.toLowerCase(Locale.ROOT).contains("mixin")) r.mixinPresent = true;
        return r;
    }

    private static Frame frameOf(String clsRaw, String location) {
        String cls = clsRaw;
        int slash = cls.lastIndexOf('/');
        if (slash >= 0) cls = cls.substring(slash + 1); // java.base/java.lang.Thread -> java.lang.Thread
        int lastDot = cls.lastIndexOf('.');
        if (lastDot <= 0) return new Frame(cls, "?", location);
        return new Frame(cls.substring(0, lastDot), cls.substring(lastDot + 1), location);
    }

    private static void scanMixinText(CrashReport r, String t) {
        String lower = t.toLowerCase(Locale.ROOT);
        boolean failCtx = lower.contains("fail") || lower.contains("error") || lower.contains("critical")
                || lower.contains("exception") || lower.contains("invalid") || lower.contains("could not");
        Matcher m = MIXIN_CFG.matcher(t);
        while (m.find()) note(r, m.group(1), failCtx);
        m = MIXIN_CFG2.matcher(t);
        while (m.find()) note(r, m.group(1), failCtx);
        m = REFMAP.matcher(t);
        while (m.find()) note(r, m.group(1), failCtx);
        if (failCtx) {
            Matcher c1 = CLAIM_FOR_MOD.matcher(t);
            while (c1.find()) r.textClaimIds.add(c1.group(1));
            Matcher c2 = CLAIM_QUOTED_MOD.matcher(t);
            while (c2.find()) r.textClaimIds.add(c2.group(1));
        }
    }

    private static void note(CrashReport r, String id, boolean failCtx) {
        r.mixinConfigIds.add(id);
        r.mixinPresent = true;
        if (failCtx) r.mixinFailLinkedIds.add(id);
    }

    public int totalFrames() {
        int n = 0;
        for (Block b : blocks) n += b.frames.size();
        return n;
    }

    public int causedBlocksCount() {
        int n = 0;
        for (Block b : blocks) {
            if (b.kind != Kind.PRIMARY) n++;
        }
        return n;
    }
}
