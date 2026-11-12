package net.weaver.api.game.entity;

import java.util.Objects;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One part of a multipart entity (a body segment, a head, a tail, a turret): its
 * own hitbox that players can hit and projectiles can strike, standing for its
 * parent. Hits on a part go to the parent's {@link MultipartEntity#hurtFromPart}.
 *
 * <p>Parts are not in the level's entity list: they are not saved, not sent to
 * clients, and not drawn; the parent draws itself and moves its parts every
 * tick ({@code part.setPos(...)}). Create the parts in the parent's constructor,
 * right after it, so their ids follow the parent's (the client gives them the
 * same ids from the parent's spawn packet).</p>
 *
 * @param <T> the parent type
 */
public abstract class PartEntity<T extends Entity & MultipartEntity> extends Entity {
    private final T parent;
    private final String name;
    private final EntityDimensions size;

    /**
     * @param parent the entity this part belongs to
     * @param name   a name for the part (for the parent to tell its parts apart)
     * @param width  the hitbox width in blocks
     * @param height the hitbox height in blocks
     */
    protected PartEntity(T parent, String name, float width, float height) {
        super(parent.getType(), parent.level());
        this.parent = Objects.requireNonNull(parent, "parent");
        this.name = Objects.requireNonNull(name, "name");
        this.size = EntityDimensions.scalable(width, height);
        refreshDimensions();
    }

    /**
     * A part that sizes itself: it overrides {@link #getDimensions(Pose)} and may set
     * its size after this constructor ran (its fields are not set yet while this
     * runs, so this does not ask for the size). Call {@link #refreshDimensions()}
     * once the size is known.
     *
     * @param parent the entity this part belongs to
     * @param name   a name for the part (for the parent to tell its parts apart)
     */
    protected PartEntity(T parent, String name) {
        super(parent.getType(), parent.level());
        this.parent = Objects.requireNonNull(parent, "parent");
        this.name = Objects.requireNonNull(name, "name");
        this.size = null;
    }

    public T parent() {
        return parent;
    }

    public String partName() {
        return name;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return parent.getPickResult();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return !parent.isRemoved() && parent.hurtFromPart(this, level, source, amount);
    }


    /** A part is its parent for "is this the same entity" checks (a projectile owner hitting its own part). */
    @Override
    public boolean is(Entity entity) {
        return this == entity || parent == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size != null ? size : super.getDimensions(pose);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
