package net.weaver.minimap.ui.theme;

import java.util.Objects;

/**
 * Immutable colour palette used by every minimap widget and surface.
 *
 * <p>All colours are packed ARGB integers. The waypoint palette is copied on
 * construction; {@link #waypointPalette()} returns a copy as well, while
 * {@link #waypointColor(int)} is the allocation-free accessor for hot paths.</p>
 *
 * @param id              the theme identity
 * @param dark            whether the theme uses light text on dark surfaces
 * @param background      the fullscreen backdrop
 * @param surface         the default panel surface
 * @param surfaceAlt      an alternate row/card surface
 * @param surfaceElevated the raised surface for popups and hovered controls
 * @param border          the 1 px hairline border
 * @param borderStrong    the emphasised border
 * @param text            primary text
 * @param textMuted       secondary text
 * @param textFaint       tertiary text and hints
 * @param accent          the interactive accent
 * @param accentMuted     the accent blended towards the surface
 * @param accentContrast  readable text drawn on top of the accent
 * @param danger          destructive actions
 * @param warning         cautionary states
 * @param success         confirmations
 * @param shadow          the soft shadow colour
 * @param overlay         the modal/backdrop scrim
 * @param mapGrid         the map grid line colour
 * @param player          the player marker colour
 * @param death           the death marker colour
 * @param waypointPalette twelve waypoint colours
 */
public record Theme(
        ThemeId id,
        boolean dark,
        int background,
        int surface,
        int surfaceAlt,
        int surfaceElevated,
        int border,
        int borderStrong,
        int text,
        int textMuted,
        int textFaint,
        int accent,
        int accentMuted,
        int accentContrast,
        int danger,
        int warning,
        int success,
        int shadow,
        int overlay,
        int mapGrid,
        int player,
        int death,
        int[] waypointPalette)
{
    /** The required number of waypoint palette entries. */
    public static final int PALETTE_SIZE = 12;

    public Theme
    {
        Objects.requireNonNull(id, "id");
        if (waypointPalette == null || waypointPalette.length != PALETTE_SIZE)
        {
            throw new IllegalArgumentException("waypointPalette must have "
                    + PALETTE_SIZE + " entries");
        }
        waypointPalette = waypointPalette.clone();
    }

    /**
     * Returns a defensive copy of the waypoint palette.
     *
     * @return twelve ARGB colours
     */
    @Override
    public int[] waypointPalette()
    {
        return waypointPalette.clone();
    }

    /**
     * Returns one waypoint colour without allocating.
     *
     * @param index the palette index, wrapped into range
     * @return the ARGB colour
     */
    public int waypointColor(int index)
    {
        int wrapped = Math.floorMod(index, PALETTE_SIZE);
        return waypointPalette[wrapped];
    }
}
