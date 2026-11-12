# Networking API

Weaver 2.2 adds a game-free networking API in `net.weaver.api.network`, a TCP
transport in `net.weaver.core.network`, and four new events in
`net.weaver.api.event.events.network`. The transport is independent of the
Minecraft hook system.

## Status

- The API and core transport are implemented.
- The standalone two-process TCP proof exercises the core transport.
- The Minecraft 26.3 bridge is implemented in the optional `api-game`
  artifact under `net.weaver.api.game.network`. It registers a real
  `weaver:bridge` custom payload codec during packet class initialisation, so
  26.3 no longer turns Weaver packets into a byte-discarding
  `DiscardedPayload`. See [Minecraft 26.3 bridge](#minecraft-263-bridge).
- The bridge is optional. It becomes active only when all of the following are
  true: `api-game` is on the game class path, the loader armed
  `weaver.network.mixins.json` before the Mixin transformer, and
  `GameNetworkBridge.install(gameLoader)` ran. `NetworkFeature.isInstalled()`
  mirrors that state; its `blocker()` carries the failing prerequisite
  otherwise.
- `LoaderAgent` makes the two calls documented in
  [Loader integration](#loader-integration) (`NetworkBridgeInstall.arm` and
  `activate`), so the bridge is live on a running game whenever `api-game` and
  the game classes are present.
- Additivity rule: no existing 2.1 behavior changed. The networking workstream
  adds new packages, types, events, tests, and an example; it does not change
  the existing API or runtime behavior.

The core proof is a plain TCP loopback proof. The bridge contract proof is
game-free. Neither is a Minecraft client or dedicated-server proof; see
[Tests and proof](#tests-and-proof).

## Overview

| Layer | Package | Contents |
|---|---|---|
| Game-free API | `net.weaver.api.network` | Payload identity and codecs, bounded buffers, channel registry, connection and context interfaces, handshake value types, protocol constants, exceptions. |
| Core transport | `net.weaver.core.network` | TCP framing, `NetworkClient`, `NetworkServer`, per-socket sessions, handshake verification, and the legacy 26.3 descriptor manifest. |
| Game bridge | `net.weaver.api.game.network` | `GamePayload` (`weaver:bridge`), `BridgeEnvelope`, `BridgeSession`, `GameNetworkBridge`, and the 26.3 codec/listener mixins. |
| Events | `net.weaver.api.event.events.network` | `NetworkHandshakeEvent`, `NetworkConnectedEvent`, `NetworkPayloadEvent`, and `NetworkDisconnectedEvent`. |
| Proof | `examples/network-proof` | A standalone TCP proof plus a game-free bridge contract proof. |

The API package never references `net.minecraft.*`. The core network package
also has no game imports; the only game names are descriptor strings in
`NetworkFeature`. The game bridge lives only in the optional `api-game`
artifact, which has compile-only game dependencies and never bundles game
classes.

`NetworkFeature` records bridge install state and keeps the historical listener
descriptor manifest. The working bridge is `GameNetworkBridge` plus
`weaver.network.mixins.json`.

## API reference

### Payload identity: `PayloadType<T>`

A `PayloadType<T>` is the immutable identity of one versioned channel. The wire
id is:

```text
<modId>:<path>/v<version>
```

For example, `example:telemetry/v1`.

| Member | Signature | Notes |
|---|---|---|
| `of` | `static <T> PayloadType<T> of(String modId, String path, PayloadCodec<T> codec)` | Uses `NetworkProtocol.CHANNEL_VERSION` (`1`). |
| `of` | `static <T> PayloadType<T> of(String modId, String path, int version, PayloadCodec<T> codec)` | Version must be at least `1`. |
| `modId` | `String modId()` | Owning mod id. |
| `namespace` | `String namespace()` | Always the same as `modId()`. |
| `path` | `String path()` | Path portion only. |
| `version` | `int version()` | Positive channel version. |
| `channel` | `String channel()` | Complete wire id. |
| `codec` | `PayloadCodec<T> codec()` | Codec used on the wire. |

Validation:

- `modId` matches `[a-z0-9_.-]+` and is at most 128 chars.
- `path` matches `[a-z0-9_./-]+`, has no leading or trailing slash, and is at
  most 160 chars.
- The complete channel id is at most 160 chars.
- The codec must not be null.

Equality and hashing use the channel id only; the codec does not participate.
`toString()` returns the channel id.

### Wire codecs: `PayloadCodec<T>`

```java
public interface PayloadCodec<T>
{
    void encode(T value, PayloadBuffer out);

    T decode(PayloadBuffer in);

    default byte[] encode(T value);

    default T decode(byte[] data);
}
```

A codec must be stateless and safe for concurrent use because one instance is
used for every connection that carries the channel. `encode(T, PayloadBuffer)`
and `decode(PayloadBuffer)` must consume exactly the bytes they own.
`decode(byte[])` additionally calls `ensureFullyRead()` and rejects trailing
bytes. Malformed or oversized wire data should raise `NetworkException`.

The default `encode(T)` allocates a `PayloadBuffer.writer()` and returns its
bytes. The default `decode(byte[])` wraps a `PayloadBuffer.reader(data)`.

### Payload buffer: `PayloadBuffer`

`PayloadBuffer` is a bounded, reusable writer or a read-only reader. Writers
grow on demand up to `NetworkProtocol.MAX_PAYLOAD_BYTES`; readers copy their
input, cannot be written, and reject underflow, invalid UTF-8, oversized length
prefixes, and oversized values. Instances are not thread-safe.

| Group | Methods |
|---|---|
| Factories | `writer()`, `writer(int initialCapacity)`, `reader(byte[] data)` |
| State | `readableBytes()`, `position()`, `toByteArray()`, `ensureFullyRead()`, `clear()` |
| Write | `writeBoolean(boolean)`, `writeByte(int)`, `writeInt(int)`, `writeLong(long)`, `writeVarInt(int)`, `writeString(String)`, `writeString(String, int maxChars)`, `writeByteArray(byte[])`, `writeByteArray(byte[], int maxBytes)`, `writeUuid(UUID)` |
| Read | `readBoolean()`, `readUnsignedByte()`, `readInt()`, `readLong()`, `readVarInt()`, `readString()`, `readString(int maxChars)`, `readByteArray()`, `readByteArray(int maxBytes)`, `readUuid()` |

Wire rules:

- Multi-byte integers are big-endian.
- Varints use a 7-bit encoding and reject encodings longer than five bytes on
  read.
- Strings are UTF-8 with a varint byte length. Decoding uses a strict UTF-8
  decoder that reports malformed input instead of replacing it. The default
  string limit is `MAX_STRING_CHARS` (256 Java chars); the limit applies to
  decoded Java chars, not encoded bytes. Embedded NUL characters are allowed.
- Byte arrays are written with a varint length and read with explicit bounds
  checked before allocation.
- UUIDs are written as two big-endian longs, most significant bits first.
- `reader(byte[])` rejects input larger than `MAX_PAYLOAD_BYTES`; `writer(int)`
  rejects initial capacities outside `0..MAX_PAYLOAD_BYTES`.
- A reader cannot be written or cleared. A writer can be cleared and reused.

### Channel registry: `NetworkRegistry`

`NetworkRegistry` is a thread-safe table of payload registrations. Each
combination of direction, phase, and channel may be registered once. The
registry preserves registration order for `registrations()`, `findAnyPhase()`,
and diagnostics; `channels()` returns a sorted, deduplicated snapshot, and the
transport advertises that snapshot in the handshake.

```java
NetworkRegistry registry = new NetworkRegistry();
NetworkRegistry global = NetworkRegistry.global();
```

Registration methods:

```java
<T> PayloadRegistration<T> registerClientbound(String modId, PayloadType<T> type,
                                               PayloadHandler<T> handler);
<T> PayloadRegistration<T> registerClientbound(String modId, PayloadType<T> type,
                                               NetworkPhase phase,
                                               PayloadHandler<T> handler);
<T> PayloadRegistration<T> registerClientbound(String modId, PayloadType<T> type,
                                               NetworkPhase phase, boolean optional,
                                               PayloadHandler<T> handler);

<T> PayloadRegistration<T> registerServerbound(String modId, PayloadType<T> type,
                                               PayloadHandler<T> handler);
<T> PayloadRegistration<T> registerServerbound(String modId, PayloadType<T> type,
                                               NetworkPhase phase,
                                               PayloadHandler<T> handler);
<T> PayloadRegistration<T> registerServerbound(String modId, PayloadType<T> type,
                                               NetworkPhase phase, boolean optional,
                                               PayloadHandler<T> handler);
```

The no-phase overloads register for `NetworkPhase.PLAY`. The no-optional
overloads set `optional` to `false`.

Lookup and inspection:

| Method | Result |
|---|---|
| `Optional<PayloadRegistration<?>> find(PayloadDirection direction, NetworkPhase phase, String channel)` | Exact direction/phase/channel lookup. |
| `Optional<PayloadRegistration<?>> findAnyPhase(PayloadDirection direction, String channel)` | First matching registration in registration order. |
| `List<PayloadRegistration<?>> registrations()` | Immutable snapshot in registration order. |
| `List<PayloadRegistration<?>> registrations(PayloadDirection direction)` | Immutable snapshot for one direction. |
| `List<String> channels()` | Immutable, sorted, deduplicated channel ids. |
| `void clear()` | Removes every registration. |
| `int size()` | Registration count. |

Rules:

- The registration key is direction + phase + channel. A duplicate key raises
  `NetworkRegistrationException` and leaves the existing registration in place.
- The same channel may be registered for different phases and for different
  directions.
- `modId` must equal `type.modId()`; a mismatch raises `IllegalArgumentException`.
- A registration is used both to receive inbound payloads and to send outbound
  payloads. Both endpoints must therefore register both directions they use.
- `optional` is metadata for policy consumers. It does not change delivery.
- `PayloadRegistration.key()` is `<DIRECTION>/<PHASE>/<channel>`, for example
  `SERVERBOUND/PLAY/example:telemetry/v1`. Equality uses direction, phase, and
  channel only.

`PayloadRegistration<T>` exposes `modId()`, `channel()`, `path()`, `version()`,
`direction()`, `phase()`, `optional()`, `type()`, `handler()`, and `key()`.
Registrations are created by `NetworkRegistry`; the constructor is not public.

### Handlers and context

```java
@FunctionalInterface
public interface PayloadHandler<T>
{
    void handle(T payload, PayloadContext context);
}
```

`PayloadContext` exposes:

| Method | Meaning |
|---|---|
| `NetworkConnection connection()` | The connection that received the payload. |
| `NetworkSide side()` | The local receiving side. |
| `NetworkPhase phase()` | The phase in which the payload was delivered. |
| `NetworkPlayer player()` | The remote player on a server; the local player on a client. |
| `<T> void reply(PayloadType<T> type, T payload)` | Sends in the direction opposite to the inbound payload. The reply goes through the same registration, phase, size, and open-state checks as `send`. |

Handlers run on the supplied game executor. An exception is logged as
`payload handler failed channel=<channel> reason=<class>` and does not close the
connection. A handler must not block on I/O, other connections, or the game
thread; expensive work should be scheduled elsewhere.

### Connections

`NetworkConnection` is one open Weaver connection. Implementations are provided
by the transport, not by mods.

| Method | Behavior |
|---|---|
| `NetworkSide side()` | Local side. |
| `NetworkPhase phase()` | Current phase. |
| `NetworkPlayer player()` | Remote player on a server, local player on a client. Before a handshake completes, a server reports the placeholder `connecting` player with the zero UUID. On the Minecraft bridge the server reports the connection's authenticated game profile, never the identity claimed in the handshake request. On the standalone TCP transport there is no authentication, so the server reports the identity from the client's `HELLO`. |
| `boolean isOpen()` | Whether the connection can currently send and receive. |
| `<T> void send(PayloadType<T> type, T payload)` | Resolves a registration for the outgoing direction and current phase, encodes with the registered type's codec, and queues the frame. It does not write to the socket. |
| `void disconnect(String reason)` | Queues an orderly `DISCONNECT` and closes. A blank reason becomes `disconnected`. Calling it on a closed connection has no effect. |
| `void close()` | Closes immediately. Idempotent, and does not declare a checked exception. |

`send` throws `NetworkException` when the connection is closed, when no
registration exists for the outgoing direction and current phase, or when the
encoded payload exceeds `NetworkProtocol.MAX_PAYLOAD_BYTES`. The encoded payload
is validated eagerly before it is queued.

A connection object remains readable after it closes so listeners can inspect
its side, phase, and player. Sending on a closed connection fails.

### Events

All four events are posted on the supplied game executor.

| Event | Where and when | Cancelable | Effect | Accessors |
|---|---|---|---|---|
| `NetworkHandshakeEvent` | Server, during `LOGIN`, after the built-in checks and policy, before the response is sent. | No. Veto with `reject(String reason)`. | The first valid rejection wins; the handshake is rejected. A listener that needs to refuse a peer uses this event. | `request()`, `localMods()`, `reject(String)`, `isRejected()`, `rejectionReason()`. Logical side is always `SERVER`. |
| `NetworkConnectedEvent` | Both sides, once when the connection reaches `PLAY`. | No. | Observation only. | `connection()`, `side()`, `phase()`, `player()`, `remoteMods()`. Logical side follows the local side. |
| `NetworkPayloadEvent` | Both sides, on the game executor before the registered handler runs. | Yes. | Canceling suppresses the registered handler on the receiving side; it does not disconnect the peer. Other listeners can still observe the payload. | `direction()`, `connection()`, `type()`, `channel()`, `payload()`, `side()`, `player()`, `phase()`. Logical side follows the local side. |
| `NetworkDisconnectedEvent` | Both sides, exactly once when the connection closes. | No. | Observation only. The connection may already be closed. | `connection()`, `reason()`, `graceful()`, `side()`, `player()`. Logical side follows the local side. |

`NetworkPayloadEvent.setCanceled(true)` is local to the receiving side. It does
not send anything to the peer and does not prevent listeners registered with
`receiveCanceled` from seeing the event. The registered handler is skipped when
the post returns `false`.

`NetworkHandshakeEvent` is not cancelable because a veto has to carry a precise
client-facing reason. `NetworkConnectedEvent` has no result; listeners that need
to refuse a peer must use the handshake event.

### Directions, phases, sides, and policy

| Type | Values | Helpers |
|---|---|---|
| `PayloadDirection` | `CLIENTBOUND`, `SERVERBOUND` | `destination()`, `source()`, `opposite()`, `of(NetworkSide source, NetworkSide destination)`. |
| `NetworkPhase` | `LOGIN`, `CONFIGURATION`, `PLAY` | `isBefore(NetworkPhase)`, `isAtLeast(NetworkPhase)`, `isTerminal()`, `parse(String)`. |
| `NetworkSide` | `CLIENT`, `SERVER` | `opposite()`, `isClient()`. |
| `UnknownChannelPolicy` | `REJECT`, `IGNORE` | Applied to an inbound payload whose channel is not registered for the inbound direction and current phase. |

`PayloadDirection` is named from the client's point of view: `CLIENTBOUND`
travels from server to client; `SERVERBOUND` travels from client to server.
`PayloadDirection.of` rejects equal source and destination sides.

`NetworkPhase.parse` trims whitespace, ignores case, and accepts `LOGIN`,
`CONFIGURATION`, or `PLAY`.

### Exceptions and protocol constants

`NetworkException` extends `RuntimeException` and has `(String message)` and
`(String message, Throwable cause)` constructors. `NetworkRegistrationException`
extends `NetworkException` and signals a duplicate registration.

| Constant | Value | Meaning |
|---|---|---|
| `NetworkProtocol.NAME` | `weaver-network` | Advertised protocol name. |
| `NetworkProtocol.VERSION` | `1` | Weaver protocol version. |
| `NetworkProtocol.CHANNEL_VERSION` | `1` | Default channel version. |
| `NetworkProtocol.MAX_PAYLOAD_BYTES` | `1048576` | Maximum encoded payload. |
| `NetworkProtocol.MAX_FRAME_BYTES` | `1052672` | Maximum frame body enforced by `NetworkWire` (`MAX_PAYLOAD_BYTES + 4096`). |
| `NetworkProtocol.MAX_QUEUE_DEPTH` | `1024` | Maximum queued outbound frames per connection. |
| `NetworkProtocol.MAX_STRING_CHARS` | `256` | Default payload string limit. |
| `NetworkProtocol.MAX_CHANNEL_CHARS` | `160` | Channel id limit. |
| `NetworkProtocol.MAX_MODS` | `1024` | Handshake mod limit. |
| `NetworkProtocol.MAX_MOD_ID_CHARS` | `128` | Mod id limit. |
| `NetworkProtocol.MAX_MOD_VERSION_CHARS` | `64` | Mod version limit. |
| `NetworkProtocol.MAX_CHANNELS` | `4096` | Handshake channel limit. |
| `NetworkProtocol.MAX_REASON_CHARS` | `512` | Disconnect and rejection reason limit. |
| `NetworkProtocol.MAX_PLAYER_NAME_CHARS` | `64` | Player name limit. |

### Handshake API

| Type | Shape | Notes |
|---|---|---|
| `NetworkModInfo` | `record NetworkModInfo(String id, String version)` | `id` uses the mod-id pattern; `version` is non-blank, at most 64 chars, and free of ISO control characters. `of(id, version)` is a factory. |
| `NetworkPlayer` | `record NetworkPlayer(UUID id, String name)` | `name` is non-blank, at most 64 chars, and free of ISO control characters. `of(id, name)` is a factory. |
| `HandshakeRequest` | `record HandshakeRequest(int protocolVersion, String weaverVersion, String gameVersion, NetworkPlayer player, List<NetworkModInfo> mods, List<String> channels)` | Client's opening message. Lists are copied, immutable, unique, and bounded. `CODEC` is a `PayloadCodec<HandshakeRequest>`. |
| `HandshakeResponse` | `record HandshakeResponse(boolean accepted, String reason, int protocolVersion, String weaverVersion, String gameVersion, List<NetworkModInfo> mods, List<String> channels)` | Server's answer. The `accept` factory uses an empty reason; a rejected response requires a non-blank reason. `CODEC` is a `PayloadCodec<HandshakeResponse>`. |
| `HandshakeDecision` | `record HandshakeDecision(boolean accepted, String reason)` | `accept()` and `reject(String reason)`. A rejected decision requires a non-blank reason. |
| `HandshakePolicy` | `@FunctionalInterface` | `HandshakeDecision evaluate(HandshakeRequest request, List<NetworkModInfo> localMods)`. |

`HandshakeResponse` has two factories:

```java
public static HandshakeResponse accept(int protocolVersion, String weaverVersion,
                                       String gameVersion, List<NetworkModInfo> mods,
                                       List<String> channels);

public static HandshakeResponse reject(String reason, int protocolVersion,
                                       String weaverVersion, String gameVersion,
                                       List<NetworkModInfo> mods,
                                       List<String> channels);
```

`HandshakeRequest.CODEC` encodes the protocol version, Weaver API version, game
version, player UUID and name, mods, and channels. `HandshakeResponse.CODEC`
encodes the accepted flag, reason, versions, mods, and channels. Counts and
lengths are varints, and counts are validated before collection allocation.
Both codecs reject trailing bytes.

`HandshakeCodec` provides the transport entry points:

```java
public static byte[] encodeRequest(HandshakeRequest request);
public static HandshakeRequest decodeRequest(byte[] data);
public static byte[] encodeResponse(HandshakeResponse response);
public static HandshakeResponse decodeResponse(byte[] data);
```

`HandshakeVerifier` provides:

```java
public static HandshakePolicy acceptAll();

public static HandshakeResponse verify(HandshakeRequest request,
                                       List<NetworkModInfo> localMods,
                                       List<String> localChannels,
                                       HandshakePolicy policy,
                                       int expectedProtocolVersion,
                                       String localWeaverVersion,
                                       String localGameVersion);

public static void checkResponse(HandshakeResponse response,
                                 HandshakeRequest request);
```

`verify` runs the server's built-in checks and then the optional policy.
`checkResponse` performs the client's compatibility pass over a server response
and throws `NetworkException` with the exact reason.

### Transport entry points

| Class | Key methods | Required builder settings |
|---|---|---|
| `NetworkClient` | `connect()`, `awaitPlay(long, TimeUnit)`, `handshakeResponse()`, `failureReason()`, `connection()`, `close()` | `gameExecutor`, `player`, and `port`. |
| `NetworkServer` | `start()`, `port()`, `isRunning()`, `connections()`, `close()` | `gameExecutor`. |

Client defaults are `host=127.0.0.1`, the global registry, `EventBus.GAME`, an
empty mod list, `UnknownChannelPolicy.REJECT`, `NetworkProtocol.VERSION`,
`WeaverApi.VERSION` (`2.1.0`), `WeaverApi.GAME_VERSION` (`26.3`), and a 5000 ms
connect timeout. `connect()` opens the socket, queues `HELLO`, and starts the
daemon reader and writer threads; it does not wait for `PLAY`. Calling
`connect()` again on a connected client returns the same instance. A client has
exactly one connection.

Server defaults are `bindAddress=127.0.0.1`, `port=0` (ephemeral), the global
registry, `EventBus.GAME`, an empty mod list, no handshake policy, `REJECT`
unknown-channel policy, `maxConnections=16`, and `handshakeTimeoutMillis=10000`.
`start()` binds the listener and starts the daemon accept thread. The actual port
is available from `port()`. Excess connections are rejected with a `DISCONNECT`
reason of `server full: maximum connections <n>`. A session that has not reached
`PLAY` within `handshakeTimeoutMillis` (for example a socket that never sends
`HELLO`) is disconnected with `handshake timed out after <n> ms` and frees its
connection slot.

`awaitPlay` returns `false` on rejection, disconnect before play, timeout, or
when the client is not connected. `failureReason()` reports the precise
rejection or disconnect reason. `NetworkServer.close()` and
`NetworkClient.close()` are idempotent; a server cannot be restarted after it is
closed.

### Example

The following registers a serverbound `PING` and a clientbound `PONG` on both
endpoints, starts a loopback server, connects a client, and sends one payload.
Both endpoints must register both directions: sending resolves a registration
for the outgoing direction, and the same entry is used on receive.

```java
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;
import net.weaver.core.network.NetworkClient;
import net.weaver.core.network.NetworkServer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

PayloadCodec<String> strings = new PayloadCodec<>()
{
    @Override
    public void encode(String value, PayloadBuffer out)
    {
        out.writeString(value);
    }

    @Override
    public String decode(PayloadBuffer in)
    {
        return in.readString();
    }
};

PayloadType<String> ping = PayloadType.of("example", "ping", strings);
PayloadType<String> pong = PayloadType.of("example", "pong", strings);

NetworkRegistry serverRegistry = new NetworkRegistry();
serverRegistry.registerServerbound("example", ping, (payload, context) ->
        context.reply(pong, payload + "-pong"));
serverRegistry.registerClientbound("example", pong, (payload, context) -> { });

NetworkRegistry clientRegistry = new NetworkRegistry();
clientRegistry.registerServerbound("example", ping, (payload, context) -> { });
clientRegistry.registerClientbound("example", pong, (payload, context) ->
        System.out.println("pong: " + payload));

ExecutorService serverGame = Executors.newSingleThreadExecutor();
ExecutorService clientGame = Executors.newSingleThreadExecutor();

NetworkServer server = NetworkServer.builder(serverRegistry)
        .port(0)
        .gameExecutor(serverGame)
        .mods(List.of(NetworkModInfo.of("example", "1.0.0")))
        .start();

NetworkClient client = NetworkClient.builder(clientRegistry)
        .port(server.port())
        .gameExecutor(clientGame)
        .player(new NetworkPlayer(UUID.randomUUID(), "ExamplePlayer"))
        .mods(List.of(NetworkModInfo.of("example", "1.0.0")))
        .connect();

if (!client.awaitPlay(5, TimeUnit.SECONDS))
{
    throw new IllegalStateException(client.failureReason());
}
client.connection().send(ping, "hello");

client.close();
server.close();
```

In a real client and server these registries live in separate processes; do not
share a registry object across a real network connection.

## Sending to the players that see an entity

`EntityTracking` (in `net.weaver.api.game.network`) sends a payload to the
players the server is tracking an entity for: the players that see it, as
vanilla does for the entity's own packets.

```java
EntityTracking.sendToTracking(boss, PHASE, new Phase(boss.getId(), 2));
EntityTracking.sendToTrackingAndSelf(player, MANA, mana); // also the player itself
```

- `trackingPlayers(entity)` lists those players.
- `sendTo(player, type, payload)` sends to one player, and `connectionOf(uuid)`
  gives a player's Weaver connection.
- Players without Weaver's connection (vanilla clients) are skipped.
- Server thread only.

The client proof spawns a pig next to the player on the integrated server and
checks that the player is among the players tracking it.

## Phases and handshake

The transport implements a three-phase state machine: `LOGIN`, then
`CONFIGURATION`, then `PLAY`.

### Frame types

Every message is a Weaver TCP frame, not a Minecraft packet.

| Frame | Code | Direction | Body |
|---|---|---|---|
| `HELLO` | 1 | client to server | `HandshakeRequest.CODEC` payload. |
| `HANDSHAKE` | 2 | server to client | `HandshakeResponse.CODEC` payload. |
| `CONFIG_READY` | 3 | client to server | Empty. |
| `CONFIG_ACK` | 4 | server to client | Empty. |
| `PAYLOAD` | 5 | either | Varint channel byte length, UTF-8 channel id, one-byte phase ordinal, varint payload byte length, payload bytes. |
| `DISCONNECT` | 6 | either | Reason string with a varint byte length. |

A payload frame carries the sender's current phase ordinal. The receiver
requires that ordinal to equal its own current phase; a mismatch closes the
connection with a precise reason.

### State machine

```text
client                                      server
  |  HELLO (HandshakeRequest)                 |
  |------------------------------------------>|
  |                                           | verify built-ins, policy,
  |                                           | NetworkHandshakeEvent
  |<------------------------------------------|  HANDSHAKE (HandshakeResponse)
  |  checkResponse                            |
  |  CONFIG_READY                             |
  |------------------------------------------>|
  |                                           | phase = PLAY
  |<------------------------------------------|  CONFIG_ACK
  |  phase = PLAY                             |
```

The server accepts one `HELLO` in `LOGIN`, verifies it, and either rejects it or
sets its phase to `CONFIGURATION` and queues `HANDSHAKE`. The client accepts one
`HANDSHAKE` in `LOGIN`, checks the response, sets its phase to `CONFIGURATION`,
and queues `CONFIG_READY`. The server moves to `PLAY`, queues `CONFIG_ACK`, and
posts `NetworkConnectedEvent`. The client moves to `PLAY` on `CONFIG_ACK` and
posts its own `NetworkConnectedEvent`. The server supports at most
`maxConnections` simultaneous open sessions.

`PAYLOAD` frames are accepted only after the local side has verified the
handshake (the server accepted the request, or the client accepted the
response). A `PAYLOAD` frame received earlier is dropped and closes the
connection with `unexpected PAYLOAD frame before handshake verification`; no
handler ever sees the placeholder `connecting` player. Likewise `send` throws
`connection is not ready: the handshake has not completed` before verification.

Unexpected or duplicate handshake frames are protocol failures. The reader side
closes with a precise `unexpected HELLO frame...`, `unexpected HANDSHAKE
frame...`, `unexpected CONFIG_READY frame...`, or `unexpected CONFIG_ACK
frame...` reason.

### Handshake message contents

| Field | Request | Response |
|---|---|---|
| Protocol version | `int protocolVersion`, at least `1` | `int protocolVersion`, at least `1` |
| Weaver API version | `String weaverVersion`, non-blank, at most 256 chars | Same |
| Game version | `String gameVersion`, non-blank, at most 256 chars | Same |
| Player identity | `NetworkPlayer` (UUID and display name) | Not present |
| Mods | Up to 128 unique `NetworkModInfo` entries | Up to 128 unique entries |
| Channels | Up to 512 unique channel ids | Up to 512 unique ids |
| Accepted flag | Not present; a request is always a request | `boolean accepted` |
| Rejection reason | Not present | `String reason`, empty when accepted, non-blank when rejected |

The handshake therefore exchanges the protocol version, Weaver API version, and
game version in both directions, the player identity from client to server, and
the mod ids, mod versions, and channel ids in both directions.

The current defaults are `NetworkProtocol.VERSION = 1`,
`WeaverApi.VERSION = 2.1.0` (the API compatibility version), and
`WeaverApi.GAME_VERSION = 26.3`.

### Built-in server checks

`HandshakeVerifier.verify` runs these checks in order and returns the first
rejection. Rejection reasons are clipped to `MAX_REASON_CHARS` (512 chars).

| Order | Check | Exact reason |
|---|---|---|
| 1 | Client protocol version equals the server's expected version. | `protocol mismatch: client=<client>, server=<server>` |
| 2 | Game version strings are equal. | `game version mismatch: client=<client>, server=<server>` |
| 3 | Client Weaver version is compatible: major `2` and at least `2.1.0`. | `weaver version mismatch: client=<client>, server=<server> (requires API 2.1.0 or newer within major 2)` |
| 4 | Every server mod is present on the client. | `missing required mod: <id> (server=<version>)` |
| 5 | Every server mod has the server version on the client, or a version inside the mod's `compatibleVersions` range. | `mod version mismatch: <id> (client=<clientVersion>, server=<serverVersion>)` |

**Compatible versions.** By default a client needs the same version of each
shared mod as the server. A mod whose releases can play together declares a
range in `weaver.mod.json`:

```json
{ "id": "examplemod", "version": "1.4.0", "compatibleVersions": ">=1.2.0 <2.0.0" }
```

The server decides with its own copy's range: two versions may join when both
are inside it. A client accepts what the server accepted, unless its own copy
declares a range that leaves out the server's version. So a new server with a
range accepts older clients released before the range existed. The range uses the same forms as dependencies (`>=`, `<`, `~`, `^`, and Maven
ranges); an invalid range rejects the mod at load. The registry check still
runs, so versions whose registries differ are still refused with that reason.
`WeaverMods.networkCompatible(id, localVersion, peerVersion)` gives the same
answer to mod code.

For example, protocol version `2` against a server at version `1` produces:

```text
protocol mismatch: client=2, server=1
```

Extra client-only mods are allowed. Server mods must be present at the exact
advertised version on the client. Server mods are checked in local registration
order.

### Veto and policy

`HandshakePolicy` is evaluated on the game thread after the built-in checks. It
must be pure and thread-safe: it may inspect the request and the immutable local
mod list, but it must not mutate the world, registries, or the connection. A
`null` decision is rejected with
`handshake policy returned no decision`; a rejected decision uses its
non-blank reason.

`NetworkHandshakeEvent` is posted on the server after the built-in checks and
policy accept, before the response is sent. It is not cancelable. Listeners veto
through `reject(String reason)`:

- The first valid rejection wins; later calls are ignored.
- A blank or `null` reason is rejected by the event, and a reason longer than
  512 chars raises `IllegalArgumentException`.
- If the event bus throws, the handshake is rejected with
  `handshake event failed: <message>`.
- The event's logical side is always `SERVER`.

The first rejection wins. A policy rejection prevents the event from being
posted. An event veto replaces the accepted response with a rejected response.
The server queues the rejected `HANDSHAKE` frame followed by `DISCONNECT`, then
closes the session gracefully.

On the client, a rejection surfaces as:

```text
handshake rejected: <reason>
```

`NetworkClient.failureReason()` holds that full string, and
`handshakeResponse()` contains the rejected response with the server's exact
reason.

### Client-side response checks

`HandshakeVerifier.checkResponse` runs on the client after a `HANDSHAKE` frame:

| Check | Exact rejection |
|---|---|
| Server rejected the client. | `handshake rejected: <server reason>` |
| Protocol differs. | `protocol mismatch: client=<client>, server=<server>` |
| Game version differs. | `game version mismatch: client=<client>, server=<server>` |
| Server Weaver version is incompatible. | `weaver version mismatch: client=<client>, server=<server> (requires API 2.1.0 or newer within major 2)` |
| A server-advertised mod is missing from the client. | `server requires mod: <id> (server=<version>)` |
| A server-advertised mod has a different client version. | `mod version mismatch: <id> (client=<clientVersion>, server=<serverVersion>)` |

### Configuration phase

The transport has a real `CONFIGURATION` phase and a readiness exchange: the
client sends an empty `CONFIG_READY`; the server replies with an empty
`CONFIG_ACK`. Channels may be registered for `CONFIGURATION`, but this
workstream exposes no configuration-phase lifecycle event and implements no
built-in configuration data exchange. The phase is a readiness barrier only.

Minecraft 26.3 has configuration-phase custom payload packets at the protocol
level, but no mod payload registry or mod dispatch. The game consequences are
covered in [Minecraft 26.3 integration](#minecraft-263-integration-not-installed).

## Threading model

`NetworkClient` and `NetworkServer` both require a `gameExecutor`. It must be a
real game-thread executor. Do not pass a network-thread executor or a direct
executor unless the caller is already the game thread.

| Work | Thread |
|---|---|
| Socket accept loop | Daemon accept thread, `weaver-net-accept-<id>`. |
| Frame reads, handshake envelope and control-frame decoding, channel metadata extraction, payload phase extraction, phase transitions, unknown-channel policy | Daemon reader thread, `weaver-net-read-<id>`. |
| Handshake verification and policy, `NetworkHandshakeEvent`, client response checks, payload decoding, `NetworkPayloadEvent`, payload handlers, `NetworkConnectedEvent`, `NetworkDisconnectedEvent` | Supplied `gameExecutor`. |
| Socket writes and flushes | Daemon writer thread, `weaver-net-write-<id>`. |
| `send` validation, encoding, and queue insertion | The calling thread. |

The reader thread handles framing, handshake envelopes, control frames, and
transport phase transitions. It never calls a payload codec, payload handler, or
event bus. It parses channel metadata, applies the unknown-channel policy, and
hands accepted payload bytes to the game executor. The writer thread only writes
socket output. No game state is touched on a network thread.

Outbound frames are queued in a bounded `LinkedBlockingQueue` with capacity
`MAX_QUEUE_DEPTH` (1024). `send` uses a non-blocking offer; it never blocks on
socket I/O. When the queue is full, the session logs `outbound queue overflow`,
closes, and the sender receives `NetworkException("outbound queue overflow")`.

Failure isolation:

- A payload handler exception is logged as
  `payload handler failed channel=<channel> reason=<class>` and does not kill the
  connection.
- A payload decode failure or a codec returning `null` logs
  `malformed payload channel=<channel>` and closes with
  `malformed payload: <channel>`.
- If the game executor rejects a task, the session closes with
  `game executor rejected task: <purpose>`.
- A handler that throws is not retried and does not disconnect the peer by
  itself.

`NetworkClient.connect()` returns before `PLAY`. Callers use
`awaitPlay(long, TimeUnit)`, which blocks the calling thread until the play
latch fires, the connection closes, or the timeout expires. Do not call
`awaitPlay` from the executor thread if that thread is the only thread that
pumps the executor: the verification and play tasks cannot run, and the call can
only time out (or hang with an unbounded timeout).

The game executor is also used for `NetworkDisconnectedEvent`, which is posted
exactly once per session. A closed connection can still be inspected for its
side, phase, and player.

## Security and bounds

Framing:

- Every frame starts with an 11-byte header: four-byte magic `0x57455632`
  (`WEV2`), two-byte unsigned wire version `1`, one-byte frame type, and
  four-byte signed big-endian body length.
- The magic, wire version, frame type, and body length are validated before the
  body is allocated. A negative length is rejected; a body larger than
  `MAX_FRAME_BYTES` is rejected before allocation.
- `NetworkWire` applies `MAX_FRAME_BYTES` to the frame body. The 11-byte header
  is outside that check, so the largest encoded frame is
  `MAX_FRAME_BYTES + 11` bytes.
- Frame bodies are copied by the frame constructor and by `NetworkWire.encode`.

Payload bounds:

- Maximum encoded payload: `MAX_PAYLOAD_BYTES` (1,048,576 bytes).
- Maximum queued frames per connection: `MAX_QUEUE_DEPTH` (1024).
- Maximum inbound payloads waiting for the game executor per connection: 256
  frames or 16 MiB of payload bytes, whichever is reached first. A peer that
  sends faster than the game thread drains is disconnected with
  `inbound payload backlog exceeded: ...`.
- Handshake deadline: a server session must reach `PLAY` within
  `handshakeTimeoutMillis` (default 10000 ms). There is no idle timeout after
  `PLAY`; the protocol has no keepalive frame.
- A graceful close (`disconnect`, a protocol failure, or a handshake rejection)
  lets the writer drain queued frames for at most 2 seconds, then closes the
  socket forcibly, so a peer that stopped reading cannot pin the writer thread
  and socket.
- Maximum handshake mods: 128. Maximum handshake channels: 512.
- Maximum channel id: 160 chars. Maximum mod id: 128 chars. Maximum mod
  version: 64 chars. Maximum player name: 64 chars. Maximum reason: 512 chars.
- Default payload string limit: 256 Java chars; encoded byte length is checked
  before allocation.
- Strict UTF-8 is used for payload strings and channel ids; malformed input is
  rejected.
- Varints are capped at five bytes on read.
- The packet path calls `PayloadCodec` and `PayloadHandler` through their
  interfaces; it does not use reflection.

Unknown channels and phase errors:

| Situation | Behavior |
|---|---|
| Unknown channel with `UnknownChannelPolicy.REJECT` | Sends `DISCONNECT` with `unknown channel: <channel>` and closes. |
| Unknown channel with `UnknownChannelPolicy.IGNORE` | Logs `dropped payload unknown-channel=<channel> phase=<phase>` and drops the payload without disconnecting. |
| Channel registered for another phase | Closes with `channel <channel> is not registered for phase <phase>; it is registered for phase <otherPhase>`. |
| `send` with a channel registered only for another phase | Throws `channel <channel> is not registered for phase <phase>; it is registered for phase <otherPhase>`. |
| `send` with a channel registered only for the opposite direction | Throws `channel <channel> is not registered for direction <direction>; it is registered for direction <oppositeDirection>`. |
| `send` with a channel registered nowhere | Throws `channel <channel> is not registered for direction <direction> in phase <phase>`. |

Optional metadata:

- `PayloadRegistration.optional()` and the `optional` registration overloads are
  metadata for policy consumers.
- The default handshake verifier validates the mod list and versions. It does
  not validate channel lists and does not use the `optional` flag.
- `NetworkHandshakeEvent` and `HandshakePolicy` do not receive registration
  metadata. A policy that has access to the registry can inspect the optional
  flags itself, but no default behavior consumes them.
- Channel ids are advertised in `HandshakeRequest` and `HandshakeResponse`, but
  channel lists are not compatibility-enforced. A peer may advertise a channel
  that the other side does not have; payload dispatch still follows the local
  registry.
- `NetworkHandshakeEvent` exposes the request and the server's immutable local
  mods, not the local channel list. A policy can inspect the request's channel
  list, but no default behavior consumes it.

Non-goals in this workstream:

- No authentication, certificates, account verification, or TLS.
- No encryption. The transport carries plain TCP bytes; any encryption is
  outside this workstream.
- No proxy support.
- No cross-loader compatibility layer.
- No rate limiter beyond the bounded queue and payload/frame limits.
- No compression.

Minecraft 26.3 fallback limits:

- For an unknown clientbound custom payload, 26.3 decodes a fallback
  `DiscardedPayload` with a maximum of 1,048,576 bytes.
- For an unknown serverbound custom payload, 26.3 uses a fallback maximum of
  32,767 bytes.
- The bridge's `weaver:bridge` type is known once the codec mixins run, so the
  32,767-byte fallback does not apply to it. Its own body limit is
  `GamePayload.MAX_BRIDGE_BODY` (1,047,552 bytes), still bounded by the 26.3
  frame limit.
- Do not promise the fallback limit for arbitrary unknown 26.3 channels.

## Minecraft 26.3 bridge

The bridge is implemented in `api-game` under
`net.weaver.api.game.network`. It has three parts: one registered custom payload
(`GamePayload`), one game-free envelope and state machine (`BridgeEnvelope` and
`BridgeSession`), and game mixins plus a connection map (`GameNetworkBridge`).

### Why codec registration is mandatory

Minecraft 26.3 turns an unknown custom payload into a `DiscardedPayload` whose
record has only an `Identifier id`. The fallback codec checks the remaining byte
count, calls `skipBytes` on the entire body, and returns the id-only record. The
bytes are gone before any listener runs. `ClientCommonPacketListenerImpl`
returns early for a `DiscardedPayload`, and both server custom-payload handlers
have empty bodies. Listener hooks alone cannot carry Weaver channels.

The bridge therefore registers a known type, `weaver:bridge`, *inside* the
packet class initialisers, before the protocol codecs capture their type lists.
The verified codec call is:

```text
net.minecraft.network.protocol.common.custom.CustomPacketPayload#codec
(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;
```

The registration mixins use `@ModifyArg` on `<clinit>` at index `1`:

- `ClientboundCustomPayloadPacketMixin`, `require = 2`: 26.3 builds
  `GAMEPLAY_STREAM_CODEC` from a mutable `Lists.newArrayList` list and
  `CONFIG_STREAM_CODEC` from an immutable `List.of`, so both calls are matched
  and each receives a fresh copied list with `GamePayload` appended.
- `ServerboundCustomPayloadPacketMixin`, `require = 1`: 26.3 builds the single
  `STREAM_CODEC` from `List.of`.

Both injectors match the exact interface invocation descriptor. They do not
target synthetic lambdas or class-initializer helper names.

### Payload and envelope

`GamePayload` is a record implementing `CustomPacketPayload`:

| Member | Value |
|---|---|
| id | `Identifier.fromNamespaceAndPath("weaver","bridge")` |
| type | `CustomPacketPayload.Type<GamePayload>` |
| codec | `CustomPacketPayload.codec(GamePayload::write, GamePayload::new)` |
| body limit | `MAX_BRIDGE_BODY = 1_047_552` bytes |

The record copies its body on construction and on every access. The codec uses
`FriendlyByteBuf.writeByteArray` and `readByteArray(MAX_BRIDGE_BODY)`.

`BridgeEnvelope` is the bridge's own wire format. Every kind
(`HELLO`, `REQUEST`, `RESPONSE`, `PAYLOAD`, `DISCONNECT`) writes:

```text
envelope version (1 byte)
kind ordinal (1 byte)
Weaver protocol version (varint)
phase ordinal (1 byte)
channel id (strict UTF-8 string, 160 chars)
payload bytes (varint length, at most 1_043_456)
handshake bytes (varint length, at most 131_072)
reason (strict UTF-8 string, 512 chars)
```

Kind-specific validation rejects unused fields, so a `PAYLOAD` cannot also carry
a handshake and a `DISCONNECT` cannot carry channel bytes. Decoding rejects an
unknown envelope version, an unknown kind or phase ordinal, malformed UTF-8,
oversized lengths, truncation, and trailing bytes. It throws `NetworkException`
only inside the bridge; the listener mixins catch it and close the session.

### Handshake flow

The handshake is server-first so vanilla peers are never probed or disconnected:

```text
server (configuration)                       client (configuration)
  |  HELLO (HandshakeResponse-like announce)   |
  |------------------------------------------->|
  |                                            |  build HandshakeRequest
  |<-------------------------------------------|  REQUEST
  |  verify built-ins, policy, event           |
  |<-------------------------------------------|  (rejection or acceptance)
  |------------------------------------------->|  RESPONSE
  |  verified                                  |  checkResponse, verified
  |  PLAY ready -> NetworkConnectedEvent       |  PLAY ready -> NetworkConnectedEvent
```

The server sends `HELLO` at the tail of its configuration
`Connection.setupInboundProtocol`, after 26.3 has switched the outbound protocol
to configuration. The client sends `REQUEST` at the tail of
`ClientHandshakePacketListenerImpl.handleLoginFinished`, after its outbound
protocol is configuration; if `HELLO` arrives earlier, the request is pending
and is sent at that tail. The server verifies with the same built-in order and
messages as the core transport, posts `NetworkHandshakeEvent`, and answers.
The client runs the response compatibility pass and throws the exact
`handshake rejected: ...`, `protocol mismatch: ...`, or mod messages.

The server never trusts the player identity in the client's `REQUEST`. The
bridge supplies each server session with the `GameProfile` the game connection
authenticated during login (`ServerCommonPacketListenerImpl.getOwner()`, read
from the configuration or play listener), and `BridgeSession` checks it before
the built-in checks, policy, and `NetworkHandshakeEvent`. A request whose UUID
differs is rejected with `player identity mismatch: request claimed <uuid> but
the connection is authenticated as <uuid>`; a request that arrives before an
authenticated profile is known is rejected with `player identity unavailable:
...`. After acceptance, `player()` on the connection, `PayloadContext`, and the
network events report the authenticated UUID and name, and the handshake event
and policy see a request carrying that identity. The client sends the profile
from `ClientboundLoginFinishedPacket`, which is the identity the server
assigned (on an offline-mode server this is the offline UUID, not the local
account UUID). Code that builds a server `BridgeSession` directly must supply
the identity with `Builder.authenticatedPlayer(...)` or
`setAuthenticatedPlayer(...)`.

A peer that never answers is marked unsupported and its session is removed at
play time. The game connection is not disconnected, no connected event is
posted, and no disconnected event is posted. A late `REQUEST` can still create a
fresh session and complete the handshake.

`NetworkConnectedEvent` is posted once per side when that side has both reached
`PLAY` and verified the handshake. `NetworkDisconnectedEvent` is posted at most
once, and only for a session that posted a connected event. Reconfiguration does
not reset verification and does not post a second connected event.

### Mod set and registry check on join

The loader advertises every installed mod whose metadata `side` is `both`
(`NetworkBridgeInstall.activate` calls `GameNetworkBridge.setLocalMods`).
The built-in handshake checks then require each mod the server advertises
on the client at the same version (`missing required mod: examplemod
(server=1.2.0)`, `mod version mismatch: examplemod (client=1.1.0,
server=1.2.0)`). Client-only and server-only mods are not advertised, and a
client may have extra mods.

Matching mods do not guarantee matching numeric registry ids (a config can
change what a mod registers, and registration order decides the ids). So right
after the server verifies the handshake, still in the configuration phase and
before any world data, it sends a `RegistryFingerprint` on the
configuration-phase channel `weaver:registry_check` (from a hook in
`BridgeSession`, not from `NetworkConnectedEvent`, which fires only in
`PLAY`). For every game registry with entries outside `minecraft` the
fingerprint holds the entry count and a SHA-256 digest of those ids in
numeric id order, plus the id lists themselves while they fit about 512 KiB.
The client builds its own and compares (`RegistrySyncCheck`). On any
difference it disconnects with the first one:

```text
registry minecraft:item differs from the server: missing here examplemod:ruby
registry minecraft:block differs from the server: not on the server othermod:lamp
registry minecraft:item differs from the server: same entries in a different order (...)
registry minecraft:item differs from the server: 41200 modded entries on the server, 41199 here
```

The last form is used when the id lists were too large to send; the digest
still catches any difference, so large modpacks are checked too.

Ids are not remapped; a mismatch is refused. The client logs
`[weaver] REGISTRY CHECK ok=true registries=<n> entries=<n>` on success, and
`RegistrySyncCheck.lastResult()` exposes the result. If the server cannot
send its fingerprint it logs `REGISTRY CHECK send FAILED` and disconnects the
client rather than let it in unchecked. The client proof requires the check to pass
for its single-player join. `examples/multiplayer` (gate step `H-multiplayer`)
runs a dedicated server and separate clients: a matching client joins and
passes the check before `PLAY`; a client with one extra item is refused by
it; a client with another mod version, or without the mod, is refused by the
handshake. A handshake refusal on the server goes through the server packet
listener's `disconnect`, which sends the reason to the client before closing,
so the player sees it (`Client disconnected with reason: mod version
mismatch: ...`).

### Phases, threading, and dispatch

| Trigger | Mixin | Phase effect |
|---|---|---|
| `Connection.setupInboundProtocol(ProtocolInfo, PacketListener)` | `ConnectionMixin` `TAIL` | Create/update session; mirror `LOGIN`, `CONFIGURATION`, or `PLAY`; server sends `HELLO` on the first configuration entry. |
| `ClientCommonPacketListenerImpl.handleCustomPayload(ClientboundCustomPayloadPacket)` | `ClientCommonPacketListenerMixin` `HEAD`, cancellable | Ensure client thread, decode `GamePayload`, dispatch, cancel vanilla. |
| `ServerCommonPacketListenerImpl.handleCustomPayload(ServerboundCustomPayloadPacket)` | `ServerCommonPacketListenerMixin` `HEAD`, cancellable | Ensure server thread, decode, dispatch, cancel vanilla. |
| `ServerGamePacketListenerImpl.handleCustomPayload(ServerboundCustomPayloadPacket)` | `ServerGamePacketListenerMixin` `HEAD`, cancellable | Same for the play-phase override. |
| `ClientHandshakePacketListenerImpl.handleLoginFinished(ClientboundLoginFinishedPacket)` | `ClientHandshakePacketListenerMixin` `TAIL` | Mark client outbound configuration ready; supply the server-assigned profile from the packet (falling back to the local user); send pending `REQUEST`. |
| `ClientPacketListener.handleLogin(ClientboundLoginPacket)` | `ClientPacketListenerMixin` `TAIL` | Mark client play ready; post connected; drain queue. |
| `PlayerList.placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)` | `PlayerListMixin` `TAIL` | Mark server play ready; post connected; drain queue. |
| `Connection.handleDisconnection()` | `ConnectionMixin` `RETURN` | Remove session; post at most one disconnected event. |

Every listener body first calls
`PacketUtils.ensureRunningOnSameThread(packet, listener, processor)` using
`Minecraft#packetProcessor()` or `MinecraftServer#packetProcessor()`. The
`RunningOnDifferentThreadException` is rethrown so the game reschedules the
packet; every other failure is logged as
`[weaver] NETWORK BRIDGE FAILED ...` and never escapes the Mixin.

Payloads received in `CONFIGURATION` after verification are dispatched
immediately. Payloads received in `PLAY` before the connected event are queued
and drained after `NetworkConnectedEvent`. A queued envelope whose phase no
longer matches is dropped with a diagnostic. Handlers run on the game thread.

### Limits and failure semantics

- Clientbound and serverbound bridge bodies are bounded by
  `GamePayload.MAX_BRIDGE_BODY` (1,047,552 bytes). The serverbound 32,767-byte
  bound in 26.3 applies only to the unknown `DiscardedPayload` fallback; a
  registered codec is not subject to it. The 26.3 frame limit still applies.
- Envelope channel payloads are bounded by `BridgeEnvelope.MAX_PAYLOAD_BYTES`
  (1,043,456 bytes); handshakes by 131,072 bytes; reasons by 512 chars.
- Before play, at most 64 envelopes or 1,048,576 estimated bytes are queued.
  Overflow fails the session with `inbound queue overflow`.
- Each session has a inbound budget of 8 MiB that refills at 1 MiB a second. Exhaustion fails the
  session with `inbound byte budget exhausted`.
- Unknown channels follow `UnknownChannelPolicy`: `REJECT` disconnects with
  `unknown channel: <channel>`; `IGNORE` logs
  `[weaver] NETWORK BRIDGE dropped payload ...`. A channel registered for a
  different phase produces the same precise phase message as the core transport.
- A handler exception is logged as
  `[weaver] NETWORK BRIDGE payload handler failed channel=... reason=<class>`
  and does not disconnect the peer.
- A malformed payload decode closes with `malformed payload: <channel>`.
- A handshake veto sends a rejected `RESPONSE` and then disconnects with the
  bounded reason. The client surfaces `handshake rejected: <reason>`.

### Loader integration

`GameNetworkBridge.install(ClassLoader)` and
`GameNetworkBridge.mixinConfigResource()` are the public activation API.
`NetworkBridgeInstall` in `net.weaver.core.network` wraps them so
`LoaderAgent`, which has no compile-time `api-game` dependency, can arm and
activate the bridge reflectively. The integrator must make exactly these two
insertions in `core/src/main/java/net/weaver/core/LoaderAgent.java`; they are not
present in this tree yet.

After `engine.boot(space.gameLoader());` (currently line 129) and before
`MixinBridge.setInstrumentation(instrumentation);` (currently line 133):

```java
boolean networkBridgeArmed = NetworkBridgeInstall.arm(space.gameLoader(),
        (name, bytes) -> engine.addConfig("weaver-network", name, bytes,
                space.gameLoader()), log);
```

After
`instrumentation.addTransformer(new WeaverMixinTransformer(engine), false);`
(currently line 134) and before game main:

```java
NetworkBridgeInstall.activate(space.gameLoader(), networkBridgeArmed, log);
```

Add `import net.weaver.core.network.NetworkBridgeInstall;`. The first call reads
`weaver.network.mixins.json` from the agent class path and arms it before any
game class can initialize. The second loads `GameNetworkBridge` after the
transformer exists, so class verification cannot define a packet class
untransformed. The bridge logs `[weaver] NETWORK BRIDGE installed=true ...` or a
precise blocker. It never fails boot.

### Feature state

`NetworkFeature.install(boolean mixinsArmed, boolean gameClassesPresent)` records
the two prerequisites. `isInstalled()` is true only when both are true;
`blocker()` returns an empty string when installed, otherwise
`weaver.network.mixins.json was not armed ...` or
`Minecraft 26.3 custom payload classes are not present ...`. The default,
uninstalled blocker still explains the `DiscardedPayload` problem. The bridge
also performs a lazy codec round-trip after the first inbound bridge packet; if
`weaver:bridge` decodes as a `DiscardedPayload`, it disables itself and logs
`[weaver] NETWORK BRIDGE CODEC missing ...`.

### Known caveats

- The bridge is one custom payload, `weaver:bridge`. Every Weaver channel shares
  it; channel ids travel inside the envelope.
- `GameNetworkBridge.install` can prove that the mixin resource was present and
  that game classes were found, but only the lazy codec round-trip proves the
  codec injector ran. A failed round-trip disables the bridge.
- The default local mod list is empty because `api-game` has no loader metadata
  source. Call `GameNetworkBridge.setLocalMods(...)` and
  `setHandshakePolicy(...)` before players connect if compatibility must enforce
  mod identity.
- The bridge does not expose login-phase custom payloads; the handshake runs in
  configuration, matching 26.3's packet registry.
- A vanilla peer receives one unknown clientbound `weaver:bridge` payload per
  configuration and ignores it. The server never disconnects that peer.
- Reconfiguration keeps the verified state. A second configuration pass does not
  re-send `HELLO` and does not post a second connected event.
- The per-session inbound budget refills at 1 MiB a second (8 MiB burst). A peer that sends
  more than that for long enough is disconnected.
- `NetworkBridgeInstall.arm` reports that the config was handed to the engine,
  not that the engine applied every injector. The engine's own
  `MIXIN CONFIG`/`MIXIN APPLIED` markers remain the audit source.

## Joining servers through Weaver Hub

A Weaver server tells launchers what it runs, and a client started by Weaver
Hub tells the Hub when a server needs another set of mods. Game and server never
send mod files to each other: the Hub fetches them from the registry, checked by
SHA-256, and restarts the game into the server.

### The status advertisement

A Weaver server's status reply (the server-list ping) carries a `weaver` field
next to the vanilla ones:

```json
"weaver": {
  "loader": "2.3.2", "api": "2.1.0",
  "mods": [ { "id": "aoso_mobs", "version": "1.11.0", "sha256": "…" } ],
  "truncated": false
}
```

- Only mods with side `both` are listed. Client-only and server-only mods are left out.
- `sha256` is the hash of the exact jar the server loaded, computed at boot.
- The list is cut to about 20,000 characters to fit the status packet; when it
  is cut, `truncated` is true.
- Vanilla clients ignore the field.

Implemented by `StatusAdvertisementMixin` (it wraps `ServerStatus.CODEC` in
`ClientboundStatusResponsePacket`) and `StatusAdvertisement`. Both are internal.

### What the client tells the Hub

When the Hub started the game (`-Dweaver.hub=true`), the client prints one line
for the Hub:

- **`server-mods`**: when a Weaver server's HELLO lists mods the client lacks, or
  versions the server would refuse (the same `networkCompatible` rule the server
  applies):

  ```text
  [weaver-hub] {"type":"server-mods","server":"host:port","missing":[…],"different":[…]}
  ```

  The server's own refusal still follows.
- **`vanilla-server`**: when the connection reaches play without a Weaver
  handshake (a vanilla server) while mods with side `both` are loaded:

  ```text
  [weaver-hub] {"type":"vanilla-server","server":"host:port","paused":[…]}
  ```

  The client then closes the connection with a message saying the Hub is
  restarting it with the client-only mods.

Without the Hub, neither line is printed and joins behave as before. A vanilla
server is still joined with every mod loaded.

### What the Hub does

Weaver Hub (`electron/join/`):

1. Pings the server.
2. Compares the server's mods with the instance's own (by SHA-256, or by id and
   version for servers without fingerprints).
3. Shows the player what it will do.
4. **For a Weaver server:** downloads each missing file from
   `<registry>/blobs/<sha256>`. A file whose hash differs from the server's is
   refused and nothing is changed. Then it moves older versions to
   `mods-disabled/`.
5. **For a vanilla server:** links only the client-only jars into a
   one-session folder. The instance's `mods/` is untouched.
6. Restarts the game with `--quickPlayMultiplayer`.

## Tests and proof

The network tests are ordinary JUnit 5 tests next to the module tests. This
workstream wrote the new tests but did not run Gradle, so no pass/fail totals are
claimed here.

| Module | Network test classes | Scope |
|---|---|---|
| `api` | `PayloadTypeTest`, `PayloadCodecTest`, `PayloadBufferTest`, `NetworkRegistryTest`, `HandshakeCodecTest`, `NetworkEventTest` | Existing API behavior, unchanged. |
| `core` | `HandshakeVerifierTest`, `NetworkWireTest`, `NetworkLoopbackTest`, `NetworkSafetyTest`, `NetworkEventTest`, `NetworkFeatureTest`, `NetworkBridgeInstallTest`, `NetworkHardeningTest` | Existing transport coverage plus updated `NetworkFeature` install state and loader helper failure isolation; handshake deadline, pre-verification payload rejection, inbound backlog limit, and forced close of a draining session whose peer stopped reading. |
| `api-game` | `BridgeEnvelopeTest`, `BridgeHandshakeVerifierTest`, `BridgeSessionTest`, `GamePayloadTest` | Envelope bounds and malformed input, handshake messages and policy, server-first session state machine with fakes, queue/drain ordering, unknown-channel policy, payload reply, disconnect counts, authenticated player identity (mismatch and missing-profile rejection), and the real `weaver:bridge` record/codec. |
| `examples/network-proof` | `BridgeContractProof` | Game-free contract run over the real envelope codec and state machine. |

Run the modules:

```powershell
.\gradlew.bat :api:test --no-daemon
.\gradlew.bat :core:test --no-daemon
.\gradlew.bat :api-game:test --no-daemon
```

The standalone core proof uses separate server and client processes; it is not a
Minecraft proof. It starts a real `NetworkServer` on an ephemeral loopback port,
then runs `ProofClient` and `ProofMismatch` as separate JVMs.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\network-proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\network-proof\run.ps1
```

Expected core markers:

```text
NETWORK PROOF BUILD ok=true jar=<path>
NETWORK SERVER READY port=<port>
NETWORK SERVER CHECK ok=true serverbound payload=hello thread=network-proof-server-game
NETWORK SERVER RESULT ok=true checks=1
NETWORK PROOF CHECK ok=true phase
NETWORK PROOF CHECK ok=true side
NETWORK PROOF CHECK ok=true player
NETWORK PROOF CHECK ok=true handshake
NETWORK PROOF CHECK ok=true server-mods
NETWORK PROOF CHECK ok=true pong
NETWORK PROOF RESULT ok=true checks=6
NETWORK MISMATCH CHECK ok=true reason=handshake rejected: protocol mismatch: client=2, server=1
NETWORK MISMATCH RESULT ok=true
NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok
```

When the `api-game` jar is available, `build.ps1` also compiles the bridge
contract proof and `run.ps1` adds:

```text
NETWORK BRIDGE PROOF BUILD ok=true jar=<path>
NETWORK BRIDGE PROOF CHECK ok=true envelope
NETWORK BRIDGE PROOF CHECK ok=true handshake
NETWORK BRIDGE PROOF CHECK ok=true identity
NETWORK BRIDGE PROOF CHECK ok=true connected
NETWORK BRIDGE PROOF CHECK ok=true connected-state
NETWORK BRIDGE PROOF CHECK ok=true payload
NETWORK BRIDGE PROOF CHECK ok=true reply
NETWORK BRIDGE PROOF CHECK ok=true payload-events
NETWORK BRIDGE PROOF CHECK ok=true disconnect
NETWORK BRIDGE PROOF RESULT ok=true checks=9
```

If `api-game` cannot be built, the scripts print
`NETWORK BRIDGE PROOF BUILD SKIP ok=true reason=api-game-unavailable` and
`NETWORK BRIDGE PROOF SKIP ok=true reason=bridge-contract-not-built`; that is an
honest skip, not a pass. The game-free contract proof does not load a Minecraft
class and does not prove packet transport inside a game.

No client-server Minecraft round-trip result is recorded in this document. The
manual/dedicated-server test plan, exact expected markers, and the conditions
under which it must be rerun are in
[GAME-BRIDGE-PROOF.md](../examples/network-proof/GAME-BRIDGE-PROOF.md). The
standalone proof and the bridge contract proof are not evidence that a Minecraft
client and dedicated server exchanged Weaver payloads.

## Limitations and non-goals

- The bridge is optional. `LoaderAgent` arms it with
  `NetworkBridgeInstall.arm` before the game transformers are installed and
  activates it with `NetworkBridgeInstall.activate` afterwards; it stays inert
  when `api-game` or the game classes are absent. The client proof records a
  real client/server payload round trip (`CLIENT PROOF NETWORK ok=true`).
- The bridge registers one custom payload, `weaver:bridge`, not one Minecraft
  payload per Weaver channel. Channel routing is inside the envelope.
- No login-phase custom payload channel. The bridge handshake runs in the
  configuration phase, matching the verified 26.3 packet flow.
- No configuration or capability synchronization beyond the handshake
  advertisement. Configuration-phase channel payloads are supported once the
  handshake verifies, but there is no built-in data exchange.
- No proxy support, encryption changes, or cross-loader compatibility.
- No authentication, certificates, account checks, or TLS.
- No rate limiter beyond the bounded queue, per-session byte budget, and
  payload/frame limits.
- No cross-machine, latency, packet-loss, or reconnect testing for the bridge.
  The core proof is loopback only. A dedicated-server round trip is a plan, not a
  recorded result.
- `optional` metadata is not enforced by the default handshake.
- Channel lists are advertised in the handshake but are not
  compatibility-enforced. The loader advertises the `both`-side mods; use
  `setHandshakePolicy(...)` for further rules. Registry ids are checked, not
  remapped.
- Reconfiguration does not post a second connected event and does not re-run the
  handshake.
- The per-session inbound budget refills at 1 MiB a second (8 MiB burst).
- `NetworkBridgeInstall.arm` proves only that the config was handed to the Mixin
  engine. A failed injector is visible in the engine's own `MIXIN CONFIG` /
  `MIXIN APPLIED` markers, and the bridge's lazy codec round-trip disables the
  bridge if `weaver:bridge` is not registered.
- A vanilla peer sees one unknown `weaver:bridge` payload during configuration
  and ignores it; the bridge never disconnects that peer.

## Related

- [Documentation index](README.md)
- [Event bus](events.md)
- [Hook points](hooks.md)
- [Architecture](architecture.md)
- [Network proof](../examples/network-proof/README.md)
- [Game bridge proof plan](../examples/network-proof/GAME-BRIDGE-PROOF.md)

Integration note: `docs/README.md` does not yet link this page. The docs owner
(P9) should add it.
