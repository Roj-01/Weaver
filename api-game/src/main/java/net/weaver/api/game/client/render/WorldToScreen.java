package net.weaver.api.game.client.render;

import net.minecraft.world.phys.Vec3;

/**
 * Projects a world position into normalized device coordinates.
 *
 * <p>The built-in hook binds this to
 * {@code GameRenderer#projectPointToScreen(Vec3)}. The returned vector is in
 * clip space: {@code x} and {@code y} are in {@code [-1, 1]} for points inside
 * the viewport and {@code z} is the depth, with values outside {@code [0, 1]}
 * indicating a point behind the camera or outside the depth range.
 */
@FunctionalInterface
public interface WorldToScreen
{
    /**
     * Projects one world position.
     *
     * @param worldPosition the world position, never {@code null}
     * @return the projected clip-space position, never {@code null}
     */
    Vec3 project(Vec3 worldPosition);
}
