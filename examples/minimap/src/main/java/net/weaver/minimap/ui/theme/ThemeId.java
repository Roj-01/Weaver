package net.weaver.minimap.ui.theme;

/**
 * Identifier for one of the built-in minimap colour themes.
 */
public enum ThemeId
{
    DARK("Dark"),
    LIGHT("Light"),
    MIDNIGHT("Midnight"),
    EMERALD("Emerald"),
    AMBER("Amber"),
    CUSTOM("Custom");

    private final String displayName;

    ThemeId(String displayName)
    {
        this.displayName = displayName;
    }

    /** The human-readable theme name. */
    public String displayName()
    {
        return displayName;
    }

    /**
     * Resolves a theme id without throwing.
     *
     * @param value the raw id, possibly {@code null}
     * @return the matching id, or {@link #DARK} when unknown
     */
    public static ThemeId byName(String value)
    {
        if (value == null)
        {
            return DARK;
        }
        String trimmed = value.trim();
        for (ThemeId id : values())
        {
            if (id.name().equalsIgnoreCase(trimmed))
            {
                return id;
            }
        }
        return DARK;
    }
}
