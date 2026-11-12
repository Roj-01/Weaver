package net.weaver.api.game.event.mixin;

import net.minecraft.CrashReport;
import net.minecraft.SystemReport;
import net.weaver.api.WeaverProblems;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crash reports list the mods Weaver already knew were not fully working. */
@Mixin(CrashReport.class)
public abstract class CrashReportProblemsMixin {
    @Shadow
    @Final
    private SystemReport systemReport;

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/Throwable;)V", at = @At("RETURN"))
    private void weaver$problems(String title, Throwable failure, CallbackInfo callback) {
        try {
            if (!WeaverProblems.isEmpty()) {
                systemReport.setDetail("Weaver mod problems", "\n" + WeaverProblems.summary());
            }
        } catch (RuntimeException ignored) {
            // a crash report must never fail because of this section
        }
    }
}
