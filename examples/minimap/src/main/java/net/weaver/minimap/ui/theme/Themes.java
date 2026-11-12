package net.weaver.minimap.ui.theme;

import java.util.List;
import java.util.Map;

/**
 * Built-in theme factory.
 *
 * <p>Every theme shares the same twelve-colour waypoint palette and the same
 * semantic colours; only surfaces, text and the accent change. Theme instances
 * are cached, so comparing instances by identity is safe.</p>
 */
public final class Themes
{
    private static final int[] WAYPOINTS = {
            0xFFFF6B6B,
            0xFFFFA94D,
            0xFFFFD43B,
            0xFF69DB7C,
            0xFF38D9A9,
            0xFF4DABF7,
            0xFF748FFC,
            0xFFDA77F2,
            0xFFF783AC,
            0xFFB0BEC5,
            0xFFFFFFFF,
            0xFFF5A524
    };

    private static final int DANGER = 0xFFFF5C6C;
    private static final int WARNING = 0xFFF5A524;
    private static final int SUCCESS = 0xFF37D39B;

    private static final Theme DARK = darkTheme();
    private static final Theme LIGHT = lightTheme();
    private static final Theme MIDNIGHT = midnightTheme();
    private static final Theme EMERALD = emeraldTheme();
    private static final Theme AMBER = amberTheme();
    private static final Theme CUSTOM = withAccent(customBase(), 0xFF5CC8FF);

    private static final Map<ThemeId, Theme> BY_ID = Map.of(
            ThemeId.DARK, DARK,
            ThemeId.LIGHT, LIGHT,
            ThemeId.MIDNIGHT, MIDNIGHT,
            ThemeId.EMERALD, EMERALD,
            ThemeId.AMBER, AMBER,
            ThemeId.CUSTOM, CUSTOM);

    private static final List<Theme> ALL = List.of(DARK, LIGHT, MIDNIGHT, EMERALD, AMBER, CUSTOM);

    private Themes()
    {
    }

    /** All built-in themes in declaration order. */
    public static List<Theme> defaults()
    {
        return ALL;
    }

    /** Resolves a built-in theme; {@code null} maps to {@link ThemeId#DARK}. */
    public static Theme byId(ThemeId id)
    {
        return id == null ? DARK : BY_ID.getOrDefault(id, DARK);
    }

    /**
     * Resolves a theme by name, case-insensitively.
     *
     * @param id the raw name, possibly {@code null}
     * @return the matching theme, or {@link ThemeId#DARK} when unknown
     */
    public static Theme byId(String id)
    {
        return byId(ThemeId.byName(id));
    }

    /**
     * Returns a copy of a theme with a different accent.
     *
     * <p>For {@link ThemeId#CUSTOM} the surfaces are re-tinted from the accent;
     * for the other themes the surfaces stay as authored.</p>
     *
     * @param base   the source theme, never {@code null}
     * @param accent the new ARGB accent
     * @return the derived theme
     */
    public static Theme withAccent(Theme base, int accent)
    {
        Theme theme = base == null ? DARK : base;
        int accentMuted = Colors.mix(accent, theme.surface(), 0.55D);
        int accentContrast = contrastText(accent);
        int background = theme.background();
        int surface = theme.surface();
        int surfaceAlt = theme.surfaceAlt();
        int surfaceElevated = theme.surfaceElevated();
        int border = theme.border();
        int borderStrong = theme.borderStrong();
        if (theme.id() == ThemeId.CUSTOM)
        {
            background = Colors.mix(0xFF0A0D12, accent, 0.07D);
            surface = Colors.mix(0xFF10151D, accent, 0.10D);
            surfaceAlt = Colors.lighten(surface, 0.045D);
            surfaceElevated = Colors.lighten(surface, 0.085D);
            border = Colors.withAlpha(Colors.mix(0xFFFFFFFF, accent, 0.45D), 0x33);
            borderStrong = Colors.withAlpha(Colors.mix(0xFFFFFFFF, accent, 0.45D), 0x5C);
        }
        return new Theme(theme.id(), theme.dark(), background, surface, surfaceAlt,
                surfaceElevated, border, borderStrong, theme.text(), theme.textMuted(),
                theme.textFaint(), accent, accentMuted, accentContrast, theme.danger(),
                theme.warning(), theme.success(), theme.shadow(), theme.overlay(),
                theme.mapGrid(), accent, theme.death(), theme.waypointPalette());
    }

    /**
     * Picks readable text for a background.
     *
     * @param background the ARGB background
     * @return near-black or white
     */
    public static int contrastText(int background)
    {
        return Colors.contrastText(background);
    }

    private static Theme darkTheme()
    {
        int background = 0xFF0B0F14;
        int surface = 0xFF121821;
        return new Theme(ThemeId.DARK, true, background, surface,
                Colors.lighten(surface, 0.045D), Colors.lighten(surface, 0.09D),
                0x33FFFFFF, 0x5CFFFFFF,
                0xFFE9EFF7, 0xFFA3B0BF, 0xFF6E7B8A,
                0xFF5CC8FF, Colors.mix(0xFF5CC8FF, surface, 0.55D),
                contrastText(0xFF5CC8FF),
                DANGER, WARNING, SUCCESS,
                0x66000000, 0xB3000000, 0x24FFFFFF,
                0xFF5CC8FF, DANGER, WAYPOINTS.clone());
    }

    private static Theme lightTheme()
    {
        int background = 0xFFF4F6F9;
        int surface = 0xFFFFFFFF;
        return new Theme(ThemeId.LIGHT, false, background, surface,
                0xFFEEF2F7, 0xFFF9FBFD,
                0x22000000, 0x44000000,
                0xFF111A25, 0xFF51606F, 0xFF7C8998,
                0xFF1D7AF3, Colors.mix(0xFF1D7AF3, surface, 0.72D),
                contrastText(0xFF1D7AF3),
                0xFFD93B4B, 0xFFB77900, 0xFF1C8F63,
                0x33000000, 0x66000000, 0x33000000,
                0xFF1D7AF3, DANGER, WAYPOINTS.clone());
    }

    private static Theme midnightTheme()
    {
        int background = 0xFF080B1A;
        int surface = 0xFF10162B;
        return new Theme(ThemeId.MIDNIGHT, true, background, surface,
                Colors.lighten(surface, 0.05D), Colors.lighten(surface, 0.10D),
                0x33AAB4FF, 0x5CAAB4FF,
                0xFFE6E9FF, 0xFFA6AEDB, 0xFF6F78A8,
                0xFF7C8CFF, Colors.mix(0xFF7C8CFF, surface, 0.55D),
                contrastText(0xFF7C8CFF),
                0xFFFF6B7D, 0xFFFFC24D, 0xFF4BE0AC,
                0x77000000, 0xB3000000, 0x26FFFFFF,
                0xFF7C8CFF, DANGER, WAYPOINTS.clone());
    }

    private static Theme emeraldTheme()
    {
        int background = 0xFF07130F;
        int surface = 0xFF0F1F19;
        return new Theme(ThemeId.EMERALD, true, background, surface,
                Colors.lighten(surface, 0.05D), Colors.lighten(surface, 0.10D),
                0x3394E7C4, 0x5C94E7C4,
                0xFFE4F5EC, 0xFF9DBCAD, 0xFF6B8A7B,
                0xFF37D39B, Colors.mix(0xFF37D39B, surface, 0.55D),
                contrastText(0xFF37D39B),
                0xFFFF6B7A, 0xFFF0B35A, 0xFF37D39B,
                0x77000000, 0xB3000000, 0x26FFFFFF,
                0xFF37D39B, DANGER, WAYPOINTS.clone());
    }

    private static Theme amberTheme()
    {
        int background = 0xFF140F07;
        int surface = 0xFF221A0F;
        return new Theme(ThemeId.AMBER, true, background, surface,
                Colors.lighten(surface, 0.05D), Colors.lighten(surface, 0.10D),
                0x33F5D9A8, 0x5CF5D9A8,
                0xFFF7EEDF, 0xFFC2B095, 0xFF8E7C63,
                0xFFF5A524, Colors.mix(0xFFF5A524, surface, 0.55D),
                contrastText(0xFFF5A524),
                0xFFFF6B63, 0xFFF5A524, 0xFF57C785,
                0x77000000, 0xB3000000, 0x26FFFFFF,
                0xFFF5A524, DANGER, WAYPOINTS.clone());
    }

    private static Theme customBase()
    {
        return new Theme(ThemeId.CUSTOM, true, 0xFF0A0D12, 0xFF10151D,
                0xFF151B24, 0xFF1A2230,
                0x33FFFFFF, 0x5CFFFFFF,
                0xFFE9EFF7, 0xFFA3B0BF, 0xFF6E7B8A,
                0xFF5CC8FF, 0xFF2E5E77, contrastText(0xFF5CC8FF),
                DANGER, WARNING, SUCCESS,
                0x66000000, 0xB3000000, 0x24FFFFFF,
                0xFF5CC8FF, DANGER, WAYPOINTS.clone());
    }
}
