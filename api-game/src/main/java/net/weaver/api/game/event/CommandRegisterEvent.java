package net.weaver.api.game.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Typed variant of {@link net.weaver.api.event.events.CommandRegisterEvent}.
 *
 * <p>The command holder and its dispatcher are narrowed to the game types. The
 * inherited accessors stay available with their {@link Object} descriptors for
 * binary compatibility.
 */
public final class CommandRegisterEvent extends net.weaver.api.event.events.CommandRegisterEvent {

    /**
     * Creates a typed command-registration event.
     *
     * @param commands the command holder whose dispatcher was rebuilt
     */
    public CommandRegisterEvent(Object commands) {
        super(commands);
    }

    @Override
    public Commands commands() {
        return (Commands) super.commands();
    }

    @Override
    @SuppressWarnings("unchecked")
    public CommandDispatcher<CommandSourceStack> dispatcher() {
        return (CommandDispatcher<CommandSourceStack>) super.dispatcher();
    }
}
