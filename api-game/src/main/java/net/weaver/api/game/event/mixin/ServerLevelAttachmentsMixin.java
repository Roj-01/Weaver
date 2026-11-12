package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.weaver.api.game.capability.HolderAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server level capability values: loaded when the level is created, stored before each save. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelAttachmentsMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void weaver$loadValues(CallbackInfo callback) {
        HolderAttachments.loadLevel((ServerLevel) (Object) this);
        try {
            net.weaver.api.game.event.support.ModMismatchCheck.check((ServerLevel) (Object) this);
        } catch (RuntimeException | LinkageError failure) {
            System.out.println("[weaver] MOD LIST CHECK FAILED reason=" + failure);
        }
    }

    @Inject(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At("HEAD"))
    private void weaver$storeValues(ProgressListener progress, boolean flush, boolean skip, CallbackInfo callback) {
        HolderAttachments.saveLevel((ServerLevel) (Object) this);
    }
}
