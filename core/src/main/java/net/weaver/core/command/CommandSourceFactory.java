package net.weaver.core.command;

import net.weaver.api.command.CommandSource;

/**
 * Builds an API {@link CommandSource} from a raw game command source.
 *
 * <p>Naming and feedback are resolved reflectively through {@link GameReflection}
 * so the core module keeps no compile-time game dependency. Feedback delivery
 * prefers {@code sendSuccess(Supplier, boolean)} for success and
 * {@code sendFailure(Component)} for failure, falls back to
 * {@code sendSystemMessage(Component)}, and finally becomes a no-op. Nothing in
 * this factory throws for a missing method or a failing feedback call.
 */
final class CommandSourceFactory
{
    private CommandSourceFactory()
    {
    }

    /**
     * Creates a command source wrapper.
     *
     * @param rawSource the raw game source, not {@code null}
     * @return the wrapped source, never {@code null}
     */
    static CommandSource create(Object rawSource)
    {
        GameReflection.Access access = GameReflection.forLoader(
                rawSource.getClass().getClassLoader());
        String name = access.sourceName(rawSource);
        return new CommandSource(rawSource, name, name, access.successConsumer(rawSource),
                access.failureConsumer(rawSource));
    }
}
