package net.weaver.api.registry;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.registry.event.DataRegistryFreezeEvent;
import net.weaver.api.registry.event.DataRegistryRegisterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistryTest
{
    private record Gem(String color, int hardness)
    {
    }

    private final List<ListenerHandle> handles = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        DataRegistryManager.reset();
        handles.clear();
    }

    @AfterEach
    void tearDown()
    {
        for (ListenerHandle handle : handles)
        {
            handle.unregister();
        }
        handles.clear();
        DataRegistryManager.reset();
    }

    @Test
    void dataDrivenLookupLoadsEntriesAndTags()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:gems");
        DataPackSnapshot snapshot = DataPackSnapshot.builder("pack", 10)
                .entry("datagenproof:gems", "ruby", gemData("red", 7))
                .entry("datagenproof:gems", "sapphire", gemData("blue", 9))
                .tag("datagenproof:gems", "datagenproof:shiny",
                        new DataPackSnapshot.TagDeclaration(List.of(
                                DataPackSnapshot.TagValue.value("datagenproof:ruby", true),
                                DataPackSnapshot.TagValue.value("datagenproof:sapphire", true)),
                                10, false))
                .build();

        DataRegistryManager.ApplyReport report = DataRegistryManager.apply(snapshot);

        assertEquals(4, report.added());
        assertTrue(report.problems().isEmpty());
        assertEquals(2, registry.size());
        assertEquals(new Gem("red", 7), registry.get("datagenproof:ruby"));
        assertEquals(new Gem("blue", 9), registry.get("datagenproof:sapphire"));

        DataRegistryKey<Gem> rubyKey =
                DataRegistryKey.of(registry.registryKey(), "datagenproof:ruby", Gem.class);
        assertTrue(registry.keySet().contains(rubyKey));
        assertTrue(registry.values().contains(new Gem("red", 7)));
        assertEquals(2, registry.entries().size());
        assertEquals("datagenproof:ruby", registry.entries().get(0).id());
        assertEquals("datagenproof:ruby", registry.entries().get(0).key().id());

        TagKey<Gem> shiny = TagKey.of(registry.registryKey(), "datagenproof:shiny");
        TagView<Gem> view = registry.tag(shiny);
        assertEquals(2, view.size());
        assertEquals(List.of(new Gem("red", 7), new Gem("blue", 9)), view.values());
    }

    @Test
    void tagFollowsAnEntryReplacedByAHigherPriorityPack()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:live_tags");
        DataPackSnapshot low = DataPackSnapshot.builder("low", 0)
                .entry("datagenproof:live_tags", "ruby", gemData("red", 7))
                .tag("datagenproof:live_tags", "datagenproof:shiny", shinyTag(0, "ruby"))
                .build();
        DataPackSnapshot high = DataPackSnapshot.builder("high", 1)
                .entry("datagenproof:live_tags", "ruby", gemData("crimson", 8))
                .build();

        DataRegistryManager.loadAll(List.of(low, high));

        TagView<Gem> view = registry.tag(TagKey.of(registry.registryKey(), "datagenproof:shiny"));
        assertEquals(List.of(new Gem("crimson", 8)), view.values());
        assertTrue(view.contains(registry.get("datagenproof:ruby")));
    }

    @Test
    void tagInALowerPackCanNameAnEntryFromAHigherPack()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:cross_pack");
        DataPackSnapshot low = DataPackSnapshot.builder("low", 0)
                .tag("datagenproof:cross_pack", "datagenproof:shiny", shinyTag(0, "sapphire"))
                .build();
        DataPackSnapshot high = DataPackSnapshot.builder("high", 1)
                .entry("datagenproof:cross_pack", "sapphire", gemData("blue", 9))
                .build();

        DataRegistryManager.ApplyReport report = DataRegistryManager.loadAll(List.of(low, high));

        TagView<Gem> view = registry.tag(TagKey.of(registry.registryKey(), "datagenproof:shiny"));
        assertEquals(List.of(new Gem("blue", 9)), view.values());
        assertTrue(report.problems().isEmpty(), String.valueOf(report.problems()));
    }

    @Test
    void reloadReplacesEditedEntriesAndTagsInsteadOfStacking()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:reload");
        DataPackSnapshot first = DataPackSnapshot.builder("pack", 5)
                .entry("datagenproof:reload", "ruby", gemData("red", 7))
                .entry("datagenproof:reload", "opal", gemData("white", 5))
                .tag("datagenproof:reload", "datagenproof:shiny", shinyTag(5, "ruby", "opal"))
                .build();
        DataRegistryManager.loadAll(List.of(first));
        RegistryEntry<Gem> rubyHandle = registry.entries().get(0);

        // The pack is edited: ruby's hardness changes and opal leaves the tag.
        DataPackSnapshot edited = DataPackSnapshot.builder("pack", 5)
                .entry("datagenproof:reload", "ruby", gemData("red", 9))
                .entry("datagenproof:reload", "opal", gemData("white", 5))
                .tag("datagenproof:reload", "datagenproof:shiny", shinyTag(5, "ruby"))
                .build();
        DataRegistryManager.reloadAll(List.of(edited));
        DataRegistryManager.reloadAll(List.of(edited));

        assertEquals(new Gem("red", 9), registry.get("datagenproof:ruby"));
        assertSame(rubyHandle, registry.entries().get(0), "reload keeps entry handles");
        TagView<Gem> view = registry.tag(TagKey.of(registry.registryKey(), "datagenproof:shiny"));
        assertEquals(List.of(new Gem("red", 9)), view.values());
    }

    private static DataPackSnapshot.TagDeclaration shinyTag(int priority, String... paths)
    {
        List<DataPackSnapshot.TagValue> values = new ArrayList<>();
        for (String path : paths)
        {
            values.add(DataPackSnapshot.TagValue.value("datagenproof:" + path, true));
        }
        return new DataPackSnapshot.TagDeclaration(values, priority, false);
    }

    @Test
    void packPriorityHigherWinsAndEqualPriorityDuplicates()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:priority");
        DataPackSnapshot low = DataPackSnapshot.builder("low", 5)
                .entry("datagenproof:priority", "ruby", gemData("blue", 3))
                .build();
        DataPackSnapshot high = DataPackSnapshot.builder("high", 10)
                .entry("datagenproof:priority", "ruby", gemData("red", 7))
                .build();

        DataRegistryManager.ApplyReport merged =
                DataRegistryManager.loadAll(List.of(high, low));

        assertEquals(1, merged.added());
        assertTrue(merged.replaced() >= 1);
        assertEquals(new Gem("red", 7), registry.get("datagenproof:ruby"));

        DataRegistryManager.ApplyReport again = DataRegistryManager.apply(high);

        assertTrue(again.duplicates() >= 1);
        assertEquals(new Gem("red", 7), registry.get("datagenproof:ruby"));
        assertEquals(1, registry.size());
    }

    @Test
    void programmaticEntriesCannotBeShadowed()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:programmatic_registry");
        Gem programmatic = new Gem("green", 10);
        handles.add(EventBus.GAME.addListener(DataRegistryRegisterEvent.class, event ->
        {
            if (event.registryId().equals(registry.id()))
            {
                event.register("datagenproof:programmatic", programmatic);
            }
        }));

        DataRegistryManager.beginRegistration();

        assertSame(programmatic, registry.get("datagenproof:programmatic"));

        DataPackSnapshot snapshot = DataPackSnapshot.builder("big", 1000000)
                .entry("datagenproof:programmatic_registry", "programmatic",
                        gemData("black", 1))
                .build();

        DataRegistryManager.ApplyReport report = DataRegistryManager.apply(snapshot);

        assertTrue(report.skipped() >= 1);
        assertSame(programmatic, registry.get("datagenproof:programmatic"));
        assertEquals(new Gem("green", 10), registry.get("datagenproof:programmatic"));
        assertThrows(RegistryException.class,
                () -> registry.register("datagenproof:programmatic", new Gem("white", 2)));
    }

    @Test
    void registerEventIsPostedOncePerRegistryAcrossRepeatedPhases()
    {
        DataRegistry<Gem> first = createGems("datagenproof:phase_first");
        List<String> posted = new ArrayList<>();
        handles.add(EventBus.GAME.addListener(DataRegistryRegisterEvent.class, event ->
        {
            if (event.registryId().startsWith("datagenproof:phase_"))
            {
                posted.add(event.registryId());
            }
        }));

        // A mod opens the phase from onInitialize, then the loader opens it again
        // during the registry bootstrap.
        DataRegistryManager.beginRegistration();
        DataRegistryManager.beginRegistration();
        assertEquals(List.of(first.id()), posted);

        DataRegistry<Gem> second = createGems("datagenproof:phase_second");
        DataRegistryManager.beginRegistration();
        assertEquals(List.of(first.id(), second.id()), posted);
    }

    @Test
    void duplicateEntriesAndRegistriesAreRejected()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:duplicates");
        DataPackSnapshot first = DataPackSnapshot.builder("first", 9)
                .entry("datagenproof:duplicates", "ruby", gemData("red", 7))
                .build();
        DataPackSnapshot second = DataPackSnapshot.builder("second", 9)
                .entry("datagenproof:duplicates", "ruby", gemData("blue", 8))
                .build();

        DataRegistryManager.ApplyReport report =
                DataRegistryManager.loadAll(List.of(first, second));

        assertEquals(1, report.added());
        assertEquals(1, report.duplicates());
        assertEquals(new Gem("red", 7), registry.get("datagenproof:ruby"));

        assertThrows(RegistryException.class,
                () -> createGems("datagenproof:duplicates"));
        assertThrows(IllegalArgumentException.class,
                () -> createGems("Bad Namespace:gems"));
        assertThrows(IllegalArgumentException.class,
                () -> createGems("datagenproof:BadPath"));
    }

    @Test
    void tagsMergeByPriorityAndDedupe()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:tag_merge");
        TagKey<Gem> tag = TagKey.of(registry.registryKey(), "datagenproof:valuable");
        Gem ruby = new Gem("red", 7);
        Gem sapphire = new Gem("blue", 9);
        Gem emerald = new Gem("green", 8);

        registry.addTag(tag, List.of(ruby, sapphire), 10, "high");
        registry.addTag(tag, List.of(sapphire, emerald), 5, "low");

        TagView<Gem> view = registry.tag(tag);

        assertEquals(List.of(ruby, sapphire, emerald), view.values());
        assertEquals(3, view.size());
    }

    @Test
    void tagReferencesExpandAcrossTags()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:tag_references");
        TagKey<Gem> base = TagKey.of(registry.registryKey(), "datagenproof:base");
        TagKey<Gem> derived = TagKey.of(registry.registryKey(), "datagenproof:derived");
        Gem ruby = new Gem("red", 7);
        Gem sapphire = new Gem("blue", 9);

        registry.addTag(base, List.of(ruby, sapphire), 5, "base");
        registry.addTag(derived, List.of(new Gem("green", 8)), 5, "derived");
        registry.tags().addReference(derived, base, 6, "derived");

        TagView<Gem> view = registry.tag(derived);

        assertEquals(3, view.size());
        assertTrue(view.contains(ruby));
        assertTrue(view.contains(sapphire));
        assertTrue(view.contains(new Gem("green", 8)));
    }

    @Test
    void tagReplaceDropsLowerPriorityAndSamePriorityMerges()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:tag_replace");
        TagKey<Gem> tag = TagKey.of(registry.registryKey(), "datagenproof:chosen");
        Gem low = new Gem("gray", 1);
        Gem middle = new Gem("blue", 5);
        Gem replacement = new Gem("red", 7);
        Gem samePriority = new Gem("green", 8);

        registry.addTag(tag, List.of(low), 5, "low");
        registry.addTag(tag, List.of(middle), 10, "middle");
        registry.tags().replace(tag, List.of(replacement), 20, "replacement");
        registry.addTag(tag, List.of(samePriority), 20, "same_priority");

        TagView<Gem> view = registry.tag(tag);

        assertEquals(List.of(replacement, samePriority), view.values());
        assertFalse(view.contains(low));
        assertFalse(view.contains(middle));
    }

    @Test
    void repeatedTagResolveIsStable()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:tag_stable");
        TagKey<Gem> tag = TagKey.of(registry.registryKey(), "datagenproof:stable");
        registry.addTag(tag, List.of(new Gem("red", 7), new Gem("blue", 9)), 5, "test");

        TagView<Gem> first = registry.tag(tag);
        TagView<Gem> second = registry.tag(tag);

        assertEquals(first.values(), second.values());
        assertEquals(2, first.size());
        assertEquals(2, second.size());
    }

    @Test
    void freezeRejectsWritesAndReportsProblems()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:frozen");
        registry.register("datagenproof:ruby", new Gem("red", 7));
        AtomicInteger freezeEvents = new AtomicInteger();
        AtomicReference<Boolean> frozenFlag = new AtomicReference<>();
        handles.add(EventBus.GAME.addListener(DataRegistryFreezeEvent.class, event ->
        {
            if (event.registryId().equals(registry.id()))
            {
                freezeEvents.incrementAndGet();
                frozenFlag.set(event.frozen());
            }
        }));

        DataRegistryManager.FreezeReport report = DataRegistryManager.freezeAll();

        assertTrue(registry.isFrozen());
        assertTrue(registry.tags().isFrozen());
        assertTrue(report.isComplete());
        assertEquals(1, report.frozenCount());
        assertEquals(List.of(registry.id()), report.frozenIds());
        assertEquals(1, freezeEvents.get());
        assertEquals(Boolean.TRUE, frozenFlag.get());
        assertThrows(RegistryException.class,
                () -> registry.register("datagenproof:late", new Gem("blue", 9)));
        assertThrows(RegistryException.class,
                () -> registry.addTag(TagKey.of(registry.registryKey(), "datagenproof:late_tag"),
                        List.of(new Gem("blue", 9)), 1, "late"));

        int sizeBefore = registry.size();
        DataPackSnapshot snapshot = DataPackSnapshot.builder("late", 5)
                .entry("datagenproof:frozen", "sapphire", gemData("blue", 9))
                .build();

        DataRegistryManager.ApplyReport applied = DataRegistryManager.apply(snapshot);

        assertTrue(applied.problems().stream().anyMatch(problem -> problem.contains("frozen")));
        assertEquals(sizeBefore, registry.size());
        assertNull(registry.get("datagenproof:sapphire"));
    }

    @Test
    void reloadRefusesFrozenRegistriesAndAppliesToOpenOnes()
    {
        DataRegistry<Gem> frozen = createGems("datagenproof:reload_frozen");
        DataRegistry<Gem> open = createGems("datagenproof:reload_open");
        frozen.freeze();

        DataPackSnapshot snapshot = DataPackSnapshot.builder("reload", 50)
                .entry("datagenproof:reload_frozen", "ruby", gemData("red", 7))
                .entry("datagenproof:reload_open", "sapphire", gemData("blue", 9))
                .tag("datagenproof:reload_frozen", "datagenproof:reload_tag",
                        new DataPackSnapshot.TagDeclaration(List.of(
                                DataPackSnapshot.TagValue.value(
                                        "datagenproof:ruby", true)), 50, false))
                .build();

        DataRegistryManager.ReloadReport report =
                DataRegistryManager.reloadAll(List.of(snapshot));

        assertEquals(1, report.frozenCount());
        assertEquals(List.of(frozen.id()), report.frozenIds());
        assertTrue(report.openIds().contains(open.id()));
        assertFalse(report.isComplete());
        assertTrue(report.problems().stream().anyMatch(problem ->
                        problem.contains("frozen") && problem.contains(frozen.id())),
                () -> report.problems().toString());
        assertTrue(frozen.isFrozen());
        assertEquals(0, frozen.size());
        assertNull(frozen.get("datagenproof:ruby"));
        assertEquals(1, open.size());
        assertEquals(new Gem("blue", 9), open.get("datagenproof:sapphire"));
        assertEquals(1, report.apply().added());
    }

    @Test
    void registryEventsReportIdsAndAllowRegistration()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:events");
        AtomicInteger registerEvents = new AtomicInteger();
        AtomicInteger freezeEvents = new AtomicInteger();
        List<String> registerIds = new ArrayList<>();
        List<String> freezeIds = new ArrayList<>();
        handles.add(EventBus.GAME.addListener(DataRegistryRegisterEvent.class, event ->
        {
            registerEvents.incrementAndGet();
            registerIds.add(event.registryId());
            event.register("datagenproof:event_gem", new Gem("gold", 3));
        }));
        handles.add(EventBus.GAME.addListener(DataRegistryFreezeEvent.class, event ->
        {
            freezeEvents.incrementAndGet();
            freezeIds.add(event.registryId());
        }));

        DataRegistryManager.beginRegistration();

        assertTrue(DataRegistryManager.isRegistrationPhase());
        assertEquals(1, registerEvents.get());
        assertEquals(List.of(registry.id()), registerIds);
        assertEquals(new Gem("gold", 3), registry.get("datagenproof:event_gem"));
        assertFalse(registry.isFrozen());

        DataRegistryManager.freezeAll();

        assertEquals(1, freezeEvents.get());
        assertEquals(List.of(registry.id()), freezeIds);
        assertTrue(registry.isFrozen());
    }

    @Test
    void unknownRegistryIsReportedWithoutBlockingOthers()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:present");
        DataPackSnapshot snapshot = DataPackSnapshot.builder("mixed", 5)
                .entry("datagenproof:missing", "ghost", gemData("white", 1))
                .entry("datagenproof:present", "ruby", gemData("red", 7))
                .build();

        DataRegistryManager.ApplyReport report = DataRegistryManager.apply(snapshot);

        assertTrue(report.problems().stream().anyMatch(problem ->
                problem.contains("unknown data registry")
                        && problem.contains("datagenproof:missing")));
        assertEquals(1, registry.size());
        assertEquals(new Gem("red", 7), registry.get("datagenproof:ruby"));
    }

    @Test
    void tagReferenceCyclesTerminate()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:tag_cycles");
        TagKey<Gem> self = TagKey.of(registry.registryKey(), "datagenproof:self");
        TagKey<Gem> left = TagKey.of(registry.registryKey(), "datagenproof:left");
        TagKey<Gem> right = TagKey.of(registry.registryKey(), "datagenproof:right");
        Gem ruby = new Gem("red", 7);
        Gem sapphire = new Gem("blue", 9);

        registry.addTag(self, List.of(ruby), 5, "self");
        registry.tags().addReference(self, self, 4, "self");
        registry.addTag(left, List.of(ruby), 5, "left");
        registry.addTag(right, List.of(sapphire), 5, "right");
        registry.tags().addReference(left, right, 6, "left");
        registry.tags().addReference(right, left, 6, "right");

        TagView<Gem> selfView = registry.tag(self);
        TagView<Gem> leftView = registry.tag(left);
        TagView<Gem> rightView = registry.tag(right);

        assertEquals(List.of(ruby), selfView.values());
        assertEquals(2, leftView.size());
        assertEquals(2, rightView.size());
        assertTrue(leftView.contains(ruby));
        assertTrue(leftView.contains(sapphire));
        assertTrue(rightView.contains(ruby));
        assertTrue(rightView.contains(sapphire));
        assertNotNull(leftView.values());
    }

    @Test
    void typeSafetyRejectsForeignKeys()
    {
        DataRegistry<Gem> registry = createGems("datagenproof:types");
        Gem ruby = new Gem("red", 7);
        RegistryKey<Gem> foreignRegistry = RegistryKey.of("datagenproof", "types_foreign");
        DataRegistryKey<Gem> foreignKey =
                DataRegistryKey.of(foreignRegistry, "datagenproof:ruby", Gem.class);

        assertThrows(IllegalArgumentException.class, () -> registry.register(foreignKey, ruby));

        @SuppressWarnings({"unchecked", "rawtypes"})
        DataRegistryKey<Gem> wrongTypeKey = (DataRegistryKey) DataRegistryKey.of(
                registry.registryKey(), "datagenproof:ruby", String.class);

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(wrongTypeKey, ruby));
        assertThrows(IllegalArgumentException.class, () -> registry.get(foreignKey));

        DataRegistryKey<Gem> rubyKey = DataRegistryKey.of(
                registry.registryKey(), "datagenproof:ruby", Gem.class);
        DataRegistryKey<Gem> sameKey = DataRegistryKey.of(
                registry.registryKey(), "datagenproof:ruby", Gem.class);
        DataRegistryKey<Gem> laterKey = DataRegistryKey.of(
                registry.registryKey(), "datagenproof:sapphire", Gem.class);
        assertEquals(0, rubyKey.compareTo(sameKey));
        assertTrue(rubyKey.compareTo(laterKey) < 0);
        assertTrue(laterKey.compareTo(rubyKey) > 0);
    }

    private static DataRegistry<Gem> createGems(String registryId)
    {
        return DataRegistryManager.create(registryId, Gem.class,
                (entryId, data) -> new Gem(
                        String.valueOf(data.get("color")),
                        ((Number) data.get("hardness")).intValue()));
    }

    private static Map<String, Object> gemData(String color, double hardness)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("color", color);
        data.put("hardness", hardness);
        return data;
    }
}
