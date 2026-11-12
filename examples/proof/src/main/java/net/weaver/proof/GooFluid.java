package net.weaver.proof;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.weaver.api.game.fluid.FluidType;
import net.weaver.api.game.fluid.FluidTypes;

/**
 * A real modded fluid for the FluidType proof: thick (low drag) and drownable,
 * neither water nor lava, so only Weaver's FluidType hooks move entities in it.
 */
abstract class GooFluid extends FlowingFluid {
    static final TagKey<Fluid> TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("weaverproof", "goo"));
    static FlowingFluid source;
    static FlowingFluid flowing;
    static LiquidBlock block;

    /** Registers the fluids, the block, and the fluid type; during the register phase. */
    static void register() {
        source = Registry.register(BuiltInRegistries.FLUID, Identifier.fromNamespaceAndPath("weaverproof", "goo"), new Source());
        flowing = Registry.register(BuiltInRegistries.FLUID, Identifier.fromNamespaceAndPath("weaverproof", "flowing_goo"), new Flowing());
        ResourceKey<net.minecraft.world.level.block.Block> key = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("weaverproof", "goo"));
        block = Registry.register(BuiltInRegistries.BLOCK, key, new GooBlock(source,
                BlockBehaviour.Properties.of().setId(key).noCollision().strength(100.0F).noLootTable().liquid()));
        FluidTypes.register(TAG, FluidType.builder()
                .speed(0.01)
                .drag(0.4, 0.4)
                .canDrown(true)
                .fog(0x4A9A2E, 0.0F, 3.0F)
                .build());
    }

    @Override
    public Fluid getFlowing() {
        return flowing;
    }

    @Override
    public Fluid getSource() {
        return source;
    }

    @Override
    public Item getBucket() {
        return Items.BUCKET;
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
    }

    @Override
    protected int getSlopeFindDistance(LevelReader level) {
        return 2;
    }

    @Override
    protected int getDropOff(LevelReader level) {
        return 2;
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return false;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 20;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return block.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == source || fluid == flowing;
    }

    static final class Source extends GooFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    static final class Flowing extends GooFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    static final class GooBlock extends LiquidBlock {
        GooBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
            super(fluid, properties);
        }
    }
}
