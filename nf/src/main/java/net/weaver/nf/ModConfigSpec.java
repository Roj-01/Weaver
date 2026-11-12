package net.weaver.nf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import net.weaver.api.ModContext;
import net.weaver.api.config.ConfigBuilder;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.config.ConfigSyncPolicy;
import net.weaver.core.config.WeaverConfig;

/**
 * A typed config, built like NeoForge's {@code ModConfigSpec} and stored by
 * Weaver's config system (with its file, settings screen, and server sync).
 *
 * <pre>{@code
 * var builder = new ModConfigSpec.Builder();
 * BooleanValue glow = builder.comment("Glow at night").define("glow", true);
 * ModConfigSpec spec = builder.build();
 * // in onInitialize:
 * spec.open(context, ModConfig.Type.COMMON, "common");
 * }</pre>
 *
 * <p>A key is the path pushed with {@link Builder#push} and the name, joined by
 * dots. Values read their defaults until {@link #open} loads the file.</p>
 */
public final class ModConfigSpec {
    private final List<ConfigValue<?>> values;
    private ConfigManager manager;

    private ModConfigSpec(List<ConfigValue<?>> values) {
        this.values = List.copyOf(values);
    }

    /**
     * Loads (or creates) the config of {@code context}'s mod and binds every
     * value. {@code SERVER} and {@code SYNCED} configs are synced to clients.
     */
    public void open(ModContext context, ModConfig.Type type, String name) {
        ConfigBuilder builder = ConfigBuilder.create(context.id(), WeaverConfig.sideOf(context))
                .schemaVersion(1)
                .description(context.id() + " " + name)
                .sync(type == ModConfig.Type.SYNCED || type == ModConfig.Type.SERVER
                        ? ConfigSyncPolicy.SERVER_TO_CLIENT : ConfigSyncPolicy.NONE);
        for (ConfigValue<?> value : values) {
            value.bind(builder);
        }
        ConfigSpec spec = builder.build();
        ConfigManager opened = WeaverConfig.open(context, spec);
        for (ConfigValue<?> value : values) {
            value.manager = opened;
        }
        manager = opened;
        if (!opened.result().filePresent() || opened.result().defaultedValues() > 0) {
            opened.save();
        }
    }

    /** Writes the current values to the file. */
    public void save() {
        if (manager != null) {
            manager.save();
        }
    }

    public boolean isLoaded() {
        return manager != null;
    }

    /** Declares values; {@link #build()} ends it. */
    public static final class Builder {
        private final List<String> path = new ArrayList<>();
        private final List<ConfigValue<?>> values = new ArrayList<>();
        private String comment = "";

        public Builder() {
        }

        /** The comment of the next value. */
        public Builder comment(String... lines) {
            comment = String.join(" ", lines);
            return this;
        }

        /** Accepted for NeoForge code; the settings screen uses {@code config.<mod>.<key>} translations. */
        public Builder translation(String key) {
            return this;
        }

        /** Accepted for NeoForge code; noted in nothing. */
        public Builder worldRestart() {
            return this;
        }

        /** Accepted for NeoForge code; noted in nothing. */
        public Builder gameRestart() {
            return this;
        }

        public Builder push(String segment) {
            path.addAll(Arrays.asList(segment.split("\\.")));
            return this;
        }

        public Builder push(List<String> segments) {
            path.addAll(segments);
            return this;
        }

        public Builder pop() {
            return pop(1);
        }

        public Builder pop(int count) {
            for (int i = 0; i < count && !path.isEmpty(); i++) {
                path.remove(path.size() - 1);
            }
            return this;
        }

        public BooleanValue define(String name, boolean defaultValue) {
            return add(new BooleanValue(key(name), defaultValue, take()));
        }

        public StringValue define(String name, String defaultValue) {
            return add(new StringValue(key(name), defaultValue, take()));
        }

        public IntValue defineInRange(String name, int defaultValue, int min, int max) {
            return add(new IntValue(key(name), defaultValue, min, max, take()));
        }

        public LongValue defineInRange(String name, long defaultValue, long min, long max) {
            return add(new LongValue(key(name), defaultValue, min, max, take()));
        }

        public DoubleValue defineInRange(String name, double defaultValue, double min, double max) {
            return add(new DoubleValue(key(name), defaultValue, min, max, take()));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(String name, E defaultValue) {
            return add(new EnumValue<>(key(name), defaultValue, defaultValue.getDeclaringClass(), take()));
        }

        public ModConfigSpec build() {
            return new ModConfigSpec(values);
        }

        private String key(String name) {
            List<String> parts = new ArrayList<>(path);
            parts.add(name);
            return String.join(".", parts);
        }

        private String take() {
            String taken = comment;
            comment = "";
            return taken;
        }

        private <V extends ConfigValue<?>> V add(V value) {
            values.add(value);
            return value;
        }
    }

    /** One value: its default until the config is opened, then the stored value. */
    public abstract static class ConfigValue<T> implements Supplier<T> {
        final String key;
        final String comment;
        final T def;
        net.weaver.api.config.ConfigValue<T> bound;
        ConfigManager manager;

        ConfigValue(String key, T def, String comment) {
            this.key = key;
            this.def = def;
            this.comment = comment;
        }

        @Override
        public T get() {
            return bound != null ? bound.get() : def;
        }

        public T getDefault() {
            return def;
        }

        public T getRaw() {
            return get();
        }

        /** Changes the value (call {@link #save()} to write it). */
        public void set(T value) {
            if (bound == null) {
                throw new IllegalStateException("config value " + key + " is not loaded yet");
            }
            bound.set(value);
        }

        public void save() {
            if (manager != null) {
                manager.save();
            }
        }

        public List<String> getPath() {
            return Arrays.asList(key.split("\\."));
        }

        void bind(ConfigBuilder builder) {
            bound = declare(builder);
        }

        abstract net.weaver.api.config.ConfigValue<T> declare(ConfigBuilder builder);
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(String key, boolean def, String comment) {
            super(key, def, comment);
        }

        public boolean getAsBoolean() {
            return get();
        }

        @Override
        net.weaver.api.config.ConfigValue<Boolean> declare(ConfigBuilder builder) {
            return builder.booleanValue(key, def, comment);
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        final int min;
        final int max;

        IntValue(String key, int def, int min, int max, String comment) {
            super(key, def, comment);
            this.min = min;
            this.max = max;
        }

        public int getAsInt() {
            return get();
        }

        @Override
        net.weaver.api.config.ConfigValue<Integer> declare(ConfigBuilder builder) {
            return builder.intValue(key, def, min, max, comment);
        }
    }

    public static final class LongValue extends ConfigValue<Long> {
        final long min;
        final long max;

        LongValue(String key, long def, long min, long max, String comment) {
            super(key, def, comment);
            this.min = min;
            this.max = max;
        }

        public long getAsLong() {
            return get();
        }

        @Override
        net.weaver.api.config.ConfigValue<Long> declare(ConfigBuilder builder) {
            return builder.longValue(key, def, min, max, comment);
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {
        final double min;
        final double max;

        DoubleValue(String key, double def, double min, double max, String comment) {
            super(key, def, comment);
            this.min = min;
            this.max = max;
        }

        public double getAsDouble() {
            return get();
        }

        @Override
        net.weaver.api.config.ConfigValue<Double> declare(ConfigBuilder builder) {
            return builder.doubleValue(key, def, min, max, comment);
        }
    }

    public static final class StringValue extends ConfigValue<String> {
        StringValue(String key, String def, String comment) {
            super(key, def, comment);
        }

        @Override
        net.weaver.api.config.ConfigValue<String> declare(ConfigBuilder builder) {
            return builder.stringValue(key, def, comment);
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {
        final Class<E> type;

        EnumValue(String key, E def, Class<E> type, String comment) {
            super(key, def, comment);
            this.type = type;
        }

        @Override
        net.weaver.api.config.ConfigValue<E> declare(ConfigBuilder builder) {
            return builder.enumValue(key, type, def, comment);
        }
    }
}
