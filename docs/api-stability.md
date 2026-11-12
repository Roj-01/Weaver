# API stability

From **2.3.2** the Weaver API is frozen. A mod built against it keeps
linking and working on every later 2.x release, without a recompile.

## What the API is

Every public and protected type, method, constructor, and field in the
`net.weaver.api` packages of the `api` and `api-game` artifacts, except:

- packages with `mixin` in their name (Weaver's own mixins);
- `net.weaver.api.game.event.support` (helpers Weaver's mixins call);
- types and members marked `@net.weaver.api.annotation.Internal`: entry points
  the loader and the mixins call, such as the `dispatch` and `hook` methods of
  the client registries and `EnumExtensions.install`.

Those may change in any release. Everything else is the API.

## What a 2.x release may do

| Change | Allowed in 2.x |
|---|---|
| Add a type, method, constructor, field, or event | yes |
| Add a default method to an interface | yes |
| Add a constant to a Weaver enum | yes (switches over it need a `default`) |
| Remove or rename a public type or member | no |
| Change a parameter, return type, or field type | no |
| Make a non-final class or method final, or a concrete method abstract | no |
| Change the value of a public constant | no |
| Deprecate a member | yes; it stays until 3.0 |

Behavior is part of the contract too: an event keeps firing at the same point,
with the same meaning of cancel and of its results. Fixes that make behavior
match the documentation are allowed.

## How it is enforced

`api-baseline/weaver-api.txt` records the frozen surface, one line per type and
member (4807 lines at 2.3.2). The unit test `ApiSurfaceTest`, part of
every gate run, compares the built API with it:

- a recorded line that is gone or changed fails the gate as a **breaking
  change**;
- a new line fails the gate until the addition is recorded:

```powershell
.\gradlew.bat :core:test --tests "*ApiSurfaceTest" -PrecordApi
```

Recording an addition is a deliberate step, reviewed with the change. A removed
line is never recorded inside 2.x.

## Versions

- `WeaverApi.VERSION` (`2.1.0`) is the binary contract version mods and the
  network handshake check. It stays `2.1.0` across additive releases, as the
  `WeaverApi` documentation explains; `WeaverApi.FEATURE_LEVEL` (`2.2`) names the
  feature level.
- The release version (`2.3.2`) names the build: the artifacts, the
  loader's mod list, and the package.
- `WeaverApi.GAME_VERSION` is the Minecraft version (`26.3`). A new Minecraft
  version is a new Weaver release; the API above stays the same where the game
  allows it.

## Related

- [Release notes](release-notes.md)
- [Typed game API](api-game.md)
