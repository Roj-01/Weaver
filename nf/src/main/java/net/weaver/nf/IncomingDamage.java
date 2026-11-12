package net.weaver.nf;

import java.util.function.Consumer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.EventBus;
import net.weaver.api.game.event.living.LivingIncomingDamageEvent;
import net.weaver.api.game.event.support.GameEvents;

/**
 * Damage about to hit a living entity, before armor and invulnerability, as
 * NeoForge's {@code LivingIncomingDamageEvent}: listeners may change the amount
 * or cancel the hit. Backed by Weaver's {@code LivingIncomingDamageEvent}.
 */
public final class IncomingDamage {
    private final LivingEntity entity;
    private final DamageSource source;
    private final float originalAmount;
    private float amount;
    private boolean canceled;

    IncomingDamage(LivingEntity entity, DamageSource source, float amount) {
        this.entity = entity;
        this.source = source;
        this.originalAmount = amount;
        this.amount = amount;
    }

    /** Calls {@code listener} for every incoming hit; what it sets applies to the hit. */
    public static void listen(Consumer<IncomingDamage> listener) {
        EventBus.GAME.addListener(LivingIncomingDamageEvent.class, event -> {
            IncomingDamage damage = new IncomingDamage(event.entity(), event.source(), event.amount());
            listener.accept(damage);
            event.setAmount(damage.amount);
            if (damage.canceled) {
                event.setCanceled(true);
            }
        });
    }

    /** Posts a hit through the listeners; the resulting amount, or 0 when a listener canceled it. */
    public static float fire(LivingEntity entity, DamageSource source, float amount) {
        LivingIncomingDamageEvent event = GameEvents.post(new LivingIncomingDamageEvent(entity, source, amount), entity.level());
        return event.isCanceled() ? 0.0F : event.amount();
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getOriginalAmount() {
        return originalAmount;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
