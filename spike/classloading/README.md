# Classloading spike

This spike decides the class-space design before the loader core is written.

It answers:

1. Is the system classloader the game classloader?
2. Does `Instrumentation.appendToSystemClassLoaderSearch` make a mod visible to
   the game classloader?
3. Can a mod class extend a game class with `==` identity?
4. Can Mixin inject into a game class and call mod-owned code directly?
5. Does the same work on Java 26 with Mixin 0.8.7 and MixinExtras?
6. If the system loader is not the game loader, does a shared `URLClassLoader`
   work as a fallback?

## Run

```powershell
.\build.ps1
```

This is a historical spike, not part of the current proof. It needs an external
Mixin agent from an older tree; set `WEAVER_MIXIN_AGENT` and `WEAVER_MIXIN_LIBS`
to point at that build.

Expected last line:

```
CLASSLOADING SPIKE RESULT system=ok shared=ok
```
