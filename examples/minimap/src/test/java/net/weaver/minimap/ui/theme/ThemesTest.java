package net.weaver.minimap.ui.theme;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThemesTest
{
    @Test
    void unknownNameFallsBackToDark()
    {
        assertSame(ThemeId.DARK, ThemeId.byName("does-not-exist"));
        assertSame(ThemeId.DARK, ThemeId.byName(null));
        assertSame(ThemeId.DARK, Themes.byId("does-not-exist").id());
        assertSame(ThemeId.DARK, Themes.byId((String) null).id());
    }

    @Test
    void nameLookupIsCaseInsensitive()
    {
        assertEquals(ThemeId.EMERALD, ThemeId.byName("  emerald  "));
        assertEquals(ThemeId.MIDNIGHT, Themes.byId("MiDnIgHt").id());
        assertEquals(ThemeId.CUSTOM, Themes.byId("custom").id());
    }

    @Test
    void everyThemeHasTheRequiredShape()
    {
        assertEquals(6, Themes.defaults().size());
        Set<ThemeId> ids = new HashSet<>();
        for (Theme theme : Themes.defaults())
        {
            ids.add(theme.id());
            assertNotNull(theme);
            assertEquals(Theme.PALETTE_SIZE, theme.waypointPalette().length);
            assertEquals(ThemeId.byName(theme.id().name()), theme.id());
        }
        assertEquals(6, ids.size());
    }

    @Test
    void waypointPaletteIsDefensive()
    {
        Theme theme = Themes.byId(ThemeId.DARK);
        int[] palette = theme.waypointPalette();
        int original = palette[0];
        palette[0] = 0;
        assertEquals(original, theme.waypointPalette()[0]);
        assertEquals(original, theme.waypointColor(0));
        assertEquals(original, theme.waypointColor(Theme.PALETTE_SIZE));
    }

    @Test
    void contrastTextIsReadable()
    {
        int onWhite = Themes.contrastText(0xFFFFFFFF);
        int onBlack = Themes.contrastText(0xFF000000);
        assertEquals(Colors.BLACK, onWhite);
        assertEquals(Colors.WHITE, onBlack);
        assertTrue(Colors.contrastRatio(onWhite, 0xFFFFFFFF) >= 4.5D);
        assertTrue(Colors.contrastRatio(onBlack, 0xFF000000) >= 4.5D);
        for (Theme theme : Themes.defaults())
        {
            assertTrue(Colors.contrastRatio(theme.accentContrast(), theme.accent()) >= 4.5D,
                    "accent contrast for " + theme.id());
        }
    }

    @Test
    void withAccentKeepsThemeIdentityAndUpdatesMarkers()
    {
        Theme base = Themes.byId(ThemeId.DARK);
        int accent = 0xFFFF00FF;
        Theme themed = Themes.withAccent(base, accent);

        assertEquals(base.id(), themed.id());
        assertEquals(accent, themed.accent());
        assertEquals(accent, themed.player());
        assertEquals(Themes.contrastText(accent), themed.accentContrast());
        assertNotEquals(base.accentMuted(), themed.accentMuted());
        assertEquals(base.text(), themed.text());
    }

    @Test
    void customAccentRetintsCustomSurfaces()
    {
        Theme base = Themes.byId(ThemeId.CUSTOM);
        Theme themed = Themes.withAccent(base, 0xFF00FF00);

        assertEquals(ThemeId.CUSTOM, themed.id());
        assertNotEquals(base.surface(), themed.surface());
        assertNotEquals(base.background(), themed.background());
        assertFalse(Colors.isDark(themed.accent()));
    }
}
