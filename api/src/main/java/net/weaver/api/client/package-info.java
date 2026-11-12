/**
 * Game-free client extension API.
 *
 * <p>The package exposes key mappings, screen factories, HUD layers, tooltip
 * providers, client chunk listeners, client tick listeners, and resource reload
 * listeners without a compile-time reference to Minecraft or Mojang classes.
 * Game objects cross the boundary as {@link java.lang.Object}, and the core
 * client backend bridges them to real game types at runtime. Nothing here
 * creates server-side state; operations that need a backend fail with
 * {@link net.weaver.api.client.ClientUnavailableException} when no usable
 * client is present.</p>
 *
 * <p>{@link net.weaver.api.client.ClientRegistry} is the only entry point. It
 * is thread-safe, serializes installation and registration, and documents the
 * client/render-thread requirements that the backend enforces with
 * {@link net.weaver.api.client.ClientThreadException}.</p>
 */
package net.weaver.api.client;
