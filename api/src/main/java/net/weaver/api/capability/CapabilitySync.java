package net.weaver.api.capability;

/**
 * Declares how a capability value participates in network synchronization.
 *
 * <p>{@link #RUNTIME} opts a value into automatic forwarding through the Weaver
 * capability channel. The channel only sends values whose holder has a
 * registered {@link CapabilitySyncProvider} identity and whose capability
 * declares a codec. {@link #ITEM_STACK} and {@link #NONE} are not forwarded
 * automatically; an explicit {@code CapabilitySyncChannel.send} can still
 * transmit a codec-backed {@link #ITEM_STACK} value when the game layer
 * resolves the holder.
 */
public enum CapabilitySync {

    /**
     * Weaver performs no network synchronization for the value.
     *
     * <p>The value stays on the side that created it unless the owning mod
     * synchronizes it through its own channel.
     */
    NONE,

    /**
     * The value is stored on the item stack's {@code minecraft:custom_data}
     * data component and therefore travels only when the game itself syncs that
     * stack.
     *
     * <p>This is a storage declaration, not a Weaver network channel. Runtime
     * attachments on entities, block entities, levels, or chunks need
     * {@link #RUNTIME} to use the Weaver capability channel, or a mod channel.
     */
    ITEM_STACK,

    /**
     * The value is synchronized through the Weaver capability channel.
     *
     * <p>The channel sends clientbound snapshots when a value changes on a
     * holder whose {@link CapabilitySyncProvider} resolves a stable identity.
     * The value must have a codec. The declaration is opt-in and does not
     * change storage: runtime holders remain runtime-only, and item stacks stay
     * in custom data.
     */
    RUNTIME
}
