package net.weaver.api.game.client.color;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.Block;
import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.ClientRegistrationHandle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for block tint sources.
 *
 * <p>Entries are queued immediately and applied by the built-in
 * {@code BlockColorMixin} at the return of {@code BlockColors#createDefault}.
 * Registration is therefore safe before the client or its {@code BlockColors}
 * instance exists: nothing is written until the game constructs its color
 * table.
 *
 * <p>Several Weaver registrations may target the same block. Their sources are
 * concatenated in registration order, so one Weaver registration does not
 * clobber another. The composed list is then passed to
 * {@code BlockColors#register}, which replaces the block's existing list. A
 * registration for a block that vanilla already tints therefore replaces the
 * vanilla source unless the caller includes an equivalent source in its own
 * list.
 *
 * <p>Removal removes a registration from future {@code createDefault} calls. It
 * cannot undo a registration that was already applied to a live
 * {@code BlockColors} instance; a resource reload or client restart is required
 * for that.
 *
 * <p><strong>Item tints are not supported.</strong> Minecraft 26.3 item colors
 * are data-driven {@code ItemTintSource} definitions loaded from item models;
 * there is no {@code ItemColors} registry to extend at runtime. Weaver does not
 * fake that capability.
 *
 * <p>The registry is thread-safe. All methods may be called from any thread,
 * and {@link #apply(BlockColors)} never throws into the game.
 */
public final class BlockColorRegistry
{
    private static final Map<Block, CopyOnWriteArrayList<Entry>> ENTRIES =
            new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicBoolean REPORTED_PROOF = new AtomicBoolean();

    private BlockColorRegistry()
    {
    }

    /**
     * Registers one or more tint sources for a block.
     *
     * @param block   the block, never {@code null}
     * @param sources the tint sources in layer order, never {@code null} and
     *                never containing {@code null}
     * @return a registration handle, never {@code null}
     * @throws NullPointerException     when an argument or a source is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code sources} is empty
     */
    public static ClientRegistration register(Block block, BlockTintSource... sources)
    {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(sources, "sources");
        if (sources.length == 0)
        {
            throw new IllegalArgumentException("sources must not be empty");
        }
        List<BlockTintSource> copy = new ArrayList<>(sources.length);
        for (BlockTintSource source : sources)
        {
            copy.add(Objects.requireNonNull(source, "source"));
        }
        return register(block, copy);
    }

    /**
     * Registers a list of tint sources for a block.
     *
     * @param block   the block, never {@code null}
     * @param sources the tint sources in layer order, never {@code null} and
     *                never containing {@code null}
     * @return a registration handle, never {@code null}
     * @throws NullPointerException     when an argument or a source is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code sources} is empty
     */
    public static ClientRegistration register(Block block, List<BlockTintSource> sources)
    {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty())
        {
            throw new IllegalArgumentException("sources must not be empty");
        }
        List<BlockTintSource> copy = new ArrayList<>(sources.size());
        for (BlockTintSource source : sources)
        {
            copy.add(Objects.requireNonNull(source, "source"));
        }
        Entry entry = new Entry(List.copyOf(copy), SEQUENCE.incrementAndGet());
        CopyOnWriteArrayList<Entry> list = ENTRIES.computeIfAbsent(block,
                ignored -> new CopyOnWriteArrayList<>());
        ClientRegistrationHandle handle = ClientRegistrationHandle.create(
                "block-color:" + entry.sequence, () -> remove(block, list, entry));
        entry.registration = handle;
        list.add(entry);
        return handle;
    }

    /**
     * Applies every active registration to a color table.
     *
     * <p>Integration entry point for the built-in mixin. A failure for one block
     * is isolated and logged; later blocks still apply. The method never throws.
     *
     * @param colors the color table returned by
     *               {@code BlockColors#createDefault}, never {@code null}
     * @return the number of blocks that were applied
     */
    @net.weaver.api.annotation.Internal
    public static int apply(BlockColors colors)
    {
        Objects.requireNonNull(colors, "colors");
        int applied = 0;
        for (Map.Entry<Block, CopyOnWriteArrayList<Entry>> mapEntry : ENTRIES.entrySet())
        {
            List<Entry> entries = new ArrayList<>(mapEntry.getValue());
            if (entries.isEmpty())
            {
                continue;
            }
            entries.sort(Comparator.comparingLong(entry -> entry.sequence));
            List<BlockTintSource> combined = new ArrayList<>();
            for (Entry entry : entries)
            {
                combined.addAll(entry.sources);
            }
            if (combined.isEmpty())
            {
                continue;
            }
            try
            {
                colors.register(List.copyOf(combined), mapEntry.getKey());
                applied++;
            }
            catch (Throwable failure)
            {
                System.err.println("[weaver] BLOCK COLOR APPLY FAILED block="
                        + mapEntry.getKey() + " reason=" + failure);
            }
        }
        if (applied > 0 && Boolean.getBoolean("weaver.proof.blockColors")
                && REPORTED_PROOF.compareAndSet(false, true))
        {
            System.out.println("[weaver] BLOCK COLORS ok blocks=" + applied);
        }
        return applied;
    }

    /**
     * Returns the number of queued registrations.
     *
     * @return the active registration count
     */
    public static int registrationCount()
    {
        int count = 0;
        for (CopyOnWriteArrayList<Entry> entries : ENTRIES.values())
        {
            count += entries.size();
        }
        return count;
    }

    /**
     * Removes every queued registration.
     *
     * <p>Intended for tests and loader shutdown. A live {@code BlockColors}
     * instance is not modified.
     */
    public static void reset()
    {
        for (CopyOnWriteArrayList<Entry> entries : ENTRIES.values())
        {
            for (Entry entry : entries)
            {
                entry.registration.deactivate();
            }
        }
        ENTRIES.clear();
        SEQUENCE.set(0L);
        REPORTED_PROOF.set(false);
    }

    private static void remove(Block block, CopyOnWriteArrayList<Entry> list, Entry entry)
    {
        list.remove(entry);
        if (list.isEmpty())
        {
            ENTRIES.remove(block, list);
        }
    }

    private static final class Entry
    {
        private final List<BlockTintSource> sources;
        private final long sequence;
        private ClientRegistrationHandle registration;

        private Entry(List<BlockTintSource> sources, long sequence)
        {
            this.sources = sources;
            this.sequence = sequence;
        }
    }
}
