package net.weaver.api.game.event.mixin;

import net.minecraft.server.ReloadableServerResources;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.TagsUpdatedEvent;
import net.weaver.api.game.event.world.AddServerReloadListenersEvent;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Datapack tags bound to the registries, and the reload listener list. */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesEventsMixin {
    @Inject(method = "updateComponentsAndStaticRegistryTags()V", at = @At("RETURN"))
    private void weaver$tagsUpdated(CallbackInfo callback) {
        // Loot and biome modifier files can name tags and items only now.
        net.weaver.api.game.data.WorldModifierFiles.finish((ReloadableServerResources) (Object) this);
        GameEvents.post(new TagsUpdatedEvent((ReloadableServerResources) (Object) this), LogicalSide.SERVER);
    }

    // Mod listeners from the event run after vanilla's; Weaver's data registry
    // listener runs last, so it sees every pack the reload applies.
    @Inject(method = "listeners()Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void weaver$addListeners(CallbackInfoReturnable<List<PreparableReloadListener>> callback) {
        List<PreparableReloadListener> all = new ArrayList<>(callback.getReturnValue());
        if (GameEvents.hasListeners(AddServerReloadListenersEvent.class)) {
            AddServerReloadListenersEvent event = GameEvents.post(
                    new AddServerReloadListenersEvent((ReloadableServerResources) (Object) this), LogicalSide.SERVER);
            all.addAll(event.added());
        }
        all.add(net.weaver.api.game.data.WorldModifierFiles.listener((ReloadableServerResources) (Object) this));
        all.add(net.weaver.api.game.data.PackDataRegistries.LISTENER);
        callback.setReturnValue(List.copyOf(all));
    }
}
