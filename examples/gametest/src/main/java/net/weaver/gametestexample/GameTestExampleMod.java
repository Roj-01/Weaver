package net.weaver.gametestexample;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.weaver.api.ModContext;
import net.weaver.api.game.GameWeaverMod;
import net.weaver.api.game.gametest.GameTests;
import net.weaver.api.game.lookup.ItemHandler;
import net.weaver.api.game.lookup.Lookups;

/**
 * Example game tests: a block test, a test that uses a Weaver API, a test
 * that waits some ticks, and an optional test that fails on purpose so the
 * report shows a failure without failing the run.
 */
public final class GameTestExampleMod implements GameWeaverMod {
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("weavergametest", path);
    }

    @Override
    public void onInitialize(ModContext context) {
        GameTests.register(id("place_block"), helper -> {
            helper.setBlock(0, 0, 0, Blocks.GOLD_BLOCK);
            helper.assertBlockPresent(Blocks.GOLD_BLOCK, 0, 0, 0);
            helper.succeed();
        });

        GameTests.register(id("chest_item_handler"), helper -> {
            BlockPos chest = new BlockPos(0, 0, 0);
            helper.setBlock(0, 0, 0, Blocks.CHEST);
            ItemHandler items = Lookups.BLOCK_ITEMS.find(helper.getLevel(), helper.absolutePos(chest), null);
            helper.assertTrue(items != null && items.slots() == 27, "chest has 27 slots");
            ItemStack left = items.insertAnywhere(new ItemStack(Items.DIAMOND, 3), false);
            helper.assertTrue(left.isEmpty() && items.stack(0).getCount() == 3, "chest took 3 diamonds");
            helper.succeed();
        });

        GameTests.test(id("waits_ticks")).maxTicks(40).register(helper -> {
            long start = helper.getLevel().getGameTime();
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(helper.getLevel().getGameTime() - start >= 10, "ten ticks passed");
                helper.succeed();
            });
        });

        GameTests.test(id("expected_failure")).required(false).register(helper ->
                helper.fail("this optional test fails on purpose"));

        // Lets the gate check that a required failure fails the run.
        if ("1".equals(System.getenv("WEAVER_GAMETEST_FORCE_FAILURE"))) {
            GameTests.register(id("forced_failure"), helper -> helper.fail("required test failing on request"));
        }

        context.log("GAMETEST EXAMPLE registered=" + GameTests.definitions().size());
    }
}
