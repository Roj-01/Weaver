package net.weaver.api.game.event.client;

import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired on each resource reload after every model is baked and before the
 * models are published to the game ({@code ModelManager}), so mods can wrap or
 * replace baked block and item models. The maps are mutable. This event runs on
 * a reload worker thread; {@link ModelBakingCompletedEvent} fires afterwards on
 * the main thread.
 */
public final class ModifyBakingResultEvent extends Event {
    private final ModelBakery.BakingResult result;

    public ModifyBakingResultEvent(ModelBakery.BakingResult result) {
        this.result = result;
    }

    /** Block state to baked model. */
    public Map<BlockState, BlockStateModel> blockStateModels() {
        return result.blockStateModels();
    }

    /** Item model id to baked item model. */
    public Map<Identifier, ItemModel> itemModels() {
        return result.itemStackModels();
    }

    /** Item model id to client item properties. */
    public Map<Identifier, ClientItem.Properties> itemProperties() {
        return result.itemProperties();
    }

    /** The whole result, including the missing-model fallbacks. */
    public ModelBakery.BakingResult result() {
        return result;
    }
}
