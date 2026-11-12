package net.weaver.api.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.weaver.api.event.events.PlayerEvent;
import net.weaver.api.game.event.LevelLoadEvent;
import net.weaver.api.game.event.PlayerJoinEvent;
import net.weaver.api.game.event.ResourceReloadEvent;
import net.weaver.api.game.event.ServerStartedEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedBinaryCompatibilityTest {

    @Test
    void basePlayerAccessorStaysObject() throws Exception {
        assertEquals(Object.class, PlayerEvent.class.getMethod("player").getReturnType());
        assertEquals(Object.class, PlayerEvent.class.getMethod("playerList").getReturnType());
    }

    @Test
    void typedPlayerAccessorsExposeGameTypes() throws Exception {
        assertEquals(ServerPlayer.class,
                declaredMethod(PlayerJoinEvent.class, "player", false).getReturnType());
        assertEquals(PlayerList.class,
                declaredMethod(PlayerJoinEvent.class, "playerList", false).getReturnType());
    }

    @Test
    void typedClassExtendsBaseClass() {
        assertEquals(net.weaver.api.event.events.PlayerJoinEvent.class,
                PlayerJoinEvent.class.getSuperclass());
        assertEquals(net.weaver.api.event.events.LevelLoadEvent.class,
                LevelLoadEvent.class.getSuperclass());
        assertEquals(net.weaver.api.event.events.ServerStartedEvent.class,
                ServerStartedEvent.class.getSuperclass());
    }

    @Test
    void covariantOverridesGenerateObjectBridges() throws Exception {
        assertBridge(PlayerJoinEvent.class, "player", ServerPlayer.class);
        assertBridge(PlayerJoinEvent.class, "playerList", PlayerList.class);
        assertBridge(LevelLoadEvent.class, "level", Level.class);
        assertBridge(ServerStartedEvent.class, "server", net.minecraft.server.MinecraftServer.class);
    }

    @Test
    void resourceReloadDoesNotNarrowItsHeterogeneousSource() throws Exception {
        Method source = ResourceReloadEvent.class.getMethod("source");
        assertEquals(Object.class, source.getReturnType());
        assertEquals(net.weaver.api.event.events.ResourceReloadEvent.class,
                source.getDeclaringClass());
    }

    @Test
    void typedConstructorsAcceptObjectPayloads() throws Exception {
        assertEquals(List.of(Object.class, Object.class), parameterTypes(
                PlayerJoinEvent.class.getConstructor(Object.class, Object.class)));
        assertEquals(List.of(Object.class), parameterTypes(
                LevelLoadEvent.class.getConstructor(Object.class)));
        assertEquals(List.of(Object.class, Object.class, boolean.class), parameterTypes(
                net.weaver.api.game.event.EntityJoinLevelEvent.class
                        .getConstructor(Object.class, Object.class, boolean.class)));
    }

    private static Method declaredMethod(Class<?> type, String name, boolean bridge) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> method.isBridge() == bridge)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing " + (bridge ? "bridge " : "") + name + " in " + type.getName()));
    }

    private static void assertBridge(Class<?> type, String name, Class<?> typedReturn) {
        Method typed = declaredMethod(type, name, false);
        Method bridge = declaredMethod(type, name, true);

        assertEquals(typedReturn, typed.getReturnType());
        assertEquals(Object.class, bridge.getReturnType());
        assertNotNull(bridge);
        assertTrue(bridge.isSynthetic());
    }

    private static List<Class<?>> parameterTypes(Executable executable) {
        return List.of(executable.getParameterTypes());
    }
}
