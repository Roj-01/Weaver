# Spikes

Small, runnable proofs used before changing the loader core.

The first spike is `classloading/`:

- verify whether the system classloader is the game classloader;
- verify `Instrumentation.appendToSystemClassLoaderSearch` with a mod jar;
- verify a mod class can extend a game class with `==` identity;
- verify Mixin can inject into a game class and call mod code;
- verify the same on Java 26 with Mixin 0.8.7 and MixinExtras;
- verify the shared URLClassLoader fallback when the system loader is not the
  game loader.
