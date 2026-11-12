package net.weaver.minimap.ui;

import net.weaver.minimap.render.IconAtlas;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IconAtlasTest
{
    private static final Set<String> REQUIRED = Set.of(
            "CLOSE", "SETTINGS", "PLUS", "TARGET", "LAYERS", "SEARCH",
            "CHEVRON_LEFT", "CHEVRON_RIGHT", "CHEVRON_DOWN", "CHEVRON_UP",
            "EYE", "EYE_OFF", "TRASH", "PENCIL", "FILTER", "SORT", "COMPASS",
            "ZOOM_IN", "ZOOM_OUT", "GRID", "CAVE", "PLAYER", "SKULL", "HOME",
            "PORTAL", "VILLAGE", "RESOURCE", "STAR", "PIN", "WAYPOINT", "DEATH",
            "ENTITY_HOSTILE", "ENTITY_PASSIVE", "ENTITY_ITEM", "ENTITY_PLAYER",
            "WORLD", "SHIELD", "TIME", "BIOME", "DIMENSION", "CHECK", "INFO",
            "WARNING");

    @Test
    void everyRequiredIconExists()
    {
        for (String name : REQUIRED)
        {
            IconAtlas.Icon icon = IconAtlas.Icon.valueOf(name);
            assertNotNull(icon);
        }
        assertEquals(REQUIRED.size(), IconAtlas.Icon.values().length);
    }

    @Test
    void iconIdentityIsUnique()
    {
        Set<IconAtlas.Icon> seen = EnumSet.noneOf(IconAtlas.Icon.class);
        for (IconAtlas.Icon icon : IconAtlas.Icon.values())
        {
            assertTrue(seen.add(icon));
        }
        assertEquals(IconAtlas.Icon.values().length, seen.size());
    }

    @Test
    void catalogueFitsTheAtlasGrid()
    {
        assertTrue(IconAtlas.Icon.values().length <= 8 * 6);
    }
}
