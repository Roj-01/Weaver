/**
 * Game-free command and permission API.
 *
 * <p>Mods build immutable {@link net.weaver.api.command.CommandSpec} and
 * {@link net.weaver.api.command.CommandNodeSpec} trees and register them with
 * {@link net.weaver.api.command.CommandRegistry}. The core command backend
 * maps the specs onto a live Brigadier dispatcher; the API never references
 * game or Brigadier classes at compile time and carries raw game objects as
 * {@link java.lang.Object}.
 *
 * <p>{@link net.weaver.api.command.PermissionService} evaluates namespaced
 * {@link net.weaver.api.command.PermissionNode} values with a configurable,
 * thread-safe {@link net.weaver.api.command.PermissionResolver}. Explicit
 * per-subject decisions win over registered policies, which win over op-level
 * fallback. An unregistered node is denied. When a
 * {@link net.weaver.api.command.PermissionStore} is installed, explicit
 * decisions persist through the resolver's
 * {@link net.weaver.api.command.PermissionResolver#subjectKey(Object)}.
 *
 * <p>Types in this package are immutable or stateless facades and are safe for
 * concurrent use unless a type documents otherwise. Raw game objects and
 * executed command callbacks remain subject to the game's server-thread rules.
 */
package net.weaver.api.command;
