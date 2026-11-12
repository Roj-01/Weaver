package net.weaver.api.game.client.blockentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.ClientRegistrationHandle;

/**
 * Registry for block entity renderers: the dynamic, per-frame drawing of a block
 * entity (rotating shafts, moving parts, fluids in a tank, items on a belt).
 *
 * <p>A renderer has two halves. {@code extractRenderState} copies what it needs
 * from the block entity into a render state on the main thread; {@code submit}
 * draws from that state through the {@code SubmitNodeCollector} (models,
 * custom geometry, block models, items, text).
 *
 * <p>The built-in mixin merges the registered providers into the map vanilla
 * builds in {@code BlockEntityRenderers#createEntityRenderers} during each client
 * resource reload. Vanilla's map is immutable and never changed; a provider
 * that throws or returns {@code null} is logged once and skipped (a vanilla
 * renderer for the same type stays). Register in client setup: a registration
 * made after the first reload applies at the next one.
 */
public final class BlockEntityRendererRegistry
{
    private static final Map<BlockEntityType<?>, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Set<BlockEntityType<?>> FAILED = ConcurrentHashMap.newKeySet();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private BlockEntityRendererRegistry()
    {
    }

    /**
     * Registers the renderer of a block entity type.
     *
     * @throws ClientException when the type is already registered through Weaver
     */
    public static <T extends BlockEntity, S extends BlockEntityRenderState> ClientRegistration register(
            BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider)
    {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        long sequence = SEQUENCE.incrementAndGet();
        Entry entry = new Entry(type, provider, sequence);
        ClientRegistrationHandle handle = ClientRegistrationHandle.create(
                "block-entity-renderer:" + sequence, () -> ENTRIES.remove(type, entry));
        entry.registration = handle;
        if (ENTRIES.putIfAbsent(type, entry) != null)
        {
            handle.deactivate();
            throw new ClientException("duplicate block entity renderer registration: " + type);
        }
        return handle;
    }

    /** Integration entry point for the built-in mixin; never throws. */
    @net.weaver.api.annotation.Internal
    public static Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> merge(
            Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> vanilla, BlockEntityRendererProvider.Context context)
    {
        if (vanilla == null || ENTRIES.isEmpty())
        {
            return vanilla;
        }
        List<Entry> entries = new ArrayList<>(ENTRIES.values());
        entries.sort(Comparator.comparingLong(entry -> entry.sequence));
        Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> merged = new LinkedHashMap<>(vanilla);
        for (Entry entry : entries)
        {
            BlockEntityRenderer<?, ?> renderer;
            try
            {
                renderer = create(entry, context);
            }
            catch (Throwable failure)
            {
                if (FAILED.add(entry.type))
                {
                    System.err.println("[weaver] BLOCK ENTITY RENDERER FAILED type=" + entry.type + " reason=" + failure);
                }
                continue;
            }
            if (renderer == null)
            {
                if (FAILED.add(entry.type))
                {
                    System.err.println("[weaver] BLOCK ENTITY RENDERER NULL type=" + entry.type);
                }
                continue;
            }
            merged.put(entry.type, renderer);
        }
        return merged;
    }

    public static int registrationCount()
    {
        return ENTRIES.size();
    }

    /** For tests and loader shutdown. */
    public static void reset()
    {
        for (Entry entry : ENTRIES.values())
        {
            entry.registration.deactivate();
        }
        ENTRIES.clear();
        FAILED.clear();
        SEQUENCE.set(0L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockEntityRenderer<?, ?> create(Entry entry, BlockEntityRendererProvider.Context context)
    {
        BlockEntityRendererProvider provider = entry.provider;
        return provider.create(context);
    }

    private static final class Entry
    {
        private final BlockEntityType<?> type;
        private final BlockEntityRendererProvider<?, ?> provider;
        private final long sequence;
        private ClientRegistrationHandle registration;

        private Entry(BlockEntityType<?> type, BlockEntityRendererProvider<?, ?> provider, long sequence)
        {
            this.type = type;
            this.provider = provider;
            this.sequence = sequence;
        }
    }
}
