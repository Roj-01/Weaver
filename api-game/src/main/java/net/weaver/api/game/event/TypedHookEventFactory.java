package net.weaver.api.game.event;

import net.weaver.api.event.events.HookEventFactory;

/**
 * Game-typed implementation of {@link HookEventFactory}.
 *
 * <p>Every method creates the typed subclass from this package, which narrows
 * the accessors to game types. The factory is stateless and can be shared by
 * every thread that posts hook events. This class references game classes and
 * is therefore only loadable when the game classpath is present.
 */
public final class TypedHookEventFactory implements HookEventFactory {

    /** Creates the typed factory. */
    public TypedHookEventFactory() {
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.BlockInteractEvent blockInteract(Object player, Object level,
                                                                        Object stack, Object hand,
                                                                        Object hitResult) {
        return new BlockInteractEvent(player, level, stack, hand, hitResult);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ItemUseEvent itemUse(Object player, Object level,
                                                            Object stack, Object hand) {
        return new ItemUseEvent(player, level, stack, hand);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.LivingEntityDeathEvent livingEntityDeath(Object entity,
                                                                                Object source) {
        return new LivingEntityDeathEvent(entity, source);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ContainerCloseEvent containerClose(Object player,
                                                                          Object menu) {
        return new ContainerCloseEvent(player, menu);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.AnvilUpdateEvent anvilUpdate(Object menu) {
        return new AnvilUpdateEvent(menu);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.EnchantEvent enchant(Object menu, Object player) {
        return new EnchantEvent(menu, player);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.PlayerTeleportEvent playerTeleport(Object player,
                                                                          Object transition) {
        return new PlayerTeleportEvent(player, transition);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.PlayerRespawnTargetEvent playerRespawnTarget(
            Object player, Object postTeleport) {
        return new PlayerRespawnTargetEvent(player, postTeleport);
    }

    /**
     * Creates the typed cancelable entity-hurt event.
     *
     * <p>Canceling makes {@code LivingEntity#hurtServer} return {@code false}
     * and skips damage for that call.
     */
    @Override
    public net.weaver.api.event.events.EntityHurtEvent entityHurt(Object level, Object entity,
                                                                  Object source) {
        return new EntityHurtEvent(level, entity, source, true);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.EntityInteractEvent entityInteract(Object player,
                                                                          Object entity,
                                                                          Object hand,
                                                                          Object hitVec) {
        return new EntityInteractEvent(player, entity, hand, hitVec);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.EntityAttackEvent entityAttack(Object player,
                                                                      Object target) {
        return new EntityAttackEvent(player, target, true);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.BlockBreakEvent blockBreak(Object player, Object pos) {
        return new BlockBreakEvent(player, pos, true);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.BlockPlaceEvent blockPlace(Object placeContext) {
        return new BlockPlaceEvent(placeContext);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ItemUseFinishEvent itemUseFinish(Object livingEntity) {
        return new ItemUseFinishEvent(livingEntity);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ItemReleaseEvent itemRelease(Object livingEntity) {
        return new ItemReleaseEvent(livingEntity);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ProjectileLaunchEvent projectileLaunch(Object projectile) {
        return new ProjectileLaunchEvent(projectile);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ClientPlayerTickEvent clientPlayerTickPre(Object player) {
        return new ClientPlayerTickEvent(player,
                net.weaver.api.event.events.ClientPlayerTickEvent.Phase.PRE);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ClientPlayerTickEvent clientPlayerTickPost(Object player) {
        return new ClientPlayerTickEvent(player,
                net.weaver.api.event.events.ClientPlayerTickEvent.Phase.POST);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.EntityTickEvent entityTick(
            Object level, Object entity, net.weaver.api.event.Side side) {
        return new EntityTickEvent(level, entity, side);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ChunkLoadEvent chunkLoad(Object level, Object chunk) {
        return new ChunkLoadEvent(level, chunk);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ChunkUnloadEvent chunkUnload(Object level, Object chunk) {
        return new ChunkUnloadEvent(level, chunk);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.EntityRemovalEvent entityRemoval(Object entity,
                                                                        Object reason) {
        return new EntityRemovalEvent(entity, reason);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.ContainerClosedEvent containerClosed(
            Object player, Object containerMenu) {
        return new ContainerClosedEvent(player, containerMenu);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.BlockEntityLoadEvent blockEntityLoad(Object blockEntity) {
        return new BlockEntityLoadEvent(blockEntity);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.BlockEntitySaveEvent blockEntitySave(Object blockEntity) {
        return new BlockEntitySaveEvent(blockEntity);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.EntitySaveEvent entitySave(Object entity) {
        return new EntitySaveEvent(entity);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.EntityLoadEvent entityLoad(Object entity) {
        return new EntityLoadEvent(entity);
    }

    /** {@inheritDoc} */
    @Override
    public net.weaver.api.event.events.PlayerRespawnActionEvent playerRespawnAction(
            Object playerList, Object oldPlayer, Object reason) {
        return new PlayerRespawnActionEvent(playerList, oldPlayer, reason);
    }
}
