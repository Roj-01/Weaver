package net.weaver.api.event.events;

/**
 * Creates the event objects posted by the built-in hook sites.
 *
 * <p>The API module cannot reference game classes, so every factory method
 * takes and returns game payloads as {@link Object}; the entity-tick method
 * additionally takes the side as a {@link net.weaver.api.event.Side} enum.
 * The base implementation,
 * {@link DefaultHookEventFactory}, creates the plain event types declared in
 * this package. The optional typed implementation lives in the
 * {@code api-game} artifact and creates the typed subclasses from
 * {@code net.weaver.api.game.event}, which expose covariant game-typed
 * accessors.
 *
 * <p>Implementations must be stateless and thread-safe: the same factory is
 * called from client, server, and integrated-server threads.
 *
 * @see DefaultHookEventFactory
 */
public interface HookEventFactory {

    /** Creates a server-side, observation-only block-interaction event. */
    BlockInteractEvent blockInteract(Object player, Object level, Object stack, Object hand,
                                     Object hitResult);

    /** Creates a server-side, observation-only item-use event. */
    ItemUseEvent itemUse(Object player, Object level, Object stack, Object hand);

    /** Creates a server-side, observation-only living-entity-death event. */
    LivingEntityDeathEvent livingEntityDeath(Object entity, Object source);

    /** Creates a server-side, observation-only container-close event. */
    ContainerCloseEvent containerClose(Object player, Object menu);

    /** Creates a server-side, observation-only anvil-update event. */
    AnvilUpdateEvent anvilUpdate(Object menu);

    /** Creates a server-side, observation-only enchant event. */
    EnchantEvent enchant(Object menu, Object player);

    /** Creates a server-side, observation-only player-teleport event. */
    PlayerTeleportEvent playerTeleport(Object player, Object transition);

    /**
     * Creates a server-side respawn-target event.
     *
     * <p>The event is result-based and not cancelable; a listener replaces the
     * vanilla lookup result by setting a non-null respawn target.
     */
    PlayerRespawnTargetEvent playerRespawnTarget(Object player, Object postTeleport);

    /**
     * Creates a cancelable entity-hurt event. A listener may cancel it to make
     * {@code LivingEntity#hurtServer} return {@code false} and skip damage for
     * that call.
     */
    EntityHurtEvent entityHurt(Object level, Object entity, Object source);

    /** Creates a server-side, observation-only entity-interaction event. */
    EntityInteractEvent entityInteract(Object player, Object entity, Object hand, Object hitVec);

    /**
     * Creates a cancelable entity-attack event. A listener may cancel it to
     * make {@code Player#attack} return immediately and skip the attack body
     * for that call.
     */
    EntityAttackEvent entityAttack(Object player, Object target);

    /**
     * Creates a cancelable block-break event. A listener may cancel it to make
     * {@code ServerPlayerGameMode#destroyBlock} return {@code false} and skip
     * the block break for that call.
     */
    BlockBreakEvent blockBreak(Object player, Object pos);

    /**
     * Creates a result-based block-place event. A listener may set a non-null
     * result override to replace the {@code BlockItem#place} result and skip
     * the vanilla body; {@code null} means no override.
     */
    BlockPlaceEvent blockPlace(Object placeContext);

    /** Creates a server-side, observation-only item-use-finish event. */
    ItemUseFinishEvent itemUseFinish(Object livingEntity);

    /** Creates a server-side, observation-only item-release event. */
    ItemReleaseEvent itemRelease(Object livingEntity);

    /** Creates a server-side, observation-only projectile-launch event. */
    ProjectileLaunchEvent projectileLaunch(Object projectile);

    /** Creates a client-side, observation-only pre local-player-tick event. */
    ClientPlayerTickEvent clientPlayerTickPre(Object player);

    /** Creates a client-side, observation-only post local-player-tick event. */
    ClientPlayerTickEvent clientPlayerTickPost(Object player);

    /**
     * Creates an observation-only entity-tick event for the supplied level side.
     *
     * @param level  the level ticking the entity
     * @param entity the ticking entity
     * @param side   the level side, {@link net.weaver.api.event.Side#SERVER} or
     *               {@link net.weaver.api.event.Side#CLIENT}
     */
    EntityTickEvent entityTick(Object level, Object entity, net.weaver.api.event.Side side);

    /** Creates a server-side, observation-only chunk-load event. */
    ChunkLoadEvent chunkLoad(Object level, Object chunk);

    /** Creates a server-side, observation-only chunk-unload event. */
    ChunkUnloadEvent chunkUnload(Object level, Object chunk);

    /** Creates a server-side, observation-only entity-removal event. */
    EntityRemovalEvent entityRemoval(Object entity, Object reason);

    /** Creates a server-side, observation-only container-closed event. */
    ContainerClosedEvent containerClosed(Object player, Object containerMenu);

    /** Creates a server-side, observation-only block-entity-load event. */
    BlockEntityLoadEvent blockEntityLoad(Object blockEntity);

    /** Creates a server-side, observation-only block-entity-save event. */
    BlockEntitySaveEvent blockEntitySave(Object blockEntity);

    /**
     * Creates a server-side, observation-only entity-save event.
     *
     * <p>The default implementation creates the base event so existing
     * implementations of this interface keep linking and compiling when the
     * entity events are not supported.</p>
     *
     * @param entity the entity saving its additional data
     * @return the entity-save event, never {@code null}
     */
    default EntitySaveEvent entitySave(Object entity) {
        return new EntitySaveEvent(entity);
    }

    /**
     * Creates a server-side, observation-only entity-load event.
     *
     * <p>The default implementation creates the base event so existing
     * implementations of this interface keep linking and compiling when the
     * entity events are not supported.</p>
     *
     * @param entity the entity loading its additional data
     * @return the entity-load event, never {@code null}
     */
    default EntityLoadEvent entityLoad(Object entity) {
        return new EntityLoadEvent(entity);
    }

    /** Creates a server-side, observation-only player-respawn-action event. */
    PlayerRespawnActionEvent playerRespawnAction(Object playerList, Object oldPlayer, Object reason);
}
