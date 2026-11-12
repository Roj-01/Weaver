package net.weaver.api.game.event.client;

import net.minecraft.client.Camera;
import net.weaver.api.event.Event;

/**
 * Fired on the client in third-person view when the camera computes how far
 * behind (or in front of) the player it wants to be
 * ({@code Camera#alignWithEntity}, the {@code getMaxZoom} call). Vanilla then
 * shortens the distance if a block is in the way.
 */
public final class CameraDistanceEvent extends Event {
    private final Camera camera;
    private final float originalDistance;
    private float distance;

    public CameraDistanceEvent(Camera camera, float distance) {
        this.camera = camera;
        this.originalDistance = distance;
        this.distance = distance;
    }

    public Camera camera() {
        return camera;
    }

    public float originalDistance() {
        return originalDistance;
    }

    public float distance() {
        return distance;
    }

    public void setDistance(float distance) {
        net.weaver.api.game.event.support.Finite.check(distance, "distance");
        this.distance = Math.max(0.0F, distance);
    }
}
