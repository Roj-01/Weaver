# Configuration API

Weaver 2.2 adds a typed configuration API. The game-free API lives in
`net.weaver.api.config`; the file-backed implementation lives in
`net.weaver.core.config`. The API is additive to 2.1: it does not change
`ModContext`, `WeaverMod`, `EventBus`, or the build. `ConfigLoadedEvent` and
`ConfigReloadedEvent` are new event types, not a change to existing event
behavior.

A configuration belongs to one mod and one physical process side. The file is
read when the mod opens its manager; saving is explicit. Hot reload is opt-in:
call `ConfigManager.reload()` directly, or enable the file watcher with
`-Dweaver.config.watch=true`. A client process and a server process keep
separate files. A server may additionally push its loaded document to a client
when the spec opts into `ConfigSyncPolicy.SERVER_TO_CLIENT`; the client applies
it read-only and never writes the file as part of the transfer.

## Status

- New in 2.2. No existing 2.1 API was changed.
- `api` owns the types and never references `net.minecraft.*`.
- `core` owns `WeaverConfig`, `ConfigFiles`, the JSON store, the optional
  `ConfigWatchService`, and the game-free `ConfigSync` layer.
- One manager per mod per process, one file per mod per side.
- The initial load runs once per manager. `ConfigManager.reload()` and the
  opt-in file watcher provide hot reload. There is no config UI. Client-server
  sync is opt-in per spec: `SERVER_TO_CLIENT` sends the server's loaded
  document to a client read-only; `NONE` keeps the configuration process-local.

The [event bus](events.md) delivers `ConfigLoadedEvent` for the initial load and
`ConfigReloadedEvent` for every reload. The [hook points](hooks.md) page
describes the mod lifecycle during which configuration is normally opened. The
[registry API](registry.md) uses the same per-mod namespace rules for mod ids.

## Quick start

A mod normally builds a `ConfigBuilder`, declares values, calls `build()`, and
opens the result during `onInitialize`. The returned `ConfigManager` performs
the initial load. Keep the `ConfigValue` handles and use `get()` for reads.

```java
package com.example.examplemod;

import net.weaver.api.ModContext;
import net.weaver.api.config.ConfigBuilder;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.config.ConfigValue;
import net.weaver.api.event.Side;
import net.weaver.core.config.WeaverConfig;

public final class ExampleConfig
{
    private final ConfigValue<Boolean> enabled;
    private final ConfigValue<Integer> radius;
    private final ConfigValue<String> greeting;
    private final ConfigManager manager;

    public ExampleConfig(ModContext context)
    {
        Side side = WeaverConfig.sideOf(context);
        ConfigBuilder builder = ConfigBuilder.create(context.id(), side)
                .schemaVersion(1)
                .description("Example server settings");

        this.enabled = builder.booleanValue("enabled", true,
                "Enable the feature.");
        this.radius = builder.intValue("radius", 8, 1, 32,
                "Effect radius in blocks.");
        this.greeting = builder.stringValue("greeting", "hello", 1, 32,
                "Message shown on join.");

        ConfigSpec spec = builder.build();
        this.manager = WeaverConfig.open(context, spec);
    }

    public boolean enabled()
    {
        return enabled.get();
    }

    public void setRadius(int value)
    {
        radius.set(value);
        manager.save();
    }
}
```

```java
public final class ExampleMod implements WeaverMod
{
    private ExampleConfig config;

    @Override
    public void onInitialize(ModContext context)
    {
        config = new ExampleConfig(context);
        context.log("enabled=" + config.enabled());
    }
}
```

`ConfigBuilder.create(context.id(), side)` uses the process side returned by
`WeaverConfig.sideOf(context)`. `build()` snapshots the declarations into an
immutable `ConfigSpec`. `WeaverConfig.open(context, spec)` checks that the spec
side matches the context side, then opens the manager on the mod's local event
bus (`context.modEvents()`) and calls `load()` once.

`ConfigValue.get()` reads the current in-memory value. `ConfigValue.set(...)`
validates and publishes a new value but does not write the file. Call
`ConfigManager.save()` when you want the value persisted. Call
`ConfigManager.reload()` when the file may have changed on disk; see
[Hot reload](#hot-reload). A save writes the file but never reloads it.

### Opting into sync

A mod that wants server-authoritative values declares the policy on both the
server and the client spec. The default is `NONE`, so existing configurations
never change behavior.

```java
ConfigBuilder builder = ConfigBuilder.create(context.id(), side)
        .schemaVersion(1)
        .sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
```

The server sends its loaded document when a connection reaches `PLAY`. The
client applies it only when its own spec also declares `SERVER_TO_CLIENT`, and
the transfer is read-only: `client.json` is not created or rewritten by the
sync. See [Client-server sync](#client-server-sync).

### Cross-mod access

`ConfigManager.get(modId)` returns the manager registered by another mod. The
owning mod must have opened its manager first; `ConfigManager.find(modId)`
returns an `Optional` instead of throwing.

```java
ConfigManager other = ConfigManager.get("examplemod");
ConfigValue<Integer> radius = other.spec().value("radius", Integer.class);
int current = radius.get();
```

`ConfigSpec.value(String, Class<T>)` checks the current Java value type. For a
list value the current object is a `List`, so `List.class` is valid but the
element class is not. `ConfigManager.value(String)` returns a `ConfigValue<?>`
without the type check.

### API-only mods

`WeaverConfig` lives in `core`. A mod that depends only on `api` can use the
same manager with its own `ConfigStore`:

```java
ConfigSpec spec = ConfigBuilder.create("examplemod", Side.SERVER)
        .booleanValue("enabled", true, "Enable the feature.")
        .build();
ConfigStore store = /* custom implementation */;
ConfigManager manager = ConfigManager.open(spec, store, eventBus, message ->
{
    // custom log sink
});
```

The three-argument `ConfigManager.open(spec, store, log)` skips the event. A
custom store must report the same side as the spec, return `null` from `read()`
when the file is absent, write atomically, preserve the previous file when a
write fails, and use a `corrupt=true` `ConfigStoreException` for unparseable
content. A store that wants automatic reloads must override `lastModified()`;
the default returns `Long.MIN_VALUE`, which disables watcher-driven reloads for
that store while leaving explicit `reload()` available.

## File layout

The file name is chosen by `ConfigFiles.fileName(Side)`:

| Side | File | When used |
|---|---|---|
| `Side.CLIENT` | `client.json` | A client process, including a client with an integrated server. |
| `Side.SERVER` | `server.json` | A dedicated server process. |
| `Side.BOTH` | `common.json` | Only with an explicit `Side.BOTH` spec and store. |

For a mod id `examplemod`, the layout is
`<config root>/examplemod/client.json` on a client process and
`<config root>/examplemod/server.json` on a server process.
`ModContext.configDir()` already points at `<config root>/examplemod`, so the
effective path is `<context.configDir()>/client.json` or
`<context.configDir()>/server.json`; do not append the mod id again.

`ConfigFiles.forContext(context)` and `WeaverConfig.store(context)` create the
store for the context's side. `ConfigFiles.forMod(configDir, side)` creates a
store for an explicit mod config directory and side. These helpers only
construct paths; they never read or write a file.

A `Side.BOTH` configuration is not reachable through `WeaverConfig.open` on a
normal mod context, because a context side is always `client` or `server`.
Embedders can use `ConfigFiles.forMod(directory, Side.BOTH)` with a matching
`Side.BOTH` spec and `ConfigManager.open`.

A generated file is a pretty-printed UTF-8 JSON object with the schema version
and a flat `values` object. Dotted keys are literal key text, not nested JSON.
The store writes a trailing newline:

```json
{
  "schemaVersion": 2,
  "values": {
    "features.enabled": true,
    "effect.radius": 8,
    "effect.mode": "SURVIVAL",
    "messages.greeting": "hello",
    "messages.channels": [
      "general",
      "help"
    ]
  }
}
```

The file shown above ends with one newline after the closing brace. There are no
comments: `"// ..."` is not JSON and makes the file corrupt.

The JSON store reads only the top-level `schemaVersion` and `values` fields.
Unknown top-level fields are ignored and are not written back. Unknown keys
inside `values` are preserved in memory and written back on the next save, so a
newer mod version can leave values for older code in place. Preserved unknown
keys are not validated. Preservation applies only after a normal parse; the
corrupt, newer-schema, migration-failure, and read-failure paths clear the
preserved map.

Opening or loading never writes a file. A missing file loads every default and
leaves the directory untouched; the first explicit save creates the file.

## Supported types

`ConfigBuilder` declares values through typed methods. Numeric range bounds and
string length or list size bounds are inclusive.

| Type | Builder methods | Java value | Default rules | File values |
|---|---|---|---|---|
| `BOOLEAN` | `booleanValue(key, default, description)` | `Boolean` | Any boolean. | JSON `true` or `false`; strings and numbers are rejected. |
| `INT` | `intValue(key, default, description)`; `intValue(key, default, min, max, description)` | `Integer` | Integral, within the inclusive bounds. | Integral JSON number in `int` range. Fractional and out-of-range numbers are rejected. |
| `LONG` | `longValue(key, default, description)`; `longValue(key, default, min, max, description)` | `Long` | Integral, within the bounds. | Integral JSON number in `long` range. |
| `DOUBLE` | `doubleValue(key, default, description)`; `doubleValue(key, default, min, max, description)` | `Double` | Finite and within the bounds. | Any JSON number that is finite and within the bounds. |
| `STRING` | `stringValue(key, default, description)`; `stringValue(key, default, minLength, maxLength, description)` | `String` | Non-null; length within the bounds. | JSON string. No conversion from numbers or booleans. |
| `ENUM` | `enumValue(key, enumType, default, description)` | The enum type | A non-null member of `enumType`. | JSON string naming a constant exactly; matching is case-sensitive. |
| `STRING_LIST` | `stringListValue(key, default, description)`; `stringListValue(key, default, minSize, maxSize, description)` | `List<String>` | Non-null, no null elements, size within the bounds. | JSON array of strings; no null elements. |
| `INT_LIST` | `intListValue(key, default, description)`; `intListValue(key, default, minSize, maxSize, description)` | `List<Integer>` | Non-null, no null elements, size within the bounds. | JSON array of integral numbers in `int` range. |
| `LONG_LIST` | `longListValue(key, default, description)`; `longListValue(key, default, minSize, maxSize, description)` | `List<Long>` | Non-null, no null elements, size within the bounds. | JSON array of integral numbers in `long` range. |
| `DOUBLE_LIST` | `doubleListValue(key, default, description)`; `doubleListValue(key, default, minSize, maxSize, description)` | `List<Double>` | Non-null, no null elements, finite elements, size within the bounds. | JSON array of finite numbers. |
| `ENUM_LIST` | `enumListValue(key, enumType, default, description)`; `enumListValue(key, enumType, default, minSize, maxSize, description)` | `List<E>` | Non-null, no null elements, members of `enumType`, size within the bounds. | JSON array of exact enum names. |

Rules shared by the types:

- Numeric `min` and `max` are inclusive. A default outside the range is
  rejected.
- For `double` values, both bounds and the default must be finite.
- String length is Java `String.length()`. List size is the number of elements.
  `minLength`/`minSize` must be at least `0`, and `maxLength`/`maxSize` must be
  at least the minimum.
- Int and long values accept integral numbers. The JSON store reads integral
  literals as `Long` when they fit and as `Double` otherwise, so an integral
  decimal form such as `8.0` also parses as an integral value. A fractional
  value such as `8.5` is rejected.
- Lists must be JSON arrays. Every element must satisfy the element type. No
  declared value and no list element may be `null`; a `null` declared value or
  element falls back to the default at load time or fails validation at
  declaration time or on `set`.
- Enum values are written and read by `Enum.name()`. A name must match exactly
  and is case-sensitive.

Defaults are validated when the value is declared, not deferred until
`build()`. An invalid default therefore raises `ConfigValidationException`
during builder use, before a spec exists. `build()` rejects duplicate migration
versions and then snapshots the already-validated declarations.

### ConfigValue handles

`ConfigValue<T>` combines the immutable declaration with the current value.

| Method | Behavior |
|---|---|
| `modId()` | The owning mod id. |
| `key()` | The dotted key used in the file. |
| `description()` | The declaration description; never `null`, possibly empty. |
| `type()` | The `ConfigType`. |
| `typeName()` | Human-readable type, including bounds when present. |
| `defaultValue()` | The immutable default. |
| `get()` | The current value; a plain volatile read with no lock and no validation. |
| `set(T)` | Validates and publishes a new value. Does not save the file. |
| `min()` | The inclusive numeric lower bound, or `null`. |
| `max()` | The inclusive numeric upper bound, or `null`. |
| `minSize()` | The inclusive minimum string length or list size, or `null`. |
| `maxSize()` | The inclusive maximum string length or list size, or `null`. |
| `allowedValues()` | Enum constant names in declaration order; empty for non-enum types. |

`typeName()` returns strings such as `"boolean"`, `"integer in [1, 32]"`,
`"enum net.example.Mode"`, and `"list of string with size in [0, 8]"`.

List values and the default are copied to immutable lists when the value is
constructed and on every `set`. Writers validate before publishing, and readers
see the published value through the volatile field, so concurrent `get()` calls
are safe. The declaration itself never changes. `set(null)` throws
`NullPointerException`; an invalid non-null value throws
`ConfigValidationException`.

## Validation

A builder key has one or more segments matching `[A-Za-z0-9_-]+`, joined by
`.`:

```text
[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*
```

Upper-case letters are allowed in key segments. Empty segments, leading or
trailing dots, `:` and `/` are not. The format is enforced for keys declared
through `ConfigBuilder`; `ConfigData`, which is used by migrations, accepts any
string key so legacy names can be transformed.

The mod id passed to `ConfigBuilder.create` must match `[a-z0-9_.-]+`.
`ConfigBuilder.create` throws `IllegalArgumentException` for a malformed id and
`NullPointerException` for a `null` id or side. `ConfigManager.open` throws
`ConfigException` when a manager is already open for that mod id.

Other builder failures:

| Failure | Exception |
|---|---|
| Malformed key | `IllegalArgumentException` |
| Duplicate key | `ConfigException` |
| `min > max` or `maxSize < minSize` or negative minimum size | `IllegalArgumentException` |
| Non-finite double bound | `IllegalArgumentException` |
| Enum type is not an enum class | `IllegalArgumentException` |
| Default violates the declaration | `ConfigValidationException` |
| Duplicate migration `fromVersion` at `build()` | `ConfigException` |

`ConfigBuilder` is mutable and not thread-safe. Once `build()` succeeds,
mutating methods throw `IllegalStateException`; later `build()` calls return the
same `ConfigSpec`.

### ConfigValidationException

Every value validation failure uses the same message format:

```text
config '<modId>' key '<key>': <reason>
```

The exception exposes `modId()`, `key()`, and `reason()`. It extends
`ConfigException`, which extends `RuntimeException`.

```java
try
{
    radius.set(64);
}
catch (ConfigValidationException failure)
{
    // failure.modId()  -> "examplemod"
    // failure.key()    -> "radius"
    // failure.reason() -> "expected integer in [1, 32] but got 64"
    // failure.getMessage()
    //   config 'examplemod' key 'radius': expected integer in [1, 32] but got 64
}
```

### Per-value load failures

A key that is absent from the file silently receives its default. A key that is
present but invalid is different: it is logged as `CONFIG INVALID`, reset to its
default, and reported as a `ConfigProblem` for that key. Other values in the
same file still load. The file is not rewritten automatically, so the invalid
bytes remain until an explicit save.

```text
[weaver] CONFIG INVALID mod=examplemod side=SERVER key=radius reason=expected integer in [1, 32] but got 64 action=default file=.../server.json
```

The load result reports how many values applied and how many defaulted. The
problem is available through `ConfigLoadResult.problems()`,
`ConfigManager.problems()`, and `ConfigLoadedEvent.problems()`.

## Loading

`ConfigManager.open` performs these steps:

1. Reject `null` spec or store.
2. Require exact side equality between the spec and the store, including
   `Side.BOTH`; a mismatch raises `ConfigException` before I/O.
3. Reject a second manager for the same mod id, even when opens race.
4. Register the manager in the process-wide registry.
5. Call `load()` once.
6. Post one `ConfigLoadedEvent` when a bus was supplied.
7. When `-Dweaver.config.watch=true` is set, register with the core watcher
   after the initial load.

Registering before the load lets a load listener call `ConfigManager.get` for
the mod that is loading. The load result is published before the event is
posted, so a reentrant `load()` call from a listener returns the same result and
does not post again.

| Manager method | Behavior |
|---|---|
| `static open(spec, store, log)` | Opens and loads without an event bus. |
| `static open(spec, store, eventBus, log)` | Opens and loads, posting on the bus. |
| `load()` | The first call performs I/O; later calls return the same result without I/O or a second event. |
| `reload()` | Re-reads and reapplies the file, publishes a new result, and posts `ConfigReloadedEvent`. |
| `generation()` | `0` before the first load, `1` after the initial load, incremented by every completed reload. |
| `isLoaded()` | Whether the initial load attempt completed. |
| `result()` | The `ConfigLoadResult` of the most recent load or reload; throws `IllegalStateException` before the first load. |
| `problems()` | The problems from the most recent load or reload; empty before the first load and when none were found. |
| `value(String)` | A declared `ConfigValue<?>`; unknown keys throw `ConfigException`. |
| `spec()` | The immutable `ConfigSpec`. |
| `store()` | The `ConfigStore`. |
| `save()` | Validates and writes the current values; see [Saving](#saving-and-atomicity). |
| `document()` | An immutable encoded snapshot at the spec schema version without writing the file. |
| `syncResult()` | The most recent applied sync snapshot as an `Optional<ConfigSyncResult>`. |
| `applySyncedValues(values, schemaVersion)` | Applies a client-side snapshot read-only; only the sync layer may call it. |
| `static loadedManagers()` | An immutable, mod-id-sorted snapshot of open managers that finished loading. |
| `static find(modId)` | The open manager as an `Optional`. |
| `static get(modId)` | The open manager; throws `ConfigException` when none is open, listing the open mod ids. |
| `static unregister(modId)` | Removes the registry entry; a test and embedding helper that does not close or reload anything. |

### Missing file

A missing file is a normal first run. Every declared value is set to its
default, no file is created, and the result has `filePresent=false`,
`appliedValues=0`, `defaultedValues` equal to the spec size, and no problems.
The `CONFIG LOADED` diagnostic is still written with `applied=0`.

### Schema versions and migrations

`ConfigBuilder.schemaVersion(int)` defaults to `1` and must be at least `1`.
The file's `schemaVersion` must be a positive integer. The manager compares it
with the spec version:

- Equal: values are applied normally.
- Older: migrations run from the file version to the spec version.
- Newer than the spec: defaults are applied, the file is left untouched, a
  file-level problem is recorded, and `CONFIG SCHEMA NEWER` is logged.

A `ConfigMigration` upgrades exactly one version. `fromVersion()` names the
source version, and `migrate(ConfigData)` mutates raw keys and values in place
for the next version. The manager applies registered migrations in ascending
`fromVersion` order and advances the document version itself after every step,
so a migration must not try to change the version.

`ConfigData` is the mutable JSON-shaped document passed to a migration:

| Method | Behavior |
|---|---|
| `schemaVersion()` | The version at the start of this step. |
| `values()` | An immutable deep snapshot of all values. |
| `keys()`, `size()`, `has(key)` | Key inspection. |
| `get(key)` | A raw value, or `null` when absent. |
| `getString`, `getInt`, `getLong`, `getDouble`, `getBoolean` | Typed reads with a fallback when the key is absent; a present value of the wrong shape throws `ConfigException`. |
| `getList(key)` | The live internal list, so a migration may mutate it in place; an absent key returns an empty immutable list. |
| `put(key, value)` | Stores a JSON-compatible value. |
| `remove(key)` | Removes a key and returns the previous value. |
| `rename(oldKey, newKey)` | Renames a present key while preserving insertion order; an absent source is a no-op and an existing target throws `ConfigException`. |

```java
ConfigBuilder builder = ConfigBuilder.create("examplemod", side)
        .schemaVersion(2)
        .migration(new ConfigMigration()
        {
            @Override
            public int fromVersion()
            {
                return 1;
            }

            @Override
            public void migrate(ConfigData data)
            {
                data.rename("legacy.radius", "effect.radius");
            }
        });

builder.intValue("effect.radius", 8, 1, 32, "Effect radius in blocks.");
ConfigSpec spec = builder.build();
```

Migrations are applied in memory only. The file keeps its old version until an
explicit `save()`, which writes the spec's current schema version.

If a step is missing, `CONFIG MIGRATION MISSING` is logged, every value falls
back to its default, a file-level problem is recorded, and the file is left
untouched. If `migrate` throws a `RuntimeException`, `CONFIG MIGRATION FAILED`
is logged and the same fallback is used. The partially migrated document is
never written.

### Load result

| Component | Meaning |
|---|---|
| `filePresent` | Whether a file was present. A missing file and a non-corrupt read failure report `false`; corrupt content reports `true`. |
| `fileVersion` | The version read from the file, or `0` when not determined. |
| `appliedVersion` | The version now applied in memory. |
| `appliedValues` | How many declared values came from the file. |
| `defaultedValues` | How many declared values used their default. |
| `migrated` | Whether at least one migration ran successfully. |
| `corrupt` | Whether the file content was unparseable or had an invalid schema version. |
| `backup` | The corrupt-file backup path, or `null`. |
| `problems` | Immutable problems in discovery order; `hasProblems()` tests whether the list is non-empty. |

A file-level problem uses the key `"<file>"`. An invalid individual value uses
the declared key.

### Diagnostics

All configuration diagnostics start with `[weaver] `. Log lines go to the sink
passed to `ConfigManager.open`; `WeaverConfig.open` uses `ModContext.log(String)`.
A `null` sink discards them.

```text
[weaver] CONFIG LOADED mod=<modId> side=<SIDE> file=<path> version=<appliedVersion> applied=<n> defaults=<n> problems=<n>
[weaver] CONFIG RELOADED mod=<modId> side=<SIDE> file=<path> version=<appliedVersion> applied=<n> defaults=<n> problems=<n>
[weaver] CONFIG WATCH RELOADED mod=<modId> file=<path> version=<appliedVersion> generation=<n> problems=<n>
[weaver] CONFIG WATCH FAILED mod=<modId> file=<path> reason=<reason>
[weaver] CONFIG INVALID mod=<modId> side=<SIDE> key=<key> reason=<reason> action=default file=<path>
[weaver] CONFIG CORRUPT mod=<modId> side=<SIDE> file=<path> reason=<reason>
[weaver] CONFIG BACKUP mod=<modId> side=<SIDE> file=<path> backup=<path>
[weaver] CONFIG BACKUP FAILED mod=<modId> side=<SIDE> file=<path> reason=<reason>
[weaver] CONFIG LOAD FAILED mod=<modId> side=<SIDE> file=<path> reason=<reason> action=defaults
[weaver] CONFIG SCHEMA NEWER mod=<modId> side=<SIDE> file=<path> version=<fileVersion> supported=<specVersion> action=defaults file-untouched
[weaver] CONFIG MIGRATION MISSING mod=<modId> side=<SIDE> file=<path> from=<version> to=<version+1> action=defaults file-untouched
[weaver] CONFIG MIGRATION FAILED mod=<modId> side=<SIDE> file=<path> from=<version> to=<version+1> reason=<reason> action=defaults file-untouched
[weaver] CONFIG SAVED mod=<modId> side=<SIDE> file=<path> version=<schemaVersion> values=<n>
[weaver] CONFIG SAVE INVALID mod=<modId> side=<SIDE> key=<key> reason=<reason> action=abort file=<path>
[weaver] CONFIG SAVE FAILED mod=<modId> side=<SIDE> file=<path> reason=<reason>
[weaver] CONFIG SYNC INSTALLED channel=weaver:config/sync/v1
[weaver] CONFIG SYNC INSTALL SKIPPED channel=weaver:config/sync/v1 reason=channel-already-registered
[weaver] CONFIG SYNC INSTALL FAILED channel=weaver:config/sync/v1 reason=<reason>
[weaver] CONFIG SYNC SENT mod=<modId> version=<schemaVersion> keys=<n>
[weaver] CONFIG SYNC SEND FAILED mod=<modId> side=SERVER reason=<reason>
[weaver] CONFIG SYNC SKIPPED mod=<modId> side=<SIDE> reason=<reason>
[weaver] CONFIG SYNC SKIPPED side=SERVER reason=<reason> loaded=<n> candidates=<n>
[weaver] CONFIG SYNC REJECTED mod=<modId> side=<SIDE> reason=<reason>
[weaver] CONFIG SYNC INVALID mod=<modId> side=<SIDE> key=<key> reason=<reason> action=default
[weaver] CONFIG SYNC APPLIED mod=<modId> side=<SIDE> version=<schemaVersion> applied=<n> defaults=<n> ignored=<n> problems=<n>
[weaver] CONFIG SYNC OK mod=<modId> side=<SIDE> version=<schemaVersion> applied=<n> defaults=<n> ignored=<n> problems=<n>
[weaver] CONFIG SYNC FAILED mod=<modId> reason=<reason>
```

`CONFIG LOADED` is the final line of every completed initial load, including a
missing file, corrupt content, a newer schema, and migration failure.
`CONFIG RELOADED` is the final line of every completed reload. `reason` is the
failure message, or the exception class name when the message is absent.
`CONFIG WATCH` lines come from the optional watcher and are also written to its
own log sink; the default sink is standard output.

## Corrupt files and backups

The JSON store reports corrupt content with `ConfigStoreException.corrupt()`.
It detects:

| Condition | Reported reason |
|---|---|
| Invalid JSON | `Corrupt config <file>: <parser message>` |
| Root is not an object | `Corrupt config <file>: root must be a JSON object` |
| `schemaVersion` missing or not a number | `Corrupt config <file>: missing or non-number "schemaVersion"` |
| `schemaVersion` is not an integer | `Corrupt config <file>: "schemaVersion" must be an integer` |
| `schemaVersion` is less than `1` | `Corrupt config <file>: "schemaVersion" must be at least 1` |
| `values` missing or not an object | `Corrupt config <file>: missing or non-object "values"` |
| Unsupported JSON value or invalid number | `Corrupt config <file>: unsupported JSON value` or `... invalid number <literal>` |

On corrupt content the manager logs `CONFIG CORRUPT`, asks the store to back the
file up, applies every default, clears preserved unknown keys, records a
file-level problem, and continues the load. Load never throws for corrupt
content.

The backup name is:

```text
<file>.<reason>-<timestamp>.bak
```

`<file>` is the current file name, the reason is sanitized to
`[A-Za-z0-9_-]` (the corrupt path uses `corrupt`), and the timestamp uses
`yyyyMMdd-HHmmss-SSS`. A numeric suffix before `.bak` keeps the name unique when
the same timestamp already exists:

```text
server.json.corrupt-20261104-142530-123.bak
server.json.corrupt-20261104-142530-123-1.bak
```

The backup is a move, not a rewrite, so the original bytes are preserved in the
backup file. A successful backup logs `CONFIG BACKUP` and is exposed as
`ConfigLoadResult.backup()`. If the backup cannot be created, the manager logs
`CONFIG BACKUP FAILED`, sets `backup` to `null`, and still applies defaults and
finishes the load. Custom stores implement the same `backup(String)` contract
and may return `null` when there is no file to back up.

A non-corrupt read failure takes a different path: `CONFIG LOAD FAILED` is
logged with `action=defaults`, defaults are applied, a file-level problem is
recorded, and no backup is attempted during the load. Content that is not valid
UTF-8 is reported as corrupt (`Corrupt config <file>: not valid UTF-8`) and
takes the corrupt path above.

### Saving after a fallback load

A read failure, a newer schema (`CONFIG SCHEMA NEWER ... file-untouched`), a
missing or failed migration, and a corrupt file whose backup failed all leave
the user's file in place while the manager runs on defaults. The first
`save()` after such a load (or reload) moves that file aside before writing, so
an explicit save, for example after downgrading a mod, can never silently
replace the user's settings with defaults:

```text
[weaver] CONFIG BACKUP mod=<modId> side=<SIDE> file=<path> backup=<path>
```

The backup uses the same naming as the corrupt path with the reason
`unreadable`, `newer-schema`, `migration-failed`, or `corrupt`, for example
`client.json.newer-schema-20261104-142530-123.bak`. If the backup fails, the
save is aborted and the file is left untouched:

```text
[weaver] CONFIG BACKUP FAILED mod=<modId> side=<SIDE> file=<path> reason=<reason> action=abort-save
[weaver] CONFIG SAVE FAILED mod=<modId> side=<SIDE> file=<path> reason=<reason>
```

and `save()` throws `ConfigStoreException`; the next save tries the backup
again. A later successful load or reload clears the pending backup.

## Saving and atomicity

Saving is always explicit. `ConfigManager.save()`:

1. Validates every declared value in declaration order. An invalid current value
   logs `CONFIG SAVE INVALID ... action=abort` and throws
   `ConfigValidationException` before any I/O.
2. Starts with the preserved unknown keys from the last normal load, then writes
   every declared key. Declared keys therefore win over any preserved key with
   the same name.
3. Creates a `ConfigDocument` with the spec's current `schemaVersion`.
4. Calls `ConfigStore.write`. The JSON store serializes a pretty-printed object
   with a trailing newline, creates parent directories, writes a temporary file
   in the same directory, forces it to stable storage (`FileChannel.force`), and
   moves it into place with an atomic move when the file system supports one.
   When atomic move is not supported it falls back to a replacing move. The
   parent directory is then forced on a best-effort basis; platforms that do not
   support it (such as Windows) are ignored.

   If the last load fell back to defaults while leaving the file in place, the
   file is backed up first; see
   [Saving after a fallback load](#saving-after-a-fallback-load).

A successful save logs `CONFIG SAVED` with the number of values written,
including preserved unknown keys. A store failure logs `CONFIG SAVE FAILED` and
propagates as `ConfigStoreException`; the previous file must remain intact. A
save is synchronized on the manager, but it is not a transactional snapshot of
all keys: values changed by other threads while the save is in progress are not
rolled back.

## Hot reload

`ConfigManager.reload()` repeats the complete load pipeline:

1. Reads the store again.
2. Applies defaults, schema migrations, and value validation exactly like the
   initial load.
3. Updates the preserved unknown keys and the manager's current
   `ConfigLoadResult`.
4. Posts a new `ConfigReloadedEvent` when the manager was opened with a bus.
5. Logs `CONFIG RELOADED` with the same fields as `CONFIG LOADED`.

Corrupt content follows the same path as the initial load: `CONFIG CORRUPT` is
logged, the store is asked to back the file up, defaults are applied, preserved
unknown keys are cleared, and a file-level problem is recorded. A missing file
reloads every default without writing. An invalid individual value is reset to
its default, reported, and does not prevent other values from applying.
`reload()` never throws for these cases and may be called any number of times.

The initial load is generation `1`. Every completed reload increments the
generation, so the first reload is generation `2`. `ConfigManager.generation()`
and `ConfigReloadedEvent.generation()` report the same value. A reload that
falls back to defaults still increments the generation because it publishes a
new in-memory state. `load()` remains the one-I/O-pass initial load: it does not
reload when a later call is made, and the manager keeps returning the result of
the most recent load or reload.

`load()`, `reload()`, and `save()` share the manager's load lock. A reload
cannot observe a partially completed save, and two reloads cannot interleave.
The result and generation are published before the event is posted, so a
listener can call `reload()` again safely.

### File watching

Hot reload is off by default so boot order and I/O stay deterministic. Start a
JVM with:

```text
-Dweaver.config.watch=true
```

With that property enabled, `ConfigManager.open` registers the new manager with
the core `ConfigWatchService`. The API module cannot depend on core, so the
registration is reflective; in an API-only deployment the property has no
effect and an explicit `reload()` is still available. `ConfigWatchService`
uses one `java.nio.file.WatchService` and one daemon thread:

- It watches the parent directory of each registered config file.
- It requests `ENTRY_MODIFY` and `ENTRY_CREATE` events only.
- Events are debounced for 250 milliseconds, so a multi-step editor write
  produces at most one reload.
- It calls `reload()` only when `ConfigStore.lastModified()` differs from the
  token observed by the previous scan. A store that does not implement
  `lastModified()` returns `Long.MIN_VALUE` and is never reloaded automatically.
- A missing file is tracked; when the file is created, the next scan reloads it.
- A watcher-driven reload and its `ConfigReloadedEvent` run on the watcher
  daemon thread. Listeners that touch game state must marshal to the appropriate
  game thread.
- Watch failures and lifecycle events are logged as `[weaver] CONFIG WATCH ...`.

The watcher is also usable directly:

```java
try (ConfigWatchService watcher = new ConfigWatchService())
{
    watcher.watch(manager);
    // ...
    watcher.unwatch(manager);
}
```

`watch()` registers a file but does not start the thread; `shared()` starts its
instance automatically.

`ConfigWatchService.shared()` returns the started process-wide instance used by
the system property. `watch()` registers a file and starts the watcher thread
lazily. `scanOnce()` checks every tracked file immediately, bypasses the
debounce, and returns the paths that were reloaded, including a reload the
watcher thread already performed; use it in tests or explicit polling instead
of waiting for the watcher thread. `close()` is idempotent.

A save still does not auto-trigger a reload: `ConfigManager.save()` writes the
current values, never calls `reload()`, and never posts a configuration event.
When watching is enabled, the save's file write is an ordinary file change, so
the watcher may observe it and schedule a reload after the debounce. The reload
is still asynchronous and reads the file that was just written.

## Client-server sync

Client-server sync is a one-way server push. It is disabled by default and
requires no mod callback beyond declaring the policy. The transport is the
game-free `net.weaver.api.network` API, so `core` stays independent of Minecraft
classes.

### Policy

`ConfigBuilder.sync(ConfigSyncPolicy)` stores the policy in the immutable
`ConfigSpec`; `ConfigSpec.syncPolicy()` returns it. The default is
`ConfigSyncPolicy.NONE`.

| Policy | Server behavior | Client behavior |
|---|---|---|
| `NONE` | Nothing is sent. | Nothing is accepted. |
| `SERVER_TO_CLIENT` | The loaded document is sent on every `NetworkConnectedEvent` that reaches `PLAY`. | A received server document is validated and applied read-only. |

Both processes normally declare the same policy for the same mod id. The server
only sends managers whose spec is `SERVER_TO_CLIENT`; the client only accepts
when its own manager also declares `SERVER_TO_CLIENT`. A policy mismatch is
logged and skipped, not treated as an error.

### Wire message

`ConfigSyncDocument` is the payload. It carries:

| Field | Meaning |
|---|---|
| `modId` | The owning mod id, matching `[a-z0-9_.-]+`. |
| `side` | The producing side. A valid server document uses `Side.SERVER`. |
| `schemaVersion` | The schema version of the encoded values. |
| `values` | The encoded key/value map in insertion order. |

The channel is `weaver:config/sync/v1`, owned by the loader (`weaver`), and is
registered clientbound in `NetworkPhase.PLAY`. `ConfigSyncDocument.CODEC`
starts with format version `1`, writes the mod id, side ordinal, schema
version, key count, and each key/value pair. Values use the same JSON-compatible
model as `ConfigDocument`: booleans, strings, integral and decimal numbers,
lists, and string-keyed nested maps.

Wire limits:

| Limit | Value |
|---|---|
| Top-level keys | `1024` |
| Key length | `256` Java chars |
| String value length | `32_768` Java chars |
| List or nested map elements | `8192` |
| Nesting depth | `8` |
| Encoded payload | `NetworkProtocol.MAX_PAYLOAD_BYTES` (1 MiB) |

Decoding is bounded before allocation, rejects duplicate keys, validates the
format version, side ordinal, and schema version, uses strict UTF-8, and requires
exact consumption of the payload. Listeners and handlers are failure-isolated:
a bad document is logged once per mod and reason, and never disconnects the
peer. A malformed wire payload is still subject to the bridge's existing
malformed-payload policy.

### Server authority

The server is authoritative for synced values. When a snapshot is applied:

- Every declared key present in the snapshot overwrites the client's live value,
  including an unsaved local edit. The server document wins.
- A declared key absent from the snapshot falls back to its default, matching
  the file-load path.
- A value that fails the declared type, bounds, or size validation is logged as
  `CONFIG SYNC INVALID`, reset to its default, and reported as a
  `ConfigProblem`.
- Keys the client spec does not declare are counted in
  `ConfigSyncResult.ignoredKeys()` and otherwise ignored.
- The client's configuration file is never written. A later explicit
  `ConfigManager.save()` writes the client's current values, including synced
  ones, but that save is local and is not sent back to the server.

The transfer does not change `ConfigManager.result()`,
`ConfigManager.problems()`, or the load `generation()`. Its outcome is published
separately through `ConfigManager.syncResult()` and `ConfigSyncedEvent`.
`applySyncedValues` shares the manager's load lock with `load()`, `reload()`,
`save()`, and `document()`, so a sync cannot interleave with a file read or
write. A later local `reload()` re-reads `client.json` and can replace synced
values with the local file.

### Connection flow

`ConfigSync.install(log::accept)` registers the payload type on
`NetworkRegistry.global()` and subscribes to `NetworkConnectedEvent` on
`EventBus.GAME`. The loader boot should call it after the network bridge is
active:

```java
NetworkBridgeInstall.activate(space.gameLoader(), networkBridgeArmed, log);
ConfigSync.install(log::accept);
```

`install` is idempotent. It returns `false` when the same registry and bus are
already installed, and it skips a channel that another component already
registered instead of throwing. Re-installing against a different registry or
bus removes the previous connection listener.

On a server connection:

1. The connected event's local side must be `SERVER`, the connection must be
   open, and the phase must be `PLAY`.
2. `ConfigManager.loadedManagers()` is snapshotted and sorted by mod id.
3. Managers without `SERVER_TO_CLIENT` are skipped. A manager whose spec side is
   not server-capable is logged once and skipped.
4. Each eligible manager is encoded from `ConfigManager.document()` and sent
   with `ConfigSync.sendTo(connection, manager)`. One manager's failure is
   logged and does not stop the remaining managers.
5. When nothing is sent, a `CONFIG SYNC SKIPPED` line records why.

A mod opened after the connection reached `PLAY` does not receive a document
until the next connection event. There is no request, acknowledgement, retry,
or client-to-server upload.

On a client, the registered handler:

1. Rejects a non-client receiver, a document whose side is not `SERVER`, a
   missing manager, a `NONE` policy, a `SERVER` manager, or a schema version
   that differs from the spec.
2. Applies the document through `ConfigManager.applySyncedValues` and posts
   `ConfigSyncedEvent`.
3. Never disconnects or throws; each rejection reason is logged once per mod.

Tests and embedders can bypass the connection event with the two explicit
steps: `ConfigSync.sendTo(connection, manager)` and
`ConfigSync.applyTo(manager, document)`.

### ConfigSyncedEvent

`net.weaver.api.event.events.config.ConfigSyncedEvent` is posted after a client
applies a snapshot, provided the manager was opened with a bus. Its logical side
is always `LogicalSide.CLIENT`, even when the spec side is `BOTH`. The event is
not cancelable and has no result.

| Accessor | Meaning |
|---|---|
| `spec()` | The receiving `ConfigSpec`. |
| `result()` | The `ConfigSyncResult` of the application. |
| `modId()` | The owning mod id; also satisfies `ModScopedEvent`. |
| `side()` | The spec side, `CLIENT` or `BOTH` for a normal client manager. |
| `schemaVersion()` | The applied snapshot schema version. |
| `appliedValues()` | How many declared values came from the snapshot. |
| `defaultedValues()` | How many declared values used their default. |
| `ignoredKeys()` | How many undeclared keys were ignored. |
| `problems()` | Invalid-value problems in discovery order. |

Because the event implements `ModScopedEvent`, `EventFilter.modId(...)` works.
The event is posted on the thread that delivered the payload: the game thread
for the real bridge, or the caller's thread for an explicit `applyTo`.

## Sides and threading

Configuration uses the physical process side, not the logical side:

- `ModContext.side()` is `client` or `server`; `WeaverConfig.sideOf` maps it
  case-insensitively to `Side.CLIENT` or `Side.SERVER` and throws
  `ConfigException` for any other value.
- A client with an integrated server is one process side. Both logical sides
  share that process's `client.json`.
- `ConfigManager.open` requires the spec side and store side to be exactly
  equal, including `Side.BOTH`. `WeaverConfig.open` separately requires the spec
  side to equal the context side before it creates or reads a file. Because the
  side is part of the path and both checks run before I/O, a server
  configuration cannot be loaded from or saved to the client file by accident.

Threading rules:

- `ConfigSpec` is immutable after `build()`.
- `ConfigValue.get()` is a volatile read and is safe from concurrent readers.
- `ConfigValue.set(...)` validates before publishing; concurrent setters do not
  corrupt the stored value, but the last publish wins.
- The manager registry is concurrent, and the initial load is guarded so it
  runs exactly once per manager.
- `load()`, `reload()`, `save()`, `document()`, and `applySyncedValues(...)`
  share the manager's load lock. A save is not transactional across keys.
- A sync applies values without writing the store and without changing the load
  generation. On a client, a later local save or reload can replace the synced
  state.
- The JSON store serializes operations on one store instance. It does not
  coordinate multiple processes; use one writer process per file.

Editing a config file while the game is running takes effect only when
`ConfigManager.reload()` is called or the opt-in watcher observes the change and
calls it. There is no automatic reload by default, and a save does not reload
the file.

## ConfigLoadedEvent

`net.weaver.api.event.events.config.ConfigLoadedEvent` is posted once after the
manager's first load attempt, provided `ConfigManager.open` was given a bus.
`WeaverConfig.open(context, spec)` supplies `context.modEvents()`. The event is
not cancelable and has no result.

| Accessor | Meaning |
|---|---|
| `spec()` | The `ConfigSpec` that was loaded. Its `ConfigValue` handles are live. |
| `result()` | The complete `ConfigLoadResult`. |
| `modId()` | The owning mod id; also satisfies `ModScopedEvent`. |
| `side()` | The spec side, `CLIENT`, `SERVER`, or `BOTH`. |
| `schemaVersion()` | The schema version now applied in memory (`result.appliedVersion()`). |
| `problems()` | The load problems (`result.problems()`). |

The event's `logicalSide()` metadata is derived from the config side:
`CLIENT` maps to `LogicalSide.CLIENT`, `SERVER` to `LogicalSide.SERVER`, and
`BOTH` to `LogicalSide.BOTH`. This describes the configuration's side; it is
not a per-callback logical side.

The manager is registered before loading and the load result is published before
the event is posted, so a listener can call `ConfigManager.get(event.modId())`.
`load()` from inside a listener returns the same result and does not post a
second event.

The event is posted for every completed first load, including a missing file, a
corrupt file, a newer schema, a failed migration, and files with invalid
individual values. A missing file has an empty problem list because it is a
normal defaults load; corrupt, newer, migration, and invalid-value cases add
`ConfigProblem` entries. Because the event implements `ModScopedEvent`,
`EventFilter.modId(...)` works:

```java
context.modEvents().addListener(ConfigLoadedEvent.class,
        event ->
        {
            for (ConfigProblem problem : event.problems())
            {
                context.log(problem.key() + ": " + problem.message());
            }
        },
        EventFilter.modId(context.id()));
```

Register the listener before opening the configuration: `WeaverConfig.open`
posts the event synchronously during the open call. Classpath discovery of
`@EventBusSubscriber` classes runs after every mod's `onInitialize`, so an
auto-discovered subscriber does not see a load that already happened during
`onInitialize`.

## ConfigReloadedEvent

`net.weaver.api.event.events.config.ConfigReloadedEvent` is posted once for every
completed `ConfigManager.reload()`, including a watcher-driven reload, provided
the manager was opened with a bus. The initial load still posts
`ConfigLoadedEvent`, not this event. The event is not cancelable and has no
result.

| Accessor | Meaning |
|---|---|
| `spec()` | The `ConfigSpec` that was reloaded. Its `ConfigValue` handles are live. |
| `result()` | The complete `ConfigLoadResult` published by this reload. |
| `generation()` | The generation after the reload: `2` for the first reload after a normal open, then one higher per reload. |
| `reloadCount()` | The number of completed reloads (`generation() - 1`): `1` for the first reload after a normal open. |
| `modId()` | The owning mod id; also satisfies `ModScopedEvent`. |
| `side()` | The spec side, `CLIENT`, `SERVER`, or `BOTH`. |
| `schemaVersion()` | The schema version now applied in memory (`result.appliedVersion()`). |
| `problems()` | The reload problems (`result.problems()`). |

The logical side metadata follows the same mapping as `ConfigLoadedEvent`. The
result and generation are published before the event is posted, so a listener
can call `ConfigManager.get(event.modId())` and can safely call `reload()`
again. A missing, corrupt, newer, migration-failure, or partially invalid file
still produces one event per completed reload. Because the event implements
`ModScopedEvent`, `EventFilter.modId(...)` works the same way as for
`ConfigLoadedEvent`.

## Integration status

The configuration work added no changes to `ModContext`, `WeaverMod`,
`EventBus`, `LoaderAgent`, or the build files. `WeaverConfig.open(context, spec)`
is the one-call path for a normal mod, and `ConfigManager.get(modId)` provides
cross-mod access once the owning mod has opened its manager. Hot reload needs no
loader call: set `-Dweaver.config.watch=true` before launch, or call
`ConfigManager.reload()` explicitly. Client-server sync needs one loader call,
`ConfigSync.install(log::accept)`, after the network bridge is active; it does
not require a mod callback. There is no `ModContext.config(...)` method in 2.2;
adding one is an integration request for a future release, not part of this
API.

## Limitations and non-goals

- The in-game settings screen is generated from the spec (see
  [client: mod list and settings screens](client.md#mod-list-and-settings-screens)).
  It has no per-value custom widgets; a mod that needs them registers its own
  screen with `ConfigScreens.register`.
- Hot reload is explicit or opt-in. `reload()` is always available, but the
  watcher requires `-Dweaver.config.watch=true`, and `save()` never triggers a
  reload. There is no reload on a timer or at a lifecycle hook.
- Sync is one-way and whole-configuration. There is no per-key sync flag, no
  client-to-server upload, no acknowledgement, retry, or conflict resolution.
  `SERVER_TO_CLIENT` sends every declared key; a client cannot opt out per key
  while keeping the policy.
- Sync runs only when a connection reaches `PLAY`. Managers opened later, or
  configurations changed on the server after that event, are not pushed until
  the next connection. A client local `save()` or `reload()` can replace the
  synced state with the client's own file.
- The server snapshot can include preserved unknown keys from its file, but a
  client that does not declare those keys ignores them.
- JSON has no inline comments. Descriptions are API metadata available through
  `ConfigValue.description()` and `ConfigSpec.description()`; the JSON file is
  generated and contains only `schemaVersion` and values.
- No per-world configuration and no automatic per-save or per-level files.
- No encryption, permissions, or access control for config files.
- One manager per mod per process. A duplicate open raises `ConfigException`.
- One writer process per file. The JSON store does not lock across processes.
- Unknown value keys are preserved but not validated, and only after a
  successful parse. Unknown top-level fields are ignored.
- List element ranges are not supported; lists only have size bounds.
- There are no nested configuration sections beyond dotted key prefixes. The
  `values` object is flat, and dots are part of the key text.

## Related

- [Event bus](events.md)
- [Registry API](registry.md)
- [Hook points](hooks.md)
- [Toolchain](toolchain.md)
- [Repository README](../README.md)
