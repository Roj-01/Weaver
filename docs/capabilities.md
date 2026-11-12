# Capabilities

Weaver 2.2 adds a small capability and attachment system for game objects. A
capability is a typed value with an id, an optional byte codec, an optional
default, and a declared copy and sync policy. A holder is any object a mod
attaches a value to, most commonly an `ItemStack` or a runtime object such as a
block entity or entity. Values are looked up by holder identity.

The public API lives in:

- `net.weaver.api.capability` - capability metadata, stores, registry, and the
  runtime fallback.
- `net.weaver.api.data` - `DataComponentKey`, `DataComponentView`, and the
  strict codec factories.

The game bridge lives in `core`:

- `net.weaver.core.data` - item stack and runtime-holder byte access, limits,
  and the reflective value bridge.
- `net.weaver.core.capability` - the item stack, entity, block entity, level,
  and chunk attachment stores, the persistence entry points, and the
  capability sync channel.

`api` and `core` main sources do not import `net.minecraft.*`. The API only
accepts `Object` holders and never names a game type. Core reaches the game only
through reflection, and the API installs core automatically.

This is an additive feature. It is not a port of NeoForge capabilities: values
are attached on first use ([Attach on first use](#attach-on-first-use)) instead
of by an event, and there is no lazy optional wrapper. Provider-based item, fluid,
and energy handlers are a separate system, [lookups](lookups.md). Entity and block entity
capability values are persisted automatically through real 26.3 save hooks.
Server level and chunk values are persisted by `api-game` mixins (the chunk's
save data and a saved-data file per dimension), and players keep
`copyOnDeath` values on respawn. See [Persistence matrix](#persistence-matrix)
and [Limitations](#limitations).

## Quick start

### Define and register capabilities

```java
package com.example.examplemod;

import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.data.DataCodecs;

public final class ExampleCapabilities
{
    public static final AttachmentType<String> OWNER =
            AttachmentType.builder("examplemod:owner", String.class)
                    .defaultValue("unowned")
                    .codec(DataCodecs.utf8String(64), 64)
                    .build();

    public static final AttachmentType<Long> LAST_USE =
            AttachmentType.runtime("examplemod:last_use", Long.class);

    private ExampleCapabilities()
    {
    }

    public static void register()
    {
        AttachmentRegistry registry = AttachmentRegistry.global();
        registry.register(OWNER);
        registry.register(LAST_USE);
    }
}
```

`OWNER` is persistent: it has a codec and is stored on an `ItemStack`.
`LAST_USE` is runtime-only: it has no codec and is never persisted.

Registration is how other code finds a capability by id. Value operations take
the capability instance directly and do not verify that it is registered.

### Use capabilities on an ItemStack

```java
AttachmentRegistry registry = AttachmentRegistry.global();

ItemStack stack = new ItemStack(Items.STONE, 2);

registry.set(stack, ExampleCapabilities.OWNER, "player:steve");
String owner = registry.get(stack, ExampleCapabilities.OWNER);
Optional<String> explicitOwner =
        registry.find(stack, ExampleCapabilities.OWNER);
boolean hasOwner = registry.has(stack, ExampleCapabilities.OWNER);
String removed = registry.remove(stack, ExampleCapabilities.OWNER);
```

A codec-backed capability is stored inside `minecraft:custom_data` under the
shared root `weaver:capabilities`. It survives item save/load and
`ItemStack.copy()` because the game copies data components with the stack. It
travels to a client only when the game itself synchronizes that stack.

### Use capabilities on a runtime object

```java
AttachmentRegistry registry = AttachmentRegistry.global();
Object token = new Object();

registry.set(token, ExampleCapabilities.LAST_USE, 1_700_000_000L);
Long lastUse = registry.get(token, ExampleCapabilities.LAST_USE);
registry.remove(token, ExampleCapabilities.LAST_USE);
```

Values on a plain `Object` are held by identity and are never persisted. Two
equal but distinct objects are different holders. Values on an `Entity` or
`BlockEntity` are also identity-keyed but are persisted when the capability has
a codec; see [Persistence matrix](#persistence-matrix).

### DataComponentKey and DataComponentView

`DataComponentKey` is a typed convenience key over the same item custom-data
storage. It is not a new game `DataComponentType` registration.

```java
public static final DataComponentKey<String> BINDING =
        DataComponentKey.of("examplemod:binding", String.class,
                DataCodecs.utf8String(128), 128);

AttachmentRegistry.global().register(BINDING);

ItemStack stack = new ItemStack(Items.STONE, 2);
DataComponentView view = DataComponentView.of(stack);

view.set(BINDING, "soulbound");
String binding = view.get(BINDING);
Optional<String> explicitBinding = view.find(BINDING);
view.remove(BINDING);
```

`DataComponentKey` implements `Capability`, so it can also be passed directly to
`AttachmentRegistry` methods. `DataComponentView` caches nothing and delegates
every call to `AttachmentRegistry.global()`.

## Defaults and missing values

`get` returns the capability default when no store has an explicit value. The
default is evaluated on every call, so a `defaultSupplier` can change over time
and may return `null`.

| Call | Behavior |
|---|---|
| `get(holder, capability)` | Explicit value, otherwise `capability.defaultValue()`. |
| `find(holder, capability)` | Explicit value only; `Optional.empty()` when absent. |
| `has(holder, capability)` | `true` only for an explicit value. |
| `remove(holder, capability)` | Removed explicit value, or `null` when none was present. |
| `set(holder, capability, null)` | Removes the explicit value; returns the previous explicit value. |
| `set(holder, capability, wrongType)` | Throws `CapabilityException`; nothing is written. |
| `supports(holder, capability)` | `true` when at least one registered store supports the pair. |
| `clear(holder)` | Clears every registered store for the holder. |

Missing capabilities follow the same rules: `get` returns the default, `find`
returns empty, `has` returns `false`, and `remove` returns `null`.

Read failures are fail-soft at the registry boundary. If a store throws during
`get`, `find`, or `has`, the registry records a diagnostic and returns the
default, an empty optional, or `false`. Write operations (`set`, `remove`)
propagate store failures.

## Attach on first use

`AttachmentRegistry.attach(capability, holderType, holder -> initial)` gives
every holder of a type (an entity class, a block entity class, any class) its
own first value. The first `get` of a holder with no explicit value computes it
from the holder, stores it, and returns it. The value is then saved, synced, and
copied like any value set by hand, and later calls return the same object.

```java
registry.attach(ENERGY, Zombie.class, zombie -> new EnergyStore(zombie.getMaxHealth() > 30 ? 2000 : 1000));
EnergyStore store = registry.get(zombie, ENERGY); // computed once, then stored
```

Holders nobody asks about cost nothing. `find` and `has` report only explicit
values and never attach. A `get` that attaches stores the value, which counts
as a change: listeners and sync see it. Attaching is done under a lock, so two
threads reading at once get the same object. It happens only where a store can
keep the value. A rule that throws or returns null is skipped (a
diagnostic records it), and the default applies.

## Versioned data and migration

Changing a codec would make old saves unreadable. `VersionedCodec` records the
format version with the data, so each old format is read by its own reader:

```java
CapabilityCodec<Energy> codec = VersionedCodec.builder(2, Energy.CODEC_V2)
        .reader(0, bytes -> Energy.fromOldInt(bytes)) // saved before the codec was versioned
        .reader(1, Energy::readV1)
        .build();
```

- Old data becomes a current value, and is written in the current format the
  next time the holder is saved.
- Data with no version marker is version 0.
- Data from a newer version (a world opened with an older mod) is rejected with
  a clear reason instead of being misread.
- A versioned payload is `VersionedCodec.OVERHEAD` (5) bytes longer: allow for
  it in the size limit.

## Requests from the client

Values on a player that the client chooses (a selected mode, a toggled overlay)
can be requested by the client and checked by the server:

```java
// Both sides, during onInitialize:
PlayerDataRequests.allow(MODE, (player, mode) -> mode >= 0 && mode < 4);
// Client, from a button:
PlayerDataRequests.request(MODE, 2);
```

The server decodes the value with the capability's codec, runs the check with
the player and the value, and sets it on that player only when the check
passes. The capability's sync then brings it back to the client. A request for
a capability that was not allowed, a payload that does not decode, or a
rejected value is dropped and logged once. Clients cannot write to any other
holder this way. A mod that needs that sends its own payload and checks it on
the server.

## Copy semantics

`ItemStack.copy()` copies the game's data components, including
`minecraft:custom_data`. Every persistent capability value on a non-empty stack
therefore follows the copy. The copy has its own custom-data component; removing
or changing a value on one stack does not affect the other.

Runtime-only values do not copy. The runtime store keys values by holder
identity, and a copied stack is a new object. A runtime-only value on the
original stack is not visible through the copy.

`copyOnItemCopy()` is declaration metadata. The item store writes every
codec-backed value to custom data regardless of the flag, and the game copies
custom data with the stack. Setting `copyOnItemCopy(false)` does not prevent the
game from copying the value. For runtime-only types Weaver provides no copy
semantics.

An empty `ItemStack` is not handled by the item store. Its values always use the
runtime store, even when the capability has a codec.

## Persistence matrix

| Holder | Storage | Survives save/load | Survives copy | Identity | 2.2 status |
|---|---|---|---|---|---|
| Non-empty `ItemStack` + codec-backed capability | `DataComponents.CUSTOM_DATA` (`minecraft:custom_data`) -> `weaver:capabilities` -> key | Yes, when the game saves the stack | Yes, via the game's custom-data copy | Per `ItemStack` instance | Persistent |
| Empty `ItemStack` | Runtime store | No | No | Per instance, weak key | Runtime-only |
| Any holder + runtime-only capability | Runtime store | No | No | Per instance, weak key | Runtime-only |
| `BlockEntity` | Block entity store, weak identity | Yes, through `BlockEntity#saveWithFullMetadata(ValueOutput)` and `#loadWithComponents(ValueInput)` | No | Weak identity key | Persistent when the capability has a codec |
| `Entity` | Entity store, weak identity | Yes, through `Entity#saveWithoutId(ValueOutput)` and `Entity#load(ValueInput)` | No | Weak identity key | Persistent when the capability has a codec |
| `ServerLevel` | Level store, weak identity | Yes, with `api-game`: `data/weaver/level_capabilities.dat` in the dimension folder, written on every level save | No | Weak identity key | Persistent when the capability has a codec |
| `Chunk` | Chunk store, weak identity; an `ImposterProtoChunk` shares its wrapped chunk's values | Yes, with `api-game`: the chunk's save data, under `weaver:capabilities` | Proto chunk to full chunk | Weak identity key | Persistent when the capability has a codec |
| `ServerPlayer` on respawn | Entity store | (see `Entity`) | Death: `copyOnDeath` values only; End return: all | Weak identity key | Copied to the new player object |
| Any other object | Runtime store | No | No | Weak identity key | Runtime-only |

Level and chunk persistence live in `api-game` (`HolderAttachments`), because
they need game types:

- **Chunks.** Values are encoded on the main thread when the chunk's save
  copy is made (`SerializableChunkData#copyOf`) and written with the rest of
  the chunk (`#write`). On load they are read with the chunk (`#parse`) and
  applied before `ChunkDataLoadEvent` (`#read`). When a proto chunk becomes a
  full chunk, its values move over. Setting or removing a chunk value marks
  the chunk unsaved; a value changed in place is only saved with the next
  chunk save for another reason, so set it again after changing it.
- **Levels.** Each server level has a `LevelAttachmentData` saved-data entry.
  It is loaded when the level is created and refreshed at the start of every
  `ServerLevel#save`; it is written only when the encoded values changed, so
  in-place changes are saved too. Client levels are not persisted.
- **Respawn.** Respawning creates a new `ServerPlayer`. After death, values
  whose capability has `copyOnDeath()` are copied to it (by reference); the
  others are dropped. Returning from the End copies every value, including
  runtime-only ones and the retained bytes of unknown ids. The copy runs
  before `PlayerCloneEvent`.

Entity and block entity values are written under the same
`weaver:capabilities` child that item stacks use. Other game keys are preserved.
Capabilities without a codec remain runtime-only on every holder. Runtime values
are lost on unload, reload, or garbage collection of the holder. Item values
travel only when the game synchronizes the stack that carries them.

## Serialization and bounds

### CapabilityCodec

`CapabilityCodec<T>` is an explicit byte format shared by the writer and the
reader:

- `encode(T value)` must never return `null`; it throws `RuntimeException` on
  failure.
- `decode(byte[] encoded)` throws `RuntimeException` on malformed or out-of-
  bounds data.
- Java serialization is not used and is not supported.
- The codec does not enforce the capability bound itself. The owning
  `Capability.maxEncodedBytes()` is enforced by the item and runtime-holder
  stores, and callers that persist bytes directly must apply the same bound.

Persistent values are nested as follows:

```text
ItemStack
  DataComponents.CUSTOM_DATA       (minecraft:custom_data)
    weaver:capabilities            (CompoundTag root)
      "examplemod:owner" -> byte[] (encoded capability value)
```

Only the `weaver:capabilities` root is touched. Other custom-data keys and all
other data components are preserved. When the root compound becomes empty it is
removed, and when the whole custom-data compound becomes empty the
`CUSTOM_DATA` component is removed from the stack.

### DataCodecs factories

| Factory | Format | Limits |
|---|---|---|
| `utf8String(maxBytes)` | UTF-8, strict decode with `CodingErrorAction.REPORT` | Rejects encoded input larger than `maxBytes`. |
| `bool()` | Exactly one byte, `0` or `1` | Rejects any other length or value. |
| `varInt()` | Unsigned LEB128, at most 5 bytes | Rejects truncation, 32-bit overflow, and trailing bytes. |
| `varLong()` | Unsigned LEB128, at most 10 bytes | Rejects truncation, 64-bit overflow, and trailing bytes. |
| `bytes(maxBytes)` | Raw byte array, copied on encode and decode | Rejects arrays larger than `maxBytes`. |
| `uuid()` | 16 bytes, big-endian, most-significant bits first | Rejects every other length. |

All decoders reject malformed data with `CapabilityException`; `null` input
throws `NullPointerException`. Constructing `utf8String` or `bytes` with a
negative bound throws `IllegalArgumentException`.

### ItemDataLimits

| Constant | Value | Meaning |
|---|---|---|
| `MAX_KEY_CHARS` | `128` | Maximum capability key length in characters. The key is the capability id. |
| `MAX_VALUE_BYTES` | `16384` | Maximum encoded size of one value. |
| `MAX_ENTRIES` | `64` | Maximum capability entries in the shared root compound. |
| `MAX_TOTAL_BYTES` | `32768` | Maximum NBT size of the root compound. |
| `ROOT_KEY` | `"weaver:capabilities"` | Root compound inside `minecraft:custom_data`. |

`AttachmentType.maxEncodedBytes` bounds are `1..16384`, or `1..1048576` for a
type declared `sync(CapabilitySync.NONE)`; `AttachmentType.of` uses `16384`.
An item stack still holds at most `16384` bytes per value, whatever the type
declares. The
item store rejects a zero-length encoding. The reflective bridge additionally
checks key length, value size, entry count, and total root size, and throws
`ItemDataException` with the offending key and limit. The total-size check runs
before the new tag is stored, so a rejected write leaves the stack unchanged.

### Invalid payloads

A stored payload that cannot be decoded, or whose decoded value does not match
`Capability.valueType()`, raises a `CapabilityException` from the item store. At
the registry boundary, `get` records a diagnostic and returns the capability
default; `find` returns empty; `has` returns `false`. The invalid bytes stay on
the stack. Weaver does not rewrite or migrate them automatically. A later
`set` replaces the payload, and `remove` deletes it.

Previous-value reads are softer: if the old payload is corrupt, the item store
returns `null` from `set` and `remove` instead of blocking the operation. The
new write or removal still happens.

Entity, block entity, level, and chunk stores keep the same guarantee across a
save. When a holder loads, every stored id that this session cannot use (the
capability is not registered, for example because its mod is missing for a
session, has no codec, or fails to decode) is kept as raw bytes for that holder
and written back unchanged on the next save. A later `set` for the same id
replaces the retained bytes, and `remove` (or clearing the holder) drops them.
Retained bytes count against the same entry and total-size limits; live values
are written first.

There is no schema version and no automatic migration. A mod that changes its
codec must handle old payloads itself, for example by decoding a legacy format
or choosing a new capability id.

## Threading

| Scope | Guarantee |
|---|---|
| `RuntimeAttachmentStore` | Every operation is synchronized on a private lock. Safe for concurrent access. |
| `ItemStackAttachmentStore` and `ReflectiveItemDataAccess` | Not internally synchronized. Call item operations on the thread that owns the stack. Concurrent mutation of the same stack is unsupported. |
| Entity, block entity, level, and chunk stores | Every operation is synchronized on a private lock, matching the runtime store. Carrier access (`ReflectiveValueDataAccess`) is not synchronized; persist one holder on the thread that owns it. |
| `AttachmentRegistry` | Capability map, store list, and diagnostics are synchronized; the diagnostic history is capped at 32. Reads take a store snapshot. |
| `CapabilityStores` | The installation map is synchronized. |
| `DataComponentView` | No caching; thread safety is inherited from the registry and the selected store. |

A registry being thread-safe does not make a game `ItemStack` thread-safe. Keep
item reads and writes on the owning game thread. Runtime attachments may be
used across threads because the runtime store synchronizes internally, but the
holder object itself still has to be safe for the operations you perform.

## Side and sync behavior

Entity, block entity, level, and chunk attachments are process-local storage.
Entity and block entity values are saved by the game hooks and restored when the
holder loads. They are not sent to clients unless the capability declares
`CapabilitySync.RUNTIME`. `ItemStack` data follows the stack: it is available
wherever that stack object is present, and it travels to a client only when the
game synchronizes that stack.

`CapabilitySync` controls the optional Weaver capability channel.

| Value | Meaning |
|---|---|
| `NONE` | Weaver performs no network synchronization for the value. |
| `ITEM_STACK` | The value is stored on the item stack's `minecraft:custom_data` component and therefore travels only when the game itself syncs that stack. Automatic capability-channel forwarding does not include it, but an explicit `CapabilitySyncChannel.send` can transmit it. |
| `RUNTIME` | The value is sent over `weaver:capability/sync/v1` when a `CapabilitySyncProvider` resolves the holder and the capability has a codec. The channel is clientbound and opt-in. |

These declarations do not change storage by themselves. A capability with
`sync(NONE)` and a codec is still stored in item custom data or on a runtime
holder. A runtime-only capability with `sync(ITEM_STACK)` is still runtime-only
and does not persist.

The capability channel is registered on `NetworkRegistry.global()` for the
`PLAY` phase. It reuses the networking API; it does not replace the bridge.
When `api-game` and the game classes are present, the loader installs the
game-typed provider and the bridge recipient list automatically:

```text
[weaver] CAPABILITY SYNC installed provider=game channel=weaver:capability/sync/v1
```

`GameCapabilitySync` names entities by UUID, block entities and chunks by
dimension plus packed position, and levels by dimension. The recipient list is
the open, verified server-side bridge sessions. A game layer can override both
with the manual form:

```java
CapabilitySyncChannel.install(AttachmentRegistry.global(), mySyncProvider);
CapabilitySyncChannel.setRecipientsSupplier(() -> myGameLayer.sessions());
```

The API never invents holder identities. A provider returns a stable kind and
key for outbound values and may implement `resolve(kind, key)` for inbound
values. Without a provider, or when the provider returns `null`, the channel
skips the holder. Without a recipient supplier, no change notification is sent.

`AttachmentRegistry.addChangeListener` reports completed `set`, `remove`, and
`clear` operations. The channel subscribes through that listener and forwards
`RUNTIME` changes automatically. A `CLEAR` change is not sent because it has no
capability or value.

## Persistent attachment SPI

`PersistentAttachmentStore` extends `AttachmentStore` with
`supportsHolder(Object)`, `saveTo(Object, Object)`, `loadFrom(Object, Object)`,
and `hasData(Object)` (default `true`; `false` only when nothing would be saved). The output and input are game-free `Object`
carriers; core passes the game `ValueOutput` and `ValueInput`. A store writes
only registered, codec-backed explicit values and isolates failures per
capability. `CapabilityPersistence` exposes the loader entry points:

| Member | Description |
|---|---|
| `saveEntity(Object entity, Object output)` | Writes entity values through the entity store. |
| `loadEntity(Object entity, Object input)` | Restores entity values. |
| `saveBlockEntity(Object blockEntity, Object output)` | Writes block entity values. |
| `loadBlockEntity(Object blockEntity, Object input)` | Restores block entity values. |
| `saveLevel(Object level, Object output)` | Writes level values into a carrier. |
| `loadLevel(Object level, Object input)` | Restores level values. |
| `saveChunk(Object chunk, Object output)` | Writes chunk values into a carrier. |
| `loadChunk(Object chunk, Object input)` | Restores chunk values. |

`AttachmentRegistry#saveHolder` and `#loadHolder` are the game-free API form
of the same calls: they pick the first persistent store that supports the
holder. `api-game` uses them for levels and chunks.

`CapabilityPersistence.ROOT_KEY` is `weaver:capabilities`, the same child key
used by item custom data. The loader hooks persist and restore values from
`AttachmentRegistry.global()`; an isolated registry is not persisted
automatically.

## Hook integration

Entity and block entity persistence are wired to real 26.3 hooks:

| Holder | Save hook | Load hook |
|---|---|---|
| Entity | `Entity#saveWithoutId(ValueOutput)` | `Entity#load(ValueInput)` |
| Block entity | `BlockEntity#saveWithFullMetadata(ValueOutput)` | `BlockEntity#loadWithComponents(ValueInput)` |

`Entity#addAdditionalSaveData(ValueOutput)` and
`Entity#readAdditionalSaveData(ValueInput)` are abstract in 26.3, so they have
no method body to instrument. The concrete wrappers call them for every entity
subclass and are the stable hook targets. The block entity specs are separate
priority -10 (load) and priority 10 (save) specs layered on the existing
observation hooks. See [hook points](hooks.md).

The loader installs the stores automatically:

```java
registry.registerStore(new ItemStackAttachmentStore());
registry.registerStore(new EntityAttachmentStore(registry));
registry.registerStore(new BlockEntityAttachmentStore(registry));
registry.registerStore(new LevelAttachmentStore(registry));
registry.registerStore(new ChunkAttachmentStore(registry));
```

All five stores are installed by `CapabilityStores.install`; the reflection
installation from an `AttachmentRegistry` constructor therefore makes entity and
block entity persistence available without shared wiring.

## API surface

### CapabilityId

Immutable, validated `namespace:path` identifier. Comparable by namespace then
path.

| Member | Description |
|---|---|
| `CapabilityId(String namespace, String path)` | Canonical validating constructor, equivalent to `of`. |
| `of(String namespace, String path)` | Creates and validates an id. |
| `parse(String id)` | Splits on the first colon and validates both parts. |
| `namespace()` | Namespace accessor. |
| `path()` | Path accessor. |
| `id()` | Returns `namespace + ":" + path`. |
| `compareTo(CapabilityId)` | Orders by namespace, then path. |

Namespace pattern: `[a-z0-9_.-]+`. Path pattern: `[a-z0-9_./-]+`. Invalid input
throws `IllegalArgumentException`; `null` parts throw `NullPointerException`.

### Capability

The contract every capability implements.

| Member | Description |
|---|---|
| `key()` | Validated `CapabilityId`. |
| `id()` | Full id, default `key().id()`. |
| `namespace()` | Namespace, default `key().namespace()`. |
| `path()` | Path, default `key().path()`. |
| `valueType()` | Runtime value type checked on every write. |
| `defaultValue()` | Value used when no explicit value exists; may be `null`; evaluated per call. |
| `codec()` | Byte codec, or `null` for a runtime-only capability. |
| `maxEncodedBytes()` | Positive encoded bound with a codec, otherwise `0`. |
| `copyOnItemCopy()` | Declaration for ItemStack copy behavior. |
| `sync()` | `CapabilitySync` declaration, never `null`. |
| `persistent()` | `true` when `codec()` is not `null`. |

### CapabilityCodec

| Member | Description |
|---|---|
| `encode(T value)` | Encodes a non-null value to non-null bytes. |
| `decode(byte[] encoded)` | Decodes bytes, throwing on malformed input. |

### CapabilityException

Unchecked API failure. Constructors: `(String message)` and
`(String message, Throwable cause)`. Used for invalid ids, duplicate
registration, wrong value types, oversize payloads, missing stores, corrupt
payloads, and bridge failures.

### CapabilitySync

| Value | Description |
|---|---|
| `NONE` | No Weaver network synchronization. |
| `ITEM_STACK` | Value lives on the item stack and moves only when the game syncs that stack. |
| `RUNTIME` | Value is sent over `weaver:capability/sync/v1` when a provider and a codec are present. |

### AttachmentType

Immutable `Capability` implementation created through a builder or factory.

| Member | Description |
|---|---|
| `builder(String id, Class<T> type)` | Starts a builder. |
| `runtime(String id, Class<T> type)` | Creates a runtime-only type with no codec. |
| `of(String id, Class<T> type, CapabilityCodec<T> codec)` | Creates a persistent type with a 16384-byte bound. |
| `key()`, `valueType()`, `defaultValue()`, `codec()`, `maxEncodedBytes()`, `copyOnItemCopy()`, `copyOnDeath()`, `sync()` | Capability accessors. |
| `id()`, `namespace()`, `path()`, `persistent()` | Inherited `Capability` convenience methods. |
| `toString()` | Diagnostic form containing the id and value type. |

`AttachmentType.Builder`:

| Member | Description |
|---|---|
| `defaultValue(T value)` | Sets a constant default, replacing any supplier. |
| `defaultSupplier(Supplier<T> supplier)` | Sets a per-call default supplier, replacing any constant. |
| `codec(CapabilityCodec<T> codec, int maxEncodedBytes)` | Sets the codec and bound; both are required together. |
| `copyOnItemCopy(boolean copy)` | Sets the copy declaration. |
| `copyOnDeath(boolean copy)` | Whether a player keeps the value on death and respawn; default `false`. |
| `sync(CapabilitySync sync)` | Sets the sync declaration. |
| `build()` | Builds and validates the immutable capability. |

Builder defaults: default value `null`; no codec and bound `0`;
`copyOnItemCopy` is `true` when a codec is present and `false` otherwise;
`sync` is `ITEM_STACK` when a codec is present and `NONE` otherwise. A bound
without a codec, a codec without a positive bound, or a bound above `16384`
(above `1048576` for a type declared `sync(CapabilitySync.NONE)`) throws
`CapabilityException`. A `NONE` type is saved with its holder and never sent,
so it may carry a large value on an entity, block entity, level or chunk; see
HolderDataLimits.

### AttachmentStore

SPI for a storage backend. Holders are identity-keyed. `get` must return `null`
when absent and must not substitute the capability default; the registry applies
defaults.

| Member | Description |
|---|---|
| `priority()` | Consultation priority, default `0`; higher runs first. |
| `supports(Object holder, Capability<?> capability)` | Whether this store handles the pair. |
| `get(Object holder, Capability<T> capability)` | Explicit value or `null`. |
| `set(Object holder, Capability<T> capability, T value)` | Stores a non-null value; returns the previous explicit value or `null`. |
| `remove(Object holder, Capability<T> capability)` | Removes and returns the explicit value or `null`. |
| `has(Object holder, Capability<?> capability)` | Whether an explicit value exists. |
| `clear(Object holder)` | Removes every explicit value for the holder. |

### PersistentAttachmentStore

Persistence SPI for runtime holders. The output and input carriers stay
`Object` so the API remains game-free.

| Member | Description |
|---|---|
| `supportsHolder(Object holder)` | Type check used before a save or load. |
| `saveTo(Object holder, Object output)` | Writes registered codec-backed explicit values into an output carrier. |
| `loadFrom(Object holder, Object input)` | Restores registered codec-backed values from an input carrier. |

### AttachmentChange and AttachmentChangeListener

`AttachmentChange` is an immutable record with `holder()`, `capability()`,
`previousValue()`, `newValue()`, and `kind()`. `Kind` is `SET`, `REMOVE`, or
`CLEAR`. A clear has a `null` capability and `null` values.
`AttachmentChangeListener` is a functional interface called after a completed
mutation. Listener failures are isolated and recorded as diagnostics.

### CapabilitySyncProvider

Resolves stable holder identities for the capability channel.

| Member | Description |
|---|---|
| `holderKind(Object holder)` | Stable kind, or `null` when the holder is not syncable. |
| `holderKey(Object holder)` | Stable key, or `null` when the holder is not syncable. |
| `resolve(String kind, String key)` | Reverse lookup used for inbound values; defaults to `null`. |

### CapabilitySyncChannel

Core channel in `net.weaver.core.capability` that registers
`weaver:capability/sync/v1` as a clientbound `PLAY` payload on
`NetworkRegistry.global()`.

| Member | Description |
|---|---|
| `TYPE` | The registered `PayloadType<CapabilitySyncPayload>`. |
| `install(AttachmentRegistry, CapabilitySyncProvider)` | Installs the change listener and network registration. |
| `uninstall()` | Removes the change listener and clears the provider. |
| `setRecipientsSupplier(Supplier<List<NetworkConnection>>)` | Installs the game-layer recipient list. |
| `send(NetworkConnection, Object holder, Capability<?>, Object value)` | Encodes and sends one value, or a removal when `value` is `null`. |
| `provider()`, `isInstalled()` | Diagnostics. |

### RuntimeAttachmentStore

Built-in fallback store for any non-null holder and capability.

| Member | Description |
|---|---|
| `RuntimeAttachmentStore()` | Creates an empty store. |
| `priority()` | Always `Integer.MIN_VALUE`, so it is consulted last. |
| `holderCount()` | Diagnostic count of weakly-held holders with values. |
| `size()` | Diagnostic count of all explicit values. |
| `supports`, `get`, `set`, `remove`, `has`, `clear` | `AttachmentStore` implementation. |

Keys are weak references with identity semantics; values are strongly held.
A collected holder drops its values. Two equal but distinct holders are
different. `null` holder or capability throws `NullPointerException`.

### AttachmentRegistry

Owns registered capabilities and the ordered store list. Every instance gets a
`RuntimeAttachmentStore` and then attempts the reflective core installation.

| Member | Description |
|---|---|
| `global()` | Process-wide singleton registry. |
| `AttachmentRegistry()` | Isolated registry for tests and tools. |
| `register(Capability<?>)` | Registers a capability; same instance and id is idempotent. |
| `find(String id)` | Looks up a registered capability by id. |
| `registeredCapabilities()` | Immutable snapshot in registration order. |
| `registerStore(AttachmentStore)` | Registers a store in priority order; same instance is idempotent. |
| `removeStore(AttachmentStore)` | Removes a store instance. |
| `supports(Object, Capability<?>)` | Whether any store supports the pair. |
| `get(Object, Capability<T>)` | Explicit value or capability default. |
| `find(Object, Capability<T>)` | Explicit value only. |
| `set(Object, Capability<T>, T)` | Stores a value, or removes when `null`; returns the previous explicit value. |
| `remove(Object, Capability<T>)` | Removes and returns the explicit value or `null`. |
| `has(Object, Capability<T>)` | Whether an explicit value exists. |
| `clear(Object)` | Clears every registered store for the holder. |
| `saveHolder(Object holder, Object output)` | Writes the holder's persistent values into a game `ValueOutput` through the first persistent store that supports it; `false` when none does. |
| `loadHolder(Object holder, Object input)` | Reads values written by `saveHolder` from a game `ValueInput`. |
| `hasPersistentData(Object holder)` | Cheap check whether `saveHolder` could write anything; save hooks use it to skip empty chunks and levels. |
| `addChangeListener(AttachmentChangeListener)` | Adds a post-mutation listener; same instance is idempotent. |
| `removeChangeListener(AttachmentChangeListener)` | Removes a listener. |
| `changeListenerCount()` | Number of registered change listeners. |
| `diagnostics()` | Immutable diagnostic snapshot, newest last, capped at 32. |
| `clearDiagnostics()` | Clears the retained diagnostic history. |
| `setDiagnostics(Consumer<String>)` | Installs or disables a diagnostic observer. |

Registering a different capability instance under an existing id throws
`CapabilityException`, even when the value type matches. There is no
unregister method for capabilities.

### DataComponentKey

`Capability` implementation for values persisted through
`minecraft:custom_data`. It delegates to an internal `AttachmentType`.

| Member | Description |
|---|---|
| `builder(String id, Class<T> type)` | Starts a builder; a codec is required before `build()`. |
| `of(String id, Class<T> type, CapabilityCodec<T> codec, int maxEncodedBytes)` | Creates a key with an explicit bound. |
| `attachmentType()` | Returns the backing `AttachmentType`. |
| `key()`, `valueType()`, `defaultValue()`, `codec()`, `maxEncodedBytes()`, `copyOnItemCopy()`, `sync()` | Delegate accessors. |
| `id()`, `namespace()`, `path()`, `persistent()` | Inherited `Capability` convenience methods. |
| `toString()` | Delegates to the backing attachment type. |

`DataComponentKey.Builder`:

| Member | Description |
|---|---|
| `defaultValue(T value)` | Sets a constant default, replacing any supplier. |
| `defaultSupplier(Supplier<T> supplier)` | Sets a per-call default supplier, replacing any constant. |
| `codec(CapabilityCodec<T> codec, int maxEncodedBytes)` | Sets the required codec and its bound. |
| `copyOnItemCopy(boolean copy)` | Sets the copy declaration. |
| `sync(CapabilitySync sync)` | Sets the sync declaration. |
| `build()` | Builds the key, throwing when no codec was configured. |

Copy defaults to `true` and sync defaults to `ITEM_STACK`.

### DataComponentView

Typed, non-caching view over one holder, backed by
`AttachmentRegistry.global()`.

| Member | Description |
|---|---|
| `get(DataComponentKey<T>)` | Explicit value or key default. |
| `find(DataComponentKey<T>)` | Explicit value only. |
| `set(DataComponentKey<T>, T)` | Stores a value, or removes when `null`; returns the previous explicit value. |
| `remove(DataComponentKey<T>)` | Removes and returns the explicit value or `null`. |
| `has(DataComponentKey<T>)` | Whether an explicit value exists. |
| `of(Object holder)` | Creates a view for a non-null holder. |

### DataCodecs

| Member | Description |
|---|---|
| `utf8String(int maxBytes)` | Strict bounded UTF-8 string codec. |
| `bool()` | One-byte boolean codec. |
| `varInt()` | Unsigned LEB128 32-bit integer codec. |
| `varLong()` | Unsigned LEB128 64-bit long codec. |
| `bytes(int maxBytes)` | Defensive-copy byte array codec. |
| `uuid()` | 16-byte big-endian UUID codec. |

### ItemDataLimits

Holds `MAX_KEY_CHARS`, `MAX_VALUE_BYTES`, `MAX_ENTRIES`, `MAX_TOTAL_BYTES`, and
`ROOT_KEY`. The class is final and cannot be instantiated.

### ItemDataAccess

Low-level byte access to the `weaver:capabilities` root of
`minecraft:custom_data`. Implementations are not required to be thread-safe.

| Member | Description |
|---|---|
| `supports(Object holder)` | Whether the holder is a supported game `ItemStack`. |
| `isEmpty(Object holder)` | Whether the stack is empty. |
| `read(Object holder, String key)` | Defensive copy of the stored bytes, or `null`. |
| `contains(Object holder, String key)` | Whether the key exists in the root compound. |
| `write(Object holder, String key, byte[] data)` | Stores a defensive copy, replacing any previous value. |
| `remove(Object holder, String key)` | Removes and returns a defensive copy, or `null`. |
| `clear(Object holder)` | Removes the whole Weaver root compound. |
| `entryCount(Object holder)` | Number of entries in the root compound. |
| `totalBytes(Object holder)` | NBT size of the root compound, or `-1` when unknown. |

### HolderDataLimits

The budget for runtime holders (entities, block entities, levels, chunks).
Their data is saved with the world and never sent with an item stack, so the
size limits are larger than `ItemDataLimits`:

| Constant | Value | Meaning |
|---|---|---|
| `MAX_KEY_CHARS` | `128` | Maximum capability key length in characters. |
| `MAX_VALUE_BYTES` | `1048576` | Maximum encoded size of one value (1 MiB). Only a type declared `sync(NONE)` can declare a bound above `16384`. |
| `MAX_ENTRIES` | `64` | Maximum capability entries in the root carrier. |
| `MAX_TOTAL_BYTES` | `2097152` | Maximum total size of the root carrier (2 MiB). A value that would pass it is skipped on save and logged. |
| `ROOT_KEY` | `"weaver:capabilities"` | Child carrier that holds the values. |

### ValueDataAccess

Low-level byte access to a game value carrier. The carrier stays `Object` so
the API remains game-free. Core's `ReflectiveValueDataAccess` accepts a 26.3
`ValueOutput` for writes and `ValueInput` for reads and stores values through
`Codec.BYTE_BUFFER`.

| Member | Description |
|---|---|
| `supportsOutput(Object carrier)` | Whether the carrier accepts writes. |
| `supportsInput(Object carrier)` | Whether the carrier supplies reads. |
| `writeAll(Object output, String rootKey, Map<String, byte[]> values)` | Replaces the Weaver child, or removes it when the map is empty. |
| `readAll(Object input, String rootKey, Collection<String> keys)` | Reads the requested ids, skipping missing and oversize values. |

### ItemDataException

Unchecked item-data failure. Constructors: `(String message)` and
`(String message, Throwable cause)`. Used for unsupported holders, unavailable
game classes, and limit violations.

### ReflectiveItemDataAccess

`ItemDataAccess` implementation that resolves the game classes from the
holder's class loader and caches the resolved members after the first successful
lookup. It supports only holders whose exact class name is
`net.minecraft.world.item.ItemStack`. All reflection failures are reported as
`ItemDataException`. It preserves other components and other custom-data keys.

| Member | Description |
|---|---|
| `ReflectiveItemDataAccess()` | Creates a lazily resolving bridge. |
| `supports`, `isEmpty`, `read`, `contains`, `write`, `remove`, `clear`, `entryCount`, `totalBytes` | `ItemDataAccess` implementation. |

### ItemStackAttachmentStore

`AttachmentStore` for non-empty item stacks with codec-backed capabilities.

| Member | Description |
|---|---|
| `ItemStackAttachmentStore()` | Creates a store backed by `ReflectiveItemDataAccess`. |
| `ItemStackAttachmentStore(ItemDataAccess access)` | Creates a store with an explicit access implementation. |
| `priority()` | Always `100`, above the runtime fallback. |
| `access()` | Returns the backing `ItemDataAccess`. |
| `supports`, `get`, `set`, `remove`, `has`, `clear` | `AttachmentStore` implementation. |

`supports` is true only when the access supports the holder, the capability has
a codec, and the stack is not empty. A codec-backed value on an empty stack
falls through to the runtime store.

### CapabilityStores

Installs and removes the core stores on a registry.

| Member | Description |
|---|---|
| `install(AttachmentRegistry)` | Installs the item, entity, block entity, level, and chunk stores; idempotent per registry identity. |
| `uninstall(AttachmentRegistry)` | Removes the installed stores. |
| `isInstalled(AttachmentRegistry)` | Whether this class installed a store on the registry. |
| `installIfPossible()` | Installs on `AttachmentRegistry.global()` and swallows every failure. |

## Diagnostics and failure modes

The registry keeps a bounded diagnostic history:

- `diagnostics()` returns at most 32 entries, newest last. Older messages are
  dropped first.
- Each message names the store class and the failure. Retrieval messages also
  name the operation and capability id.
- `setDiagnostics(Consumer<String>)` observes every new message, including
  messages beyond the 32-entry history. Observer exceptions are swallowed.
  Passing `null` resets the observer to a no-op.
- `clearDiagnostics()` clears the history but leaves the observer installed.

Failure behavior by operation:

| Operation | Behavior |
|---|---|
| `get` | Store failure records a diagnostic and returns `capability.defaultValue()`. |
| `find` | Store failure records a diagnostic and returns `Optional.empty()`. |
| `has` | Store failure records a diagnostic and returns `false`. |
| `set` | Validation, encoding, and store failures propagate. |
| `remove` | Store failures propagate. |
| `clear` | A failing store is recorded and the remaining stores are still cleared. |
| `set` previous read | Corrupt previous payload yields `null`; the new value is still written. |
| `remove` previous read | Corrupt previous payload yields `null`; the entry is still removed. |

Other failure modes:

- Missing capability id: `get` returns the default, `find` is empty, `has` is
  `false`, and `remove` is `null`.
- Wrong value type passed to `set`: `CapabilityException` before any store is
  called.
- Unsupported item holder: `ReflectiveItemDataAccess` throws `ItemDataException`;
  `ItemStackAttachmentStore.clear` ignores unsupported holders.
- Oversize encoded value: `ItemStackAttachmentStore` throws
  `CapabilityException` before writing.
- Oversize key, entry count, value, or total root size: the bridge throws
  `ItemDataException` and leaves the stack unchanged.
- Corrupt stored payload: the item store throws `CapabilityException` on direct
  use; the registry converts it to a diagnostic and a default/empty/false
  result. The bytes are not rewritten automatically.
- API-only classpath: the reflective core installation is skipped and item
  stacks use the runtime store. No item persistence is available in that
  configuration.

## Tests and proof

Run the module tests:

```powershell
.\gradlew.bat :api:test
.\gradlew.bat :core:test
```

The API tests cover id and builder validation, defaults and null semantics,
identity-keyed runtime storage, duplicate registration, store priority,
fail-soft diagnostics, concurrency, every `DataCodecs` factory, and
`DataComponentKey`/`DataComponentView`. The core tests cover `ItemDataLimits`,
reflective bridge failure on unsupported holders, item store set/get/remove,
previous values, oversize and empty payloads, corrupt payload fallback, empty
stack fallback, runtime-only fallback, and install/uninstall idempotence. The
runtime-holder tests cover identity storage, save/load round trips, skipped
runtime-only values, encode/decode isolation, total-size limits, unsupported
carriers, and all installed store priorities. The sync tests cover payload
validation and bounds, inbound apply/removal, unknown capabilities, provider
skips, sync-mode gating, recipient delivery, change notifications, and listener
failure isolation.

Run the standalone proof:

```powershell
cd examples\capability-proof
.\build.ps1
.\run.ps1
```

`build.ps1` compiles the proof. `run.ps1` builds first and then runs the plain
main class `net.weaver.capabilityproof.CapabilityProof`.

The proof prints one marker per check:

```text
CAPABILITY PROOF CHECK ok=true <name>
```

and finishes with:

```text
CAPABILITY PROOF RESULT ok=true checks=<N>
```

Minecraft's bootstrap may prefix each line with its logger; the marker text after
that prefix is exact. A failed check prints
`CAPABILITY PROOF RESULT ok=false reason=...` and exits with a non-zero status.
The proof verifies real `ItemStack` codec save/load:

```java
Tag tag = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
        .getOrThrow();
ItemStack loaded = ItemStack.CODEC.parse(NbtOps.INSTANCE, tag)
        .getOrThrow();
```

It also checks that `DataComponents.CUSTOM_DATA` is present, that values survive
the round trip, that copies carry persistent values independently, that
runtime-only values and identity semantics work, that corrupt payloads fall
back with a diagnostic, that oversize values are rejected, and that concurrent
runtime access does not corrupt the store.

## Limitations

- Migration needs a `VersionedCodec`. A capability that changed its plain codec
  without one can orphan old payloads.
- Attaching is lazy (on first `get`), not an event at creation. Provider
  registrations per block, block entity type, entity type, and item are in
  [lookups](lookups.md).
- Level and chunk persistence needs `api-game` (it is installed with it).
  Client levels and chunks are not persisted. A chunk value changed in place
  without `set` is saved only with the next chunk save.
- On death only `copyOnDeath` values move to the new player; the retained
  bytes of unknown ids are dropped then (they survive an End return).
- The capability channel is clientbound only. The client changes values only on
  its own player, through `PlayerDataRequests` with a server-side check. There
  are no retries or conflict resolution. The loader installs the game provider
  and recipient supplier when `api-game` and the bridge are available; without
  them the channel remains inert.
- `CapabilitySync.RUNTIME` does not change storage; a runtime-only capability
  still does not persist.
- The item custom-data root is shared by every Weaver capability. All values on
  one stack share the 64-entry and 32 KiB budget, and the capability id is the
  storage key.
- Corruption is not rewritten automatically. Invalid bytes remain until the
  value is overwritten or removed.
- No capability unregister API. Registrations live for the registry lifetime.
- Runtime values disappear when their weakly-held holder is collected, and on
  unload or reload.
- There is no game data component registration and no automatic item tooltip,
  recipe, or predicate integration.
- `copyOnItemCopy` is a metadata declaration; it does not override the game's
  data-component copy behavior.
- Entity persistence hooks the concrete `saveWithoutId`/`load` wrappers because
  the 26.3 `addAdditionalSaveData`/`readAdditionalSaveData` methods are abstract.
  A subclass that overrides one of the concrete wrappers without calling
  `super` can bypass the hook.

## Integration notes

Core installs automatically. Every `AttachmentRegistry` constructor, including
`AttachmentRegistry.global()`, first registers a `RuntimeAttachmentStore` and
then tries:

```java
Class.forName("net.weaver.core.capability.CapabilityStores", true,
        AttachmentRegistry.class.getClassLoader())
        .getMethod("install", AttachmentRegistry.class)
        .invoke(null, registry);
```

All failures are swallowed, so an API-only classpath still works. No shared
wiring, loader hook, mod lifecycle method, or event registration is needed. If
core is present on the same class path, all five stores are installed before the
registry is returned.

`CapabilityStores.install` is idempotent per registry identity, and
`CapabilityStores.installIfPossible()` is safe to call from a mod. Installation
is tracked for the registry's lifetime unless `uninstall` is called. Isolated
registries created with `new AttachmentRegistry()` also receive all five stores,
which is what the tests and tools rely on.
