package net.weaver.api.event;

/**
 * Implemented by events that belong to exactly one mod.
 *
 * <p>{@link EventFilter#modId(String)} accepts an event that implements this
 * interface when {@link #modId()} matches the requested id. Mod-defined
 * events may implement it to make per-mod filtering explicit; events that do
 * not implement it can still match a mod filter when they are posted on the
 * matching {@link EventBus#local(String) local bus}.
 */
public interface ModScopedEvent
{
    /**
     * The stable id of the mod that owns this event.
     *
     * @return the owning mod id
     */
    String modId();
}
