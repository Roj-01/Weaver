package net.weaver.api.game.event.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.weaver.api.game.data.LoadConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Data load conditions ({@link LoadConditions}) for every loader that lists its
 * files through {@code FileToIdConverter}: recipes, advancements, loot tables and
 * other reloadable registries, worldgen and other data registries, and tags.
 */
@Mixin(FileToIdConverter.class)
public abstract class LoadConditionsMixin {
    @Inject(method = "listMatchingResources", at = @At("RETURN"), cancellable = true)
    private void weaver$conditions(ResourceManager manager, CallbackInfoReturnable<Map<Identifier, Resource>> callback) {
        Map<Identifier, Resource> listed = callback.getReturnValue();
        if (listed == null || listed.isEmpty()) {
            return;
        }
        try {
            try {
                LoadConditions.filter(listed);
            } catch (UnsupportedOperationException immutable) {
                Map<Identifier, Resource> copy = new TreeMap<>(listed);
                LoadConditions.filter(copy);
                callback.setReturnValue(copy);
            }
        } catch (RuntimeException failure) {
            System.out.println("[weaver] DATA CONDITIONS FAILED reason=" + failure + " action=all files kept");
        }
    }

    @Inject(method = "listMatchingResourceStacks", at = @At("RETURN"), cancellable = true)
    private void weaver$stackConditions(ResourceManager manager,
            CallbackInfoReturnable<Map<Identifier, List<Resource>>> callback) {
        Map<Identifier, List<Resource>> listed = callback.getReturnValue();
        if (listed == null || listed.isEmpty()) {
            return;
        }
        try {
            Map<Identifier, List<Resource>> copy = new TreeMap<>();
            for (Map.Entry<Identifier, List<Resource>> entry : listed.entrySet()) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            if (LoadConditions.filterStacks(copy) > 0) {
                callback.setReturnValue(copy);
            }
        } catch (RuntimeException failure) {
            System.out.println("[weaver] DATA CONDITIONS FAILED reason=" + failure + " action=all files kept");
        }
    }
}
