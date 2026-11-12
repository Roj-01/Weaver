package net.weaver.api.game.client.mixin;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.weaver.api.game.client.model.ModelLoaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A model JSON with {@code "loader"} gets its geometry from the registered loader. */
@Mixin(targets = "net.minecraft.client.resources.model.cuboid.CuboidModel$Deserializer")
public abstract class ModelLoaderMixin
{
    @Inject(method = "getElements(Lcom/google/gson/JsonDeserializationContext;Lcom/google/gson/JsonObject;)Lnet/minecraft/client/resources/model/geometry/UnbakedGeometry;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$loader(JsonDeserializationContext context, JsonObject json,
            CallbackInfoReturnable<UnbakedGeometry> callback)
    {
        if (json.has("loader"))
        {
            callback.setReturnValue(ModelLoaderRegistry.read(json, context));
        }
    }
}
