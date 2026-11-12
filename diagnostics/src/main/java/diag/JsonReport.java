package diag;

import java.util.List;

/** بناء مخرَج JSON المنظم الكامل. */
public final class JsonReport {

    private JsonReport() {}

    public static String build(Analyzer.Result res, String reportPath, String modsPath, List<String> summary) {
        CrashReport r = res.report();
        Json j = new Json();
        j.obj();
        j.key("tool").val("weaver-diagnostics");
        j.key("version").val(Main.VERSION);
        j.key("report").val(reportPath);
        j.key("mods_file").val(modsPath);
        j.key("description").val(r.description);
        j.key("head_exception").obj()
                .key("type").val(r.headType)
                .key("message").val(r.headMessage)
                .end();
        j.key("stats").obj()
                .key("frames_total").val(res.framesTotal())
                .key("frames_mod").val(res.byCategory().getOrDefault(Category.MOD, 0))
                .key("frames_vanilla").val(res.byCategory().getOrDefault(Category.VANILLA, 0))
                .key("frames_loader").val(res.byCategory().getOrDefault(Category.LOADER, 0)
                        + res.byCategory().getOrDefault(Category.MIXIN_INFRA, 0))
                .key("frames_platform").val(res.byCategory().getOrDefault(Category.PLATFORM, 0))
                .key("caused_by_blocks").val(r.causedBlocksCount())
                .key("mixin_present").val(r.mixinPresent)
                .key("mixin_config_ids").arr();
        for (String s : r.mixinConfigIds) j.val(s);
        j.end(); // mixin_config_ids
        j.end(); // stats
        j.key("candidates").arr();
        for (Candidate c : res.candidates()) {
            j.obj();
            j.key("rank").val(c.rank);
            j.key("id").val(c.id);
            j.key("package").val(c.pkg == null || c.pkg.equals(c.id) ? null : c.pkg);
            j.key("attribution").val(c.attribution);
            j.key("score").val(c.score);
            j.key("share_pct").val(c.sharePct);
            j.key("signals").obj()
                    .key("frames").val(c.frames)
                    .key("caused_by_blocks").val(c.causedByBlocks)
                    .key("mixin_frames").val(c.mixinFrames)
                    .key("top_frame").val(c.topFrame)
                    .key("caused_by_top").val(c.causedTop)
                    .key("mixin_config_hit").val(c.mixinConfigHit)
                    .key("mixin_fail_linked").val(c.mixinFailLinked)
                    .key("mods_list_hit").val(c.modsListHit)
                    .key("text_hit").val(c.textHit)
                    .end();
            j.key("sample_frames").arr();
            for (String s : c.samples) j.val(s);
            j.end();
            j.end();
        }
        j.end(); // candidates
        j.key("summary_ar").arr();
        for (String s : summary) j.val(s);
        j.end();
        j.end();
        return j.done();
    }
}
