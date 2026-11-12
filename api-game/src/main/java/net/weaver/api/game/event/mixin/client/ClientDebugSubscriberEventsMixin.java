package net.weaver.api.game.event.mixin.client;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientDebugSubscriber;
import net.minecraft.util.debug.DebugSubscription;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.AddDebugSubscriptionFlagsEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The debug subscriptions the client asks the server for. */
@Mixin(ClientDebugSubscriber.class)
public abstract class ClientDebugSubscriberEventsMixin {
    @Inject(method = "requestedSubscriptions()Ljava/util/Set;", at = @At("RETURN"), cancellable = true)
    private void weaver$subscriptions(CallbackInfoReturnable<Set<DebugSubscription<?>>> callback) {
        if (GameEvents.hasListeners(AddDebugSubscriptionFlagsEvent.class)) {
            Set<DebugSubscription<?>> subscriptions = new ReferenceOpenHashSet<>(callback.getReturnValue());
            GameEvents.post(new AddDebugSubscriptionFlagsEvent(subscriptions), LogicalSide.CLIENT);
            callback.setReturnValue(subscriptions);
        }
    }
}
