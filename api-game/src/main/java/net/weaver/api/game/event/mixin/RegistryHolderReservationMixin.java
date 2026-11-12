package net.weaver.api.game.event.mixin;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.weaver.api.game.registry.HolderReservations;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link HolderReservations}: a registry without intrusive holders adopts the
 * holder reserved for the key being registered. Vanilla makes the holder in
 * {@code byKey.computeIfAbsent}, so putting the reserved one there first is all
 * it takes; the registry then binds the value to it as to its own.
 */
@Mixin(MappedRegistry.class)
public abstract class RegistryHolderReservationMixin<T> {
    @Shadow
    @Final
    private Map<ResourceKey<T>, Holder.Reference<T>> byKey;

    @Shadow
    private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

    @Inject(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At("HEAD"))
    private void weaver$adoptReservedHolder(ResourceKey<T> key, T value, RegistrationInfo info,
            CallbackInfoReturnable<Holder.Reference<T>> callback) {
        if (unregisteredIntrusiveHolders != null || byKey.containsKey(key)) {
            return;
        }
        Holder.Reference<T> reserved = HolderReservations.take(key);
        if (reserved != null) {
            byKey.put(key, reserved);
        }
    }
}
