package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedHookEventFactoryTest
{
    private final TypedHookEventFactory factory = new TypedHookEventFactory();

    @Test
    void createsTypedClassesForEveryHook()
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
        assertEquals(ProjectileLaunchEvent.class, factory.projectileLaunch(new Object()).getClass());
        assertEquals(ClientPlayerTickEvent.class,
                factory.clientPlayerTickPre(new Object()).getClass());
        assertEquals(ClientPlayerTickEvent.class,
                factory.clientPlayerTickPost(new Object()).getClass());
        assertEquals(EntityTickEvent.class,
                factory.entityTick(new Object(), new Object(),
                        net.weaver.api.event.Side.SERVER).getClass());
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
        assertEquals(EntitySaveEvent.class,
                factory.entitySave(new Object()).getClass());
        assertEquals(EntityLoadEvent.class,
                factory.entityLoad(new Object()).getClass());
        assertEquals(PlayerRespawnActionEvent.class,
                factory.playerRespawnAction(new Object(), new Object(), new Object()).getClass());
    }

    @Test
    void typedEntityHurtIsCancelable()
    {
        net.weaver.api.event.events.EntityHurtEvent event =
                factory.entityHurt(new Object(), new Object(), new Object());

        assertEquals(EntityHurtEvent.class, event.getClass());
        assertTrue(event.isCancelable());
        assertFalse(event.isCanceled());
        event.setCanceled(true);
        assertTrue(event.isCanceled());
    }

    @Test
    void typedActionHooksKeepTheirContracts()
    {
        EntityAttackEvent attack = (EntityAttackEvent)
                factory.entityAttack(new Object(), new Object());
        assertTrue(attack.isCancelable());
        assertFalse(attack.isCanceled());
        attack.setCanceled(true);
        assertTrue(attack.isCanceled());

        BlockBreakEvent block = (BlockBreakEvent) factory.blockBreak(new Object(), new Object());
        assertTrue(block.isCancelable());

        BlockPlaceEvent place = (BlockPlaceEvent) factory.blockPlace(new Object());
        assertTrue(place.hasResult());
        assertFalse(place.isCancelable());
        place.setResultOverride(InteractionResult.SUCCESS);
        assertSame(InteractionResult.SUCCESS, place.resultOverride());
    }

    @Test
    void typedRespawnTargetIsResultBasedAndNotCancelable()
    {
        PlayerRespawnTargetEvent event = (PlayerRespawnTargetEvent)
                factory.playerRespawnTarget(new Object(), new Object());

        assertEquals(PlayerRespawnTargetEvent.class, event.getClass());
        assertTrue(event.hasResult());
        assertFalse(event.isCancelable());

        TeleportTransition target = new TeleportTransition(null, Vec3.ZERO, Vec3.ZERO, 0.0F,
                0.0F, TeleportTransition.DO_NOTHING);
        event.setRespawnTarget(target);
        assertSame(target, event.respawnTarget());
    }

    @Test
    void typedClassesExtendTheSameNamedBaseClass() throws Exception
    {
        List<Class<?>> typedClasses = List.of(
                BlockInteractEvent.class,
                ItemUseEvent.class,
                LivingEntityDeathEvent.class,
                ContainerCloseEvent.class,
                AnvilUpdateEvent.class,
                EnchantEvent.class,
                PlayerTeleportEvent.class,
                PlayerRespawnTargetEvent.class,
                EntityHurtEvent.class,
                EntityInteractEvent.class,
                EntityAttackEvent.class,
                BlockBreakEvent.class,
                BlockPlaceEvent.class,
                ItemUseFinishEvent.class,
                ItemReleaseEvent.class,
                ProjectileLaunchEvent.class,
                ClientPlayerTickEvent.class,
                EntityTickEvent.class,
                ChunkLoadEvent.class,
                ChunkUnloadEvent.class,
                EntityRemovalEvent.class,
                ContainerClosedEvent.class,
                BlockEntityLoadEvent.class,
                BlockEntitySaveEvent.class,
                EntitySaveEvent.class,
                EntityLoadEvent.class,
                PlayerRespawnActionEvent.class);

        for (Class<?> type : typedClasses)
        {
            Class<?> base = Class.forName("net.weaver.api.event.events." + type.getSimpleName());
            assertEquals(base, type.getSuperclass(), type.getName());
        }
        assertEquals(ServerPlayer.class,
                declaredMethod(BlockInteractEvent.class, "player", false).getReturnType());
        assertEquals(TeleportTransition.class,
                declaredMethod(PlayerRespawnTargetEvent.class, "respawnTarget", false)
                        .getReturnType());
        assertEquals(net.minecraft.core.BlockPos.class,
                declaredMethod(BlockBreakEvent.class, "pos", false).getReturnType());
        assertEquals(net.minecraft.world.level.chunk.LevelChunk.class,
                declaredMethod(ChunkLoadEvent.class, "chunk", false).getReturnType());
        assertEquals(net.minecraft.world.entity.Entity.class,
                declaredMethod(EntityTickEvent.class, "entity", false).getReturnType());
    }

    @Test
    void typedEventTickKeepsItsSide()
    {
        EntityTickEvent event = (EntityTickEvent) factory.entityTick(new Object(), new Object(),
                net.weaver.api.event.Side.CLIENT);

        assertEquals(net.weaver.api.event.Side.CLIENT, event.side());
        assertEquals(EntityTickEvent.class, event.getClass());
    }

    private static Method declaredMethod(Class<?> type, String name, boolean bridge)
    {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> method.isBridge() == bridge)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing " + (bridge ? "bridge " : "") + name + " in " + type.getName()));
    }
}
