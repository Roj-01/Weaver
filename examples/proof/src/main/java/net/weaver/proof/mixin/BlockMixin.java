package net.weaver.proof.mixin;

import java.util.function.Supplier;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import net.weaver.proof.ProofHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects into a real game class. The handler deliberately uses an anonymous
 * class: Mixin copies it into the target as a synthetic class that the loader
 * must define, which third-party mods rely on.
 */
@Mixin(Block.class)
public class BlockMixin {

    @Inject(method = "getName", at = @At("HEAD"))
    private void weaver$proof(CallbackInfoReturnable<MutableComponent> info) {
        Supplier<String> name = new Supplier<String>() {
            @Override
            public String get() {
                return "Block.getName";
            }
        };
        ProofHooks.mixinHit(name.get());
    }
}
