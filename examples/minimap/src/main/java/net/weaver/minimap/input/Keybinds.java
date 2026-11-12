package net.weaver.minimap.input;

import net.weaver.api.client.KeyMappingSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The eleven minimap keybinds and pure conflict detection helpers.
 *
 * <p>The table is loaded by the integrator through the existing loader API.
 * Nothing in this class touches the client or registers a mapping.</p>
 */
public final class Keybinds
{
    public static final String MOD_ID = "weaver-minimap";
    public static final String CATEGORY = "minimap";

    public static final String OPEN_MAP = "open_map";
    public static final String OPEN_SETTINGS = "open_settings";
    public static final String ADD_WAYPOINT = "add_waypoint";
    public static final String TOGGLE_HUD = "toggle_hud";
    public static final String ZOOM_IN = "zoom_in";
    public static final String ZOOM_OUT = "zoom_out";
    public static final String CYCLE_CAVE = "cycle_cave";
    public static final String TOGGLE_ENTITIES = "toggle_entities";
    public static final String TOGGLE_WAYPOINT_LABELS = "toggle_waypoint_labels";
    public static final String HIDE_HUD_TEMP = "hide_hud_temp";
    public static final String OPEN_WAYPOINTS = "open_waypoints";

    private static final List<KeybindSpec> ALL = List.of(
            spec(OPEN_MAP, MinimapKeys.KEY_M),
            spec(OPEN_SETTINGS, MinimapKeys.KEY_O),
            spec(ADD_WAYPOINT, MinimapKeys.KEY_B),
            spec(TOGGLE_HUD, MinimapKeys.KEY_H),
            spec(ZOOM_IN, MinimapKeys.KEY_RIGHT_BRACKET),
            spec(ZOOM_OUT, MinimapKeys.KEY_LEFT_BRACKET),
            spec(CYCLE_CAVE, MinimapKeys.KEY_J),
            spec(TOGGLE_ENTITIES, MinimapKeys.KEY_K),
            spec(TOGGLE_WAYPOINT_LABELS, MinimapKeys.KEY_L),
            spec(HIDE_HUD_TEMP, MinimapKeys.KEY_P),
            spec(OPEN_WAYPOINTS, MinimapKeys.KEY_N));

    private Keybinds()
    {
    }

    /**
     * Returns the immutable keybind table in display order.
     *
     * @return the eleven specs, never {@code null}
     */
    public static List<KeybindSpec> all()
    {
        return ALL;
    }

    /**
     * Looks up a keybind by action id.
     *
     * @param id the action id, never {@code null}
     * @return the spec when present
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public static Optional<KeybindSpec> byId(String id)
    {
        Objects.requireNonNull(id, "id");
        for (KeybindSpec spec : ALL)
        {
            if (spec.id().equals(id))
            {
                return Optional.of(spec);
            }
        }
        return Optional.empty();
    }

    /**
     * Converts a keybind into a loader mapping with the default order.
     *
     * @param spec the keybind, never {@code null}
     * @return the loader mapping, never {@code null}
     * @throws NullPointerException when {@code spec} is {@code null}
     */
    public static KeyMappingSpec mappingSpec(KeybindSpec spec)
    {
        Objects.requireNonNull(spec, "spec");
        return spec.toMappingSpec(MOD_ID, Math.max(0, ALL.indexOf(spec)));
    }

    /**
     * Converts a keybind into a loader mapping with an optional screen.
     *
     * @param spec            the keybind, never {@code null}
     * @param screenFactoryId the optional screen factory id
     * @return the loader mapping, never {@code null}
     * @throws NullPointerException when {@code spec} is {@code null}
     */
    public static KeyMappingSpec mappingSpec(KeybindSpec spec, String screenFactoryId)
    {
        Objects.requireNonNull(spec, "spec");
        return spec.toMappingSpec(MOD_ID, Math.max(0, ALL.indexOf(spec)), screenFactoryId);
    }

    /**
     * Returns duplicate default bindings in the built-in table.
     *
     * @return an unmodifiable list of conflicts, empty when clean
     */
    public static List<KeyConflict> conflicts()
    {
        return conflicts(ALL);
    }

    /**
     * Returns duplicate bindings in a spec list.
     *
     * <p>A keyboard binding and a mouse binding with the same numeric code do
     * not conflict because the code spaces are separate.</p>
     *
     * @param specs the specs to inspect, never {@code null}
     * @return an unmodifiable list of conflicts, empty when clean
     * @throws NullPointerException when the list or an entry is {@code null}
     */
    public static List<KeyConflict> conflicts(List<KeybindSpec> specs)
    {
        Objects.requireNonNull(specs, "specs");
        Map<String, List<String>> byBinding = new LinkedHashMap<>();
        Map<String, int[]> codes = new LinkedHashMap<>();
        for (KeybindSpec spec : specs)
        {
            Objects.requireNonNull(spec, "spec");
            String key = bindingKey(spec.mouse(), spec.defaultKey());
            byBinding.computeIfAbsent(key, ignored -> new ArrayList<>()).add(spec.id());
            codes.computeIfAbsent(key, ignored -> new int[]{spec.mouse() ? 1 : 0,
                    spec.defaultKey()});
        }
        List<KeyConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byBinding.entrySet())
        {
            if (entry.getValue().size() > 1)
            {
                int[] code = codes.get(entry.getKey());
                conflicts.add(new KeyConflict(code[1], code[0] == 1, entry.getValue()));
            }
        }
        return List.copyOf(conflicts);
    }

    /**
     * Detects duplicate key codes in a plain code array.
     *
     * <p>Codes are treated as keyboard keys and identified by their index in
     * the array ({@code "#0"}, {@code "#1"}, ...).</p>
     *
     * @param keyCodes the key codes, never {@code null}
     * @return an unmodifiable list of conflicts, empty when clean
     * @throws NullPointerException when {@code keyCodes} is {@code null}
     */
    public static List<KeyConflict> conflicts(int[] keyCodes)
    {
        Objects.requireNonNull(keyCodes, "keyCodes");
        Map<Integer, List<String>> byCode = new LinkedHashMap<>();
        for (int index = 0; index < keyCodes.length; index++)
        {
            int code = keyCodes[index];
            if (code < 0)
            {
                throw new IllegalArgumentException("key code must not be negative: " + code);
            }
            byCode.computeIfAbsent(code, ignored -> new ArrayList<>()).add("#" + index);
        }
        List<KeyConflict> conflicts = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> entry : byCode.entrySet())
        {
            if (entry.getValue().size() > 1)
            {
                conflicts.add(new KeyConflict(entry.getKey(), false,
                        new ArrayList<>(entry.getValue())));
            }
        }
        return List.copyOf(conflicts);
    }

    /**
     * Returns whether any duplicate binding exists in a spec list.
     *
     * @param specs the specs to inspect, never {@code null}
     * @return {@code true} when at least one conflict exists
     */
    public static boolean hasConflicts(List<KeybindSpec> specs)
    {
        return !conflicts(specs).isEmpty();
    }

    private static KeybindSpec spec(String id, int keyCode)
    {
        return new KeybindSpec(id, "key." + MOD_ID + "." + id, keyCode, false, CATEGORY);
    }

    private static String bindingKey(boolean mouse, int keyCode)
    {
        return (mouse ? "mouse:" : "key:") + keyCode;
    }

    /**
     * One duplicate binding group.
     *
     * @param keyCode the duplicated code
     * @param mouse   whether the codes are mouse buttons
     * @param ids     the ids (or {@code #index} labels) sharing the code
     */
    public record KeyConflict(int keyCode, boolean mouse, List<String> ids)
    {
        /**
         * Validates and freezes the id list.
         *
         * @throws NullPointerException when the id list is {@code null}
         */
        public KeyConflict
        {
            Objects.requireNonNull(ids, "ids");
            ids = List.copyOf(ids);
        }
    }
}
