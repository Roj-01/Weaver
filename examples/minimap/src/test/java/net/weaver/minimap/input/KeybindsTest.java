package net.weaver.minimap.input;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeybindsTest
{
    @Test
    void exposesElevenUniqueActions()
    {
        List<KeybindSpec> specs = Keybinds.all();

        assertEquals(11, specs.size());
        assertEquals(11, specs.stream().map(KeybindSpec::id).distinct().count());
        assertEquals(Keybinds.OPEN_MAP, specs.get(0).id());
        assertEquals(Keybinds.OPEN_WAYPOINTS, specs.get(10).id());
        assertEquals(MinimapKeys.KEY_M, specs.get(0).defaultKey());
        assertEquals(MinimapKeys.KEY_N, specs.get(10).defaultKey());
        assertTrue(Keybinds.conflicts().isEmpty());
    }

    @Test
    void defaultBindingsCoverTheExpectedKeys()
    {
        assertEquals(MinimapKeys.KEY_M, byId(Keybinds.OPEN_MAP).defaultKey());
        assertEquals(MinimapKeys.KEY_O, byId(Keybinds.OPEN_SETTINGS).defaultKey());
        assertEquals(MinimapKeys.KEY_B, byId(Keybinds.ADD_WAYPOINT).defaultKey());
        assertEquals(MinimapKeys.KEY_H, byId(Keybinds.TOGGLE_HUD).defaultKey());
        assertEquals(MinimapKeys.KEY_RIGHT_BRACKET, byId(Keybinds.ZOOM_IN).defaultKey());
        assertEquals(MinimapKeys.KEY_LEFT_BRACKET, byId(Keybinds.ZOOM_OUT).defaultKey());
        assertEquals(MinimapKeys.KEY_J, byId(Keybinds.CYCLE_CAVE).defaultKey());
        assertEquals(MinimapKeys.KEY_K, byId(Keybinds.TOGGLE_ENTITIES).defaultKey());
        assertEquals(MinimapKeys.KEY_L, byId(Keybinds.TOGGLE_WAYPOINT_LABELS).defaultKey());
        assertEquals(MinimapKeys.KEY_P, byId(Keybinds.HIDE_HUD_TEMP).defaultKey());
        assertEquals(MinimapKeys.KEY_N, byId(Keybinds.OPEN_WAYPOINTS).defaultKey());
        assertEquals(295, MinimapKeys.KEY_F6);
    }

    @Test
    void conflictsFindsDuplicateSpecs()
    {
        KeybindSpec first = new KeybindSpec("first", "key.first", MinimapKeys.KEY_H, false,
                "minimap");
        KeybindSpec second = new KeybindSpec("second", "key.second", MinimapKeys.KEY_H, false,
                "minimap");
        KeybindSpec third = new KeybindSpec("third", "key.third", MinimapKeys.KEY_J, false,
                "minimap");

        List<Keybinds.KeyConflict> conflicts = Keybinds.conflicts(List.of(first, second, third));

        assertEquals(1, conflicts.size());
        assertEquals(MinimapKeys.KEY_H, conflicts.get(0).keyCode());
        assertEquals(List.of("first", "second"), conflicts.get(0).ids());
        assertTrue(Keybinds.hasConflicts(List.of(first, second, third)));
        assertFalse(Keybinds.hasConflicts(List.of(first, third)));
    }

    @Test
    void keyboardAndMouseCodesDoNotConflict()
    {
        KeybindSpec keyboard = new KeybindSpec("keyboard", "key.keyboard", 5, false, "minimap");
        KeybindSpec mouse = new KeybindSpec("mouse", "key.mouse", 5, true, "minimap");

        assertTrue(Keybinds.conflicts(List.of(keyboard, mouse)).isEmpty());
    }

    @Test
    void conflictsFindsDuplicateRawCodes()
    {
        List<Keybinds.KeyConflict> conflicts = Keybinds.conflicts(
                new int[]{MinimapKeys.KEY_M, MinimapKeys.KEY_B, MinimapKeys.KEY_M});

        assertEquals(1, conflicts.size());
        assertEquals(MinimapKeys.KEY_M, conflicts.get(0).keyCode());
        assertEquals(List.of("#0", "#2"), conflicts.get(0).ids());
        assertTrue(Keybinds.conflicts(new int[]{1, 2, 3}).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Keybinds.conflicts(new int[]{-1}));
    }

    @Test
    void specsCarryTranslationAndCategoryMetadata()
    {
        KeybindSpec spec = byId(Keybinds.OPEN_MAP);

        assertEquals("key.weaver-minimap.open_map", spec.translationKey());
        assertEquals(Keybinds.CATEGORY, spec.category());
        assertFalse(spec.mouse());
        assertTrue(Keybinds.all().stream()
                .allMatch(bind -> bind.translationKey().startsWith("key.weaver-minimap.")));
        assertTrue(Keybinds.all().stream()
                .allMatch(bind -> bind.category().equals(Keybinds.CATEGORY)));
    }

    @Test
    void unknownActionIdReturnsEmpty()
    {
        Optional<KeybindSpec> missing = Keybinds.byId("missing");
        assertTrue(missing.isEmpty());
        assertNotNull(Keybinds.byId(Keybinds.OPEN_MAP).orElse(null));
    }

    private static KeybindSpec byId(String id)
    {
        return Keybinds.byId(id).orElseThrow();
    }
}
