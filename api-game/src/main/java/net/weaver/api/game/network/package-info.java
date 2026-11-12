/**
 * The Minecraft 26.3 custom-payload bridge for the Weaver networking API.
 *
 * <p>The package has three layers:</p>
 *
 * <ul>
 *   <li>{@link net.weaver.api.game.network.GamePayload} is the single known
 *       custom payload, {@code weaver:bridge}. Its stream codec is appended to
 *       the clientbound and serverbound packet codec lists during class
 *       initialisation, before 26.3 captures those lists.</li>
 *   <li>{@link net.weaver.api.game.network.BridgeEnvelope} and
 *       {@link net.weaver.api.game.network.BridgeSession} are the game-free
 *       wire format and state machine. They can be tested without Minecraft on
 *       the class path.</li>
 *   <li>{@link net.weaver.api.game.network.GameNetworkBridge} maps a Minecraft
 *       {@code Connection} to a session, converts envelopes to game packets,
 *       mirrors phases, and posts the Weaver network events.</li>
 * </ul>
 *
 * <p>The bridge is server-authoritative: a server sends the first bridge
 * message, and an unanswered handshake marks the peer as unsupported instead
 * of disconnecting it. See {@code docs/network.md} for integration order,
 * limits, threading, and known caveats.</p>
 */
package net.weaver.api.game.network;
