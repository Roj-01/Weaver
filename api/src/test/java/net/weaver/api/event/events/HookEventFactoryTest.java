package net.weaver.api.event.events;

import net.weaver.api.event.Event;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.Side;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookEventFactoryTest
{
    private final HookEventFactory factory = new DefaultHookEventFactory();

    @Test
    void createsBaseEventTypesForEveryHook()
    {
        assertEquals(BlockInteractEvent.class,
                factory.blockInteract(new Object(), new Object(), new Object(), new Object(),
                        new Object()).getClass());
        assertEquals(ItemUseEvent.class,
                factory.itemUse(new Object(), new Object(), new Object(), new Object()).getClass());
        assertEquals(LivingEntityDeathEvent.class,
                factory.livingEntityDeath(new Object(), new Object()).getClass());
        assertEquals(ContainerCloseEvent.class,
                factory.containerClose(new Object(), new Object()).getClass());
        assertEquals(AnvilUpdateEvent.class, factory.anvilUpdate(new Object()).getClass());
        assertEquals(EnchantEvent.class,
                factory.enchant(new Object(), new Object()).getClass());
        assertEquals(PlayerTeleportEvent.class,
                factory.playerTeleport(new Object(), new Object()).getClass());
        assertEquals(PlayerRespawnTargetEvent.class,
                factory.playerRespawnTarget(new Object(), new Object()).getClass());
        assertEquals(EntityHurtEvent.class,
                factory.entityHurt(new Object(), new Object(), new Object()).getClass());
        assertEquals(EntityInteractEvent.class,
                factory.entityInteract(new Object(), new Object(), new Object(),
                        new Object()).getClass());
        assertEquals(EntityAttackEvent.class,
                factory.entityAttack(new Object(), new Object()).getClass());
        assertEquals(BlockBreakEvent.class,
                factory.blockBreak(new Object(), new Object()).getClass());
        assertEquals(BlockPlaceEvent.class, factory.blockPlace(new Object()).getClass());
        assertEquals(ItemUseFinishEvent.class, factory.itemUseFinish(new Object()).getClass());
        assertEquals(ItemReleaseEvent.class, factory.itemRelease(new Object()).getClass());
        assertEquals(ProjectileLaunchEvent.class,
                factory.projectileLaunch(new Object()).getClass());
        assertEquals(ClientPlayerTickEvent.Pre.class,
                factory.clientPlayerTickPre(new Object()).getClass());
        assertEquals(ClientPlayerTickEvent.Post.class,
                factory.clientPlayerTickPost(new Object()).getClass());
        assertEquals(EntityTickEvent.class,
                factory.entityTick(new Object(), new Object(), Side.SERVER).getClass());
        assertEquals(ChunkLoadEvent.class,
                factory.chunkLoad(new Object(), new Object()).getClass());
        assertEquals(ChunkUnloadEvent.class,
                factory.chunkUnload(new Object(), new Object()).getClass());
        assertEquals(EntityRemovalEvent.class,
                factory.entityRemoval(new Object(), new Object()).getClass());
        assertEquals(ContainerClosedEvent.class,
                factory.containerClosed(new Object(), new Object()).getClass());
        assertEquals(BlockEntityLoadEvent.class,
                factory.blockEntityLoad(new Object()).getClass());
        assertEquals(BlockEntitySaveEvent.class,
                factory.blockEntitySave(new Object()).getClass());
        assertEquals(EntitySaveEvent.class, factory.entitySave(new Object()).getClass());
        assertEquals(EntityLoadEvent.class, factory.entityLoad(new Object()).getClass());
        assertEquals(PlayerRespawnActionEvent.class,
                factory.playerRespawnAction(new Object(), new Object(), new Object()).getClass());
    }

    @Test
    void entityHurtIsCancelable()
    {
        Object level = new Object();
        Object entity = new Object();
        Object source = new Object();

        EntityHurtEvent event = factory.entityHurt(level, entity, source);

        assertTrue(event.isCancelable());
        assertFalse(event.isCanceled());
        event.setCanceled(true);
        assertTrue(event.isCanceled());
        assertSame(level, event.level());
        assertSame(entity, event.entity());
        assertSame(source, event.source());
    }

    @Test
    void newEventsExposeTheirPayloads()
    {
        Object player = new Object();
        Object level = new Object();
        Object stack = new Object();
        Object hand = new Object();
        Object hitResult = new Object();
        Object entity = new Object();
        Object source = new Object();
        Object menu = new Object();
        Object transition = new Object();

        BlockInteractEvent block = factory.blockInteract(player, level, stack, hand, hitResult);
        assertSame(player, block.player());
        assertSame(level, block.level());
        assertSame(stack, block.stack());
        assertSame(hand, block.hand());
        assertSame(hitResult, block.hitResult());

        ItemUseEvent item = factory.itemUse(player, level, stack, hand);
        assertSame(player, item.player());
        assertSame(level, item.level());
        assertSame(stack, item.stack());
        assertSame(hand, item.hand());

        LivingEntityDeathEvent death = factory.livingEntityDeath(entity, source);
        assertSame(entity, death.entity());
        assertSame(source, death.source());

        ContainerCloseEvent close = factory.containerClose(player, menu);
        assertSame(player, close.player());
        assertSame(menu, close.menu());

        AnvilUpdateEvent anvil = factory.anvilUpdate(menu);
        assertSame(menu, anvil.menu());

        EnchantEvent enchant = factory.enchant(menu, player);
        assertSame(menu, enchant.menu());
        assertSame(player, enchant.player());

        PlayerTeleportEvent teleport = factory.playerTeleport(player, transition);
        assertSame(player, teleport.player());
        assertSame(transition, teleport.transition());
    }

    @Test
    void expansionEventsExposeTheirPayloads()
    {
        Object player = new Object();
        Object entity = new Object();
        Object hand = new Object();
        Object hitVec = new Object();
        Object pos = new Object();
        Object context = new Object();
        Object reason = new Object();

        EntityInteractEvent interact = factory.entityInteract(player, entity, hand, hitVec);
        assertSame(player, interact.player());
        assertSame(entity, interact.entity());
        assertSame(hand, interact.hand());
        assertSame(hitVec, interact.hitVec());

        EntityAttackEvent attack = factory.entityAttack(player, entity);
        assertSame(player, attack.player());
        assertSame(entity, attack.target());
        assertTrue(attack.isCancelable());
        assertFalse(attack.isCanceled());

        BlockBreakEvent block = factory.blockBreak(player, pos);
        assertSame(player, block.player());
        assertSame(pos, block.pos());
        assertTrue(block.isCancelable());
        assertFalse(block.isCanceled());

        BlockPlaceEvent place = factory.blockPlace(context);
        assertSame(context, place.placeContext());
        assertNull(place.resultOverride());
        assertTrue(place.hasResult());
        assertFalse(place.isCancelable());

        ItemUseFinishEvent finish = factory.itemUseFinish(entity);
        assertSame(entity, finish.livingEntity());

        ItemReleaseEvent release = factory.itemRelease(entity);
        assertSame(entity, release.livingEntity());

        ProjectileLaunchEvent launch = factory.projectileLaunch(entity);
        assertSame(entity, launch.projectile());

        ClientPlayerTickEvent pre = factory.clientPlayerTickPre(player);
        assertSame(player, pre.player());
        assertEquals(ClientPlayerTickEvent.Phase.PRE, pre.phase());
        ClientPlayerTickEvent post = factory.clientPlayerTickPost(player);
        assertSame(player, post.player());
        assertEquals(ClientPlayerTickEvent.Phase.POST, post.phase());

        EntityTickEvent tick = factory.entityTick(player, entity, Side.SERVER);
        assertSame(player, tick.level());
        assertSame(entity, tick.entity());
        assertEquals(Side.SERVER, tick.side());

        ChunkLoadEvent load = factory.chunkLoad(player, entity);
        assertSame(player, load.level());
        assertSame(entity, load.chunk());

        ChunkUnloadEvent unload = factory.chunkUnload(player, entity);
        assertSame(player, unload.level());
        assertSame(entity, unload.chunk());

        EntityRemovalEvent removal = factory.entityRemoval(entity, reason);
        assertSame(entity, removal.entity());
        assertSame(reason, removal.reason());

        ContainerClosedEvent closed = factory.containerClosed(player, entity);
        assertSame(player, closed.player());
        assertSame(entity, closed.containerMenu());

        BlockEntityLoadEvent entityLoad = factory.blockEntityLoad(entity);
        assertSame(entity, entityLoad.blockEntity());

        BlockEntitySaveEvent entitySave = factory.blockEntitySave(entity);
        assertSame(entity, entitySave.blockEntity());

        EntitySaveEvent save = factory.entitySave(entity);
        assertSame(entity, save.entity());

        EntityLoadEvent entityLoadEvent = factory.entityLoad(entity);
        assertSame(entity, entityLoadEvent.entity());

        PlayerRespawnActionEvent respawn = factory.playerRespawnAction(player, entity, reason);
        assertSame(player, respawn.playerList());
        assertSame(entity, respawn.oldPlayer());
        assertSame(reason, respawn.reason());
    }

    @Test
    void blockPlaceExposesAResultOverride()
    {
        BlockPlaceEvent event = factory.blockPlace(new Object());
        Object override = new Object();

        event.setResultOverride(override);

        assertSame(override, event.resultOverride());
        event.setResultOverride(null);
        assertNull(event.resultOverride());
    }

    @Test
    void playerRespawnTargetIsResultBasedAndNotCancelable()
    {
        Object player = new Object();
        Object post = new Object();

        PlayerRespawnTargetEvent event = factory.playerRespawnTarget(player, post);

        assertTrue(event.hasResult());
        assertFalse(event.isCancelable());
        assertSame(player, event.player());
        assertSame(post, event.postTeleport());
        assertNull(event.respawnTarget());

        Object target = new Object();
        event.setRespawnTarget(target);
        assertSame(target, event.respawnTarget());

        assertThrows(IllegalStateException.class, () -> event.setCanceled(true));
    }

    @Test
    void allHookEventsReachABaseListenerOnALocalBus()
    {
        EventBus bus = new EventBus("hook-events");
        List<Event> seen = new ArrayList<>();
        ListenerHandle handle = bus.addListener(Event.class, seen::add);
        try
        {
            bus.post(factory.blockInteract(new Object(), new Object(), new Object(),
                    new Object(), new Object()));
            bus.post(factory.itemUse(new Object(), new Object(), new Object(), new Object()));
            bus.post(factory.livingEntityDeath(new Object(), new Object()));
            bus.post(factory.containerClose(new Object(), new Object()));
            bus.post(factory.anvilUpdate(new Object()));
            bus.post(factory.enchant(new Object(), new Object()));
            bus.post(factory.playerTeleport(new Object(), new Object()));
            bus.post(factory.playerRespawnTarget(new Object(), new Object()));
            bus.post(factory.entityHurt(new Object(), new Object(), new Object()));
            bus.post(factory.entityInteract(new Object(), new Object(), new Object(),
                    new Object()));
            bus.post(factory.entityAttack(new Object(), new Object()));
            bus.post(factory.blockBreak(new Object(), new Object()));
            bus.post(factory.blockPlace(new Object()));
            bus.post(factory.itemUseFinish(new Object()));
            bus.post(factory.itemRelease(new Object()));
            bus.post(factory.projectileLaunch(new Object()));
            bus.post(factory.clientPlayerTickPre(new Object()));
            bus.post(factory.clientPlayerTickPost(new Object()));
            bus.post(factory.entityTick(new Object(), new Object(), Side.SERVER));
            bus.post(factory.chunkLoad(new Object(), new Object()));
            bus.post(factory.chunkUnload(new Object(), new Object()));
            bus.post(factory.entityRemoval(new Object(), new Object()));
            bus.post(factory.containerClosed(new Object(), new Object()));
            bus.post(factory.blockEntityLoad(new Object()));
            bus.post(factory.blockEntitySave(new Object()));
            bus.post(factory.entitySave(new Object()));
            bus.post(factory.entityLoad(new Object()));
            bus.post(factory.playerRespawnAction(new Object(), new Object(), new Object()));

            assertEquals(28, seen.size());
            assertEquals(BlockInteractEvent.class, seen.get(0).getClass());
            assertEquals(ItemUseEvent.class, seen.get(1).getClass());
            assertEquals(LivingEntityDeathEvent.class, seen.get(2).getClass());
            assertEquals(ContainerCloseEvent.class, seen.get(3).getClass());
            assertEquals(AnvilUpdateEvent.class, seen.get(4).getClass());
            assertEquals(EnchantEvent.class, seen.get(5).getClass());
            assertEquals(PlayerTeleportEvent.class, seen.get(6).getClass());
            assertEquals(PlayerRespawnTargetEvent.class, seen.get(7).getClass());
            assertEquals(EntityHurtEvent.class, seen.get(8).getClass());
            assertEquals(EntityInteractEvent.class, seen.get(9).getClass());
            assertEquals(EntityAttackEvent.class, seen.get(10).getClass());
            assertEquals(BlockBreakEvent.class, seen.get(11).getClass());
            assertEquals(BlockPlaceEvent.class, seen.get(12).getClass());
            assertEquals(ItemUseFinishEvent.class, seen.get(13).getClass());
            assertEquals(ItemReleaseEvent.class, seen.get(14).getClass());
            assertEquals(ProjectileLaunchEvent.class, seen.get(15).getClass());
            assertEquals(ClientPlayerTickEvent.Pre.class, seen.get(16).getClass());
            assertEquals(ClientPlayerTickEvent.Post.class, seen.get(17).getClass());
            assertEquals(EntityTickEvent.class, seen.get(18).getClass());
            assertEquals(ChunkLoadEvent.class, seen.get(19).getClass());
            assertEquals(ChunkUnloadEvent.class, seen.get(20).getClass());
            assertEquals(EntityRemovalEvent.class, seen.get(21).getClass());
            assertEquals(ContainerClosedEvent.class, seen.get(22).getClass());
            assertEquals(BlockEntityLoadEvent.class, seen.get(23).getClass());
            assertEquals(BlockEntitySaveEvent.class, seen.get(24).getClass());
            assertEquals(EntitySaveEvent.class, seen.get(25).getClass());
            assertEquals(EntityLoadEvent.class, seen.get(26).getClass());
            assertEquals(PlayerRespawnActionEvent.class, seen.get(27).getClass());
        }
        finally
        {
            handle.unregister();
        }
    }
}
