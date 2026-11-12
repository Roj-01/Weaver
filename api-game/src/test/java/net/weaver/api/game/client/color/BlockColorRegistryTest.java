package net.weaver.api.game.client.color;

import net.minecraft.SharedConstants;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.weaver.api.client.ClientRegistration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class BlockColorRegistryTest
{
    @BeforeAll
    static void bootstrapGameClasses()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp()
    {
        BlockColorRegistry.reset();
    }

    @AfterEach
    void tearDown()
    {
        BlockColorRegistry.reset();
    }

    @Test
    void registrationsAreQueuedAndAppliedAtCreateDefault()
    {
        BlockTintSource source = BlockTintSources.constant(0xFFFF0000);

        BlockColorRegistry.register(Blocks.DIRT, source);
        assertEquals(1, BlockColorRegistry.registrationCount());

        BlockColors colors = new BlockColors();
        assertEquals(1, BlockColorRegistry.apply(colors));

        List<BlockTintSource> applied = colors.getTintSources(Blocks.DIRT.defaultBlockState());
        assertEquals(1, applied.size());
        assertSame(source, applied.get(0));
    }

    @Test
    void multipleRegistrationsForOneBlockComposeInOrder()
    {
        BlockTintSource first = BlockTintSources.constant(0xFF00FF00);
        BlockTintSource second = BlockTintSources.constant(0xFF0000FF);

        BlockColorRegistry.register(Blocks.DIRT, first);
        BlockColorRegistry.register(Blocks.DIRT, second);

        BlockColors colors = new BlockColors();
        assertEquals(1, BlockColorRegistry.apply(colors));

        List<BlockTintSource> applied = colors.getTintSources(Blocks.DIRT.defaultBlockState());
        assertEquals(2, applied.size());
        assertSame(first, applied.get(0));
        assertSame(second, applied.get(1));
    }

    @Test
    void unregisteredEntriesAreNotApplied()
    {
        ClientRegistration registration = BlockColorRegistry.register(Blocks.DIRT,
                BlockTintSources.constant(0xFFFF0000));

        registration.unregister();

        assertFalse(registration.isActive());
        assertEquals(0, BlockColorRegistry.registrationCount());
        assertEquals(0, BlockColorRegistry.apply(new BlockColors()));
    }

    @Test
    void oneFailingBlockDoesNotStopLaterBlocks()
    {
        BlockColorRegistry.register(Blocks.DIRT, BlockTintSources.constant(0xFFFF0000));
        BlockColorRegistry.register(Blocks.STONE, BlockTintSources.constant(0xFF0000FF));

        FailingBlockColors colors = new FailingBlockColors();
        assertDoesNotThrow(() -> BlockColorRegistry.apply(colors));

        assertEquals(0, colors.getTintSources(Blocks.DIRT.defaultBlockState()).size());
        assertEquals(1, colors.getTintSources(Blocks.STONE.defaultBlockState()).size());
    }

    @Test
    void resetClearsQueuedRegistrations()
    {
        BlockColorRegistry.register(Blocks.DIRT, BlockTintSources.constant(0xFFFF0000));

        BlockColorRegistry.reset();

        assertEquals(0, BlockColorRegistry.registrationCount());
        assertEquals(0, BlockColorRegistry.apply(new BlockColors()));
    }

    private static final class FailingBlockColors extends BlockColors
    {
        @Override
        public void register(List<BlockTintSource> sources, Block... blocks)
        {
            for (Block block : blocks)
            {
                if (block == Blocks.DIRT)
                {
                    throw new IllegalStateException("boom");
                }
            }
            super.register(sources, blocks);
        }
    }
}
