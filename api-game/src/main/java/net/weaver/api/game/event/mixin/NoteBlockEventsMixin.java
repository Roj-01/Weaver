package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.weaver.api.game.event.block.NoteBlockChangeEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Note block pitch changes. The event is posted at entry; a changed note is then
 * applied to the {@code state.cycle(NOTE)} result the vanilla body stores.
 */
@Mixin(NoteBlock.class)
public abstract class NoteBlockEventsMixin {
    @Unique
    private static final ThreadLocal<Integer> weaver$note = new ThreadLocal<>();

    @Inject(method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$noteChange(BlockState state, Level level, BlockPos pos, Player player,
                                   BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback) {
        weaver$note.remove();
        if (level.isClientSide()) {
            return;
        }
        int oldNote = state.getValue(NoteBlock.NOTE);
        int newNote = state.cycle(NoteBlock.NOTE).getValue(NoteBlock.NOTE);
        NoteBlockChangeEvent event = GameEvents.post(
                new NoteBlockChangeEvent(level, pos, state, player, oldNote, newNote), level);
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(InteractionResult.FAIL);
        } else if (event.newNote() != newNote) {
            weaver$note.set(event.newNote());
        }
    }

    @ModifyExpressionValue(method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;cycle(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Object;"))
    private Object weaver$applyNote(Object cycled) {
        Integer note = weaver$note.get();
        if (note == null) {
            return cycled;
        }
        weaver$note.remove();
        return ((BlockState) cycled).setValue(NoteBlock.NOTE, note);
    }
}
