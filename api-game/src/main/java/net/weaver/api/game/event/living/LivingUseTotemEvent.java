package net.weaver.api.game.event.living;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a dying living entity holding a death-protection item (a totem of
 * undying) is about to be saved by it ({@code checkTotemDeathProtection}).
 * Cancelable: a canceled event lets the entity die and keeps the item.
 */
public final class LivingUseTotemEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    private final ItemStack totem;
    private final InteractionHand hand;

    public LivingUseTotemEvent(LivingEntity entity, DamageSource source, ItemStack totem, InteractionHand hand) {
        super(true);
        this.entity = entity;
        this.source = source;
        this.totem = totem;
        this.hand = hand;
    }

    public LivingEntity entity() {
        return entity;
    }

    public DamageSource source() {
        return source;
    }

    public ItemStack totem() {
        return totem;
    }

    public InteractionHand hand() {
        return hand;
    }
}
