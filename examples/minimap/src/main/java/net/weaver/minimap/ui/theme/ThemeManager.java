package net.weaver.minimap.ui.theme;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Process-wide holder for the active theme.
 *
 * <p>The integrator creates the manager, pushes the configured theme id and
 * accent once, and every widget reads {@link #get()}. Listeners are notified
 * after every successful change and are isolated from each other; a failing
 * listener never blocks the remaining listeners.</p>
 */
public final class ThemeManager
{
    private static final ThemeManager INSTANCE = new ThemeManager();

    private final List<Consumer<Theme>> listeners = new CopyOnWriteArrayList<>();
    private Theme current = Themes.byId(ThemeId.DARK);
    private ThemeId themeId = ThemeId.DARK;
    private Integer accentOverride;

    /** Creates an independent manager, used by tests and previews. */
    public ThemeManager()
    {
    }

    /** The shared manager used by the UI. */
    public static ThemeManager get()
    {
        return INSTANCE;
    }

    /** The active theme, never {@code null}. */
    public Theme current()
    {
        return current;
    }

    /** The configured theme id. */
    public ThemeId themeId()
    {
        return themeId;
    }

    /** The explicit accent override, or {@code null} when the theme default is used. */
    public Integer accentOverride()
    {
        return accentOverride;
    }

    /**
     * Replaces the active theme.
     *
     * @param theme the new theme, never {@code null}
     */
    public void set(Theme theme)
    {
        Theme next = Objects.requireNonNull(theme, "theme");
        current = next;
        themeId = next.id();
        accentOverride = null;
        notifyListeners();
    }

    /**
     * Selects a built-in theme by name.
     *
     * @param id the raw theme name; unknown values fall back to dark
     */
    public void setThemeId(String id)
    {
        themeId = ThemeId.byName(id);
        current = Themes.byId(themeId);
        accentOverride = null;
        notifyListeners();
    }

    /**
     * Applies an accent override to the current theme.
     *
     * @param accent the ARGB accent
     */
    public void setAccent(int accent)
    {
        accentOverride = accent;
        current = Themes.withAccent(current, accent);
        notifyListeners();
    }

    /** Restores the default dark theme and clears the accent override. */
    public void reset()
    {
        themeId = ThemeId.DARK;
        accentOverride = null;
        current = Themes.byId(ThemeId.DARK);
        notifyListeners();
    }

    /**
     * Registers a theme listener.
     *
     * @param listener the listener, never {@code null}
     */
    public void addListener(Consumer<Theme> listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(Consumer<Theme> listener)
    {
        listeners.remove(listener);
    }

    private void notifyListeners()
    {
        for (Consumer<Theme> listener : listeners)
        {
            try
            {
                listener.accept(current);
            }
            catch (RuntimeException ignored)
            {
                // A preview listener must not break the global theme state.
            }
        }
    }
}
