# Registry API

Weaver exposes Minecraft registries through a game-free API in
`net.weaver.api.registry`. The `api` module never references `net.minecraft.*`;
`core` installs a reflection-backed `RegistryBackend` that talks to the game.
Mods normally use `RegistryKey`, `DeferredRegister`, `WeaverRegistries`, and
`RegisterEvent`.

The [event bus](events.md) delivers `RegisterEvent` and `RegistryFreezeEvent`.
The [hook points](hooks.md) page describes the lifecycle around them. The
optional [typed game API](api-game.md) adds typed registry keys and typed
`registry(RegistryKey<T>)` helpers without changing registry semantics.

The data-driven `DataRegistry` subsystem is separate from the game registries
described above; it is documented under [Data-driven
registries](#data-driven-registries) near the end of this page. Its tags,
events, freezing, and keys are not shared with `RegistryView`,
`DeferredRegister`, `RegisterEvent`, or `RegistryFreezeEvent`.

## RegistryKey

`RegistryKey<T>` is an immutable `namespace:path` identifier. The type parameter
is compile-time documentation; the key itself only stores the two strings.

```java
RegistryKey<Item> ruby = RegistryKey.of("examplemod", "ruby");
RegistryKey<Item> parsed = RegistryKey.parse("examplemod:ruby");
RegistryKey<Item> vanilla = RegistryKey.parse("stone"); // minecraft:stone

RegistryKey<Item> raw = ruby.withPrefix("raw_");        // examplemod:raw_ruby
RegistryKey<Item> block = ruby.withSuffix("_block");    // examplemod:ruby_block
```

Rules:

- The namespace must match `[a-z0-9_.-]+`.
- The path must match `[a-z0-9_./-]+`. `CamelCase` and spaces are rejected.
- `of` validates immediately and throws `IllegalArgumentException` for an
  invalid namespace or path.
- `parse` splits on the first `:`. An id without a namespace defaults to
  `minecraft`.
- `equals` and `hashCode` use namespace and path. `toString` returns the id.
- `compareTo` orders by namespace, then path, so keys sort predictably.
- `withNamespace`, `withPath`, `withPrefix`, and `withSuffix` return new keys.

`resolve` is not provided: a key identifies an entry, it does not load one.

## RegistryView

`RegistryView<T>` is a live view over one game registry. It is deliberately not
called `Registry` so a mod can import it next to `net.minecraft.core.Registry`
without an import clash.

Obtain it through `ModContext`:

```java
RegistryView<Item> items = context.registry(WeaverRegistries.ITEM);
Optional<RegistryView<Block>> blocks = context.findRegistry(WeaverRegistries.BLOCK);
```

`context.registry` throws `RegistryException` when the registry is unavailable.
`context.findRegistry` returns an empty `Optional` instead. The same methods
exist on `RegistryManager` for code that does not have a context.

| Method | Behavior |
|---|---|
| `registryKey()` | The key this view was opened with. |
| `id()` | The registry id, for example `minecraft:item`. |
| `nativeRegistry()` | The underlying game registry object; throws `RegistryException` when it cannot be resolved. |
| `isFrozen()` | Whether the registry is frozen for writes. |
| `containsKey(RegistryKey<T>)` | Whether the key exists. |
| `containsKey(String)` | String form of the same check. |
| `get(RegistryKey<T>)` | The value, or `null` when absent. |
| `get(String)` | The value, or `null` when absent. |
| `getOptional(RegistryKey<T>)` | `Optional` form of `get`. |
| `getEntry(RegistryKey<T>)` | `Optional<RegistryEntry<T>>` for the key. |
| `getEntry(String)` | String form of `getEntry`. |
| `keySet()` | The current keys. |
| `values()` | The current values. |
| `entries()` | The current entries, including their keys. |
| `size()` | The number of entries. |
| `register(RegistryKey<T>, T)` | Direct registration; returns the entry. |
| `register(String, T)` | Direct registration by id. |
| `getTag(TagKey<T>)` | A read-only `TagView<T>` for one registry tag. |

Reads are safe after the registration phase. Writes are only allowed while the
registry is not frozen; a write after a successful freeze is rejected with
`RegistryException`. Prefer `DeferredRegister` or `RegisterEvent` over direct
`register` calls so registration stays inside the loader's window.

Successful reads are cached per view. A direct `register` call that reaches the
backend invalidates those caches, even when the backend rejects the write; a
call rejected by the pre-flight frozen check throws before the caches are
touched. Every `markFrozen()` pass invalidates the caches as it freezes each
view. A read that fails at the backend boundary is not cached, so a later read
retries the backend. If a backend read
throws, the view logs once per operation per view, then returns the empty or
`null` fallback instead of propagating the failure: `keySet`, `values`,
`entries`, and `getTag` return empty collections, `size` returns `0`, `get`
returns `null`, `containsKey` returns `false`, and `getEntry` returns an empty
`Optional`. Individual invalid entries returned by an otherwise successful
backend call are skipped and logged; the remaining valid entries are cached.

Do not depend on iteration order. If you need a stable order, collect `keySet()`
and sort it; `RegistryKey` is `Comparable`.

For `RegistryView` string methods, an id without a namespace is parsed as
`minecraft:...` by `RegistryKey.parse`, so pass the full `namespace:path` when
reading or directly registering into your own namespace. `DeferredRegister`
string methods are different: a path without `:` uses the register's namespace.

`nativeRegistry()` is an escape hatch for game APIs that require the concrete
registry:

```java
net.minecraft.core.Registry<Item> nativeItems =
        (net.minecraft.core.Registry<Item>) items.nativeRegistry();
```

That cast is necessarily unchecked because `api` cannot name the game type.

## Tags

`TagKey<T>` identifies a tag inside one registry, and `getTag` returns a
read-only `TagView<T>`:

```java
TagKey<Item> logs = TagKey.of(WeaverRegistries.ITEM, "minecraft:logs");
TagView<Item> view = context.registry(WeaverRegistries.ITEM).getTag(logs);

for (Item item : view.values())
{
    // ...
}

boolean isLog = view.contains(someItem);
```

`TagView` exposes `key()`, `values()`, `contents()`, `contains`, `isEmpty`, and
`size`. The values are resolved through the game registry every time the view
is created; tags loaded from a data pack are visible after the server or client
resource load that applies them. A tag must belong to the view's registry;
passing a `TagKey` for another registry throws `RegistryException`.

Game tags are read-only here. Adding entries to a game tag is a data-pack
operation outside this API; `TagView` only reads what the game loaded. The
data-driven `TagStore` described under [Writing tags and merge
priority](#writing-tags-and-merge-priority) is different: it is writable until
the owning `DataRegistry` is frozen.

## RegistryEntry

`RegistryEntry<T>` is a stable handle to one registration. It is created before
the value is bound, which is what makes deferred registration possible.

| Method | Behavior |
|---|---|
| `key()` | The entry key. |
| `id()` | The entry id. |
| `get()` | The value; throws `RegistryException` when not bound. |
| `getOrNull()` | The value, or `null`. |
| `optional()` | `Optional` form of the value. |
| `isPresent()` | Whether the entry is bound and its value can be read. |
| `isBound()` | Whether the entry has completed binding; currently the same state as `isPresent()`. |
| `holder()` | The native holder, or `null` when no holder is available. |
| `holder(Class<H>)` | Typed holder accessor. |
| `holderOptional(Class<H>)` | `Optional` typed holder accessor. |
| `ifPresent(Consumer<? super T>)` | Runs the consumer when bound. |

`isPresent()` and `isBound()` currently report the same bound state: both are
true after a successful bind and false for an unbound or failed entry. Binding
does not require a holder, so `holder()` can still return `null` when the
backend did not produce one; use `holderOptional` when that matters.

```java
RegistryEntry<Block> entry = /* ... */;
if (entry.isPresent())
{
    Block block = entry.get();
    Holder<?> holder = entry.holder(Holder.class);
    // ...
}
```

`holder(Class<H>)` returns the holder cast to the requested type. It throws
`RegistryException` when the entry has no holder or the holder is not an
instance of the requested type; `holderOptional` returns an empty `Optional`
instead. Because `Holder.class` is a raw class literal, assign it to `Holder<?>`
or perform an explicit cast if you need a parameterized type:

```java
@SuppressWarnings("unchecked")
Holder<Block> holder = (Holder<Block>) RUBY_BLOCK.holder();
```

That unchecked cast is the normal way to recover a parameterized game type from
the erased `Object` API. Use `holderOptional` when the entry may not be bound.

## DeferredRegister

`DeferredRegister<T>` collects entry suppliers and registers them during the
loader's registration phase. It is the normal way to register blocks, items,
and other content.

Create one per registry and keep it in a static field:

```java
DeferredRegister<Block> blocks =
        DeferredRegister.create(WeaverRegistries.BLOCK, "examplemod");
DeferredRegister<Item> items =
        DeferredRegister.create(WeaverRegistries.ITEM, "examplemod");
```

Within a `ModContext` you can omit the namespace:

```java
DeferredRegister<Item> items = context.deferredRegister(WeaverRegistries.ITEM);
```

| Method | Behavior |
|---|---|
| `create(RegistryKey<?>, String namespace)` | Creates a register for an explicit namespace. |
| `create(ModContext, RegistryKey<?>)` | Creates a register using `context.id()`. |
| `registryKey()` | The target registry. |
| `namespace()` | The namespace used by path-based registration. |
| `register(String path, Supplier<? extends T>)` | Adds an entry under `namespace:path`. |
| `register(RegistryKey<T>, Supplier<? extends T>)` | Adds an entry under the given key. |
| `entries()` | Entries in registration order. |
| `size()` | The number of entries in this register. |

Registration is lazy. The supplier runs when the loader flushes the register,
not when `register` is called:

```java
RegistryEntry<Block> rubyBlock = blocks.register("ruby_block",
        () -> new Block(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK,
                        Identifier.fromNamespaceAndPath("examplemod", "ruby_block")))
                .strength(4.0f)));
```

`register` returns an unbound `RegistryEntry` immediately. Calling `get()` on
it before the flush throws `RegistryException`. Read content in `onCommonSetup`
or later, not while the field initializers are still running.

The `DeferredRegister` tracks itself with `RegistryManager` when it is created,
so you do not need to register it with the loader. Entries cannot be removed or
replaced, and a failed entry is not retried by a later flush.

### Complete block and item example

```java
package com.example.examplemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.weaver.api.registry.DeferredRegister;
import net.weaver.api.registry.RegistryEntry;
import net.weaver.api.registry.WeaverRegistries;

public final class ExampleContent
{
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(WeaverRegistries.BLOCK, "examplemod");
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(WeaverRegistries.ITEM, "examplemod");

    public static final RegistryEntry<Block> RUBY_BLOCK = BLOCKS.register("ruby_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .setId(blockKey("ruby_block"))
                    .strength(4.0f)
                    .requiresCorrectToolForDrops()));

    public static final RegistryEntry<Item> RUBY = ITEMS.register("ruby",
            () -> new Item(new Item.Properties().setId(itemKey("ruby"))));

    public static final RegistryEntry<Item> RUBY_BLOCK_ITEM = ITEMS.register("ruby_block",
            () -> new BlockItem(RUBY_BLOCK.get(), new Item.Properties()
                    .setId(itemKey("ruby_block"))));

    private ExampleContent()
    {
    }

    public static void init()
    {
    }

    private static ResourceKey<Block> blockKey(String path)
    {
        return ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("examplemod", path));
    }

    private static ResourceKey<Item> itemKey(String path)
    {
        return ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("examplemod", path));
    }
}
```

Force the class to initialize before registration ends:

```java
public final class ExampleMod implements WeaverMod
{
    @Override
    public void onInitialize(ModContext context)
    {
        ExampleContent.init();
    }
}
```

The block item supplier calls `RUBY_BLOCK.get()`. That relies on the block
registry being flushed before the item registry. Cross-registry flush order is
not part of the API contract, so capture the block in a lazy supplier or
register the block item from a later phase, after the block entry is bound.

The loader flushes all tracked registers twice during `onRegister`: once before
mod `onRegister` calls, so content declared at class initialization is present
before `RegisterEvent` listeners run, and once after, so content declared inside
`onRegister` is also registered. `onCommonSetup` runs after the freeze.

## Registration timeline

`LifecycleHooks.onRegister` performs these steps in order:

1. `RegistryManager.beginRegistration()`.
2. `RegistryManager.flushAll()` for deferred entries created before this point.
3. Fire one `RegisterEvent` per discovered registry, if the bus has listeners
   for that event.
4. Call every mod's `onRegister`.
5. `RegistryManager.flushAll()` again for entries created during `onRegister`.
6. `RegistryManager.markFrozen()`.
7. Fire one `RegistryFreezeEvent` per discovered registry, if the bus has
   listeners.
8. `RegistryManager.endRegistration()`.

`markFrozen()` and the freeze events run from a `finally` block, and
`endRegistration()` runs from an outer `finally`, so freezing and closing the
phase still happen when an earlier registration step throws.

Consequences:

- A `RegisterEvent` listener sees deferred entries from step 2.
- A registry write made by a `RegisterEvent` listener happens before any mod's
  `onRegister`.
- A deferred entry created during `onRegister` is registered after direct
  writes made by earlier mods.
- After a successful step 6, nothing can register. A registry whose freeze
  failed may remain open and is listed in the freeze report.
- Events are only fired when at least one listener matches that event type,
  including listeners registered for a supertype.

## RegisterEvent

`RegisterEvent` is posted on `EventBus.GAME` once per discovered registry while
the loader is in its registration window.

| Method | Behavior |
|---|---|
| `registryKey()` | The registry key. |
| `registryId()` | The registry id, for example `minecraft:item`. |
| `registry()` | A live `RegistryView<T>` view. |
| `registry(RegistryKey<T>)` | The live view for that key, validated against this event's registry. |
| `isFrozen()` | Whether the registry is already frozen. |
| `register(RegistryKey<T>, Supplier<? extends T>)` | Evaluates the supplier now, registers the value, and returns an entry. |
| `register(String namespace, String path, Supplier<? extends T>)` | Same, from namespace and path. |
| `register(String id, Supplier<? extends T>)` | Same, from a full `namespace:path` id. |
| `registerRaw(String id, Object value)` | Registers an already-built value without generics. |

Unlike `DeferredRegister`, these suppliers are not deferred; they run inside the
event handler. `RegisterEvent` is not cancelable. Skipping a registry means
doing nothing in the listener; the registry still freezes.

Register directly:

```java
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public final class ExampleRegistration
{
    @SubscribeEvent
    public static void onRegister(RegisterEvent event)
    {
        if (!GameRegistries.ITEM.id().equals(event.registryId()))
        {
            return;
        }

        RegistryEntry<Item> entry = event.register(
                "examplemod", "direct_item",
                () -> new Item(new Item.Properties().setId(ResourceKey.create(
                        Registries.ITEM,
                        Identifier.fromNamespaceAndPath("examplemod", "direct_item")))));
    }
}
```

The typed overload keeps the element type and validates the key:

```java
RegistryView<Item> items = event.registry(GameRegistries.ITEM);
```

It throws `NullPointerException` for a `null` key and
`IllegalArgumentException` when the key names a different registry, before the
registry backend is accessed. Otherwise it returns the same live view as
`event.registry()`.

Or use the live registry view:

```java
RegistryView<Item> items = event.registry();
items.register(
        RegistryKey.of("examplemod", "direct_item"),
        new Item(new Item.Properties().setId(itemKey)));
```

`registerRaw` is for values that are already constructed and for cases where
the generic type cannot be expressed. It returns no entry:

```java
event.registerRaw("examplemod:direct_item", item);
```

Check `event.isFrozen()` if the same listener can be called from other code.
In the normal loader flow it is `false`.

## RegistryFreezeEvent

`RegistryFreezeEvent` is posted on `EventBus.GAME` after
`RegistryManager.markFrozen()`, once per discovered registry. Freezing is
best-effort, so a registry that failed to freeze is still announced. Check
`event.registry().isFrozen()` instead of assuming every announced registry is
closed; use `markFrozenWithReport()` for the failure list.

```java
@SubscribeEvent
public static void onFreeze(RegistryFreezeEvent event)
{
    if (!WeaverRegistries.BLOCK.id().equals(event.registryId()))
    {
        return;
    }

    RegistryView<Block> blocks = event.registry();
    // Inspect the contents; check isFrozen() before writing.
}
```

Use this event to validate content, cache registry references, or build lookup
tables. Do not attempt to register from here. The typed
`event.registry(RegistryKey<T>)` overload validates that the key names this
event's registry, exactly like the `RegisterEvent` helper; the view comes from
the same frozen registry.

## WeaverRegistries

`WeaverRegistries` holds typed-by-convention `RegistryKey<Object>` constants for
the built-in game registries. The type is `Object` so that `DeferredRegister`
and `RegistryView` can infer the real type at the call site:

```java
DeferredRegister<Block> blocks =
        DeferredRegister.create(WeaverRegistries.BLOCK, "examplemod");
```

| Constant | Registry id |
|---|---|
| `BLOCK` | `minecraft:block` |
| `ITEM` | `minecraft:item` |
| `ENTITY_TYPE` | `minecraft:entity_type` |
| `BLOCK_ENTITY_TYPE` | `minecraft:block_entity_type` |
| `FLUID` | `minecraft:fluid` |
| `SOUND_EVENT` | `minecraft:sound_event` |
| `MOB_EFFECT` | `minecraft:mob_effect` |
| `POTION` | `minecraft:potion` |
| `PARTICLE_TYPE` | `minecraft:particle_type` |
| `MENU` | `minecraft:menu` |
| `RECIPE_TYPE` | `minecraft:recipe_type` |
| `RECIPE_SERIALIZER` | `minecraft:recipe_serializer` |
| `ATTRIBUTE` | `minecraft:attribute` |
| `CREATIVE_MODE_TAB` | `minecraft:creative_mode_tab` |
| `COMMAND_ARGUMENT_TYPE` | `minecraft:command_argument_type` |
| `DATA_COMPONENT_TYPE` | `minecraft:data_component_type` |
| `STAT_TYPE` | `minecraft:stat_type` |
| `TRIGGER_TYPE` | `minecraft:trigger_type` |
| `LOOT_POOL_ENTRY_TYPE` | `minecraft:loot_pool_entry_type` |
| `LOOT_FUNCTION_TYPE` | `minecraft:loot_function_type` |
| `LOOT_CONDITION_TYPE` | `minecraft:loot_condition_type` |
| `ENCHANTMENT_EFFECT_COMPONENT_TYPE` | `minecraft:enchantment_effect_component_type` |
| `GAME_RULE` | `minecraft:game_rule` |
| `TICKET_TYPE` | `minecraft:ticket_type` |
| `STRUCTURE_TYPE` | `minecraft:worldgen/structure_type` |
| `STRUCTURE_PIECE` | `minecraft:worldgen/structure_piece` |
| `FEATURE_TYPE` | `minecraft:worldgen/feature_type` |
| `CHUNK_STATUS` | `minecraft:chunk_status` |
| `CARVER_TYPE` | `minecraft:worldgen/carver_type` |
| `PLACEMENT_MODIFIER_TYPE` | `minecraft:worldgen/placement_modifier_type` |
| `MATERIAL_CONDITION_TYPE` | `minecraft:worldgen/material_condition_type` |
| `ENTITY_SUB_PREDICATE_TYPE` | `minecraft:entity_sub_predicate_type` |
| `DATA_COMPONENT_PREDICATE_TYPE` | `minecraft:data_component_predicate_type` |
| `MAP_DECORATION_TYPE` | `minecraft:map_decoration_type` |
| `NUMBER_FORMAT_TYPE` | `minecraft:number_format_type` |
| `DIALOG_TYPE` | `minecraft:dialog_type` |
| `PERMISSION_TYPE` | `minecraft:permission_type` |
| `ENVIRONMENT_ATTRIBUTE` | `minecraft:environment_attribute` |
| `ATTRIBUTE_TYPE` | `minecraft:attribute_type` |

Use `constant.id()` in code rather than hardcoding the right-hand column. The
table follows the Minecraft server registry names for 26.3; `id()` is
authoritative for the running game.

For a registry that has no constant, build a key:

```java
RegistryKey<Object> custom = WeaverRegistries.of("examplemod:custom_registry");
RegistryKey<Object> explicit = WeaverRegistries.of("examplemod", "custom_registry");
```

`WeaverRegistries.availableIds()` returns the registry ids reported by the
backend. It returns an empty set when no backend is installed. A constant can
exist in the API and still be absent from the running game; use
`context.findRegistry` when that is a possibility.

## GameRegistries

The optional `api-game` artifact adds `GameRegistries` in
`net.weaver.api.game.registry`. Its constants are typed
`RegistryKey<T>` values for the same built-in registries:

```java
RegistryView<Item> items = event.registry(GameRegistries.ITEM);
DeferredRegister<Block> blocks = context.deferredRegister(GameRegistries.BLOCK);
```

`GameRegistries.ITEM` has id `minecraft:item`, exactly like
`WeaverRegistries.ITEM`. The difference is compile-time typing: `ITEM` is a
`RegistryKey<Item>`, so `event.registry(...)` returns `RegistryView<Item>`
without an unchecked cast. See [typed game API](api-game.md#gameregistries) for
the full list of constants and their element types.

A base-only mod should continue to use `WeaverRegistries` from `api`; `api`
does not depend on `api-game`.

## Mod namespace

- `context.deferredRegister(key)` uses `context.id()` as the namespace.
- `DeferredRegister.create(key, namespace)` uses the namespace you pass.
- `register(String path, supplier)` builds `namespace:path` when the path has no
  `:`; an explicit `namespace:path` is used as given.
- `register(RegistryKey, supplier)` uses the key as given. The namespace field
  is not applied to an explicit key.
- A mod id is always a valid registry namespace, but registry namespaces also
  allow `.`; paths allow `/` and `.`.
- Use your own namespace for every entry. Registering into `minecraft` is
  possible through the low-level API but collides with built-in content and
  should be reserved for deliberate overrides.

Invalid keys fail early with `IllegalArgumentException`, before any supplier
runs.

## Ordering and duplicates

- Registered deferred registers are tracked in creation order.
- Entries inside one `DeferredRegister` flush in registration order.
- `DeferredRegister.entries()` returns that same order.
- Registries are discovered and `RegisterEvent` is fired once per registry, but
  the order in which registries are visited is not specified.
- Listener priority controls the order of `RegisterEvent` listeners for one
  registry: `HIGHEST` first, `MONITOR` last.
- Registering the same id twice is an error. The second attempt fails; it does
  not replace or remove the first entry.
- There is no unregister or replace operation.

## Failure isolation

Failures are contained at three levels:

1. Deferred entry. If a supplier throws or the backend rejects the value, that
   entry is logged and marked failed. Other entries in the same register, other
   registers, and other mods continue.
2. Registry event. Each `RegisterEvent` or `RegistryFreezeEvent` post is
   wrapped. A failure for one registry does not stop the remaining registries.
3. Listener. The event bus catches `Throwable` per listener and continues with
   the next listener. Use an `EventExceptionHandler` on `EventBus.GAME` to
   observe failures.

Additional rules:

- Registry discovery failures are contained. `RegistryManager.registryIds()`
  returns an empty list when the backend is unavailable or throws, and
  `markFrozenWithReport()` records `discoveryFailed` and logs it. A discovery
  failure results in no registry events rather than a failed boot.
- A missing registry makes `context.registry` throw `RegistryException`. Use
  `findRegistry` to probe without an exception.
- If no backend is installed, `RegistryManager.isInstalled()` is false and no
  registry is available.
- A failed deferred entry is not retried by a later flush.

Flush and freeze isolation:

- `RegistryManager.flushAll()` opens each target registry independently. A
  lookup failure logs and fails only the deferred entries for that registry;
  other registries still flush. If opening succeeds but a flush throws, the
  remaining unbound entries for that register are failed and later registers
  continue.
- `RegistryManager.markFrozen()` parses and freezes each id independently. An
  invalid registry id or a backend that rejects one freeze is logged as
  `REGISTRY FREEZE FAILED id=...`; a throwing `registryIds()` is logged as
  `REGISTRY FREEZE FAILED discovery ...`. Both are recorded and skipped. Every
  other registry still freezes.
- `markFrozen()` never throws because one id or one registry failed. Use
  `markFrozenWithReport()` when the caller needs the frozen and failed ids.
- With no backend installed, `flushAll()` fails each tracked deferred entry and
  logs instead of throwing; `markFrozen()` returns without freezing anything.
- Registry reads catch backend failures, log once per operation per view, and
  return the empty or `null` fallback. Reads that fail at the backend boundary
  are not cached, so a later read retries instead of keeping a permanently
  empty result.

## Frozen semantics

`RegistryManager.markFrozen()` attempts to close every known and discovered
registry for writes. Freezing is best-effort and isolated: a malformed id or one
registry that cannot freeze is logged and recorded, and the remaining registries
are still frozen.

- `RegistryView.isFrozen()` returns true after a successful freeze; a registry
  whose freeze threw may remain unfrozen.
- `RegistryView.register` throws `RegistryException` when the registry is
  frozen.
- Deferred entries flushed after a successful freeze fail individually and are
  logged.
- `RegisterEvent.isFrozen()` reports the state of the target registry. It is
  false during the normal registration window.
- `RegistryFreezeEvent` is posted for every discovered registry after
  `markFrozen()`. Check `event.registry().isFrozen()`; a registry whose freeze
  failed may still be writable.
- A successful freeze is permanent for the process. There is no unfreeze and no
  reload.
- Reads and holder access remain valid after the freeze.
- A registry whose freeze failed may not be marked frozen, but later registries
  are still processed. The failure is visible in the `markFrozenWithReport()`
  report and in the `REGISTRY FREEZE FAILED` log line.
- `markFrozenWithReport()` returns an immutable `RegistryManager.FreezeReport`
  with `frozenCount`, `frozenIds`, `failures` (id to reason),
  `discoveryFailed`, and `isComplete()`. `isComplete()` is true when discovery
  succeeded and no registry failed; an empty report with no backend is
  complete. `markFrozen()` is the void wrapper.

## Threading

- Registry mutation happens during the loader's registration phase on the game
  or bootstrap thread. Create and populate deferred registers there, normally
  during class initialization, `onInitialize`, or `onRegister`.
- Do not share a `DeferredRegister` across threads or mutate one while the
  loader is flushing it.
- `RegistryView` reads are safe once the registry is frozen. Direct writes are
  not thread-safe and are rejected once the registry is frozen.
- The event bus is thread-safe, but that does not make game registries
  thread-safe. Posting a registry operation from another thread is still
  incorrect.

## RegistryManager and backend

`RegistryManager` is the loader-side entry point. Most of it is internal:

| Method | Use |
|---|---|
| `registry(RegistryKey<?>)` | Open a required registry; throws when absent. |
| `findRegistry(RegistryKey<?>)` | Open an optional registry. |
| `registryIds()` | Discovered registry ids. |
| `isInstalled()`, `backend()` | Backend state and instance. |
| `install(RegistryBackend, Consumer<String>)` | Loader startup only. |
| `track(DeferredRegister<?>)` | Called automatically on creation. |
| `flushAll()` | Loader registration phase only. |
| `markFrozen()` | Loader registration phase only; freezes known and discovered registries, isolating failures. |
| `markFrozenWithReport()` | Same, returning an immutable `FreezeReport`. |
| `beginRegistration()`, `endRegistration()` | Loader registration phase only. |
| `isRegistrationPhase()` | Whether writes are currently allowed. |
| `log(String)`, `reset()` | Diagnostics and tests. |

`RegistryException` is the unchecked exception used for registry lookup,
binding, holder access, tag-scope, and post-freeze write failures.

`RegistryBackend` is the SPI between the game-free API and the game. `core`
provides a reflection-backed implementation over `BuiltInRegistries`. Mods
should not implement or replace the backend.

### Backend diagnostics

`MinecraftRegistryBackend` contains reflection failures and logs at most once
per operation per backend instance. The operations are `RESOLVE`, `DISCOVERY`,
`GET`, `CONTAINS`, `KEYS`, `ENTRIES`, `HOLDER`, `IDENTIFIER`, `TAG`, and
`REGISTER`. Each log line starts with
`[weaver] REGISTRY BACKEND <OPERATION> FAILED`.

Loader diagnostics and tests can inspect the cause without changing the
`RegistryBackend` contract:

- `lastFailure()` returns a `Failure` record with the operation, message,
  cause, and count, or `null` when nothing has failed.
- `failureCounts()` returns an immutable snapshot of failure counts per
  operation.

`get` still returns `null` for an absent key, and it also returns `null` when
the backend operation itself fails. Use `lastFailure()` when the distinction
matters for diagnostics. The same pattern applies to the other read fallbacks,
such as `keys` and `entries` returning empty lists.

## Data-driven registries

Weaver 2.2 adds an in-memory registry for mod-owned data. The API is in
`net.weaver.api.registry`; the optional directory loader is in
`net.weaver.core.registry`. Values are decoded from JSON by a mod-supplied
codec. They are not Minecraft registry entries, and nothing in this subsystem
calls `net.minecraft.*`.

| Type | Role |
|---|---|
| `DataRegistry<T>` | One typed, in-memory registry with priority-aware loading and a writable tag store. |
| `DataRegistryKey<T>` | A registry key, entry key, and `Class<T>` in one value. |
| `DataRegistryCodec<T>` | Decodes one parsed JSON object into a `T`. |
| `DataPackSnapshot` | An immutable snapshot of one pack layer: entries and tag declarations. |
| `TagStore<T>` | The writable, priority-aware tag store owned by one `DataRegistry`. |
| `DataRegistryManager` | Static manager and event source for all data registries. |

### DataRegistry<T>

Create a registry through `DataRegistryManager.create`; the constructor and the
static factory are package-private.

```java
record Gem(String color, int hardness)
{
}

DataRegistry<Gem> gems = DataRegistryManager.create("examplemod:gems", Gem.class,
        (entryId, data) -> new Gem(
                (String) data.get("color"),
                ((Number) data.get("hardness")).intValue()));

gems.register("ruby", new Gem("red", 7));
```

| Method | Behavior |
|---|---|
| `registryKey()`, `id()`, `namespace()` | The registry key, full id, and namespace. |
| `type()`, `codec()` | The entry type and codec. |
| `isFrozen()` | Whether writes are rejected. |
| `register(DataRegistryKey<T>, T)` | Programmatic entry at `Integer.MAX_VALUE` priority, source `api`. |
| `register(String path, T)` | Programmatic entry; a path without `:` uses the registry namespace. |
| `load(String entryPath, Map<String, Object> data, int priority, String source)` | Loads one decoded entry and compares it with the existing priority. |
| `loadTag(String tagPath, List<DataPackSnapshot.TagValue> values, int priority, String source, boolean replace)` | Loads one tag declaration; returns one result per declared value, or a single `FROZEN`/`INVALID` result. |
| `size()`, `keySet()`, `values()`, `entries()` | Read snapshots. `entries()` returns live `RegistryEntry` handles. |
| `get(DataRegistryKey<T>)`, `get(String)`, `getOptional(...)` | Entry reads; `get` returns `null` when absent. |
| `containsKey(...)`, `getEntry(...)` | Presence and live-handle reads. |
| `tag(TagKey<T>)`, `tags()`, `addTag(...)` | Resolve a tag, access the writable `TagStore`, or add a tag layer. |
| `freeze()` | Permanently freezes the registry and its tag store; repeated calls are no-ops. |

`register` throws `RegistryException` when the registry is frozen or the id
already exists, and `IllegalArgumentException` when the key belongs to another
registry or uses another type. `load` and `loadTag` return result records
instead of throwing for ordinary load problems.

`load` builds the entry id as `registryNamespace + ":" + entryPath`. One result
is a `DataRegistry.LoadResult` with a `Status`:

| Status | Meaning |
|---|---|
| `ADDED` | No entry existed, or a tag value was accepted. |
| `REPLACED` | The incoming priority was higher than the existing entry; the existing handle was rebound. |
| `SKIPPED` | The existing priority was higher. |
| `DUPLICATE` | The existing priority was equal. |
| `DECODE_FAILED` | The codec threw or returned `null`; the old value was kept. |
| `FROZEN` | The registry is frozen; nothing was written. |
| `INVALID` | The entry path or tag value was invalid. |
| `MISSING` | An optional tag value is absent; it is skipped. |
| `REQUIRED_MISSING` | A required tag value is absent; it is skipped but reported as a problem. |

A replacement keeps the original insertion position and rebinds the existing
`RegistryEntry`; a handle read before the replacement sees the new value.

### DataRegistryKey<T> and DataRegistryCodec<T>

`DataRegistryKey<T>` combines the registry key, the entry key, and the entry
type. The type participates in equality, so keys for the same id with different
types are not equal.

| Method | Behavior |
|---|---|
| `of(RegistryKey<?>, String namespace, String path, Class<T>)` | Build from namespace and path. |
| `of(RegistryKey<?>, String id, Class<T>)` | Build from a full id; a bare path defaults to `minecraft`. |
| `registry()`, `id()`, `namespace()`, `path()`, `type()` | Accessors. |
| `cast(Object)` | `type.cast(value)`; returns `null` for `null` and throws `ClassCastException` on a type mismatch. |
| `compareTo(...)` | Orders by registry id, then entry id, then the type name. |
| `toString()` | `registry.id() + "#" + id.id()`, for example `examplemod:gems#examplemod:ruby`. |

`DataRegistryCodec<T>` is a functional interface:

```java
T decode(String entryId, Map<String, Object> data);
```

The data map is the root JSON object of one entry file. Nested objects are
`Map`, arrays are `List`, and numbers are `Double` when the map came from
`DataPackLoader`. The map is read-only and must not be mutated. Throwing or
returning `null` marks the load `DECODE_FAILED`.

### DataRegistryManager

`DataRegistryManager` is the static entry point. It is thread-safe.

| Method | Behavior |
|---|---|
| `create(RegistryKey<?>, Class<T>, DataRegistryCodec<T>)` | Creates and registers a registry. |
| `create(String registryId, Class<T>, DataRegistryCodec<T>)` | Same, from a full id; a bare id defaults to `minecraft`. |
| `register(DataRegistry<?>)` | Registers an existing registry. |
| `find(RegistryKey<?>)` | `Optional<DataRegistry<?>>`. |
| `require(RegistryKey<?>)` | The registry, or `RegistryException` when absent. |
| `all()`, `registryIds()` | Immutable snapshots in registration order. |
| `beginRegistration()`, `endRegistration()` | Open and close the event phase; see [Data registry events](#data-registry-events). |
| `isRegistrationPhase()` | The current phase flag. |
| `apply(DataPackSnapshot)` | Applies one snapshot to the registered registries. |
| `loadAll(List<DataPackSnapshot>)` | Applies snapshots in ascending priority order; the sort is stable. |
| `reloadAll(List<DataPackSnapshot>)` | Reloads open registries, refuses every frozen registry, and returns a `ReloadReport`. |
| `freezeAll()` | Freezes every registered registry and returns a `FreezeReport`. |
| `reset()` | Clears all registrations and closes the phase; intended for tests. |

`apply` returns an `ApplyReport` with `added`, `replaced`, `skipped`,
`duplicates`, `decodeFailures`, `missing`, and `problems`. Unknown registry ids,
decode failures, invalid entries, frozen registries, and missing required tag
values are recorded as problems instead of aborting the remaining registries.
The `skipped` count also includes frozen and invalid results.

### Pack load priority

Pack entries are compared by priority:

| Existing entry | Incoming layer | Result |
|---|---|---|
| none | any | `ADDED` |
| higher priority | lower priority | `SKIPPED`; the codec is not called |
| equal priority | equal priority | `DUPLICATE`; the codec is not called |
| lower priority | higher priority | `REPLACED`; the codec runs and the old value is replaced |
| any | codec throws or returns `null` | `DECODE_FAILED`; the old value is kept |
| any | registry frozen | `FROZEN`; nothing changes |

`loadAll` sorts snapshots ascending by priority and applies them in turn, so a
higher-priority snapshot can replace a lower-priority value. Equal priorities
keep their input order; for entries, the first applied value wins. For tags,
equal-priority layers still merge. `apply` does not sort a single snapshot; it
applies the entries and tags in that snapshot's order. Entries use the snapshot
priority; tags use each `DataPackSnapshot.TagDeclaration`'s own priority and
`replace` flag.

Programmatic entries use `Integer.MAX_VALUE` priority and source `api`. A pack
cannot replace them: a lower priority is `SKIPPED`, and `Integer.MAX_VALUE` is
`DUPLICATE`.

### Freeze semantics

`DataRegistry.freeze()` is permanent and also freezes the tag store. Freezing
twice has no further effect.

After a freeze:

- `register` and `addTag` throw `RegistryException`.
- `TagStore` mutators throw `RegistryException`.
- `load` returns a `FROZEN` result and `loadTag` returns one `FROZEN` result.
- Reads and existing `RegistryEntry` handles remain valid.

`DataRegistryManager.freezeAll()` freezes each registry independently. A failure
is recorded in the `FreezeReport` (`frozenCount`, `frozenIds`, `failures`,
`isComplete()`) instead of being thrown. There is no unfreeze.

### Reload semantics

`DataRegistryManager.reloadAll(List<DataPackSnapshot>)` is the only reload
entry point. It applies the snapshots in ascending priority order, exactly like
`loadAll`, but only to registries that are not frozen. Every frozen registry is
reported by id in `ReloadReport.frozenIds()` and is left byte-for-byte
untouched. The embedded `ApplyReport` counts only work performed on open
registries; `ReloadReport.problems()` appends one
`data registry '<id>' is frozen; reload refused` line per frozen registry so a
caller cannot miss the refusal.

`ReloadReport` also exposes `openIds()` (registries that were reloadable),
`frozenCount()`, and `isComplete()` (`frozenIds()` is empty). It never replaces
a registry instance, never creates a fresh generation under the same id, and
never adds a public unfreeze. The generation approach is deliberately refused:
a fresh writable registry under the same id would bypass the documented
permanent-freeze contract and would leave existing `RegistryEntry` handles
pointing at the old generation. A reload is a safe, partial apply, not a class
or registry swap.

### Difference from game registries

| Game registries | Data-driven registries |
|---|---|
| `RegistryView`, `DeferredRegister`, `RegisterEvent`, `RegistryFreezeEvent` | `DataRegistry`, `DataRegistryManager`, `DataRegistryRegisterEvent`, `DataRegistryFreezeEvent` |
| Backed by a Minecraft registry through `RegistryBackend` | In-memory; no game registry and no backend |
| Values are game objects | Values are decoded by the mod's codec |
| Loader drives the registration window and freeze | The mod or an integration must call `beginRegistration`/`freezeAll` |
| Tags are read-only `TagView` snapshots | Tags are writable `TagStore` layers with priority |

Both use `RegistryEntry<T>` handles, but a data entry is bound with no native
holder, so `holder()` and `holder(Class)` return `null`. The two managers are
separate: `DataRegistryManager` does not register game content, and
`RegistryManager` does not see data registries. A data registry id that happens
to equal a game registry id does not read or write that game registry.

## Data registry events

The manager posts two event types on `EventBus.GAME` with logical side `BOTH`.
Both are not cancelable.

| Event | Posted by | Contents |
|---|---|---|
| `DataRegistryRegisterEvent` | `DataRegistryManager.beginRegistration()`, once per registered registry | `registryKey()`, `registryId()`, `registry()`, `isFrozen()`, `register(DataRegistryKey<T>, T)`, `register(String path, Object value)` |
| `DataRegistryFreezeEvent` | `DataRegistryManager.freezeAll()`, after every freeze attempt | `registryKey()`, `registryId()`, `registry()`, `frozen()` |

A register listener can add entries through the event. The generic overload
uses the key's type; the `(String, Object)` overload casts with
`registry.type().cast(value)` and throws `ClassCastException` on a mismatch.
The freeze event is posted even when a registry failed to freeze; check
`frozen()` or `registry().isFrozen()` instead of assuming the registry closed.

With `api-game` on a server, the data pack integration opens the
registration phase once, on the first data pack load (see [Data packs from
the running game](#data-packs-from-the-running-game)). Without it, wiring is
not automatic: `LifecycleHooks.onRegister`
drives only `RegistryManager`; it does not call `DataRegistryManager`. A mod or
an integration must call `beginRegistration()` itself if it wants the register
events, and must call `freezeAll()` (and `endRegistration()` if it cares about
`isRegistrationPhase()`) when loading is finished. The datagen proof does this
from `onInitialize` and its server-started listener.

`beginRegistration()` only sets the phase flag and posts events. It does not
gate writes: `DataRegistry.register` rejects only a frozen registry or a
duplicate id. A registry created by a listener during `beginRegistration()` does
not receive an event in that pass because the manager snapshots the registry
list before posting.

`reloadAll` does not post `DataRegistryRegisterEvent` or
`DataRegistryFreezeEvent`. It applies snapshots directly to open registries;
listeners are not re-run, and frozen registries are refused rather than
announced as writable.

## Writing tags and merge priority

Each data registry owns a `TagStore<T>`. Tags are keyed by `TagKey<T>`, which
must belong to the same registry as the store.

| Method | Behavior |
|---|---|
| `add(TagKey<T>, Collection<T>, int priority, String source)` | Appends a layer of direct values. |
| `addReference(TagKey<T>, TagKey<T>, int priority, String source)` | Appends a layer containing one tag reference. |
| `replace(TagKey<T>, Collection<T>, int priority, String source)` | Appends a replacing layer; an empty collection clears lower-priority values. |
| `resolve(TagKey<T>)` | Returns an immutable `TagView` snapshot. |
| `keys()`, `isFrozen()`, `freeze()` | Tag keys with at least one layer, freeze state, and permanent freeze. |

Layers are merged by descending priority and then by ascending insertion
sequence. Values are deduplicated by `Object.equals` while preserving the first
occurrence. Tag references are expanded depth-first with a visited set, so
cycles terminate. A referenced tag with no layers resolves to nothing and is
ignored.

After the first replacing layer in the merged order, layers with a strictly
lower priority are ignored. Layers at the same priority are still merged, even
when they were inserted before the replacing layer. A replacing layer with an
empty value collection clears lower-priority values but not same-priority
layers.

Missing tag values are handled during `DataRegistry.loadTag`:

- An optional missing value produces `MISSING` and is skipped.
- A required missing value produces `REQUIRED_MISSING`, is skipped, and is
  reported as a problem by `DataRegistryManager.apply`.
- The remaining values in the declaration are still added.

File values may use `#namespace:path` for a tag reference. The
`DataPackSnapshot.TagValue.reference` factory takes the id without `#`.
`DataRegistry.loadTag` also accepts an id with `#` and strips it. A direct value
id without a namespace defaults to `minecraft` through `RegistryKey.parse`; the
directory loader canonicalizes ids before building the snapshot. A tag path
passed to `loadTag` without a namespace also becomes `minecraft:path`; the
loader passes `<packNamespace>:<tagPath>` instead.

Game tags obtained through `RegistryView.getTag` remain read-only. The writable
`TagStore` belongs only to `DataRegistry`; it does not edit `minecraft:tags`
files or the game registry's tag maps.

## Data packs from the running game

With `api-game` installed, the server fills data registries from its own
active data packs, the same packs vanilla uses for recipes and loot
(`PackDataRegistries`). A listener appended to every server data pack load
(startup, `/reload`, enabling or disabling a pack) reads
`data/<ns>/weaver/registry/...` and `data/<ns>/weaver/tags/...` from each
active pack, in the server's pack order (lowest priority first, so a later
pack overrides an earlier one), with the layout, JSON rules, and tag format
of the directory loader below. It applies them with `reloadAll`, which
replaces pack values in their existing handles; programmatic entries and
frozen registries are left alone, and a frozen registry's refusal is logged.

- The first load opens the registration phase once (`beginRegistration`,
  then `endRegistration`), so `DataRegistryRegisterEvent` listeners can add
  programmatic entries before the packs are applied. Create registries during
  mod setup, before the server loads its packs.
- Registries are not frozen by the integration, so `/reload` keeps updating
  them. A mod that wants fixed values freezes its registry itself.
- An entry removed from every pack keeps its last value until the server
  restarts; a reload replaces values but does not delete handles.
- The server logs `[weaver] DATA REGISTRIES loaded packs=<n> registries=<n>
  frozen=<n> problems=<n>` and up to 20 problem lines per load.

After each load, and when a Weaver client joins, the server sends the applied
pack layers on `weaver:data_registries`; the client applies them the same way
(`[weaver] DATA REGISTRIES synced ...`), so client code sees the server's
values. The client must create the same registries. A client in the same
process as the server (single player) already shares the registries and
skips the sync. A sync larger than the 1 MiB payload limit is not sent and is
logged as `DATA REGISTRIES sync FAILED`.

Mod jars are data packs too (see [Mod jars as packs](#mod-jars-as-packs)),
so a mod ships its Weaver data in its jar; a world data pack can override it.

The server proof installs `examples/proof/datapack/weaverproof_data` into
the server world and checks that `weaverproof:gems` holds the pack's
entries and tag. `examples/multiplayer` (gate step `H-multiplayer`) checks the
client sync between a dedicated server and a separate client
(`DATA REGISTRIES synced`); `DataPackFilesTest` covers the codec.

## Mod jars as packs

With `api-game` installed, every Weaver mod jar is offered to the game as a
built-in pack, the way vanilla offers its own (`ModPacks`):

- A jar with a `data/` folder becomes the data pack `mod/<modid>` on every
  server, dedicated and single player. Everything vanilla reads from data
  packs works: recipes, loot tables, advancements, tags, worldgen, functions,
  and Weaver data registry files.
- A jar with an `assets/` folder becomes the resource pack `mod/<modid>` on
  the client: textures, models, block states, sounds, and translations.

Mod packs are known packs (namespace `weaver`, the pack id, the mod version),
like vanilla's own. Vanilla treats world data (biomes, structures, dimensions)
from packs it does not know as experimental, which put every world using a
mod's worldgen behind the "experimental settings" warning. Entries from known
packs are stable. A dimension in the namespace of an installed Weaver mod is
stable too, where vanilla calls every extra dimension experimental. A client
with the same mod versions also loads that data from its own jars instead of
receiving it from the server.

Mod packs are required: always enabled, never offered as optional. They are
kept directly above vanilla, in loader order, in every selected-pack list,
so world data packs and user resource packs placed above them can still
override a mod's files. A jar needs no `pack.mcmeta`; mod packs are always
compatible and request no feature flags. A world remembers its packs, so a
removed mod leaves vanilla's "missing data pack" warning, as for any pack.

The loader passes the installed mods at startup (`[weaver] MOD PACKS mods=<n>
packs=<n>`). The server proof loads a recipe and a data registry entry from
its own jar, checks the order `vanilla, mod/weaverproof, file/...`, and checks
that the world pack's value overrides the jar's; the client proof reads a
translation from its jar.

## Conventional tags

Mods share materials through common tags in the `c` namespace: an iron ingot
from any mod goes in `c:ingots/iron`, so a machine from another mod can take it
without knowing the mod. Weaver ships the vanilla entries of these tags in its
own built-in data pack, `mod/weaver` (the `api-game` jar). It has 191 tag files:

- 141 item tags: `ingots`, `nuggets`, `raw_materials`, `gems`, `dusts`,
  `rods`, `ores`, `storage_blocks`, `dyes`, `seeds`, `crops`, `tools`,
  `buckets`, `chests`, `glass_blocks`, `stones`, and others, with per-material
  children such as `c:ingots/iron` and `c:dyes/red`.
- 48 block tags: `ores`, `storage_blocks`, `chests`, `barrels`,
  `glass_blocks`, `glass_panes`, `stones`, `cobblestones`, `sands`,
  `obsidians`, `workbenches`, and per-material children.
- 2 fluid tags: `c:water` and `c:lava`.

Each parent tag lists its children by reference (`#c:ingots/iron`), so a mod
that adds `c:ingots/tin` and lists it in `c:ingots` is found by both queries.
Mods add their own entries with ordinary tag files in their jar; tag files
merge, so Weaver's vanilla entries stay.

`net.weaver.api.game.tag.ConventionalTags` holds `TagKey` constants for the
common parents (`INGOTS`, `ORES`, `STORAGE_BLOCKS`, `WATER`, ...), and
`item(path)`, `block(path)`, and `fluid(path)` for any other path:

```java
boolean iron = stack.is(ConventionalTags.item("ingots/iron"));
boolean anyOre = state.is(ConventionalTags.ORES);
```

The server proof checks that iron and copper ingots are in `c:ingots` (and
`c:ingots/copper`), deepslate iron ore in `c:ores`, red dye in `c:dyes/red`,
and water in `c:water`, and that dirt is not in `c:ingots`.

## Load conditions

A data or resource file whose conditions fail is not loaded at all, as if it
were not in the pack. A mod uses this to ship recipes, loot, tags, or worldgen
that apply only when another mod is present:

```json
{
  "weaver:conditions": [ { "type": "weaver:mod_loaded", "modid": "biomes" } ],
  "type": "minecraft:crafting_shaped"
}
```

Files written for other loaders work unchanged:

- `neoforge:conditions` and `forge:conditions`, whose entries have a `"type"`;
- `fabric:load_conditions`, whose entries have a `"condition"`.

All conditions in the list must hold. Built-in types (under any of the
`weaver`, `neoforge`, `forge`, `fabric`, or `c` namespaces):

- `mod_loaded` (`modid`), `all_mods_loaded` and `any_mods_loaded` (`values`);
- `not` (`value`), `and` and `or` (`values`);
- `true` and `false`;
- `item_exists` (`item`), and `registered` / `registry_contains` (`registry`
  with `value` or `values`).

`LoadConditions.register("examplemod:feature_enabled", condition -> ...)` adds
a type. An unknown or invalid condition keeps the file and is logged once
(`DATA CONDITION`). This also holds inside `not`, `and`, and `or`, which are
evaluated with three values: true, false, and unknown. `registered` checks
built-in registries only: data-driven ones (biomes, structures) are not loaded
yet when files are listed, so naming one keeps the file.
Each skipped file is logged (`DATA CONDITION SKIPPED file=...`).

The check runs where every loader lists its files (`FileToIdConverter`), so it
covers recipes, advancements, loot tables, tags, and worldgen alike. Files from
the vanilla pack are not read twice; other files are opened only when their
text mentions conditions.

## Data packs: DataPackRepository and DataPackLoader

`DataPackRepository` (in `net.weaver.core.registry`) reads an ordered set of
pack roots into immutable `DataPackSnapshot` layers. `DataPackLoader` remains a
compatibility facade over the repository for one pack and keeps its original
`Report` shape. The expected layout is:

```text
data/<packNamespace>/weaver/registry/<registryNamespace>/<registryPath>/<entryPath>.json
data/<packNamespace>/weaver/tags/<registryNamespace>/<registryPath>/<tagPath>.json
```

`<registryPath>` is exactly one path segment. `<entryPath>` and `<tagPath>` may
contain additional `/` separators. Example:

```text
data/examplemod/weaver/registry/examplemod/gems/ruby.json
data/examplemod/weaver/tags/examplemod/gems/shiny.json
```

The first file is entry `examplemod:ruby` in registry `examplemod:gems`; the
second is tag `examplemod:gems#examplemod:shiny` when the pack namespace is also
`examplemod`. Tag declarations are keyed by `<packNamespace>:<tagPath>`, not by
the registry namespace. If a pack named `mypack` writes the same file, the tag
id is `examplemod:gems#mypack:shiny`. The proof keeps the pack and registry
namespaces equal, so its tag path is `datagenproof:shiny` (full tag id
`datagenproof:gems#datagenproof:shiny`).

Every directory directly under `data/` is read, so one pack may contribute
several namespaces. The loader does not require the pack namespace to match the
registry namespace or a mod id. Only `weaver/registry` and `weaver/tags` under
each data namespace are read; other vanilla data paths and `assets/` are
ignored.

### Pack roots, precedence, and metadata

`DataPackRepository.load(List<Path>)` assigns priority `0, 1, 2, ...` in input
order, where the first root is the lowest priority and the last root is the
highest. `load(List<Path>, int basePriority)` offsets the sequence. A higher
priority snapshot replaces a lower-priority entry when the layers are applied
with `DataRegistryManager.loadAll(report.snapshots())`; tags keep their own
`priority` and `replace` flags. Input order is the only precedence input:
`pack_format` metadata is descriptive and never reorders packs.

Each root may contain a vanilla-style `pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 15,
    "description": "Example pack",
    "supported_formats": [15, 20]
  }
}
```

`pack_format` must be an integer of at least `1`. `description` may be a string
or a JSON text component object/array. `supported_formats` is optional and may
be a single integer, a two-element `[min, max]` array, or an object with
`min_inclusive` and `max_inclusive`; the range must be ordered and positive.
`PackInfo` reports the parsed values and offers `supports(int)`.

A missing `pack.mcmeta` is accepted with defaults (`packFormat` `0`,
description `""`, range `0..0`) so packs written for the old single-directory
reader keep working. A `pack.mcmeta` that exists but is malformed (bad JSON,
missing `pack`, non-integer format, invalid range) skips that pack as a whole
and is recorded in `Report.problems()`; later packs still load. `Report.packs()`
and `Report.snapshots()` contain accepted packs only, in input order.

### Limits and path safety

The repository bounds work per load:

| Limit | Value | Behavior |
|---|---|---|
| Packs per load | `MAX_PACKS` = 64 | Later roots are skipped and reported. |
| JSON files per pack | `MAX_FILES_PER_PACK` = 4096 | Remaining files in that pack are skipped and reported. |
| Bytes per file | `MAX_FILE_BYTES` = 1,048,576 | The oversized file is skipped and reported. |
| Total bytes per pack | `MAX_TOTAL_BYTES_PER_PACK` = 16,777,216 | Remaining files in that pack are skipped and reported. |

Only regular `.json` files under the Weaver-owned trees are read. Symbolic
links are not followed. Absolute entry paths, `..` segments, backslashes, empty
segments, and paths that escape the pack root are rejected. A rejected or
oversized file is a problem, not a thrown exception; the rest of the pack and
the rest of the load continue unless a pack-wide limit was reached.

```java
DataPackRepository.Report packs = DataPackRepository.load(
        List.of(lowPriorityRoot, highPriorityRoot));
for (String problem : packs.problems())
{
    // ...
}
DataRegistryManager.ApplyReport applied =
        DataRegistryManager.loadAll(packs.snapshots());
```

A registry entry file must contain a JSON object at its root. The object is
passed to the codec unchanged except for conversion into maps, lists, and
scalars:

```json
{
  "color": "red",
  "hardness": 7
}
```

JSON numbers become `Double`, nested objects become `Map`, arrays become
`List`, and JSON `null` becomes Java `null`. A codec that needs an integer
should read `((Number) data.get("hardness")).intValue()`.

A tag file has this shape:

```json
{
  "values": [
    "examplemod:ruby",
    {"id": "examplemod:sapphire", "required": true},
    "#examplemod:shiny"
  ],
  "priority": 10,
  "replace": true
}
```

`priority` and `replace` are optional. A tag file without `priority` uses the
snapshot priority assigned by the repository (or the explicit priority passed
to `DataPackLoader.read`). Values are either strings (`namespace:path`, or
`#namespace:path` for a reference) or `{"id": "...", "required": true}`
objects; `required` defaults to `false`. An empty or omitted `values` array
still produces an empty declaration/layer.

Parse and layout problems are returned in `Report.problems()`; the snapshot is
still returned even when some files fail. A missing `data/` directory is
reported as `data: missing data directory`.

Single-pack compatibility reads still use the facade:

```java
DataPackLoader.Report report = DataPackLoader.read(packRoot, "examplemod-pack", 10);
for (String problem : report.problems())
{
    // ...
}
DataRegistryManager.ApplyReport applied = DataRegistryManager.apply(report.snapshot());
```

The facade delegates to the repository with one pack, so a missing
`pack.mcmeta` is accepted and the size/count limits above apply. A malformed
`pack.mcmeta` produces an empty snapshot plus the metadata problem rather than
throwing.

`DataPackSnapshot.builder(packId, priority)` can also be filled directly when a
mod does not want to read a directory. The snapshot copies the nested data maps
and makes them unmodifiable. Adding the same registry id and path twice to one
builder keeps the last value.

## Typed keys and casting

`RegistryKey<T>` is compile-time documentation only: it stores a namespace and
a path, and its type parameter is erased. `DataRegistryKey<T>` carries a real
`Class<T>` and uses it for validation and equality.

`DataRegistry` entry operations validate a key in two ways: the key's registry
must equal the registry's key, and `key.type()` must be the same `Class` object
as `type()`. Both failures throw `IllegalArgumentException`. The check is exact
class identity, not `isAssignableFrom`.

`DataRegistryManager.find` and `require` return `DataRegistry<?>` because the
base API cannot name a mod's type at compile time. Recover the type with an
unchecked cast:

```java
@SuppressWarnings("unchecked")
DataRegistry<Gem> gems = (DataRegistry<Gem>) DataRegistryManager.require(
        RegistryKey.of("examplemod", "gems"));
```

The same escape hatch applies to `DataRegistryKey.cast(Object)` when a value has
crossed an erased boundary. Prefer `event.register(DataRegistryKey<T>, T)` when
the key is available; `event.register(String, Object)` performs a runtime
`Class.cast` and throws `ClassCastException` on a mismatch.

String keys are asymmetric:

- `DataRegistry.register(String path, T)` resolves a bare path against the
  registry namespace.
- `DataRegistry.get(String)`, `getOptional(String)`, `containsKey(String)`,
  `getEntry(String)`, and `DataRegistryKey.of(registry, id, type)` parse a bare
  path as `minecraft:path`.
- `DataPackLoader` produces a full tag id (`<packNamespace>:<tagPath>`) and a
  path-only entry key; `DataRegistry.load` adds the registry namespace to the
  entry key. This asymmetry only affects hand-written lookups.

Pass the full `namespace:path` when reading your own entries by string.

## API shadow and replace policy

`WeaverRegistries` constants are immutable id-only keys (`RegistryKey<Object>`).
They are not replaceable, and recreating the same key does not change any
backend. Registering a duplicate game id is rejected by the game registry
backend; there is no unregister, replace, or remove API for game registries.

For data registries:

- `DataRegistryManager.create`/`register` reject a duplicate registry id with
  `RegistryException`.
- `DataRegistry.register` rejects a duplicate entry id with `RegistryException`.
- Programmatic entries use `Integer.MAX_VALUE` priority and cannot be shadowed
  by a pack.
- Pack-vs-pack replacement is explicit through priority: higher replaces,
  equal is a duplicate for entries, lower is skipped. Decode failures keep the
  old value.
- There is no unregister, replace, or remove API for data registry entries.
  `TagStore.replace` replaces tag layers only; it does not replace registry
  entries.
- `DataRegistryManager.reset()` clears every data registry, but it is intended
  for tests, not as a reload API.
- `DataRegistryManager.reloadAll` can apply new snapshots to open registries,
  but it refuses every frozen registry and reports its id. There is no unfreeze
  and no generation swap; a frozen registry stays frozen for the process.

The same id string in a game registry and a data registry names two unrelated
things. Data registries never write to game registries, and game registry events
never fire for data registries.

## Creative tabs

Register a tab in the vanilla registry, during mod setup like other content.
Vanilla's own tabs fill every slot of the creative screen, so Weaver gives
mod tabs their own pages (`net.weaver.api.game.creative`):

```java
Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
        Identifier.fromNamespaceAndPath("examplemod", "main"),
        ModCreativeTabs.builder()
                .title(Component.translatable("itemGroup.examplemod"))
                .icon(() -> new ItemStack(RUBY))
                .displayItems(ModCreativeTabs.items(items -> {
                    items.accept(RUBY);
                    items.accept(RUBY_BLOCK, CreativeTabItems.Visibility.TAB_ONLY);
                }))
                .build());
```

- **Pages.** Page 1 is vanilla, unchanged. Every tab whose id is outside
  `minecraft` goes onto page 2 and up, ten per page (columns 0 to 4 of the
  top row, then of the bottom row), in registry order. The row and column
  the builder was given are ignored. Vanilla's right-aligned tabs (saved
  hotbars, search, operator items, inventory) show on every page.
  `CreativeTabPages` has the layout (`pageOf`, `pageCount`, `currentPage`).
- **Screen.** With more than one page the creative screen shows `<` and `>`
  buttons and a `page / count` label above the window. Turning selects the
  first tab on the new page; selecting a tab any other way turns to its page.
- **Validation.** Vanilla refuses two tabs in one slot at bootstrap; Weaver
  keeps that check for vanilla tabs and skips mod tabs, whose slots cannot
  collide.
- **Why `ModCreativeTabs`.** Vanilla's `displayItems` callback takes
  `CreativeModeTab.Output`, a protected nested type that mod code cannot
  compile against. `ModCreativeTabs.items` adapts a callback that takes
  `CreativeTabItems` instead (`accept`, with a `Visibility` of tab and search,
  tab only, or search only). `ModCreativeTabs.builder()` is
  `CreativeModeTab.builder(TOP, 0)`; the position is assigned anyway.
- **Existing tabs.** Add items to any tab, vanilla or mod, with
  `BuildCreativeTabContentsEvent` (docs/hooks.md).
- The layout is computed from the registry on each side, so client and
  server agree when they have the same tabs, which the join-time registry
  check enforces.

## Limitations

- The repository is not a replacement for Minecraft's pack repository.
  `DataPackRepository` orders multiple roots and parses `pack.mcmeta`, but it
  reads only Weaver's `weaver/registry` and `weaver/tags` trees. It does not
  load arbitrary vanilla data paths, resource packs, or `assets/`, and it does
  not apply packs to the running game.
- The loader opens and closes the data registration phase
  (`beginRegistration()`/`endRegistration()`) during the game's registry
  bootstrap, and each registry receives its `DataRegistryRegisterEvent` at most
  once even if a mod also calls `beginRegistration()` from `onInitialize`.
  Loading packs and calling `freezeAll()` remain the mod's or integration's job.
- There is no game registry bridging for custom data. `DataRegistry` values are
  not game registry entries and are invisible to `RegistryView`,
  `DeferredRegister`, and game tags.
- `registryPath` is a single segment in the loader layout. Extra directories
  become part of the entry or tag path, not part of the registry id.
- `DataPackRepository` and `DataPackLoader` convert every JSON number to
  `Double`. Codecs must convert numeric values themselves. Tag `priority` is
  the exception: the loader converts it to an `int` only when it is integral
  and in range; otherwise it records a problem and keeps the snapshot priority.
- `DataRegistryManager.reloadAll` performs a safe partial apply only. It is not
  a generation swap or class reload: frozen registries are refused and
  `DataRegistryManager.reset()` remains a test hook. For open registries it
  drops pack-loaded tag layers and pack entry priorities first, so re-applying
  edited packs rebinds existing `RegistryEntry` handles with the new values and
  rebuilds tags instead of stacking them. Entries removed from every pack are
  kept with their last value.
- `loadAll` applies every snapshot's entries before any snapshot's tags, and
  pack-loaded tags follow their entries' current values, so a tag may name an
  entry defined by a higher-priority pack.
- Data registry events are independent of `RegisterEvent` and
  `RegistryFreezeEvent`. A listener for one family never receives the other.
- The data registration phase is informational. `beginRegistration()` does not
  reject writes; only `freeze()` does.
- Data entry handles are bound with no native holder, so `holder()` returns
  `null`.

## Related

- [Event bus](events.md)
- [Dispatch model](dispatch.md)
- [Typed game API](api-game.md)
- [Hook points](hooks.md)
- [Datagen](datagen.md)
- [Documentation index](README.md)
