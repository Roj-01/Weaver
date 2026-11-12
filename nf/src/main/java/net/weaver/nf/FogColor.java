package net.weaver.nf;

import net.minecraft.client.renderer.fog.FogData;

/**
 * The fog color of the frame, as NeoForge's {@code ViewportEvent.ComputeFogColor},
 * over the {@link FogData} a Weaver {@code FogRenderListener} gets.
 */
public final class FogColor {
    private final FogData data;
    private final float partialTick;

    public FogColor(FogData data, float partialTick) {
        this.data = data;
        this.partialTick = partialTick;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public float getRed() {
        return data.color.x;
    }

    public float getGreen() {
        return data.color.y;
    }

    public float getBlue() {
        return data.color.z;
    }

    public void setRed(float red) {
        data.color.x = red;
    }

    public void setGreen(float green) {
        data.color.y = green;
    }

    public void setBlue(float blue) {
        data.color.z = blue;
    }
}
