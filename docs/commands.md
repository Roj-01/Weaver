# Command API

Weaver 2.2 adds a server-authoritative command and permission API. The game-free
API lives in `net.weaver.api.command`; the reflective Brigadier backend lives
in `net.weaver.core.command`. Commands belong to the server side. The API is
additive: it does not change `ModContext`, `WeaverMod`, the event bus, the
registry API, or the build.

The [hook points](hooks.md) page describes `CommandRegisterEvent` and its
worker-thread caveat. The [event bus](events.md) page describes the general
dispatch model; `CommandTreeSyncEvent` is declared in
`net.weaver.api.event.events.command`. The [registry API](registry.md) page
describes the separate game-registry system; command names are not registry
keys.

## Status

- New in 2.2. No existing 2.1 API was changed.
- Server-authoritative. Commands are registered into the game's Brigadier
  dispatcher by the server.
- `api` and `core` do not compile against Brigadier. The API checks for
  `com.mojang.brigadier.CommandDispatcher` with a non-initializing class lookup
  and loads `net.weaver.core.command.CoreCommandBackend` reflectively. A custom
  backend can be installed for tests or embedding.
- The core registrar is reflection-only. It builds the live tree through
  Brigadier objects supplied by the game class loader.
- Permissions are Weaver-owned. Explicit decisions are in-memory by default;
  when the loader installs `PermissionStoreSupport`, they persist to a JSON
  file under the loader data directory. They are not vanilla permission sets
  and are not forwarded to a proxy.
- Automatic client command-tree synchronization is implemented by the built-in
  `ClientCommandTreeMixin` in `api-game`. The client records a snapshot with
  source `"client"` after `ClientPacketListener#handleCommands`. See
  [Client command tree sync](#client-command-tree-sync).
- Late registrations are applied on the server thread and the updated tree is
  resent to online players. Live removal rebuilds the Weaver portion of the
  live dispatcher and resends it. See
  [Late registration and resend](#late-registration-and-resend) and
  [Live removal](#live-removal).

## Quick start

A mod builds immutable `CommandSpec` and `CommandNodeSpec` trees and registers
the root with `CommandRegistry`. Register during `onInitialize` or `onRegister`
so the spec exists before the server builds its dispatcher.

```java
package com.example.examplemod;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;
import net.weaver.api.command.CommandContext;
import net.weaver.api.command.CommandNodeSpec;
import net.weaver.api.command.CommandRegistry;
import net.weaver.api.command.CommandSpec;
import net.weaver.api.command.PermissionNode;
import net.weaver.api.command.PermissionPolicy;

public final class ExampleMod implements WeaverMod
{
    @Override
    public void onInitialize(ModContext context)
    {
        CommandNodeSpec gate = CommandNodeSpec.literal("gate")
                .permission(PermissionNode.parse("examplemod:command/gate"),
                        PermissionPolicy.DENY, 2)
                .executor(ExampleMod::openGate)
                .build();

        CommandSpec spec = CommandSpec.command("example")
                .modId(context.id())
                .description("Example commands")
                .alias("ex")
                .child(gate)
                .build();

        CommandRegistry.register(spec);
    }

    private static int openGate(CommandContext context)
    {
        context.source().sendSuccess("gate open");
        return 1;
    }
}
```

This registers `/example gate` and the alias `/ex gate`. The `gate` node uses
the permission node `examplemod:command/gate` with the default `DENY` policy and
required op level `2`. The alias is a top-level redirect literal to the root,
so `/ex gate` executes the child with the same permission predicate.

An argument child with suggestions:

```java
CommandNodeSpec greeting = CommandNodeSpec.literal("greet")
        .child(CommandNodeSpec.argument("name", CommandArgumentType.WORD)
                .suggests(context -> List.of("alex", "steve"))
                .executor(context ->
                {
                    String name = context.stringArgument("name");
                    context.source().sendSuccess("hello " + name);
                    return 1;
                })
                .build())
        .build();
```

Argument nodes should be shorthand. `CommandArgumentType.WORD` maps to
Brigadier's `StringArgumentType.word()`, and the parsed value is available under
the argument name.

## Tree model

`CommandSpec` is the immutable root definition. It carries:

- a root literal name;
- an optional mod id and description;
- an optional root permission node, policy, and required op level;
- aliases;
- an optional root executor;
- optional root suggestions;
- child nodes.

The root name must be non-blank and match `[a-z0-9_\-/:]+`; uppercase letters,
whitespace, and other characters are rejected. Aliases must be non-blank,
contain no whitespace, differ from the root name, and be unique. Child names
must be unique under one parent.

`CommandNodeSpec` is a literal or an argument:

```java
CommandNodeSpec.literal("gate")
CommandNodeSpec.argument("amount", CommandArgumentType.INTEGER)
```

Literal names and argument names must be non-blank and contain no whitespace.
Argument nodes require a `CommandArgumentType`. Literal nodes must not have an
argument type. Suggestions are only valid on argument nodes; a literal with
suggestions is rejected at `build()`.

Supported built-in argument kinds:

| Constant | Kind | Brigadier mapping |
|---|---|---|
| `CommandArgumentType.STRING` | `STRING` | Quoted or unquoted string. |
| `CommandArgumentType.WORD` | `WORD` | Single unquoted word. |
| `CommandArgumentType.GREEDY_STRING` | `GREEDY_STRING` | Rest of the input. |
| `CommandArgumentType.INTEGER` | `INTEGER` | 32-bit signed integer. |
| `CommandArgumentType.LONG` | `LONG` | 64-bit signed integer. |
| `CommandArgumentType.DOUBLE` | `DOUBLE` | Double-precision float. |
| `CommandArgumentType.FLOAT` | `FLOAT` | Single-precision float. |
| `CommandArgumentType.BOOLEAN` | `BOOLEAN` | Boolean. |

A mod that needs an exact Brigadier type wraps the raw object:

```java
CommandArgumentType.custom(
        net.minecraft.commands.arguments.EntityArgument.entities());
```

The raw object must be an instance of Brigadier's `ArgumentType` as seen by the
core registrar. A non-Brigadier object is rejected with
`CommandRegistrationException`; the spec is logged at apply time and is not
inserted into the dispatcher. `CommandArgumentType` compares by kind and custom
type, so a raw argument type should have stable equality or be a singleton.

Aliases are additional top-level root literals. The registrar creates the
alias literal, copies the target root's executor and requirement onto it, and
then redirects it to the root node. Copying the short form keeps a bare `/alias`
usable; longer inputs follow the redirect into the target subtree. The alias
therefore inherits the root's permission requirement, but child permissions are
still enforced when the child node is reached.

`CommandNodeSpec.redirect(String sibling)` creates a redirect from a literal to
a sibling literal in the same parent. A redirect node cannot be an argument,
cannot have an executor, and cannot have children. The target must be a sibling
literal; a missing target or a redirect cycle is rejected by the registrar and
logged. Redirects are useful for short subcommand names:

```java
CommandNodeSpec.literal("list")
        .redirect("show")
        .build()
```

The target node is built first, then the redirecting literal copies its
command and requirement and points at it. When the redirecting literal also
declares its own `permission(...)`, its requirement is the AND of its own
permission and the target's requirement, so a privileged literal that redirects
to an unprivileged sibling stays gated by its own permission.

Executors and suggestions:

- `CommandSpec.executor(CommandExecutor)` and
  `CommandNodeSpec.executor(CommandExecutor)` attach an executor. The root and
  every child may have one.
- `CommandSpec.suggests(CommandSuggestionProvider)` attaches root suggestions.
  The root is a literal, so Brigadier may ignore root suggestions unless an
  argument child can consume them.
- `CommandNodeSpec.suggests(CommandSuggestionProvider)` is only valid on
  argument nodes.
- A literal may have children and an executor; an executor makes the literal
  directly executable while its children remain reachable.

## Execution

`CommandExecutor` receives a `CommandContext` and returns an `int`:

```java
@FunctionalInterface
public interface CommandExecutor
{
    int execute(CommandContext context);
}
```

The result is the Brigadier result value. `1` conventionally means success and
`0` conventionally means failure. The backend does not reinterpret the value.

`CommandContext` is immutable and exposes:

| Method | Result |
|---|---|
| `source()` | The wrapped `CommandSource`, never `null`. |
| `input()` | The raw command input, never `null`. |
| `arguments()` | An unmodifiable map of parsed argument values, never `null`. |
| `raw()` | The opaque raw Brigadier context, or `null`. |
| `argument(String)` | The raw argument value, or `null` when absent. |
| `stringArgument(String)` | `String.valueOf(value)`, or `null` when absent. |
| `optionalStringArgument(String)` | `Optional` form of `stringArgument`. |
| `intArgument(String)` | A `Number` converted with `intValue()`, or a trimmed string parsed as an integer. Throws `IllegalArgumentException` when absent or not numeric. |

The argument map contains only values that Brigadier reports as non-null. It
is built from every argument node name in the registered spec tree, so an
argument nested under a literal is available under its own name. There are no
built-in `long` or `double` accessors; use `argument(name)` and convert or cast
the value.

`CommandSource` wraps the raw game source:

```java
public final class CommandSource
{
    public CommandSource(Object raw, String name, String displayName,
            Consumer<String> success, Consumer<String> failure) { }

    public static CommandSource of(Object raw) { }

    public Object raw() { }
    public String name() { }
    public String displayName() { }
    public boolean isAllowed(PermissionNode node) { }
    public void sendSuccess(String message) { }
    public void sendFailure(String message) { }
}
```

- `raw()` is the game `CommandSourceStack` in a live command.
- `name()` is the diagnostic source name, resolved from `getTextName()` first,
  then from the display name, then from the source class's simple name.
- `isAllowed(node)` delegates to `PermissionService.isAllowed(raw, node)`.
- `sendSuccess` and `sendFailure` never throw. `null` messages become empty
  strings, a missing consumer is a no-op, and a failing consumer is ignored.
  The core source factory prefers `sendSuccess(Supplier, boolean)` and
  `sendFailure(Component)`, falls back to `sendSystemMessage(Component)`, and
  finally becomes a no-op.

Exception isolation is deliberate. If an executor throws any throwable, the
core proxy logs
`[weaver] COMMAND EXECUTE FAILED name=<root> node=<node> reason=...` and
returns `0`. The failure does not escape into Brigadier or stop other commands.
Throwing `CommandException` is therefore a controlled failure only in the sense
that it is logged and converted to result `0`; it does not produce a special
Brigadier error message by itself. Use `source().sendFailure(...)` when the
caller should see a message.

Suggestions are evaluated by the game while it builds a suggestion list. The
provider receives a `CommandSuggestionContext`:

- `commandContext()` is the invocation `CommandContext`;
- `input()` is the full current input;
- `remaining()` is the unparsed input at the cursor;
- `rawContext()` is the opaque Brigadier suggestion context.

`CommandSuggestionProvider.suggest` returns a `List<String>`; `null` is treated
as empty and `null` entries are skipped. A provider failure is logged as
`COMMAND SUGGEST FAILED` and returns an empty suggestion list. Suggestions run
on the server thread and must be fast and must not block.

## Permissions

Permission nodes are namespaced values:

```java
PermissionNode node = PermissionNode.parse("examplemod:command/gate");
PermissionNode same = PermissionNode.of("examplemod", "command/gate");
PermissionNode wildcard = PermissionNode.wildcard("examplemod");
```

Grammar:

- The namespace matches `[a-z0-9_.-]+`.
- The path is either the single wildcard `*` or slash-separated segments
  matching `[a-z0-9_.-]+`.
- `parse` requires exactly one colon and a non-empty namespace and path. There
  is no implicit namespace.
- `of` validates the namespace and path directly.
- `wildcard(namespace)` creates `namespace:*`. The wildcard is the only path
  that may contain an asterisk.
- Hierarchy is path-based. `examplemod:command/gate/open` has the ancestors
  `examplemod:command/gate` and `examplemod:command`; its wildcard is
  `examplemod:*`. The wildcard has no ancestors.
- `parent()` returns the immediate parent, empty for a single-segment path or
  the wildcard. `ancestors()` returns the nearest-first immutable ancestor list
  excluding the node itself and the wildcard.
- Equality and hashing use the canonical `namespace:path` text.

`PermissionPolicy` has two values:

- `ALLOW`: allow when no explicit decision applies.
- `DENY`: deny when no explicit decision applies, unless the registered required
  op level is positive and the source's op level reaches it.

The default policy for `CommandSpec` and `CommandNodeSpec` is `DENY` with op
level `0`. That default only applies when a permission node is set. A node with
no permission node has no `requires` predicate and is open to anyone the game
lets reach it.

### Registering nodes

The core registers every permission node in a command spec when
`CommandRegistry.register(spec)` is called. Nodes may also be registered
directly:

```java
PermissionRegistration registration = PermissionService.register(
        PermissionNode.parse("examplemod:feature"),
        PermissionPolicy.ALLOW, 0);
```

Registration is reference-counted by node, policy, and required op level:

- The same node with the same policy and op level increments the shared count
  and returns a new handle.
- The same node with a different policy or op level is rejected with
  `IllegalArgumentException`. A command spec that contains the same node with
  conflicting policy/op level is rejected with `CommandRegistrationException`.
- `PermissionRegistration.unregister()` is idempotent and decrements the count;
  the registration is removed at zero.
- `PermissionRegistration.node()` returns the node; `isActive()` reports
  whether the handle still contributes a live reference.

`PermissionService` is a process-wide static service. `install(PermissionResolver)`
swaps the resolver and keeps existing registrations and explicit decisions.
`reset()` clears registrations and decisions and restores the simple resolver;
it is a shutdown/test helper and is not atomic with concurrent registrations.

### Decision algorithm

`PermissionService.isAllowed(Object source, PermissionNode node)` uses this
exact order:

1. Resolve the subject with `resolver.subject(source)`. If it returns `null`,
   use the raw source as the subject. If the resolver throws, use the raw source.
2. Look for an explicit decision for that subject on the exact node, then on
   the node's ancestors nearest first, then on the namespace wildcard, then on
   the wildcard's ancestors. The first explicit decision wins. An explicit deny
   therefore beats an `ALLOW` policy and an op fallback; an explicit allow beats
   a `DENY` policy.
3. If there is no explicit decision and the exact node has never been
   registered, deny.
4. If the registered policy is `ALLOW`, allow.
5. If the registered policy is `DENY` and the registered required op level is
   at or below zero, deny.
6. Otherwise allow only when the source's op level is greater than or equal to
   the registered required op level.

An explicit decision is checked before registration, so an explicit allow can
allow a node that was never registered. Registration policy is not consulted
for a node with an explicit decision.

Explicit decisions:

```java
PermissionService.allow(source, node);
PermissionService.deny(source, node);
PermissionService.clear(source, node);
PermissionService.clearAll(source);
boolean explicit = PermissionService.hasExplicitDecision(source, node);
int op = PermissionService.opLevel(source);
```

- `allow`/`deny` record one decision for the resolved subject and exact node.
  Through ancestor and wildcard lookup, the decision also applies to
  descendants of that node.
- `clear(source, node)` removes the explicit decision for that exact node only.
  Ancestor and wildcard decisions remain.
- `clearAll(source)` removes every explicit decision for the subject.
- `hasExplicitDecision` uses the same node/ancestor/wildcard lookup but ignores
  registered policy.
- `opLevel(source)` returns the resolver's op level, or `0` when the resolver
  throws.

### Resolvers

`PermissionResolver` maps a raw source to a subject and an op level:

```java
public interface PermissionResolver
{
    Object subject(Object source);

    int opLevel(Object source);

    default String subjectKey(Object subject) { }

    static PermissionResolver simple() { }
}
```

The simple resolver uses the source itself as the subject and always reports op
level `0`. The core command backend installs a `MinecraftPermissionResolver`
when it installs:

- The subject is the source entity (`getEntity()`), then the source player
  (`getPlayer()`), then the raw source. This lets explicit decisions follow a
  player or entity across command source wrappers.
- On 26.3 the game no longer attaches an integer op level to a command source.
  The resolver reads `CommandSourceStack.permissions()`, probes the
  `Commands.LEVEL_OWNERS`, `LEVEL_ADMINS`, `LEVEL_GAMEMASTERS`, and
  `LEVEL_MODERATORS` `PermissionCheck` constants from highest to lowest, and
  maps the first match to `4`, `3`, `2`, or `1`. A source without a permission
  set, or a non-game source such as a test string, resolves to `0`.
- Reflective failures are contained: a missing class or method yields the raw
  source as the subject and op level `0` rather than an exception.

Resolver methods may be called on hot paths, including command tree evaluation.
They must be fast, allocation-light, and thread-safe. Do not block or schedule
work from a resolver. Return stable, immutable subjects such as a player
identifier when decisions must survive wrapper changes; decisions are compared
with `Object.equals`.

`PermissionResolver.subjectKey(Object subject)` returns the stable key used for
persistence. The default implementation persists `String`, `UUID`, and `Number`
subjects by their `toString()` form and returns `null` for every other subject;
`null` means "keep this decision in memory only". `MinecraftPermissionResolver`
overrides the method to read a game entity UUID and returns `player:<uuid>`.
Strings, UUIDs, and numbers keep their default form.

### Persistence

`PermissionStore` is the small persistence seam:

```java
public interface PermissionStore
{
    Map<String, Map<String, Boolean>> load();

    void save(Map<String, Map<String, Boolean>> decisions);
}
```

The outer map is keyed by `subjectKey` and the inner map by permission node
text. `PermissionService.install(PermissionResolver, PermissionStore)` installs
both and loads the current snapshot into a string-keyed persisted map. The
single-argument `install(PermissionResolver)` keeps any installed store and
persisted decisions. `PermissionService.hasStore()` reports whether a store is
installed, and `PermissionService.snapshot()` returns a deep, deterministically
ordered copy for tests and diagnostics.

`allow`, `deny`, `clear`, and `clearAll` mutate the object-keyed decision map
and the persisted string-keyed map, then call `PermissionStore.save` on a
best-effort basis. A store failure is logged at most once as
`[weaver] PERMISSION STORE SAVE FAILED reason=...` and never propagates to the
caller or to command execution. A load failure is logged once as
`[weaver] PERMISSION STORE LOAD FAILED reason=...` and treated as an empty
snapshot. `reset()` clears the persisted map, releases the store reference,
clears registrations and decisions, and restores the simple resolver; it does
not overwrite the backing file.

`JsonPermissionStore` in `core.command` implements the store with one UTF-8 JSON
file:

```json
{
  "schemaVersion": 1,
  "subjects": {
    "player:069a79f4-44e9-4726-a5be-fca90e38aaf5": {
      "examplemod:command/gate": true
    }
  }
}
```

Writes use a temporary file in the same directory that is flushed to stable
storage (`FileChannel.force`) before an atomic move when the file system
supports one; the parent directory is then forced on a best-effort basis
(ignored where unsupported, such as Windows). Subject and node keys are sorted,
so the file is deterministic. A missing file loads as empty with one
`[weaver] PERMISSION STORE EMPTY` line. Any other load failure (malformed JSON,
invalid UTF-8, an empty file, an I/O error) also loads as empty, but the
unreadable file is first moved aside to
`permissions.json.corrupt-<yyyyMMdd-HHmmss-SSS>.bak` (copied when it cannot be
moved) and reported as
`[weaver] PERMISSION STORE LOAD FAILED file=... backup=<path> reason=...`. Later
saves write a fresh file and never touch the backup; restore grants and denies
by fixing and renaming the backup while the server is stopped. Until then,
explicit denies from the unreadable file are not in effect. When the file can be
neither moved nor copied, the line reports `backup=none saves=blocked` and
every save is refused (and logged once as `PERMISSION STORE SAVE FAILED`)
so the original file is never overwritten. A save failure is thrown to the
service, which isolates it.

The loader-facing installer is:

```java
PermissionStoreSupport.install(dataDir.resolve("permissions.json"), log);
```

It installs `MinecraftPermissionResolver` and a `JsonPermissionStore` together,
is idempotent, and isolates every failure. `LoaderAgent` owns the actual wiring;
the exact one-line integration for the integrator is the call above, placed
after the `dataDir` option is resolved. The command backend checks
`PermissionService.hasStore()` during `install()` and keeps the configured
resolver instead of overwriting the pair.

### Thread safety and limitations

`PermissionService` uses concurrent maps and atomic reference counts. Reads do
not take a global lock. `install` swaps a volatile resolver and does not clear
registrations or decisions. `reset` is not atomic with respect to operations
that race with it.

Limitations:

- Weaver permissions are not a proxy or a vanilla permission provider. An
  explicit Weaver decision never modifies a game `PermissionSet`, and no
  decision is forwarded to a proxy, LuckPerms-style service, or vanilla
  permission check.
- Explicit allow/deny decisions persist only when a `PermissionStore` is
  installed. Without `PermissionStoreSupport.install` they are in-memory and
  are lost on process exit and on `PermissionService.reset()`.
- Decisions are keyed by the resolved subject identity/equality and, when
  persisted, by `PermissionResolver.subjectKey`. If two source wrappers resolve
  to different subjects and keys, a decision recorded through one does not apply
  to the other. If you change the resolver so subject identity or key changes,
  clear old decisions.
- The op scale is Weaver's 0-through-4 approximation of the 26.3 permission
  sets. It is not a general permission-node evaluator for arbitrary game
  permissions.
- Registration conflicts are rejected rather than merged. Two mods that want
  different policies for the same node must coordinate; reference counting only
  supports identical policy and op level.

## Registration and threading

Register command specs during `onInitialize` or `onRegister`. The backend
accepts registrations from any thread and is thread-safe, but the spec must
exist before the server builds its dispatcher to appear in that first tree.

`CommandRegistry.register(CommandSpec)`:

1. Requires a non-null spec.
2. Lazily resolves the command backend. If Brigadier is absent, it throws
   `CommandUnavailableException` without loading a core command class.
3. Rejects a duplicate root name or alias conflict with another active Weaver
   spec by throwing `CommandRegistrationException`.
4. Registers the permission nodes declared in the spec tree.
5. Creates a registration handle and marks the spec pending when a dispatcher
   is already known.

`CommandRegisterEvent` fires from the `Commands` constructor, on the thread
that constructs the dispatcher. That is often a worker thread, not the server
thread. The core backend applies specs during this event because the dispatcher
is still being constructed. A mod that listens to the event must do only tree
construction there: do not touch world, player, block-entity, or other live
server state from a `CommandRegisterEvent` listener.

Late registrations are queued. If `register` is called after the backend knows a
dispatcher, the spec is marked pending. `ServerTickEvent.Pre` flushes pending
specs on the server thread. A successful flush records the tree with the
`server-tick` source label; the initial `CommandRegisterEvent` pass records it
with the `server` source label. After a successful flush the backend also calls
`CommandBackend.resendTree(dispatcher, null, names)` so every online player
receives the updated tree through the game's `Commands#sendCommands` path. The
resend is best-effort: a missing packet type, a player without a connection, or
a send failure is logged once as `[weaver] COMMAND TREE RESEND FAILED` and never
breaks registration.

Conflict behavior differs by conflict type:

- A duplicate Weaver root or alias is rejected at `register` time with
  `CommandRegistrationException`.
- A root that already exists in the live Brigadier dispatcher is skipped by the
  registrar and logged as `[weaver] COMMAND CONFLICT name=<name>
  reason=existing`. The rest of the tree is not inserted under that root, and
  other specs still register.
- An alias that already exists in the live dispatcher is skipped and logged;
  the root itself can still register.

Tree-mapping failures (a bad custom argument type, a missing redirect target, a
redirect cycle, or a registrar failure) are caught per spec during apply,
logged as `COMMAND REGISTER FAILED name=<name> reason=...`, and isolated. The
`register` call may already have returned a handle; inspect the dispatcher or
the log when a command does not appear.

`CommandRegistration` exposes the registered spec, its active flag, and
unregistration:

```java
public interface CommandRegistration
{
    CommandSpec spec();

    boolean isActive();

    void unregister();
}
```

`unregister()` marks the registration inactive and prevents the spec from being
included in future dispatcher builds. When a dispatcher is already known, the
backend also rebuilds the Weaver portion of the live tree in place: it detaches
every Weaver-owned root node (roots and aliases) by identity from the dispatcher
root, re-registers the remaining active specs through the same registrar,
records a snapshot with the `rebuild` source label, and resends the tree to
online players. The removed command therefore stops parsing immediately.
Identity-based removal means a same-named vanilla or third-party node is never
detached. `isActive()` is `false` after `unregister()`, and the operation is
idempotent.

Rebuild failures are isolated. If the registrar cannot detach or re-register a
spec, the logical inactive state is kept and the first failure is logged as
`[weaver] COMMAND TREE REBUILD FAILED reason=...`. The backend does not replace
the game `Commands` object: Minecraft 26.3 stores its dispatcher in a private
final field, so the rebuild mutates the existing dispatcher instead. Vanilla
nodes are never removed.

### Late registration and resend

The complete sequence for a late registration is:

1. `register` accepts the spec, marks it pending, and returns a handle.
2. `ServerTickEvent.Pre` applies pending specs on the server thread.
3. The backend records a `server-tick` snapshot.
4. The backend calls `resendTree` and each online `ServerPlayer` receives a new
   `ClientboundCommandsPacket` built by the game from the updated dispatcher.

The default `CommandBackend.resendTree` method does nothing, so third-party
backends remain compatible. The core implementation captures the game
`Commands` holder from `CommandRegisterEvent` and prefers its
`sendCommands(ServerPlayer)` method. When the holder is unavailable it builds a
`ClientboundCommandsPacket` from the dispatcher root and the game's
`COMMAND_NODE_INSPECTOR`, then sends it through each player connection.

### Live removal

Calling `CommandRegistration.unregister()` while a dispatcher is active removes
the command from the live Brigadier tree. The command name may be registered
again afterwards; the new registration is pending until the next server tick.
Removal is best-effort and never throws into the caller. A failed rebuild leaves
the registration inactive, keeps the old live node if it could not be detached,
and logs once. The live rebuild mutates the dispatcher immediately on the
calling thread, so unregister from the server thread when a live dispatcher is
present; an unregister from another thread still marks the registration
inactive before the best-effort rebuild runs.

`CommandBackend.rebuildTree(Object dispatcher, List<Object> players)` is the
public backend entry point used by the core implementation. Mods do not call it
directly; they call `CommandRegistration.unregister()`.

`CommandRegistry` also exposes:

| Method | Result |
|---|---|
| `backend()` | The active backend, resolving it lazily; throws `CommandUnavailableException` when unavailable. |
| `isAvailable()` | Whether a backend is already installed or resolved and reports itself available. Never resolves and never throws. |
| `dispatcher()` | The raw game dispatcher, or `null` when the backend has not built one. |
| `commandNames()` | An immutable list of the backend's active spec names and aliases. This is not a complete list of vanilla Brigadier roots. |
| `treeSnapshot()` | The last `CommandTreeSnapshot`, or `null`. |
| `recordTreeSync(Object dispatcher, List<String> commandNames, String source)` | Stores a snapshot with a monotonically increasing sequence and posts `CommandTreeSyncEvent` on `EventBus.GAME`. |
| `resetTreeSnapshot()` | Clears the stored snapshot without rewinding the sequence. |
| `install(CommandBackend)` | Installs a custom backend explicitly. |
| `uninstall()` | Uninstalls the active backend and clears the snapshot. |
| `reset()` | Clears backend and Brigadier-presence caches for tests/embedding. |

`commandNames()` includes aliases as well as root names because the core
backend adds both to its active name list. It reports registered specs, not
whether a Brigadier conflict caused one to be skipped.

`CommandBackend` also declares two additive default methods used for live
updates: `resendTree(Object dispatcher, List<Object> players, List<String>
names)` and `rebuildTree(Object dispatcher, List<Object> players)`. Both default
to no-ops so existing backends keep linking.

## Client command tree sync

The server records a tree snapshot after it populates a dispatcher:

- after the initial `CommandRegisterEvent` pass, with source `"server"`;
- after a `ServerTickEvent.Pre` flush of late registrations, with source
  `"server-tick"`;
- after a live rebuild caused by `CommandRegistration.unregister()`, with source
  `"rebuild"`.

The client records a snapshot with source `"client"` through the built-in
`ClientCommandTreeMixin` in `api-game`:

```text
net.minecraft.client.multiplayer.ClientPacketListener#handleCommands(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V
```

The injection runs at `TAIL`, reads the live dispatcher from
`ClientPacketListener#getCommands()`, builds the root child names in dispatcher
order from `getCommands().getRoot().getChildren()`, and calls:

```java
CommandRegistry.recordTreeSync(dispatcher, names, "client");
```

The mixin is armed by the `client` array in `weaver.client.mixins.json` and is
installed by `LoaderAgent` only on a client. The dispatcher stays opaque in the
API; client UI code can cast it to
`CommandDispatcher<ClientSuggestionProvider>` in the shared class space. Every
failure is contained and reported once as
`[weaver] CLIENT COMMAND TREE FAILED reason=...`; nothing throws into the game.

`CommandRegistry.recordTreeSync` stores an immutable `CommandTreeSnapshot` and
posts `CommandTreeSyncEvent` on `EventBus.GAME`. The event is not cancelable and
exposes `dispatcher()`, `commandNames()`, and `source()`. Listener failures
cannot break the caller. `CommandRegistry.treeSnapshot()` returns the last
snapshot regardless of source, so an integrated-server process can observe
`server`, `server-tick`, `rebuild`, and `client` snapshots in one sequence; use
`source()` to tell them apart.

Threading for the client hook follows the game: `handleCommands` runs on the
client thread, the names list is a plain local snapshot, and
`recordTreeSync` serializes concurrent snapshot updates.

Client-only command tree merging is not supported. The client snapshot is an
observation of a server tree, not a mechanism for adding client-side commands
to the server's dispatcher.

## Limitations and non-goals

- No Brigadier compile dependency in `api` or core. Brigadier objects are
  reached through strings, reflection, and `Object` handles.
- No proxy permissions and no forwarding to vanilla permission checks.
  `PermissionService` is a Weaver-owned decision layer.
- Live removal rebuilds the Weaver portion of the existing dispatcher in place
  because the game `Commands` dispatcher field is final. Vanilla nodes are never
  removed, and a failed detach leaves the old node in place while the logical
  registration stays inactive.
- Resends use the game `Commands#sendCommands` path and the game packet
  constructor. A custom or test dispatcher without the game classes cannot
  receive a resend; that failure is logged once.
- No client-only command tree merging.
- Root suggestions may be ignored by Brigadier because the root is a literal.
- Custom argument types must be real Brigadier `ArgumentType` instances; the API
  cannot validate a game argument type at compile time.
- Explicit permission decisions are in-memory unless a `PermissionStore` is
  installed, and they are not a security boundary against trusted mods. Mods
  are trusted code in Weaver.
- A command spec with a permission node defaults to `DENY` with op level `0`.
  If you want an open command, omit the permission node rather than relying on
  a permissive policy.

## Related

- [Event bus](events.md)
- [Hook points](hooks.md)
- [Registry API](registry.md)
- [Client API](client.md)
- [Documentation index](README.md)
