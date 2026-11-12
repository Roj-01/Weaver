/**
 * Events posted by the Weaver networking transport.
 *
 * <p>The lifecycle is ordered: a {@link net.weaver.api.event.events.network.NetworkHandshakeEvent}
 * is posted during login, then a
 * {@link net.weaver.api.event.events.network.NetworkConnectedEvent} once the
 * connection reaches play. Every inbound payload produces a
 * {@link net.weaver.api.event.events.network.NetworkPayloadEvent} before its
 * registered handler runs, and exactly one
 * {@link net.weaver.api.event.events.network.NetworkDisconnectedEvent} is
 * posted when the connection ends. All events are posted on the game thread.</p>
 */
package net.weaver.api.event.events.network;
