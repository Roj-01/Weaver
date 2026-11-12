/**
 * Game-independent networking primitives for the Weaver protocol.
 *
 * <p>The package defines versioned channel identities ({@link net.weaver.api.network.PayloadType}),
 * bounded payload buffers and codecs, a thread-safe registration table, and the
 * login handshake value types. Channel ids always carry the owning mod id and a
 * positive version, for example {@code example:telemetry/v1}, and all lists and
 * strings have hard protocol limits so a malicious peer cannot force unbounded
 * allocation.</p>
 *
 * <p>Nothing in this package references Minecraft or any other game class. The
 * transport implementation lives outside the API module and provides the
 * concrete {@link net.weaver.api.network.NetworkConnection} and
 * {@link net.weaver.api.network.PayloadContext} implementations.</p>
 */
package net.weaver.api.network;
