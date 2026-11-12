package net.weaver.minimap.ui.theme;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThemeManagerTest
{
    @Test
    void setThemeIdNotifiesEveryListener()
    {
        ThemeManager manager = new ThemeManager();
        List<ThemeId> seen = new ArrayList<>();
        manager.addListener(theme -> seen.add(theme.id()));
        manager.setThemeId("emerald");
        manager.setThemeId("EMERALD");
        assertEquals(List.of(ThemeId.EMERALD, ThemeId.EMERALD), seen);
    }

    @Test
    void accentOverrideKeepsTheThemeAndCanBeReset()
    {
        ThemeManager manager = new ThemeManager();
        manager.setThemeId("midnight");
        manager.setAccent(0xFF00FF00);
        assertEquals(ThemeId.MIDNIGHT, manager.themeId());
        assertEquals(0xFF00FF00, manager.current().accent());
        assertEquals(0xFF00FF00, manager.current().player());

        manager.reset();
        assertEquals(ThemeId.DARK, manager.themeId());
        assertEquals(Themes.byId(ThemeId.DARK).accent(), manager.current().accent());
        assertNull(manager.accentOverride());
    }

    @Test
    void changingTheThemeDropsTheAccentOverride()
    {
        ThemeManager manager = new ThemeManager();
        manager.setAccent(0xFFFF0000);
        manager.setThemeId("amber");
        assertNull(manager.accentOverride());
        assertEquals(Themes.byId(ThemeId.AMBER).accent(), manager.current().accent());
    }

    @Test
    void failingListenerIsIsolated()
    {
        ThemeManager manager = new ThemeManager();
        List<String> seen = new ArrayList<>();
        manager.addListener(theme ->
        {
            throw new IllegalStateException("boom");
        });
        manager.addListener(theme -> seen.add(theme.id().name()));
        manager.setThemeId("light");
        assertEquals(List.of("LIGHT"), seen);
        assertTrue(manager.current().id() == ThemeId.LIGHT);
    }
}
