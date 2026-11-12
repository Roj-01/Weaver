package net.weaver.capabilityproof;

import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.CapabilityException;
import net.weaver.api.capability.CapabilitySync;
import net.weaver.api.data.DataCodecs;
import net.weaver.core.capability.CapabilityPersistence;
import net.weaver.core.capability.CapabilityStores;
import net.weaver.core.data.ReflectiveItemDataAccess;

/**
 * Standalone proof for Weaver 2.2 item and runtime capabilities.
 *
 * <p>The proof bootstraps the real Minecraft registries, binds the stone item
 * components, and then exercises the Weaver API against real {@link ItemStack}
 * instances, the real reflective item data bridge, a real NBT save/load cycle,
 * and the runtime attachment store. It prints one
 * {@code CAPABILITY PROOF CHECK ok=true <name>} line per check and exits with a
 * non-zero status when any check fails.</p>
 */
public final class CapabilityProof {

    private static final String LABEL_ID = "proof:label";
    private static final String COUNT_ID = "proof:count";
    private static final String RUNTIME_ID = "proof:runtime";
    private static final String CORRUPT_ID = "proof:corrupt";
    private static final String OVERSIZE_ID = "proof:oversize";
    private static final int OVERSIZE_LIMIT = 4;

    private static final AttachmentType<String> LABEL = AttachmentType
            .builder(LABEL_ID, String.class)
            .defaultValue("unset")
            .codec(DataCodecs.utf8String(256), 256)
            .build();

    private static final AttachmentType<Integer> COUNT = AttachmentType
            .builder(COUNT_ID, Integer.class)
            .defaultValue(7)
            .codec(DataCodecs.varInt(), 5)
            .build();

    private static final AttachmentType<String> RUNTIME =
            AttachmentType.runtime(RUNTIME_ID, String.class);

    private static final AttachmentType<Boolean> CORRUPT = AttachmentType
            .builder(CORRUPT_ID, Boolean.class)
            .defaultValue(Boolean.TRUE)
            .codec(DataCodecs.bool(), 1)
            .build();

    private static final AttachmentType<byte[]> OVERSIZE = AttachmentType
            .builder(OVERSIZE_ID, byte[].class)
            .codec(DataCodecs.bytes(16), OVERSIZE_LIMIT)
            .build();

    private static int checks;

    private CapabilityProof() {
    }

    /**
     * Runs the capability proof.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        try {
            bootstrap();
            AttachmentRegistry registry = AttachmentRegistry.global();
            CapabilityStores.install(registry);
            register(registry);

            check("item_set_get", () -> itemSetGet(registry));
            check("item_missing_default", () -> itemMissingDefault(registry));
            check("item_copy_semantics", () -> itemCopySemantics(registry));
            check("item_save_load", () -> itemSaveLoad(registry));
            check("block_entity_save_load",
                    () -> blockEntitySaveLoad(registry));
            check("item_remove_returns_previous_and_clears",
                    () -> itemRemoveReturnsPreviousAndClears(registry));
            check("runtime_attach_get_remove",
                    () -> runtimeAttachGetRemove(registry));
            check("isolated_registry_side_behavior",
                    () -> isolatedRegistrySideBehavior(registry));
            check("invalid_payload_falls_back",
                    () -> invalidPayloadFallsBack(registry));
            check("oversize_rejected", () -> oversizeRejected(registry));
            check("concurrent_runtime_access",
                    () -> concurrentRuntimeAccess(registry));
            check("sync_declaration", CapabilityProof::syncDeclaration);

            System.out.println("CAPABILITY PROOF RESULT ok=true checks=" + checks);
        } catch (Throwable failure) {
            System.out.println("CAPABILITY PROOF RESULT ok=false reason="
                    + oneLine(reason(failure)));
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    @SuppressWarnings("deprecation")
    private static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.STONE.builtInRegistryHolder()
                .bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
    }

    private static void register(AttachmentRegistry registry) {
        registry.register(LABEL);
        registry.register(COUNT);
        registry.register(RUNTIME);
        registry.register(CORRUPT);
        registry.register(OVERSIZE);
    }

    private static void itemSetGet(AttachmentRegistry registry) {
        ItemStack stack = new ItemStack(Items.STONE, 2);
        require("unset".equals(registry.get(stack, LABEL)),
                "missing value should be the label default");
        require(Integer.valueOf(7).equals(registry.get(stack, COUNT)),
                "missing value should be the count default");
        require(registry.set(stack, LABEL, "alpha") == null,
                "first label set should have no previous value");
        require(registry.set(stack, COUNT, 42) == null,
                "first count set should have no previous value");
        require("alpha".equals(registry.get(stack, LABEL)),
                "label should round-trip through the item store");
        require(Integer.valueOf(42).equals(registry.get(stack, COUNT)),
                "count should round-trip through the item store");
        require(registry.has(stack, LABEL) && registry.has(stack, COUNT),
                "both values should be present on the stack");
        require(registry.supports(stack, LABEL),
                "the item store should support a persistent capability");
        require(stack.getCount() == 2, "the stack count should be untouched");
    }

    private static void itemMissingDefault(AttachmentRegistry registry) {
        ItemStack stack = new ItemStack(Items.STONE, 1);
        require("unset".equals(registry.get(stack, LABEL)),
                "a fresh stack should report the label default");
        require(Integer.valueOf(7).equals(registry.get(stack, COUNT)),
                "a fresh stack should report the count default");
        require(registry.find(stack, LABEL).isEmpty(),
                "find must not substitute the label default");
        require(registry.find(stack, COUNT).isEmpty(),
                "find must not substitute the count default");
        require(!registry.has(stack, LABEL) && !registry.has(stack, COUNT),
                "a fresh stack must not report explicit values");
    }

    private static void itemCopySemantics(AttachmentRegistry registry) {
        ItemStack stack = new ItemStack(Items.STONE, 2);
        registry.set(stack, LABEL, "alpha");
        registry.set(stack, COUNT, 9);

        ItemStack copy = stack.copy();
        require(copy != stack, "ItemStack.copy should create a new instance");
        require("alpha".equals(registry.get(copy, LABEL)),
                "the copy should keep the label value");
        require(Integer.valueOf(9).equals(registry.get(copy, COUNT)),
                "the copy should keep the count value");

        require("alpha".equals(registry.remove(copy, LABEL)),
                "removing on the copy should return the copied value");
        require("unset".equals(registry.get(copy, LABEL)),
                "the copy should fall back to the label default");
        require(!registry.has(copy, LABEL),
                "the copy should no longer have a label value");
        require("alpha".equals(registry.get(stack, LABEL)),
                "removing on the copy must not change the original");
        require(Integer.valueOf(9).equals(registry.get(stack, COUNT)),
                "the original count must survive a copy removal");
    }

    private static void itemSaveLoad(AttachmentRegistry registry) throws Exception {
        ItemStack stack = new ItemStack(Items.STONE, 2);
        registry.set(stack, LABEL, "saved-value");
        registry.set(stack, COUNT, 99);
        require(stack.get(DataComponents.CUSTOM_DATA) != null,
                "the original stack should carry the custom_data component");

        DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack);
        Tag tag = encoded.result().orElseThrow(
                () -> new IllegalStateException("ItemStack.CODEC failed to encode"));
        DataResult<ItemStack> decoded = ItemStack.CODEC.parse(NbtOps.INSTANCE, tag);
        ItemStack loaded = decoded.result().orElseThrow(
                () -> new IllegalStateException("ItemStack.CODEC failed to parse"));

        require("saved-value".equals(registry.get(loaded, LABEL)),
                "the label should survive an NBT encode/decode cycle");
        require(Integer.valueOf(99).equals(registry.get(loaded, COUNT)),
                "the count should survive an NBT encode/decode cycle");
        require(loaded.get(DataComponents.CUSTOM_DATA) != null,
                "the loaded stack should carry the custom_data component");
        require(loaded.getCount() == 2,
                "the loaded stack should keep its count");
    }

    private static void blockEntitySaveLoad(AttachmentRegistry registry) {
        ChestBlockEntity original = new ChestBlockEntity(BlockPos.ZERO,
                Blocks.CHEST.defaultBlockState());
        ChestBlockEntity loaded = new ChestBlockEntity(BlockPos.ZERO,
                Blocks.CHEST.defaultBlockState());
        require(registry.set(original, LABEL, "chest-value") == null,
                "the first block entity set should have no previous value");

        TagValueOutput output = TagValueOutput.createWithoutContext(
                ProblemReporter.DISCARDING);
        CapabilityPersistence.saveBlockEntity(original, output);
        CompoundTag tag = output.buildResult();
        require(tag.contains(CapabilityPersistence.ROOT_KEY),
                "the saved block entity should carry the weaver:capabilities root");

        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), tag);
        CapabilityPersistence.loadBlockEntity(loaded, input);

        require("chest-value".equals(registry.get(loaded, LABEL)),
                "the label should survive a real block entity value round trip");
        require(!registry.has(loaded, COUNT),
                "an absent capability must not be restored");
        require(original != loaded,
                "the block entity round trip should use a new instance");
    }

    private static void itemRemoveReturnsPreviousAndClears(
            AttachmentRegistry registry) {
        ItemStack stack = new ItemStack(Items.STONE, 1);
        registry.set(stack, LABEL, "beta");
        registry.set(stack, COUNT, 5);

        require("beta".equals(registry.remove(stack, LABEL)),
                "remove should return the previous label value");
        require("unset".equals(registry.get(stack, LABEL)),
                "remove should clear the explicit label value");
        require(!registry.has(stack, LABEL),
                "has should be false after removing the label");
        require(registry.find(stack, LABEL).isEmpty(),
                "find should be empty after removing the label");

        require(Integer.valueOf(5).equals(registry.remove(stack, COUNT)),
                "remove should return the previous count value");
        require(Integer.valueOf(7).equals(registry.get(stack, COUNT)),
                "remove should clear the explicit count value");
        require(registry.remove(stack, LABEL) == null,
                "removing an absent value should return null");
    }

    private static void runtimeAttachGetRemove(AttachmentRegistry registry) {
        Object holder = new Object();
        require(registry.get(holder, RUNTIME) == null,
                "a runtime value should default to null");
        require(registry.set(holder, RUNTIME, "runtime-value") == null,
                "the first runtime set should have no previous value");
        require("runtime-value".equals(registry.get(holder, RUNTIME)),
                "the runtime value should round-trip");
        require(registry.has(holder, RUNTIME),
                "has should report the runtime value");
        require("runtime-value".equals(registry.remove(holder, RUNTIME)),
                "remove should return the runtime value");
        require(!registry.has(holder, RUNTIME),
                "the runtime value should be gone after remove");
        require(registry.get(holder, RUNTIME) == null,
                "get should return null after remove");

        EqualHolder first = new EqualHolder();
        EqualHolder second = new EqualHolder();
        require(first.equals(second), "the identity holders should compare equal");
        registry.set(first, RUNTIME, "first-only");
        require(registry.get(second, RUNTIME) == null,
                "equal but distinct holders must not share runtime values");
        require("first-only".equals(registry.get(first, RUNTIME)),
                "the original equal holder should keep its value");
    }

    private static void isolatedRegistrySideBehavior(AttachmentRegistry registry) {
        Object holder = new Object();
        registry.set(holder, RUNTIME, "global-only");

        AttachmentRegistry isolated = new AttachmentRegistry();
        require(isolated != registry, "the isolated registry should be a new instance");
        require(isolated.find(holder, RUNTIME).isEmpty(),
                "an isolated registry should see no runtime value");
        require(isolated.get(holder, RUNTIME) == null,
                "an isolated registry should return the runtime default");
        require(!isolated.has(holder, RUNTIME),
                "an isolated registry should not report a runtime value");
        require("global-only".equals(registry.get(holder, RUNTIME)),
                "the global registry should keep its runtime value");
    }

    private static void invalidPayloadFallsBack(AttachmentRegistry registry) {
        ItemStack stack = new ItemStack(Items.STONE, 1);
        registry.clearDiagnostics();

        ReflectiveItemDataAccess access = new ReflectiveItemDataAccess();
        access.write(stack, CORRUPT_ID, new byte[]{1, 2, 3});
        require(registry.has(stack, CORRUPT),
                "the corrupt payload should be present on the stack");
        require(Boolean.TRUE.equals(registry.get(stack, CORRUPT)),
                "a corrupt payload should fall back to the boolean default");
        require(registry.find(stack, CORRUPT).isEmpty(),
                "find should be empty for a corrupt payload");

        List<String> diagnostics = registry.diagnostics();
        require(!diagnostics.isEmpty(),
                "a corrupt payload should record a diagnostic");
        require(diagnostics.stream().anyMatch(message -> message.contains(CORRUPT_ID)),
                "the diagnostic should mention the corrupt capability");
    }

    private static void oversizeRejected(AttachmentRegistry registry) {
        ItemStack stack = new ItemStack(Items.STONE, 1);
        boolean rejected = false;
        try {
            registry.set(stack, OVERSIZE, new byte[]{1, 2, 3, 4, 5});
        } catch (CapabilityException expected) {
            rejected = true;
        }
        require(rejected,
                "an encoded payload larger than the bound should be rejected");
        require(registry.find(stack, OVERSIZE).isEmpty(),
                "a rejected oversize value must not be stored");
    }

    private static void concurrentRuntimeAccess(AttachmentRegistry registry)
            throws Exception {
        int threadCount = 8;
        Object[] holders = new Object[threadCount];
        for (int index = 0; index < threadCount; index++) {
            holders[index] = new Object();
        }

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < threadCount; index++) {
                final int worker = index;
                final Object holder = holders[index];
                futures.add(pool.submit(() -> {
                    start.await();
                    String value = "thread-" + worker;
                    for (int round = 0; round < 100; round++) {
                        String previous = registry.set(holder, RUNTIME, value);
                        if (round == 0 && previous != null) {
                            throw new IllegalStateException(
                                    "unexpected previous runtime value in thread "
                                            + worker);
                        }
                        String current = registry.get(holder, RUNTIME);
                        if (!value.equals(current)) {
                            throw new IllegalStateException(
                                    "runtime value mismatch in thread " + worker);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        for (int index = 0; index < threadCount; index++) {
            require(("thread-" + index).equals(registry.get(holders[index], RUNTIME)),
                    "thread " + index + " value should remain readable");
        }
        require(!Objects.equals(registry.get(holders[0], RUNTIME),
                        registry.get(holders[1], RUNTIME)),
                "distinct holders should hold distinct runtime values");
    }

    private static void syncDeclaration() {
        require(LABEL.sync() == CapabilitySync.ITEM_STACK,
                "a persistent capability should declare ITEM_STACK sync");
        require(COUNT.sync() == CapabilitySync.ITEM_STACK,
                "the persistent count should declare ITEM_STACK sync");
        require(RUNTIME.sync() == CapabilitySync.NONE,
                "a runtime-only capability should declare NONE sync");
        require(LABEL.persistent() && COUNT.persistent(),
                "persistent capabilities should report persistent=true");
        require(!RUNTIME.persistent(),
                "a runtime-only capability should report persistent=false");
    }

    private static void check(String name, Check body) {
        try {
            body.run();
        } catch (Throwable failure) {
            throw new ProofFailure(name, failure);
        }
        checks++;
        System.out.println("CAPABILITY PROOF CHECK ok=true " + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String reason(Throwable failure) {
        if (failure instanceof ProofFailure proofFailure) {
            return proofFailure.getMessage();
        }
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null ? "" : ": " + message);
    }

    private static String oneLine(String message) {
        return message.replace('\r', ' ').replace('\n', ' ');
    }

    @FunctionalInterface
    private interface Check {

        void run() throws Exception;
    }

    private static final class ProofFailure extends RuntimeException {

        private ProofFailure(String checkName, Throwable cause) {
            super("check " + checkName + ": " + reasonOf(cause), cause);
        }

        private static String reasonOf(Throwable failure) {
            String message = failure.getMessage();
            return failure.getClass().getSimpleName()
                    + (message == null ? "" : ": " + message);
        }
    }

    private static final class EqualHolder {

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualHolder;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
