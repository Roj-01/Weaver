package net.weaver.api.game.network.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the common server listener state to mixins that target its
 * subclasses.
 *
 * <p>{@code ServerGamePacketListenerImpl} overrides
 * {@code handleCustomPayload} and therefore needs its own injection, but its
 * {@code server} and {@code connection} fields are declared on the common
 * superclass. A mixin targeting the subclass cannot shadow inherited fields,
 * so this accessor mixin is applied to the common listener instead and the
 * subclass injection casts {@code this} to this interface.</p>
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerAccessor
{
    /**
     * The server that owns the listener.
     *
     * @return the server, never {@code null}
     */
    @Accessor("server")
    MinecraftServer weaver$server();

    /**
     * The connection the listener is bound to.
     *
     * @return the connection, never {@code null}
     */
    @Accessor("connection")
    Connection weaver$connection();
}
