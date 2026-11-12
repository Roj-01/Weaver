package net.weaver.api.event.events;

/**
 * Game-free hook event factory used when the typed {@code api-game} artifact
 * is not available.
 *
 * <p>Every method returns the plain event type declared in this package.
 * {@link #entityHurt(Object, Object, Object)}, {@link #entityAttack(Object,
 * Object)} and {@link #blockBreak(Object, Object)} create the cancelable forms
 * so cancellation still works in fallback mode;
 * {@link #blockPlace(Object)} creates the result-based form. Every other hook
 * event is observation-only. The factory is stateless and thread-safe.
 */
public final class DefaultHookEventFactory implements HookEventFactory {

    /** Creates the shared fallback factory. */
    public DefaultHookEventFactory() {
    }

    @Override
    public BlockInteractEvent blockInteract(Object player, Object level, Object stack, Object hand,
                                            Object hitResult) {
        return new BlockInteractEvent(player, level, stack, hand, hitResult);
    }

    @Override
    public ItemUseEvent itemUse(Object player, Object level, Object stack, Object hand) {
        return new ItemUseEvent(player, level, stack, hand);
    }

    @Override
    public LivingEntityDeathEvent livingEntityDeath(Object entity, Object source) {
        return new LivingEntityDeathEvent(entity, source);
    }

    @Override
    public ContainerCloseEvent containerClose(Object player, Object menu) {
        return new ContainerCloseEvent(player, menu);
    }

    @Override
    public AnvilUpdateEvent anvilUpdate(Object menu) {
        return new AnvilUpdateEvent(menu);
    }

    @Override
    public EnchantEvent enchant(Object menu, Object player) {
        return new EnchantEvent(menu, player);
    }

    @Override
    public PlayerTeleportEvent playerTeleport(Object player, Object transition) {
        return new PlayerTeleportEvent(player, transition);
    }

    @Override
    public PlayerRespawnTargetEvent playerRespawnTarget(Object player, Object postTeleport) {
        return new PlayerRespawnTargetEvent(player, postTeleport);
    }

    @Override
    public EntityHurtEvent entityHurt(Object level, Object entity, Object source) {
        return new EntityHurtEvent(level, entity, source, true);
    }

    @Override
    public EntityInteractEvent entityInteract(Object player, Object entity, Object hand,
                                              Object hitVec) {
        return new EntityInteractEvent(player, entity, hand, hitVec);
    }

    @Override
    public EntityAttackEvent entityAttack(Object player, Object target) {
        return new EntityAttackEvent(player, target, true);
    }

    @Override
    public BlockBreakEvent blockBreak(Object player, Object pos) {
        return new BlockBreakEvent(player, pos, true);
    }

    @Override
    public BlockPlaceEvent blockPlace(Object placeContext) {
        return new BlockPlaceEvent(placeContext);
    }

    @Override
    public ItemUseFinishEvent itemUseFinish(Object livingEntity) {
        return new ItemUseFinishEvent(livingEntity);
    }

    @Override
    public ItemReleaseEvent itemRelease(Object livingEntity) {
        return new ItemReleaseEvent(livingEntity);
    }

    @Override
    public ProjectileLaunchEvent projectileLaunch(Object projectile) {
        return new ProjectileLaunchEvent(projectile);
    }

    @Override
    public ClientPlayerTickEvent clientPlayerTickPre(Object player) {
        return new ClientPlayerTickEvent.Pre(player);
    }

    @Override
    public ClientPlayerTickEvent clientPlayerTickPost(Object player) {
        return new ClientPlayerTickEvent.Post(player);
    }

    @Override
    public EntityTickEvent entityTick(Object level, Object entity, net.weaver.api.event.Side side) {
        return new EntityTickEvent(level, entity, side);
    }

    @Override
    public ChunkLoadEvent chunkLoad(Object level, Object chunk) {
        return new ChunkLoadEvent(level, chunk);
    }

    @Override
    public ChunkUnloadEvent chunkUnload(Object level, Object chunk) {
        return new ChunkUnloadEvent(level, chunk);
    }

    @Override
    public EntityRemovalEvent entityRemoval(Object entity, Object reason) {
        return new EntityRemovalEvent(entity, reason);
    }

    @Override
    public ContainerClosedEvent containerClosed(Object player, Object containerMenu) {
        return new ContainerClosedEvent(player, containerMenu);
    }

    @Override
    public BlockEntityLoadEvent blockEntityLoad(Object blockEntity) {
        return new BlockEntityLoadEvent(blockEntity);
    }

    @Override
    public BlockEntitySaveEvent blockEntitySave(Object blockEntity) {
        return new BlockEntitySaveEvent(blockEntity);
    }

    @Override
    public EntitySaveEvent entitySave(Object entity) {
        return new EntitySaveEvent(entity);
    }

    @Override
    public EntityLoadEvent entityLoad(Object entity) {
        return new EntityLoadEvent(entity);
    }

    @Override
    public PlayerRespawnActionEvent playerRespawnAction(Object playerList, Object oldPlayer,
                                                        Object reason) {
        return new PlayerRespawnActionEvent(playerList, oldPlayer, reason);
    }
}
