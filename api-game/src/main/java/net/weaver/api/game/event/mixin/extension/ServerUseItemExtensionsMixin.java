package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link ItemExtension#usesBlockWhileSneaking} on the server. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerUseItemExtensionsMixin {
    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
    private boolean weaver$sneakUse(ServerPlayer player, Operation<Boolean> original,
            @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockHitResult hit) {
        return original.call(player) && !ExtensionHooks.usesBlockWhileSneaking(player, level, hit.getBlockPos());
    }
}
