package net.weaver.api.config;

import net.weaver.api.event.Side;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBuilderTest
{
    private static final String MOD = "api.builder";

    enum Mode
    {
        SLOW,
        NORMAL,
        FAST
    }

    enum Other
    {
        ON,
        OFF
    }

    @Test
    void buildsSpecWithEveryType()
    {
        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT)
                .schemaVersion(7)
                .description("every supported type");

        ConfigValue<Boolean> bool = builder.booleanValue("types.boolean", true, "a boolean");
        ConfigValue<Integer> intUnbounded = builder.intValue("types.int", 5, "an integer");
        ConfigValue<Integer> intBounded =
                builder.intValue("types.int.bounded", 5, 0, 10, "a bounded integer");
        ConfigValue<Long> longUnbounded = builder.longValue("types.long", 6L, "a long");
        ConfigValue<Long> longBounded =
                builder.longValue("types.long.bounded", 6L, 0L, 10L, "a bounded long");
        ConfigValue<Double> doubleUnbounded =
                builder.doubleValue("types.double", 1.5, "a decimal");
        ConfigValue<Double> doubleBounded =
                builder.doubleValue("types.double.bounded", 1.5, 0.0, 2.0, "a bounded decimal");
        ConfigValue<String> stringUnbounded =
                builder.stringValue("types.string", "text", "a string");
        ConfigValue<String> stringBounded =
                builder.stringValue("types.string.bounded", "text", 1, 8, "a bounded string");
        ConfigValue<Mode> enumValue =
                builder.enumValue("types.enum", Mode.class, Mode.NORMAL, "an enum");
        ConfigValue<List<String>> stringList =
                builder.stringListValue("types.string.list", List.of("a", "b"), "a string list");
        ConfigValue<List<String>> stringListBounded = builder.stringListValue(
                "types.string.list.bounded", List.of("a"), 0, 4, "a bounded string list");
        ConfigValue<List<Integer>> intList =
                builder.intListValue("types.int.list", List.of(1, 2), "an integer list");
        ConfigValue<List<Long>> longList =
                builder.longListValue("types.long.list", List.of(3L), "a long list");
        ConfigValue<List<Double>> doubleList =
                builder.doubleListValue("types.double.list", List.of(1.0), "a decimal list");
        ConfigValue<List<Mode>> enumList =
                builder.enumListValue("types.enum.list", Mode.class, List.of(Mode.NORMAL),
                        "an enum list");

        List<String> keys = List.of(
                "types.boolean",
                "types.int",
                "types.int.bounded",
                "types.long",
                "types.long.bounded",
                "types.double",
                "types.double.bounded",
                "types.string",
                "types.string.bounded",
                "types.enum",
                "types.string.list",
                "types.string.list.bounded",
                "types.int.list",
                "types.long.list",
                "types.double.list",
                "types.enum.list");

        ConfigSpec spec = builder.build();

        assertEquals(MOD, spec.modId());
        assertEquals(Side.CLIENT, spec.side());
        assertEquals(7, spec.schemaVersion());
        assertEquals("every supported type", spec.description());
        assertEquals(keys.size(), spec.size());
        assertEquals(keys, spec.values().stream().map(ConfigValue::key).toList());
        assertEquals(keys, new ArrayList<>(spec.keySet()));
        assertEquals(new LinkedHashSet<>(keys), spec.keySet());

        assertSame(bool, spec.value("types.boolean"));
        assertSame(intBounded, spec.value("types.int.bounded"));
        assertSame(enumList, spec.value("types.enum.list"));

        assertEquals(true, bool.defaultValue());
        assertEquals("a boolean", bool.description());
        assertEquals("boolean", bool.typeName());

        assertEquals(5, intUnbounded.defaultValue());
        assertEquals("integer", intUnbounded.typeName());
        assertNull(intUnbounded.min());
        assertNull(intUnbounded.max());
        assertEquals(5, intBounded.defaultValue());
        assertEquals(0, intBounded.min().intValue());
        assertEquals(10, intBounded.max().intValue());
        assertEquals("integer in [0, 10]", intBounded.typeName());

        assertEquals(6L, longUnbounded.defaultValue());
        assertEquals("long", longUnbounded.typeName());
        assertNull(longUnbounded.min());
        assertNull(longUnbounded.max());
        assertEquals(6L, longBounded.defaultValue());
        assertEquals(0L, longBounded.min().longValue());
        assertEquals(10L, longBounded.max().longValue());
        assertEquals("long in [0, 10]", longBounded.typeName());

        assertEquals(1.5, doubleUnbounded.defaultValue());
        assertEquals("decimal", doubleUnbounded.typeName());
        assertNull(doubleUnbounded.min());
        assertNull(doubleUnbounded.max());
        assertEquals(1.5, doubleBounded.defaultValue());
        assertEquals(0.0, doubleBounded.min().doubleValue());
        assertEquals(2.0, doubleBounded.max().doubleValue());
        assertEquals("decimal in [0.0, 2.0]", doubleBounded.typeName());

        assertEquals("text", stringUnbounded.defaultValue());
        assertEquals("string", stringUnbounded.typeName());
        assertNull(stringUnbounded.minSize());
        assertNull(stringUnbounded.maxSize());
        assertEquals(1, stringBounded.minSize().intValue());
        assertEquals(8, stringBounded.maxSize().intValue());
        assertEquals("string with length in [1, 8]", stringBounded.typeName());

        assertEquals(Mode.NORMAL, enumValue.defaultValue());
        assertEquals(List.of("SLOW", "NORMAL", "FAST"), enumValue.allowedValues());
        assertTrue(enumValue.typeName().contains(Mode.class.getName()));
        assertNull(enumValue.min());
        assertNull(enumValue.max());

        assertEquals(List.of("a", "b"), stringList.defaultValue());
        assertEquals("list of string", stringList.typeName());
        assertNull(stringList.minSize());
        assertNull(stringList.maxSize());
        assertEquals(0, stringListBounded.minSize().intValue());
        assertEquals(4, stringListBounded.maxSize().intValue());
        assertEquals("list of string with size in [0, 4]", stringListBounded.typeName());

        assertEquals(List.of(1, 2), intList.defaultValue());
        assertEquals("list of integer", intList.typeName());
        assertEquals(List.of(3L), longList.defaultValue());
        assertEquals("list of long", longList.typeName());
        assertEquals(List.of(1.0), doubleList.defaultValue());
        assertEquals("list of decimal", doubleList.typeName());

        assertEquals(List.of(Mode.NORMAL), enumList.defaultValue());
        assertEquals(List.of("SLOW", "NORMAL", "FAST"), enumList.allowedValues());
        assertTrue(enumList.typeName().contains(Mode.class.getName()));
        assertTrue(enumList.typeName().startsWith("list of enum"));

        assertThrows(UnsupportedOperationException.class,
                () -> stringList.defaultValue().add("c"));
        assertThrows(UnsupportedOperationException.class, () -> stringList.get().add("c"));
        assertThrows(UnsupportedOperationException.class, () -> intList.defaultValue().add(3));
        assertThrows(UnsupportedOperationException.class, () -> enumList.get().add(Mode.FAST));

        stringList.set(List.of("c"));
        assertThrows(UnsupportedOperationException.class, () -> stringList.get().add("d"));

        List<String> mutableInput = new ArrayList<>(List.of("e"));
        stringList.set(mutableInput);
        mutableInput.add("f");
        assertEquals(List.of("e"), stringList.get());
        assertEquals(List.of("a", "b"), stringList.defaultValue());
    }

    @Test
    void rejectsInvalidKeys()
    {
        for (String key : List.of("has space", "two  spaces", "a..b", ".a", "a.", "", "a/b",
                "a:b", "a b.c"))
        {
            ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> builder.booleanValue(key, true, "description"),
                    () -> "key should be rejected: '" + key + "'");
            assertTrue(failure.getMessage().contains(key)
                            || failure.getMessage().contains("'" + key + "'"),
                    () -> "message should mention key: " + failure.getMessage());
        }

        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT);
        assertThrows(NullPointerException.class,
                () -> builder.booleanValue(null, true, "description"));
    }

    @Test
    void rejectsDuplicateKeys()
    {
        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT);
        builder.stringValue("general.value", "one", "first");

        ConfigException failure = assertThrows(ConfigException.class,
                () -> builder.stringValue("general.value", "two", "second"));
        assertTrue(failure.getMessage().contains("general.value"));
        assertTrue(failure.getMessage().contains(MOD));
    }

    @Test
    void rejectsInvalidModIds()
    {
        for (String modId : List.of("", "Bad", "bad id", "bad/id", "bad:id", "UPPER"))
        {
            assertThrows(IllegalArgumentException.class,
                    () -> ConfigBuilder.create(modId, Side.CLIENT),
                    () -> "mod id should be rejected: '" + modId + "'");
        }

        assertThrows(NullPointerException.class, () -> ConfigBuilder.create(null, Side.CLIENT));
        assertThrows(NullPointerException.class, () -> ConfigBuilder.create("ok", null));

        assertEquals("good.mod-1_2", ConfigBuilder.create("good.mod-1_2", Side.BOTH)
                .build().modId());
    }

    @Test
    void rejectsNullDescriptions()
    {
        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT);
        assertThrows(NullPointerException.class, () -> builder.description(null));

        assertThrows(NullPointerException.class,
                () -> builder.booleanValue("general.flag", true, null));
        assertThrows(NullPointerException.class,
                () -> builder.intValue("general.int", 1, null));
        assertThrows(NullPointerException.class,
                () -> builder.stringValue("general.string", "x", null));
        assertThrows(NullPointerException.class,
                () -> builder.stringListValue("general.list", List.of(), null));
    }

    @Test
    void rejectsInvalidBounds()
    {
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .intValue("general.int", 5, 10, 0, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .longValue("general.long", 5L, 10L, 0L, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .doubleValue("general.double", 1.0, 2.0, 1.0, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .doubleValue("general.double", 1.0, Double.NaN, 1.0, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .doubleValue("general.double", 1.0, 0.0, Double.POSITIVE_INFINITY, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .stringValue("general.string", "abc", -1, 3, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .stringValue("general.string", "abc", 3, 2, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .stringListValue("general.list", List.of(), -1, 2, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .intListValue("general.list", List.of(), 2, 1, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigBuilder.create(MOD, Side.CLIENT)
                .enumListValue("general.list", Mode.class, List.of(), -1, 2, "d"));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigBuilder.create(MOD, Side.CLIENT).schemaVersion(0));
    }

    @Test
    void rejectsNonEnumType()
    {
        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT);

        assertThrows(IllegalArgumentException.class,
                () -> declareEnum(builder, "general.enum", String.class, (Mode) null));
        assertThrows(NullPointerException.class,
                () -> builder.enumValue("general.enum", null, Mode.NORMAL, "d"));
    }

    @Test
    void rejectsInvalidDefaults()
    {
        assertValidationFailure("general.int",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .intValue("general.int", 11, 0, 10, "d"));
        assertValidationFailure("general.long",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .longValue("general.long", 11L, 0L, 10L, "d"));
        assertValidationFailure("general.double",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .doubleValue("general.double", 3.0, 0.0, 2.0, "d"));
        assertValidationFailure("general.double.finite",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .doubleValue("general.double.finite", Double.NaN, "d"));
        assertValidationFailure("general.string",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .stringValue("general.string", "too long", 1, 3, "d"));
        assertValidationFailure("general.string.null",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .stringValue("general.string.null", null, "d"));
        assertValidationFailure("general.enum",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .enumValue("general.enum", Mode.class, null, "d"));
        assertValidationFailure("general.list",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .stringListValue("general.list", List.of("a", "b", "c"), 0, 2, "d"));
        assertValidationFailure("general.list.null",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .stringListValue("general.list.null", Arrays.asList("a", null), "d"));
        assertValidationFailure("general.int.list",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .intListValue("general.int.list", List.of(1), 2, 4, "d"));
        assertValidationFailure("general.enum.list",
                () -> ConfigBuilder.create(MOD, Side.CLIENT)
                        .enumListValue("general.enum.list", Mode.class,
                                Arrays.asList(Mode.NORMAL, null), "d"));
    }

    @Test
    void configValueSetValidatesAndKeepsPreviousValue()
    {
        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT);
        ConfigValue<Integer> bounded = builder.intValue("value.int", 5, 0, 10, "d");
        ConfigValue<String> text = builder.stringValue("value.text", "abc", 1, 5, "d");
        ConfigValue<Mode> mode = builder.enumValue("value.enum", Mode.class, Mode.NORMAL, "d");
        ConfigValue<List<Integer>> ints =
                builder.intListValue("value.int.list", List.of(1, 2), 1, 4, "d");
        ConfigSpec spec = builder.build();

        bounded.set(7);
        assertEquals(7, bounded.get());
        text.set("hello");
        assertEquals("hello", text.get());
        mode.set(Mode.FAST);
        assertEquals(Mode.FAST, mode.get());
        ints.set(List.of(3, 4, 5));
        assertEquals(List.of(3, 4, 5), ints.get());
        assertThrows(UnsupportedOperationException.class, () -> ints.get().add(6));

        ConfigValidationException typeFailure = assertThrows(ConfigValidationException.class,
                () -> raw(spec, "value.int").set("wrong"));
        assertEquals(MOD, typeFailure.modId());
        assertEquals("value.int", typeFailure.key());
        assertTrue(typeFailure.reason().contains("expected integer"));
        assertEquals(7, bounded.get());

        assertValidationSetFailure(bounded, 11, "value.int", 7);
        assertValidationSetFailure(bounded, -1, "value.int", 7);
        assertValidationSetFailure(text, "toolong", "value.text", "hello");
        assertValidationSetFailure(text, "", "value.text", "hello");
        assertValidationSetFailure(ints, List.of(), "value.int.list", List.of(3, 4, 5));
        assertValidationSetFailure(ints, List.of(1, 2, 3, 4, 5), "value.int.list",
                List.of(3, 4, 5));

        ConfigValidationException enumFailure = assertThrows(ConfigValidationException.class,
                () -> raw(spec, "value.enum").set("ULTRA"));
        assertEquals(MOD, enumFailure.modId());
        assertEquals("value.enum", enumFailure.key());
        assertTrue(enumFailure.reason().contains("expected enum"));
        assertEquals(Mode.FAST, mode.get());

        ConfigValidationException elementFailure = assertThrows(ConfigValidationException.class,
                () -> raw(spec, "value.int.list").set(List.of("not-an-integer")));
        assertEquals("element 0", elementFailure.reason().substring(0, 9));
        assertEquals(List.of(3, 4, 5), ints.get());

        ConfigValidationException nullElementFailure =
                assertThrows(ConfigValidationException.class,
                        () -> raw(spec, "value.int.list").set(Arrays.asList(1, null)));
        assertTrue(nullElementFailure.reason().contains("element 1"));
        assertTrue(nullElementFailure.reason().contains("null"));
        assertEquals(List.of(3, 4, 5), ints.get());

        assertThrows(NullPointerException.class, () -> bounded.set(null));
        assertEquals(7, bounded.get());
        assertThrows(NullPointerException.class, () -> ints.set(null));
        assertEquals(List.of(3, 4, 5), ints.get());
    }

    @Test
    void specLookupSupportsKeysFindAndTypeChecks()
    {
        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT);
        ConfigValue<Integer> integer = builder.intValue("lookup.int", 4, "d");
        ConfigValue<String> string = builder.stringValue("lookup.string", "text", "d");
        ConfigValue<Mode> enumValue = builder.enumValue("lookup.enum", Mode.class, Mode.SLOW, "d");
        ConfigValue<List<String>> list =
                builder.stringListValue("lookup.list", List.of("a"), "d");
        ConfigSpec spec = builder.build();

        assertSame(integer, spec.value("lookup.int"));
        assertSame(string, spec.value("lookup.string"));
        assertSame(enumValue, spec.value("lookup.enum"));
        assertSame(list, spec.value("lookup.list"));

        ConfigException missing = assertThrows(ConfigException.class,
                () -> spec.value("lookup.missing"));
        assertTrue(missing.getMessage().contains("lookup.missing"));
        assertTrue(missing.getMessage().contains("lookup.int"));
        assertTrue(missing.getMessage().contains("lookup.string"));

        assertTrue(spec.find("lookup.int").isPresent());
        assertSame(integer, spec.find("lookup.int").orElseThrow());
        assertTrue(spec.find("lookup.missing").isEmpty());

        assertSame(integer, spec.value("lookup.int", Integer.class));
        assertSame(string, spec.value("lookup.string", String.class));
        assertSame(enumValue, spec.value("lookup.enum", Mode.class));
        ConfigValue<?> typedList = spec.value("lookup.list", List.class);
        assertSame(list, typedList);

        ConfigException mismatch = assertThrows(ConfigException.class,
                () -> spec.value("lookup.int", String.class));
        assertTrue(mismatch.getMessage().contains("lookup.int"));
        assertTrue(mismatch.getMessage().contains("String"));
        assertThrows(ConfigException.class, () -> spec.value("lookup.missing", Integer.class));
        assertThrows(ConfigException.class, () -> spec.value("lookup.enum", Other.class));
    }

    @Test
    void builderIsFrozenAfterBuild()
    {
        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT);
        ConfigValue<String> value = builder.stringValue("frozen.value", "text", "d");
        ConfigSpec spec = builder.build();

        assertSame(spec, builder.build());
        assertSame(value, spec.value("frozen.value"));

        assertThrows(IllegalStateException.class, () -> builder.schemaVersion(2));
        assertThrows(IllegalStateException.class, () -> builder.description("changed"));
        assertThrows(IllegalStateException.class, () -> builder.migration(migration(1, data -> {
        })));
        assertThrows(IllegalStateException.class,
                () -> builder.booleanValue("frozen.boolean", true, "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.intValue("frozen.int", 1, "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.intValue("frozen.int.bounded", 1, 0, 2, "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.longValue("frozen.long", 1L, "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.doubleValue("frozen.double", 1.0, "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.stringValue("frozen.string", "x", "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.enumValue("frozen.enum", Mode.class, Mode.SLOW, "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.stringListValue("frozen.string.list", List.of(), "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.intListValue("frozen.int.list", List.of(), "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.longListValue("frozen.long.list", List.of(), "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.doubleListValue("frozen.double.list", List.of(), "d"));
        assertThrows(IllegalStateException.class,
                () -> builder.enumListValue("frozen.enum.list", Mode.class, List.of(), "d"));
    }

    @Test
    void duplicateMigrationVersionIsRejectedAtBuild()
    {
        ConfigBuilder builder = ConfigBuilder.create(MOD, Side.CLIENT)
                .schemaVersion(2)
                .migration(migration(1, data -> {
                }))
                .migration(migration(1, data -> {
                }));

        ConfigException failure = assertThrows(ConfigException.class, builder::build);
        assertTrue(failure.getMessage().contains("Duplicate"));
        assertTrue(failure.getMessage().contains("1"));
    }

    @Test
    void migrationsAreSortedByFromVersion()
    {
        ConfigSpec spec = ConfigBuilder.create(MOD, Side.CLIENT)
                .schemaVersion(3)
                .migration(migration(2, data -> {
                }))
                .migration(migration(1, data -> {
                }))
                .build();

        assertEquals(List.of(1, 2),
                spec.migrations().stream().map(ConfigMigration::fromVersion).toList());
    }

    private static void assertValidationFailure(String key, Runnable action)
    {
        ConfigValidationException failure =
                assertThrows(ConfigValidationException.class, action::run);
        assertEquals(MOD, failure.modId());
        assertEquals(key, failure.key());
        assertTrue(failure.getMessage().contains(MOD));
        assertTrue(failure.getMessage().contains(key));
        assertNotNull(failure.reason());
    }

    private static <T> void assertValidationSetFailure(ConfigValue<T> value, T candidate,
                                                       String key, T previous)
    {
        ConfigValidationException failure =
                assertThrows(ConfigValidationException.class, () -> value.set(candidate));
        assertEquals(MOD, failure.modId());
        assertEquals(key, failure.key());
        assertTrue(failure.getMessage().contains(key));
        assertEquals(previous, value.get());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ConfigValue raw(ConfigSpec spec, String key)
    {
        ConfigValue<?> value = spec.value(key);
        assertInstanceOf(ConfigValue.class, value);
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <E extends Enum<E>> void declareEnum(ConfigBuilder builder, String key,
                                                        Class<?> type, E defaultValue)
    {
        builder.enumValue(key, (Class<E>) type, defaultValue, "d");
    }

    private static ConfigMigration migration(int fromVersion, Consumer<ConfigData> action)
    {
        return new ConfigMigration()
        {
            @Override
            public int fromVersion()
            {
                return fromVersion;
            }

            @Override
            public void migrate(ConfigData data)
            {
                action.accept(data);
            }
        };
    }
}
