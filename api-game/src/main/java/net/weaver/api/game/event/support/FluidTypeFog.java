package net.weaver.api.game.event.support;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.weaver.api.game.fluid.FluidType;
import net.weaver.api.game.fluid.FluidTypes;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Fog inside a modded fluid type; first in vanilla's fog environment list. Client only. */
public final class FluidTypeFog extends FogEnvironment {
    private static volatile FluidType current;

    /** Works out the modded fluid at the camera; called when vanilla asks which fluid the camera is in. */
    public static void update(Camera camera, Level level) {
        if (FluidTypes.all().isEmpty() || level == null) {
            current = null;
            return;
        }
        BlockPos pos = camera.blockPosition();
        FluidState state = level.getFluidState(pos);
        FluidType type = FluidTypes.of(state.getType());
        if (type != null && type.fogColor() >= 0 && camera.position().y < pos.getY() + state.getHeight(level, pos)) {
            current = type;
        } else {
            current = null;
        }
    }

    @Override
    public boolean isApplicable(FogType fogType, Entity entity) {
        return current != null;
    }

    @Override
    public void setupFog(FogData data, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta) {
        FluidType type = current;
        if (type == null) {
            return;
        }
        data.environmentalStart = type.fogStart();
        data.environmentalEnd = type.fogEnd();
        data.skyEnd = type.fogEnd();
        data.cloudEnd = type.fogEnd();
    }

    @Override
    public boolean providesColor() {
        return true;
    }

    @Override
    public Vector3fc getBaseColor(ClientLevel level, Camera camera, int renderDistance, float partialTick) {
        FluidType type = current;
        int rgb = type == null ? 0 : type.fogColor();
        return new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
    }
}
