package net.weaver.proof;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.Side;
import net.weaver.api.event.SubscribeEvent;
import net.weaver.api.event.events.ServerTickEvent;

/**
 * Loaded on a client with an integrated server because the logical side is
 * available even though the physical process is a client.
 */
@EventBusSubscriber(side = Side.BOTH, logicalSide = LogicalSide.SERVER)
public final class ProofLogicalSubscriber {

    private ProofLogicalSubscriber() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        if (event.logicalSide() != LogicalSide.SERVER) {
            ProofHooks.event("LogicalServerSubscriber BAD SIDE " + event.logicalSide());
            return;
        }
        ProofHooks.eventOnce("LogicalServerSubscriber");
    }
}
