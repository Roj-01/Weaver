package net.weaver.api.command;

/**
 * Executes one command invocation.
 *
 * <p>The executor runs on the server thread after the command backend has
 * parsed the arguments and enforced the command's permission. It may throw a
 * {@link CommandException} to report a controlled failure; other exceptions are
 * reported by the backend as command failures.
 */
@FunctionalInterface
public interface CommandExecutor
{
    /**
     * Executes the command.
     *
     * @param context the parsed invocation context, not {@code null}
     * @return the Brigadier result value; zero conventionally means failure
     */
    int execute(CommandContext context);
}
