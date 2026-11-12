package net.weaver.api.game.event.mixin.extension;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link ItemExtension#createItemEntity}: a dropped stack becomes the entity of its item. */
@Mixin(ServerLevel.class)
public abstract class ItemEntityReplaceExtensionsMixin {
    @Unique
    private static final ThreadLocal<Boolean> weaver$replacing = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void weaver$replaceItemEntity(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity.getClass() != ItemEntity.class || weaver$replacing.get()) {
            return;
        }
        ItemEntity item = (ItemEntity) entity;
        if (!(item.getItem().getItem() instanceof ItemExtension extension)) {
            return;
        }
        ServerLevel self = (ServerLevel) (Object) this;
        Entity replacement = extension.createItemEntity(self, item, item.getItem());
        if (replacement == null || replacement == entity) {
            return;
        }
        weaver$replacing.set(Boolean.TRUE);
        try {
            callback.setReturnValue(self.addFreshEntity(replacement));
        } finally {
            weaver$replacing.set(Boolean.FALSE);
        }
    }
}
