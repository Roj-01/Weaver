package net.weaver.proof;

import com.mojang.serialization.Codec;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.AABB;
import net.weaver.api.ModContext;
import net.weaver.nf.BlockEntityBlocks;
import net.weaver.nf.DeferredBlock;
import net.weaver.nf.DeferredHolder;
import net.weaver.nf.DeferredItem;
import net.weaver.nf.DeferredRegister;
import net.weaver.nf.EntityAttributes;
import net.weaver.nf.ModConfig;
import net.weaver.nf.ModConfigSpec;
import net.weaver.nf.Mods;
import net.weaver.nf.Multipart;
import net.weaver.nf.NeoBiomeModifiers;
import net.weaver.nf.PartEntity;
import net.weaver.nf.PartLink;
import net.weaver.nf.Parts;
import net.weaver.nf.PersistentData;
import net.weaver.nf.Servers;

/**
 * The NeoForge-shaped library ({@code net.weaver.nf}) on a real server: content
 * registered the NeoForge way (deferred registers, attributes, a multipart mob, a
 * {@code neoforge:add_spawns} file), then checked against the registries and the
 * level. The holder check is the one the per-mod copies got wrong: a deferred
 * holder of a non-intrusive registry must be the registry's own holder, or maps
 * keyed by holders (attribute maps) miss it.
 */
final class NfProof {
    private static final String NS = "weaverproof";

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NS);
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NS);
    static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, NS);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(NS);
    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(NS);

    static final DeferredItem<Item> NF_ITEM = ITEMS.registerSimpleItem("nf_item");
    static final DeferredBlock<Block> NF_BLOCK = BLOCKS.registerSimpleBlock("nf_block");
    static final DeferredHolder<Attribute, Attribute> NF_POWER = ATTRIBUTES.register("nf_power",
            () -> new RangedAttribute("attribute.weaverproof.nf_power", 1.0, 0.0, 100.0));
    static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> NF_CHARGE =
            COMPONENTS.registerComponentType("nf_charge", builder -> builder.persistent(Codec.INT));
    static final DeferredHolder<EntityType<?>, EntityType<Serpent>> NF_SERPENT = ENTITIES.registerEntityType("nf_serpent",
            Serpent::new, MobCategory.MISC, builder -> builder.sized(1.0F, 1.0F));

    static ModConfigSpec.IntValue configValue;

    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;

    NfProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    /** NeoForge-style setup, from {@code onInitialize}: the static registers above flush on their registry events. */
    static void install(ModContext context) {
        PersistentData.init(context);
        Servers.init(context);
        NeoBiomeModifiers.register();
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("nf");
        configValue = builder.comment("A value read through the NeoForge-shaped config").defineInRange("charge", 7, 0, 64);
        builder.pop();
        builder.build().open(context, ModConfig.Type.COMMON, "weaverproof-nf");
        // As a NeoForge mod does from its constructor: the entity type and the attributes
        // (vanilla's and the mod's) are not bound yet, so the library runs this at common setup.
        EntityAttributes.register(event -> event.put(NF_SERPENT.get(), PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0).add(NF_POWER, 5.0).build()));
    }

    /** From {@code onCommonSetup}. */
    static void setup() {
        BlockEntityBlocks.add(BlockEntityTypes.CHEST, Blocks.STONE);
    }

    void run() {
        checkHolders();
        checkPlain();
        checkPlayerPersisted();
        checkMultipart();
        checkSpawns();
    }

    private void checkHolders() {
        Holder<Attribute> registryPower = BuiltInRegistries.ATTRIBUTE.get(NF_POWER.getKey()).orElse(null);
        Holder<DataComponentType<?>> registryCharge = BuiltInRegistries.DATA_COMPONENT_TYPE.get(NF_CHARGE.getKey()).orElse(null);
        boolean sameAttribute = registryPower == NF_POWER;
        boolean sameComponent = registryCharge == NF_CHARGE;
        Item item = BuiltInRegistries.ITEM.getValue(NF_ITEM.getKey());
        boolean itemBound = NF_ITEM.isBound() && NF_ITEM.get() == item && NF_ITEM.toStack().is(item);
        boolean blockBound = NF_BLOCK.isBound() && NF_BLOCK.get() == BuiltInRegistries.BLOCK.getValue(NF_BLOCK.getKey());
        ItemStack stack = NF_ITEM.toStack();
        stack.set(NF_CHARGE.get(), 3);
        boolean component = Integer.valueOf(3).equals(stack.get(NF_CHARGE.get()));
        // Codecs check every holder they write against the registry: the adopted holder must pass, both ways.
        net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = net.minecraft.resources.RegistryOps.create(
                net.minecraft.nbt.NbtOps.INSTANCE, level.registryAccess());
        boolean encodes = Attribute.CODEC.encodeStart(ops, NF_POWER).result()
                .flatMap(tag -> Attribute.CODEC.parse(ops, tag).result())
                .map(decoded -> decoded == NF_POWER).orElse(false);
        boolean stackEncodes = ItemStack.CODEC.encodeStart(ops, stack).result()
                .flatMap(tag -> ItemStack.CODEC.parse(ops, tag).result())
                .map(decoded -> Integer.valueOf(3).equals(decoded.get(NF_CHARGE.get()))).orElse(false);
        proof.check(sameAttribute && sameComponent && itemBound && blockBound && component && encodes && stackEncodes,
                "nf holders attribute=" + sameAttribute + " component=" + sameComponent + " item=" + itemBound
                        + " block=" + blockBound + " componentValue=" + component + " codec=" + encodes
                        + " stackCodec=" + stackEncodes);
    }

    private void checkPlain() {
        boolean loaded = Mods.isLoaded(NS) && !Mods.isLoaded("weaverproof_absent");
        boolean server = Servers.get() == level.getServer();
        boolean config = configValue != null && configValue.get() == 7;
        boolean chestOnStone = BlockEntityTypes.CHEST.isValid(Blocks.STONE.defaultBlockState())
                && !BlockEntityTypes.CHEST.isValid(Blocks.DIRT.defaultBlockState());
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        boolean persistent = false;
        if (pig != null) {
            CompoundTag data = PersistentData.of(pig);
            data.putInt("nf", 11);
            persistent = PersistentData.of(pig).getIntOr("nf", 0) == 11;
            pig.discard();
        }
        proof.check(loaded && server && config && chestOnStone && persistent, "nf plain mods=" + loaded + " server=" + server
                + " config=" + config + " blockEntityBlocks=" + chestOnStone + " persistentData=" + persistent);
    }

    /** On a respawn after death only the PlayerPersisted tag carries over, as on NeoForge. */
    private void checkPlayerPersisted() {
        ServerPlayer dead = player("NfPersistedOld");
        ServerPlayer respawned = player("NfPersistedNew");
        try {
            CompoundTag data = PersistentData.of(dead);
            data.putInt("loose", 1);
            CompoundTag persisted = new CompoundTag();
            persisted.putInt("kept", 2);
            data.put(PersistentData.PERSISTED, persisted);
            respawned.restoreFrom(dead, false);
            CompoundTag after = PersistentData.of(respawned);
            boolean kept = after.getCompound(PersistentData.PERSISTED).map(tag -> tag.getIntOr("kept", 0) == 2).orElse(false);
            boolean dropped = !after.contains("loose");
            boolean copied = after.getCompound(PersistentData.PERSISTED).orElse(null) != persisted;
            proof.check(kept && dropped && copied, "nf persisted data on death kept=" + kept + " looseDropped=" + dropped
                    + " copied=" + copied);
        } finally {
            dead.discard();
            respawned.discard();
        }
    }

    private ServerPlayer player(String name) {
        return new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name),
                net.minecraft.server.level.ClientInformation.createDefault());
    }

    private void checkMultipart() {
        Serpent serpent = NF_SERPENT.get().create(level, EntitySpawnReason.COMMAND);
        if (serpent == null) {
            proof.check(false, "nf multipart serpent not created");
            return;
        }
        try {
            BlockPos at = base.above(4);
            serpent.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            level.addFreshEntity(serpent);
            serpent.placeParts();
            Segment tail = serpent.tail;
            AABB around = tail.getBoundingBox().inflate(0.25);
            boolean levelSees = level.getEntities((Entity) null, around, entity -> entity == tail).contains(tail);
            List<Entity> found = new ArrayList<>();
            Parts.addParts(level, null, around, entity -> true, found);
            boolean partsSee = found.contains(tail);
            boolean link = PartLink.isPart(tail) && PartLink.whole(tail) == serpent && PartLink.parent(serpent) == null
                    && tail.getParent() == serpent && Parts.byId(level, tail.getId()) == tail;
            boolean sized = tail.getBbWidth() == 0.5F;
            double power = serpent.getAttributeValue(NF_POWER);
            boolean health = serpent.getMaxHealth() == 30.0F;
            float before = serpent.getHealth();
            tail.hurtServer(level, level.damageSources().generic(), 2.0F);
            boolean hurt = serpent.getHealth() < before;
            proof.check(levelSees && partsSee && link && sized && power == 5.0 && health && hurt,
                    "nf multipart levelSees=" + levelSees + " parts=" + partsSee + " link=" + link + " sized=" + sized
                            + " power=" + power + " maxHealth=" + serpent.getMaxHealth() + " hurt=" + hurt);
        } finally {
            serpent.discard();
        }
    }

    private void checkSpawns() {
        try {
            Method mobsAt = NaturalSpawner.class.getDeclaredMethod("mobsAt", ServerLevel.class, StructureManager.class,
                    ChunkGenerator.class, MobCategory.class, BlockPos.class);
            mobsAt.setAccessible(true);
            WeightedList<?> creatures = (WeightedList<?>) mobsAt.invoke(null, level, level.structureManager(),
                    level.getChunkSource().getGenerator(), MobCategory.CREATURE, base);
            WeightedList<?> monsters = (WeightedList<?>) mobsAt.invoke(null, level, level.structureManager(),
                    level.getChunkSource().getGenerator(), MobCategory.MONSTER, base);
            boolean goat = creatures.unwrap().stream().anyMatch(entry ->
                    ((MobSpawnSettings.SpawnerData) entry.value()).type() == EntityTypes.GOAT && entry.weight() == 7);
            boolean notInMonsters = monsters.unwrap().stream().noneMatch(entry ->
                    ((MobSpawnSettings.SpawnerData) entry.value()).type() == EntityTypes.GOAT);
            proof.check(goat && notInMonsters, "nf biome modifier goat=" + goat + " categoryKept=" + notInMonsters);
        } catch (ReflectiveOperationException failure) {
            proof.check(false, "nf biome modifier " + failure);
        }
    }

    /** A two-part creature, written the NeoForge way. */
    static final class Serpent extends PathfinderMob implements Multipart {
        final Segment head;
        final Segment tail;
        private final Segment[] parts;

        Serpent(EntityType<? extends Serpent> type, Level level) {
            super(type, level);
            head = new Segment(this, 1.0F);
            tail = new Segment(this, 0.5F);
            parts = new Segment[] {head, tail};
        }

        @Override
        public Segment[] getParts() {
            return parts;
        }

        void placeParts() {
            head.setPos(getX(), getY() + 1.0, getZ());
            tail.setPos(getX() + 2.0, getY(), getZ());
        }

        @Override
        public void aiStep() {
            super.aiStep();
            placeParts();
        }
    }

    /** A hitbox that sets its size after the constructor, as NeoForge parts do. */
    static final class Segment extends PartEntity<Serpent> {
        private final EntityDimensions size;

        Segment(Serpent parent, float width) {
            super(parent);
            this.size = EntityDimensions.scalable(width, width);
            refreshDimensions();
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return size;
        }
    }
}
