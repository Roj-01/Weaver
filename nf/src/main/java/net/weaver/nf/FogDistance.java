package net.weaver.nf;

import net.minecraft.client.renderer.fog.FogData;

/**
 * The fog distances of the frame, as NeoForge's {@code ViewportEvent.RenderFog},
 * over the {@link FogData} a Weaver {@code FogRenderListener} gets: the near and
 * far planes are the environmental fog start and end.
 */
public final class FogDistance {
    private final FogData data;

    public FogDistance(FogData data) {
        this.data = data;
    }

    public float getNearPlaneDistance() {
        return data.environmentalStart;
    }

    public float getFarPlaneDistance() {
        return data.environmentalEnd;
    }

    public void setNearPlaneDistance(float distance) {
        data.environmentalStart = distance;
    }

    public void setFarPlaneDistance(float distance) {
        data.environmentalEnd = distance;
    }
}
