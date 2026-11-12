package net.weaver.core.hook;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.AnvilUpdateEvent;
import net.weaver.api.event.events.BlockBreakEvent;
import net.weaver.api.event.events.BlockEntityLoadEvent;
import net.weaver.api.event.events.BlockEntitySaveEvent;
import net.weaver.api.event.events.BlockInteractEvent;
import net.weaver.api.event.events.BlockPlaceEvent;
import net.weaver.api.event.events.ChatEvent;
import net.weaver.api.event.events.ChunkLoadEvent;
import net.weaver.api.event.events.ChunkUnloadEvent;
import net.weaver.api.event.events.ClientPlayerTickEvent;
import net.weaver.api.event.events.ContainerCloseEvent;
import net.weaver.api.event.events.ContainerClosedEvent;
import net.weaver.api.event.events.ContainerOpenEvent;
import net.weaver.api.event.events.EnchantEvent;
import net.weaver.api.event.events.EntityAttackEvent;
import net.weaver.api.event.events.EntityHurtEvent;
import net.weaver.api.event.events.EntityInteractEvent;
import net.weaver.api.event.events.EntityJoinLevelEvent;
import net.weaver.api.event.events.EntityLoadEvent;
import net.weaver.api.event.events.EntityRemovalEvent;
import net.weaver.api.event.events.EntitySaveEvent;
import net.weaver.api.event.events.EntityTickEvent;
import net.weaver.api.event.events.ItemReleaseEvent;
import net.weaver.api.event.events.ItemUseEvent;
import net.weaver.api.event.events.ItemUseFinishEvent;
import net.weaver.api.event.events.LivingEntityDeathEvent;
import net.weaver.api.event.events.LootTableEvent;
import net.weaver.api.event.events.PlayerDeathEvent;
import net.weaver.api.event.events.PlayerRespawnActionEvent;
import net.weaver.api.event.events.PlayerRespawnEvent;
import net.weaver.api.event.events.PlayerRespawnTargetEvent;
import net.weaver.api.event.events.PlayerTeleportEvent;
import net.weaver.api.event.events.ProjectileLaunchEvent;
import net.weaver.api.event.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameHooksEventsTest
{
    @BeforeEach
    void setUp()
    {
        GameEventFactoryHolder.resetForTests();
    }

    @AfterEach
    void tearDown()
    {
        GameEventFactoryHolder.resetForTests();
    }

    @Test
    void entityJoinLevelAllowsAdditionWhenNotCanceled()
    {
        Object level = new Object();
        Object entity = new Object();
        AtomicReference<EntityJoinLevelEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(EntityJoinLevelEvent.class,
                seen::set);
        try
        {
            assertTrue(GameHooks.entityJoinLevel(level, entity));

            assertEquals(level, seen.get().level());
            assertEquals(entity, seen.get().entity());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void entityJoinLevelBlocksAdditionWhenCanceled()
    {
        ListenerHandle handle = EventBus.GAME.addListener(EntityJoinLevelEvent.class,
                event -> event.setCanceled(true));
        try
        {
            assertFalse(GameHooks.entityJoinLevel(new Object(), new Object()));
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void chatBlocksBroadcastWhenCanceled()
    {
        AtomicReference<ChatEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(ChatEvent.class,
                event ->
                {
                    seen.set(event);
                    event.setCanceled(true);
                });
        try
        {
            assertFalse(GameHooks.chat(new Object(), new Object(), new Object()));
            assertTrue(seen.get().isCancelable());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void chatAllowsBroadcastWhenNotCanceled()
    {
        ListenerHandle handle = EventBus.GAME.addListener(ChatEvent.class, event -> { });
        try
        {
            assertTrue(GameHooks.chat(new Object(), new Object(), new Object()));
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void entityHurtCancellationSkipsDamage()
    {
        AtomicReference<EntityHurtEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(EntityHurtEvent.class,
                event ->
                {
                    seen.set(event);
                    event.setCanceled(true);
                });
        try
        {
            assertFalse(GameHooks.entityHurt(new Object(), new Object(), new Object()));

            assertTrue(seen.get().isCancelable());
            assertTrue(seen.get().isCanceled());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void entityHurtAllowsDamageWhenNotCanceled()
    {
        AtomicReference<EntityHurtEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(EntityHurtEvent.class, seen::set);
        try
        {
            assertTrue(GameHooks.entityHurt(new Object(), new Object(), new Object()));

            assertTrue(seen.get().isCancelable());
            assertFalse(seen.get().isCanceled());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void hookExpansionEventsPostTheirPayloadsOnTheServer()
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
        List<Object> events = new ArrayList<>();
        List<ListenerHandle> handles = new ArrayList<>();
        handles.add(EventBus.GAME.addListener(BlockInteractEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(ItemUseEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(LivingEntityDeathEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(AnvilUpdateEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(EnchantEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(PlayerTeleportEvent.class, events::add));
        try
        {
            GameHooks.blockInteract(player, level, stack, hand, hitResult);
            GameHooks.itemUse(player, level, stack, hand);
            GameHooks.livingEntityDeath(entity, source);
            GameHooks.anvilUpdate(menu);
            GameHooks.enchant(menu, player);
            GameHooks.playerTeleport(player, transition);

            assertEquals(6, events.size());
            BlockInteractEvent block = (BlockInteractEvent) events.get(0);
            assertSame(player, block.player());
            assertSame(level, block.level());
            assertSame(stack, block.stack());
            assertSame(hand, block.hand());
            assertSame(hitResult, block.hitResult());
            assertEquals(LogicalSide.SERVER, block.logicalSide());
            ItemUseEvent item = (ItemUseEvent) events.get(1);
            assertSame(player, item.player());
            assertSame(level, item.level());
            assertSame(stack, item.stack());
            assertSame(hand, item.hand());
            assertEquals(LogicalSide.SERVER, item.logicalSide());
            LivingEntityDeathEvent death = (LivingEntityDeathEvent) events.get(2);
            assertSame(entity, death.entity());
            assertSame(source, death.source());
            assertEquals(LogicalSide.SERVER, death.logicalSide());
            AnvilUpdateEvent anvil = (AnvilUpdateEvent) events.get(3);
            assertSame(menu, anvil.menu());
            assertEquals(LogicalSide.SERVER, anvil.logicalSide());
            EnchantEvent enchant = (EnchantEvent) events.get(4);
            assertSame(menu, enchant.menu());
            assertSame(player, enchant.player());
            assertEquals(LogicalSide.SERVER, enchant.logicalSide());
            PlayerTeleportEvent teleport = (PlayerTeleportEvent) events.get(5);
            assertSame(player, teleport.player());
            assertSame(transition, teleport.transition());
            assertEquals(LogicalSide.SERVER, teleport.logicalSide());
        }
        finally
        {
            for (ListenerHandle handle : handles)
            {
                handle.unregister();
            }
        }
    }

    @Test
    void containerClosePostsTheOpenMenu()
    {
        HookContainerFixture fixture = new HookContainerFixture();
        fixture.containerMenu = new Object();
        fixture.inventoryMenu = new Object();
        AtomicReference<ContainerCloseEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(ContainerCloseEvent.class, seen::set);
        try
        {
            GameHooks.containerClose(fixture);

            assertSame(fixture, seen.get().player());
            assertSame(fixture.containerMenu, seen.get().menu());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void containerCloseSkipsTheInventoryMenu()
    {
        HookContainerFixture fixture = new HookContainerFixture();
        Object menu = new Object();
        fixture.containerMenu = menu;
        fixture.inventoryMenu = menu;
        List<ContainerCloseEvent> events = new ArrayList<>();
        ListenerHandle handle = EventBus.GAME.addListener(ContainerCloseEvent.class, events::add);
        try
        {
            GameHooks.containerClose(fixture);

            assertTrue(events.isEmpty());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void containerCloseSkipsMissingFields()
    {
        List<ContainerCloseEvent> events = new ArrayList<>();
        ListenerHandle handle = EventBus.GAME.addListener(ContainerCloseEvent.class, events::add);
        try
        {
            GameHooks.containerClose(new Object());

            assertTrue(events.isEmpty());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void playerRespawnTargetReturnsTheListenerOverride()
    {
        Object player = new Object();
        Object post = new Object();
        Object override = new Object();
        AtomicReference<PlayerRespawnTargetEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(PlayerRespawnTargetEvent.class,
                event ->
                {
                    seen.set(event);
                    event.setRespawnTarget(override);
                });
        try
        {
            assertSame(override, GameHooks.playerRespawnTarget(player, post));

            assertSame(player, seen.get().player());
            assertSame(post, seen.get().postTeleport());
            assertSame(override, seen.get().respawnTarget());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void playerRespawnTargetReturnsNullWithoutOverride()
    {
        Object player = new Object();
        Object post = new Object();
        AtomicReference<PlayerRespawnTargetEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(PlayerRespawnTargetEvent.class,
                seen::set);
        try
        {
            assertNull(GameHooks.playerRespawnTarget(player, post));

            assertSame(player, seen.get().player());
            assertSame(post, seen.get().postTeleport());
            assertNull(seen.get().respawnTarget());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void observationHooksPostTheirEventsOnTheServer()
    {
        Object entity = new Object();
        Object level = new Object();
        Object source = new Object();
        Object player = new Object();
        Object menuProvider = new Object();
        Object lootTable = new Object();
        Object params = new Object();
        List<Object> events = new ArrayList<>();
        List<ListenerHandle> handles = new ArrayList<>();
        handles.add(EventBus.GAME.addListener(EntityHurtEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(PlayerDeathEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(ContainerOpenEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(PlayerRespawnEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(LootTableEvent.class, events::add));
        try
        {
            GameHooks.entityHurt(entity, level, source);
            GameHooks.playerDeath(player, source);
            GameHooks.containerOpen(player, menuProvider);
            GameHooks.playerRespawn(player);
            GameHooks.lootTable(lootTable, params);

            assertEquals(5, events.size());
            EntityHurtEvent hurt = (EntityHurtEvent) events.get(0);
            assertSame(entity, hurt.entity());
            assertSame(level, hurt.level());
            assertSame(source, hurt.source());
            assertEquals(LogicalSide.SERVER, hurt.logicalSide());
            PlayerDeathEvent death = (PlayerDeathEvent) events.get(1);
            assertSame(player, death.player());
            assertSame(source, death.source());
            ContainerOpenEvent container = (ContainerOpenEvent) events.get(2);
            assertSame(player, container.player());
            assertSame(menuProvider, container.menuProvider());
            PlayerRespawnEvent respawn = (PlayerRespawnEvent) events.get(3);
            assertSame(player, respawn.player());
            LootTableEvent loot = (LootTableEvent) events.get(4);
            assertSame(lootTable, loot.lootTable());
            assertSame(params, loot.params());
        }
        finally
        {
            for (ListenerHandle handle : handles)
            {
                handle.unregister();
            }
        }
    }

    @Test
    void entityInteractPostsTheInteractionPayload()
    {
        Object player = new Object();
        Object entity = new Object();
        Object hand = new Object();
        Object hitVec = new Object();
        AtomicReference<EntityInteractEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(EntityInteractEvent.class, seen::set);
        try
        {
            GameHooks.entityInteract(player, entity, hand, hitVec);

            assertSame(player, seen.get().player());
            assertSame(entity, seen.get().entity());
            assertSame(hand, seen.get().hand());
            assertSame(hitVec, seen.get().hitVec());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void entityAttackCancellationSkipsTheAttack()
    {
        AtomicReference<EntityAttackEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(EntityAttackEvent.class,
                event ->
                {
                    seen.set(event);
                    event.setCanceled(true);
                });
        try
        {
            assertFalse(GameHooks.entityAttack(new Object(), new Object()));

            assertTrue(seen.get().isCancelable());
            assertTrue(seen.get().isCanceled());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void entityAttackAllowsTheAttackWhenNotCanceled()
    {
        AtomicReference<EntityAttackEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(EntityAttackEvent.class, seen::set);
        try
        {
            assertTrue(GameHooks.entityAttack(new Object(), new Object()));

            assertTrue(seen.get().isCancelable());
            assertFalse(seen.get().isCanceled());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void blockBreakReadsThePlayerFieldAndHonorsCancellation()
    {
        HookGameModeFixture fixture = new HookGameModeFixture();
        fixture.player = new Object();
        Object pos = new Object();
        AtomicReference<BlockBreakEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(BlockBreakEvent.class,
                event ->
                {
                    seen.set(event);
                    event.setCanceled(true);
                });
        try
        {
            assertFalse(GameHooks.blockBreak(fixture, pos));

            assertSame(fixture.player, seen.get().player());
            assertSame(pos, seen.get().pos());
            assertTrue(seen.get().isCancelable());
            assertTrue(seen.get().isCanceled());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void blockBreakAllowsTheBreakWhenNotCanceled()
    {
        HookGameModeFixture fixture = new HookGameModeFixture();
        fixture.player = new Object();
        ListenerHandle handle = EventBus.GAME.addListener(BlockBreakEvent.class, event -> { });
        try
        {
            assertTrue(GameHooks.blockBreak(fixture, new Object()));
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void blockBreakSkipsTheEventWhenThePlayerFieldIsMissing()
    {
        List<BlockBreakEvent> events = new ArrayList<>();
        ListenerHandle handle = EventBus.GAME.addListener(BlockBreakEvent.class, events::add);
        try
        {
            assertTrue(GameHooks.blockBreak(new Object(), new Object()));

            assertTrue(events.isEmpty());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void blockPlaceReturnsTheListenerOverride()
    {
        Object context = new Object();
        Object override = new Object();
        AtomicReference<BlockPlaceEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(BlockPlaceEvent.class,
                event ->
                {
                    seen.set(event);
                    event.setResultOverride(override);
                });
        try
        {
            assertSame(override, GameHooks.blockPlace(context));

            assertSame(context, seen.get().placeContext());
            assertSame(override, seen.get().resultOverride());
            assertTrue(seen.get().hasResult());
            assertFalse(seen.get().isCancelable());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void blockPlaceReturnsNullWithoutAnOverride()
    {
        Object context = new Object();
        AtomicReference<BlockPlaceEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(BlockPlaceEvent.class, seen::set);
        try
        {
            assertNull(GameHooks.blockPlace(context));

            assertSame(context, seen.get().placeContext());
            assertNull(seen.get().resultOverride());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void itemUseFinishAndReleasePostTheirPayloads()
    {
        Object entity = new Object();
        List<Object> events = new ArrayList<>();
        List<ListenerHandle> handles = new ArrayList<>();
        handles.add(EventBus.GAME.addListener(ItemUseFinishEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(ItemReleaseEvent.class, events::add));
        try
        {
            GameHooks.itemUseFinish(entity);
            GameHooks.itemRelease(entity);

            assertEquals(2, events.size());
            ItemUseFinishEvent finish = (ItemUseFinishEvent) events.get(0);
            assertSame(entity, finish.livingEntity());
            assertEquals(LogicalSide.SERVER, finish.logicalSide());
            ItemReleaseEvent release = (ItemReleaseEvent) events.get(1);
            assertSame(entity, release.livingEntity());
            assertEquals(LogicalSide.SERVER, release.logicalSide());
        }
        finally
        {
            for (ListenerHandle handle : handles)
            {
                handle.unregister();
            }
        }
    }

    @Test
    void projectileLaunchPostsTheProjectile()
    {
        Object projectile = new Object();
        AtomicReference<ProjectileLaunchEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(ProjectileLaunchEvent.class, seen::set);
        try
        {
            GameHooks.projectileLaunch(projectile);

            assertSame(projectile, seen.get().projectile());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void clientPlayerTickPostsPreAndPostOnTheClient()
    {
        Object player = new Object();
        List<ClientPlayerTickEvent> events = new ArrayList<>();
        ListenerHandle handle = EventBus.GAME.addListener(ClientPlayerTickEvent.class, events::add);
        try
        {
            GameHooks.clientPlayerTickEnter(player);
            GameHooks.clientPlayerTickExit(player);

            assertEquals(2, events.size());
            ClientPlayerTickEvent pre = events.get(0);
            assertSame(player, pre.player());
            assertEquals(ClientPlayerTickEvent.Phase.PRE, pre.phase());
            assertEquals(LogicalSide.CLIENT, pre.logicalSide());
            ClientPlayerTickEvent post = events.get(1);
            assertSame(player, post.player());
            assertEquals(ClientPlayerTickEvent.Phase.POST, post.phase());
            assertEquals(LogicalSide.CLIENT, post.logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void entityTickPostsBothLogicalSides()
    {
        Object level = new Object();
        Object entity = new Object();
        List<EntityTickEvent> events = new ArrayList<>();
        ListenerHandle handle = EventBus.GAME.addListener(EntityTickEvent.class, events::add);
        try
        {
            GameHooks.serverEntityTick(level, entity);
            GameHooks.clientEntityTick(level, entity);

            assertEquals(2, events.size());
            EntityTickEvent server = events.get(0);
            assertSame(level, server.level());
            assertSame(entity, server.entity());
            assertEquals(Side.SERVER, server.side());
            assertEquals(LogicalSide.SERVER, server.logicalSide());
            EntityTickEvent client = events.get(1);
            assertSame(level, client.level());
            assertSame(entity, client.entity());
            assertEquals(Side.CLIENT, client.side());
            assertEquals(LogicalSide.CLIENT, client.logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void chunkLoadAndUnloadPostTheirPayloads()
    {
        Object level = new Object();
        Object chunk = new Object();
        List<Object> events = new ArrayList<>();
        List<ListenerHandle> handles = new ArrayList<>();
        handles.add(EventBus.GAME.addListener(ChunkLoadEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(ChunkUnloadEvent.class, events::add));
        try
        {
            GameHooks.chunkLoad(level, chunk);
            GameHooks.chunkUnload(level, chunk);

            assertEquals(2, events.size());
            ChunkLoadEvent load = (ChunkLoadEvent) events.get(0);
            assertSame(level, load.level());
            assertSame(chunk, load.chunk());
            assertEquals(LogicalSide.SERVER, load.logicalSide());
            ChunkUnloadEvent unload = (ChunkUnloadEvent) events.get(1);
            assertSame(level, unload.level());
            assertSame(chunk, unload.chunk());
            assertEquals(LogicalSide.SERVER, unload.logicalSide());
        }
        finally
        {
            for (ListenerHandle handle : handles)
            {
                handle.unregister();
            }
        }
    }

    @Test
    void entityRemovalPostsTheReason()
    {
        Object entity = new Object();
        Object reason = new Object();
        AtomicReference<EntityRemovalEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(EntityRemovalEvent.class, seen::set);
        try
        {
            GameHooks.entityRemoval(entity, reason);

            assertSame(entity, seen.get().entity());
            assertSame(reason, seen.get().reason());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void containerClosedPostsTheMenuBeingClosed()
    {
        HookContainerFixture fixture = new HookContainerFixture();
        fixture.containerMenu = new Object();
        fixture.inventoryMenu = new Object();
        AtomicReference<ContainerClosedEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(ContainerClosedEvent.class, seen::set);
        try
        {
            GameHooks.containerClosed(fixture);

            assertSame(fixture, seen.get().player());
            assertSame(fixture.containerMenu, seen.get().containerMenu());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void containerClosedSkipsTheInventoryMenu()
    {
        HookContainerFixture fixture = new HookContainerFixture();
        Object menu = new Object();
        fixture.containerMenu = menu;
        fixture.inventoryMenu = menu;
        List<ContainerClosedEvent> events = new ArrayList<>();
        ListenerHandle handle = EventBus.GAME.addListener(ContainerClosedEvent.class, events::add);
        try
        {
            GameHooks.containerClosed(fixture);

            assertTrue(events.isEmpty());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void blockEntityLoadAndSavePostTheirPayloads()
    {
        Object blockEntity = new Object();
        List<Object> events = new ArrayList<>();
        List<ListenerHandle> handles = new ArrayList<>();
        handles.add(EventBus.GAME.addListener(BlockEntityLoadEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(BlockEntitySaveEvent.class, events::add));
        try
        {
            GameHooks.blockEntityLoad(blockEntity);
            GameHooks.blockEntitySave(blockEntity);

            assertEquals(2, events.size());
            BlockEntityLoadEvent load = (BlockEntityLoadEvent) events.get(0);
            assertSame(blockEntity, load.blockEntity());
            assertEquals(LogicalSide.SERVER, load.logicalSide());
            BlockEntitySaveEvent save = (BlockEntitySaveEvent) events.get(1);
            assertSame(blockEntity, save.blockEntity());
            assertEquals(LogicalSide.SERVER, save.logicalSide());
        }
        finally
        {
            for (ListenerHandle handle : handles)
            {
                handle.unregister();
            }
        }
    }

    @Test
    void entityLoadAndSavePostTheirPayloads()
    {
        Object entity = new Object();
        Object carrier = new Object();
        List<Object> events = new ArrayList<>();
        List<ListenerHandle> handles = new ArrayList<>();
        handles.add(EventBus.GAME.addListener(EntitySaveEvent.class, events::add));
        handles.add(EventBus.GAME.addListener(EntityLoadEvent.class, events::add));
        try
        {
            GameHooks.entitySave(entity, carrier);
            GameHooks.entityLoad(entity, carrier);

            assertEquals(2, events.size());
            EntitySaveEvent save = (EntitySaveEvent) events.get(0);
            assertSame(entity, save.entity());
            assertEquals(LogicalSide.SERVER, save.logicalSide());
            EntityLoadEvent load = (EntityLoadEvent) events.get(1);
            assertSame(entity, load.entity());
            assertEquals(LogicalSide.SERVER, load.logicalSide());
        }
        finally
        {
            for (ListenerHandle handle : handles)
            {
                handle.unregister();
            }
        }
    }

    @Test
    void entityAndBlockEntityPersistenceHooksIgnoreNonGameHolders()
    {
        Object holder = new Object();
        Object carrier = new Object();

        GameHooks.entitySave(holder, carrier);
        GameHooks.entityLoad(holder, carrier);
        GameHooks.blockEntityPersistenceSave(holder, carrier);
        GameHooks.blockEntityPersistenceLoad(holder, carrier);
    }

    @Test
    void playerRespawnActionPostsTheOldPlayerAndReason()
    {
        Object playerList = new Object();
        Object oldPlayer = new Object();
        Object reason = new Object();
        AtomicReference<PlayerRespawnActionEvent> seen = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(PlayerRespawnActionEvent.class, seen::set);
        try
        {
            GameHooks.playerRespawnAction(playerList, oldPlayer, reason);

            assertSame(playerList, seen.get().playerList());
            assertSame(oldPlayer, seen.get().oldPlayer());
            assertSame(reason, seen.get().reason());
            assertEquals(LogicalSide.SERVER, seen.get().logicalSide());
        }
        finally
        {
            handle.unregister();
        }
    }

    private static final class HookGameModeFixture
    {
        private Object player;
    }
}
