package net.weaver.proof;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.player.AnvilCraftPostEvent;
import net.weaver.api.game.event.player.AnvilCraftPreEvent;
import net.weaver.api.game.event.player.GrindstoneResultEvent;
import net.weaver.api.game.event.player.GrindstoneTakeEvent;
import net.weaver.api.game.event.player.ItemSmithedEvent;
import net.weaver.api.game.event.player.ItemStackedOnOtherEvent;
import net.weaver.api.game.event.player.PlayerBrewedPotionEvent;
import net.weaver.api.game.event.player.TradeWithVillagerEvent;
import net.weaver.api.game.event.player.UseItemOnBlockEvent;

/**
 * Batch 4b (menus, trading, item use) part of {@link GameEventsProof}, run
 * with the detached player of {@link GameEventsPlayerProof}. Menus are built
 * directly and their result slots taken by calling {@code onTake}, so nothing
 * is sent to a client. Hotbar switches and hand swaps arrive as packets and
 * are covered by the mixin-applied check.
 */
final class GameEventsMenuProof {
    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;
    private final ServerPlayer player;

    GameEventsMenuProof(ServerLevel level, BlockPos base, GameEventsProof proof, ServerPlayer player) {
        this.level = level;
        this.base = base;
        this.proof = proof;
        this.player = player;
    }

    void run() {
        guard("anvil", this::checkAnvil);
        guard("smithing", this::checkSmithing);
        guard("grindstone", this::checkGrindstone);
        guard("stacked on other", this::checkStackedOnOther);
        guard("brewed potion", this::checkBrewed);
        guard("trade", this::checkTrade);
        guard("use item on block", this::checkUseItemOnBlock);
        guard("enchantment level", this::checkEnchantmentLevel);
        guard("item broken", this::checkItemBroken);
    }

    private void guard(String name, Runnable check) {
        try {
            check.run();
        } catch (Throwable failure) {
            proof.check(false, name + " unexpected " + failure);
        } finally {
            proof.clearListeners();
            player.getInventory().clearContent();
        }
    }

    private void checkAnvil() {
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        proof.listen(AnvilCraftPreEvent.class, event -> {
            if (event.player() == player) {
                event.setCanceled(true);
            }
        });
        menu.setItemName("Weaver Proof");
        player.experienceLevel = 30;
        boolean vanillaAllows = menu.getCost() > 0 && !menu.getSlot(menu.getResultSlot()).getItem().isEmpty();
        boolean refused = !menu.getSlot(menu.getResultSlot()).mayPickup(player);
        boolean kept = vanillaAllows && refused && menu.getSlot(0).getItem().is(Items.IRON_SWORD);
        proof.clearListeners();
        boolean allowed = menu.getSlot(menu.getResultSlot()).mayPickup(player);
        kept = kept && allowed;

        AtomicInteger post = new AtomicInteger();
        proof.listen(AnvilCraftPostEvent.class, event -> {
            if (event.player() == player && event.left().is(Items.IRON_SWORD)) {
                post.incrementAndGet();
            }
        });
        menu.getSlot(menu.getResultSlot()).onTake(player, new ItemStack(Items.IRON_SWORD));
        boolean used = menu.getSlot(0).getItem().isEmpty();
        proof.check(kept, "anvil craft pre canceled kept=" + kept);
        proof.check(post.get() == 1 && used, "anvil craft post fired=" + post.get() + " used=" + used);
    }

    private void checkSmithing() {
        SmithingMenu menu = new SmithingMenu(2, player.getInventory(), ContainerLevelAccess.NULL);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(ItemSmithedEvent.class, event -> {
            if (event.player() == player && event.result().is(Items.NETHERITE_SWORD)) {
                fired.incrementAndGet();
            }
        });
        menu.getSlot(menu.getResultSlot()).onTake(player, new ItemStack(Items.NETHERITE_SWORD));
        proof.check(fired.get() == 1, "item smithed fired=" + fired.get());
    }

    private void checkGrindstone() {
        GrindstoneMenu menu = new GrindstoneMenu(3, player.getInventory(), ContainerLevelAccess.create(level, base));
        proof.listen(GrindstoneResultEvent.class, event -> {
            if (event.menu() == menu && event.top().is(Items.IRON_SWORD)) {
                event.setResult(new ItemStack(Items.DIAMOND));
            }
        });
        menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        ItemStack result = menu.getSlot(2).getItem();
        proof.check(result.is(Items.DIAMOND), "grindstone result=" + result);
        proof.clearListeners();
        AtomicInteger take = new AtomicInteger();
        proof.listen(GrindstoneTakeEvent.class, event -> {
            if (event.menu() == menu) {
                take.incrementAndGet();
                event.setCanceled(true);
            }
        });
        boolean refused = !menu.getSlot(2).mayPickup(player);
        boolean kept = menu.getSlot(0).getItem().is(Items.IRON_SWORD);
        proof.check(take.get() == 1 && refused && kept, "grindstone take canceled fired=" + take.get()
                + " refused=" + refused + " kept=" + kept);
    }

    private void checkStackedOnOther() {
        InventoryMenu menu = player.inventoryMenu;
        int slot = InventoryMenu.INV_SLOT_START;
        menu.getSlot(slot).set(new ItemStack(Items.STONE));
        menu.setCarried(new ItemStack(Items.DIRT));
        AtomicInteger fired = new AtomicInteger();
        proof.listen(ItemStackedOnOtherEvent.class, event -> {
            if (event.player() == player && event.carried().is(Items.DIRT) && event.stackedOn().is(Items.STONE)) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        try {
            menu.clicked(slot, 0, ContainerInput.PICKUP, player);
            boolean unchanged = menu.getCarried().is(Items.DIRT) && menu.getSlot(slot).getItem().is(Items.STONE);
            proof.check(fired.get() == 1 && unchanged,
                    "stacked on other canceled fired=" + fired.get() + " unchanged=" + unchanged);
        } finally {
            menu.setCarried(ItemStack.EMPTY);
        }
    }

    private void checkBrewed() {
        BrewingStandMenu menu = new BrewingStandMenu(4, player.getInventory());
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PlayerBrewedPotionEvent.class, event -> {
            if (event.player() == player) {
                fired.incrementAndGet();
            }
        });
        menu.getSlot(0).onTake(player, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
        proof.check(fired.get() == 1, "brewed potion fired=" + fired.get());
    }

    private void checkTrade() {
        Villager villager = EntityTypes.VILLAGER.spawn(level, base.east(6), EntitySpawnReason.COMMAND);
        if (villager == null) {
            proof.check(false, "trade spawn villager");
            return;
        }
        try {
            villager.setNoAi(true);
            MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 10, 1, 0.05F);
            AtomicInteger fired = new AtomicInteger();
            proof.listen(TradeWithVillagerEvent.class, event -> {
                if (event.villager() == villager && event.player() == player && event.offer() == offer) {
                    fired.incrementAndGet();
                }
            });
            villager.setTradingPlayer(player);
            villager.notifyTrade(offer);
            proof.check(fired.get() == 1 && offer.getUses() == 1, "trade fired=" + fired.get() + " uses=" + offer.getUses());
        } finally {
            villager.setTradingPlayer(null);
            villager.discard();
        }
    }

    private void checkUseItemOnBlock() {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false);
        AtomicInteger phases = new AtomicInteger();
        proof.listen(UseItemOnBlockEvent.class, event -> {
            if (event.player() == player) {
                phases.incrementAndGet();
                event.setResult(event.phase() == UseItemOnBlockEvent.Phase.BLOCK
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
        net.minecraft.world.level.block.state.BlockState before = level.getBlockState(base.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        InteractionResult block = Blocks.STONE.defaultBlockState().useItemOn(player.getMainHandItem(), level, player,
                InteractionHand.MAIN_HAND, hit);
        InteractionResult item = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        boolean untouched = level.getBlockState(base.above()) == before;
        proof.check(phases.get() == 2 && block == InteractionResult.SUCCESS && item == InteractionResult.FAIL && untouched,
                "use item on block phases=" + phases.get() + " block=" + block + " item=" + item + " untouched=" + untouched);
    }

    private void checkEnchantmentLevel() {
        net.minecraft.world.inventory.EnchantmentMenu menu = new net.minecraft.world.inventory.EnchantmentMenu(5,
                player.getInventory(), ContainerLevelAccess.create(level, base));
        AtomicInteger fired = new AtomicInteger();
        proof.listen(net.weaver.api.game.event.world.EnchantmentLevelSetEvent.class, event -> {
            if (event.pos().equals(base)) {
                fired.incrementAndGet();
                event.setEnchantLevel(30);
            }
        });
        menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        int[] costs = menu.costs;
        proof.check(fired.get() >= 3 && fired.get() % 3 == 0 && costs[0] == 30 && costs[1] == 30 && costs[2] == 30,
                "enchantment level fired=" + fired.get() + " costs=" + java.util.Arrays.toString(costs));
    }

    private void checkItemBroken() {
        ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
        sword.setDamageValue(sword.getMaxDamage() - 1);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(net.weaver.api.game.event.player.PlayerItemBrokenEvent.class, event -> {
            if (event.player() == player && event.slot() == net.minecraft.world.entity.EquipmentSlot.MAINHAND) {
                fired.incrementAndGet();
            }
        });
        sword.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        proof.check(fired.get() == 1, "item broken fired=" + fired.get());
    }
}
