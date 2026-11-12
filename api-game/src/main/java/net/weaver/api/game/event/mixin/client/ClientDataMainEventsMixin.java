package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.data.Main;
import net.minecraft.data.DataGenerator;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.InitializeClientRegistriesEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla client data generator, before it adds its providers. */
@Mixin(Main.class)
public abstract class ClientDataMainEventsMixin {
    @Inject(method = "addClientProviders(Lnet/minecraft/data/DataGenerator;Z)V", at = @At("HEAD"))
    private static void weaver$initialize(DataGenerator generator, boolean client, CallbackInfo callback) {
        System.out.println("[weaver] CLIENT DATAGEN initialize client=" + client);
        GameEvents.post(new InitializeClientRegistriesEvent(generator, client), LogicalSide.CLIENT);
    }
}
