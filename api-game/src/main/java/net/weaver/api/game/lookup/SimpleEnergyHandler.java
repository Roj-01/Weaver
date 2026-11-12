package net.weaver.api.game.lookup;

/**
 * An {@link EnergyHandler} with a capacity and per-call insert and extract
 * limits. {@link #onChanged()} runs after every real change; override it to
 * mark a block entity changed or write the value back to where it is saved.
 */
public class SimpleEnergyHandler implements EnergyHandler {
    private final long capacity;
    private final long maxInsert;
    private final long maxExtract;
    private long stored;

    public SimpleEnergyHandler(long capacity) {
        this(capacity, capacity, capacity, 0);
    }

    public SimpleEnergyHandler(long capacity, long maxInsert, long maxExtract, long stored) {
        if (capacity < 0 || maxInsert < 0 || maxExtract < 0) {
            throw new IllegalArgumentException("negative energy limit");
        }
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
        this.stored = Math.max(0, Math.min(capacity, stored));
    }

    @Override
    public long stored() {
        return stored;
    }

    @Override
    public long capacity() {
        return capacity;
    }

    /** Sets the stored energy directly, clamped to the capacity; no {@link #onChanged()}. */
    public void setStored(long energy) {
        stored = Math.max(0, Math.min(capacity, energy));
    }

    @Override
    public long insert(long amount, boolean simulate) {
        long accepted = Math.max(0, Math.min(Math.min(amount, maxInsert), capacity - stored));
        if (!simulate && accepted > 0) {
            stored += accepted;
            onChanged();
        }
        return accepted;
    }

    @Override
    public long extract(long amount, boolean simulate) {
        long taken = Math.max(0, Math.min(Math.min(amount, maxExtract), stored));
        if (!simulate && taken > 0) {
            stored -= taken;
            onChanged();
        }
        return taken;
    }

    @Override
    public boolean canInsert() {
        return maxInsert > 0;
    }

    @Override
    public boolean canExtract() {
        return maxExtract > 0;
    }

    /** Called after the stored energy changed through {@link #insert} or {@link #extract}. */
    protected void onChanged() {
    }
}
