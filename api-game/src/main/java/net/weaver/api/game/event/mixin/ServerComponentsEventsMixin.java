package net.weaver.api.game.event.mixin;

import net.minecraft.server.ReloadableServerResources;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.DefaultDataComponentsBoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Default components bound after a server data pack load. */
@Mixin(ReloadableServerResources.class)
public abstract class ServerComponentsEventsMixin {
    @Inject(method = "updateComponentsAndStaticRegistryTags()V", at = @At("RETURN"))
    private void weaver$bound(CallbackInfo callback) {
        if (GameEvents.hasListeners(DefaultDataComponentsBoundEvent.class)) {
            GameEvents.post(new DefaultDataComponentsBoundEvent(
                    ((ReloadableServerResources) (Object) this).fullRegistries().lookup(), false), LogicalSide.SERVER);
        }
    }
}
