package net.weaver.api.event.events.command;

import net.weaver.api.event.Event;

import java.util.List;

/**
 * Posted when a command tree has been synchronized into a dispatcher.
 *
 * <p>The server-side core posts this event after a dispatcher is populated. A
 * future client command-tree hook may post it after
 * {@code ClientPacketListener#handleCommands} so client user interface code can
 * observe the server tree. The dispatcher may be {@code null} when only the
 * command names are known.
 *
 * <p>The event is not cancelable and exposes no result. The command name list
 * is copied defensively and exposed as an unmodifiable list.
 */
public class CommandTreeSyncEvent extends Event
{
    private final Object dispatcher;
    private final List<String> commandNames;
    private final String source;

    /**
     * Creates a command tree synchronization event.
     *
     * @param dispatcher   the raw game dispatcher, possibly {@code null}
     * @param commandNames the command names, or {@code null} for none
     * @param source       the source label, for example {@code "server"}, or
     *                     {@code null}
     */
    public CommandTreeSyncEvent(Object dispatcher, List<String> commandNames, String source)
    {
        this.dispatcher = dispatcher;
        this.commandNames = commandNames == null ? List.of() : List.copyOf(commandNames);
        this.source = source;
    }

    /**
     * The raw game dispatcher.
     *
     * @return the dispatcher, or {@code null} when unavailable
     */
    public Object dispatcher()
    {
        return dispatcher;
    }

    /**
     * The command names present when the tree was synchronized.
     *
     * @return an unmodifiable command name list, never {@code null}
     */
    public List<String> commandNames()
    {
        return commandNames;
    }

    /**
     * The source label.
     *
     * @return the source label, possibly {@code null}
     */
    public String source()
    {
        return source;
    }
}
