/**
 * Events for command tree synchronization.
 *
 * <p>{@link net.weaver.api.event.events.command.CommandTreeSyncEvent} is posted
 * on {@link net.weaver.api.event.EventBus#GAME} by the core command backend
 * after a dispatcher is populated, and may be posted by a future client
 * command-tree hook after the game applies a server command packet. All events
 * in this package are observation-only and are not cancelable.
 */
package net.weaver.api.event.events.command;
