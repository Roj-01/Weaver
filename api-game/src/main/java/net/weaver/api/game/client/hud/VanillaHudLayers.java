package net.weaver.api.game.client.hud;

/**
 * Ids of the parts of the game's own HUD, for
 * {@link net.weaver.api.game.event.client.HudLayerRenderEvent}. They are the ids
 * NeoForge gives the same layers, so ported code can keep its constants.
 *
 * <p>A layer added through {@link HudRenderRegistry} is asked for under its
 * listener id instead.</p>
 */
public final class VanillaHudLayers {
    public static final String CAMERA_OVERLAYS = "minecraft:camera_overlays";
    public static final String CROSSHAIR = "minecraft:crosshair";
    public static final String HOTBAR = "minecraft:hotbar";
    public static final String SELECTED_ITEM_NAME = "minecraft:selected_item_name";
    public static final String PLAYER_HEALTH = "minecraft:player_health";
    public static final String ARMOR_LEVEL = "minecraft:armor_level";
    public static final String FOOD_LEVEL = "minecraft:food_level";
    public static final String VEHICLE_HEALTH = "minecraft:vehicle_health";
    public static final String AIR_LEVEL = "minecraft:air_level";
    /** The bar above the hotbar: experience, jump strength or locator bar. */
    public static final String CONTEXTUAL_INFO_BAR = "minecraft:contextual_info_bar";
    public static final String EXPERIENCE_LEVEL = "minecraft:experience_level";
    public static final String EFFECTS = "minecraft:effects";
    public static final String BOSS_OVERLAY = "minecraft:boss_overlay";
    public static final String SLEEP_OVERLAY = "minecraft:sleep_overlay";
    public static final String DEMO_OVERLAY = "minecraft:demo_overlay";
    public static final String SCOREBOARD_SIDEBAR = "minecraft:scoreboard_sidebar";
    public static final String OVERLAY_MESSAGE = "minecraft:overlay_message";
    public static final String TITLE = "minecraft:title";
    public static final String CHAT = "minecraft:chat";
    public static final String TAB_LIST = "minecraft:tab_list";
    public static final String SUBTITLE_OVERLAY = "minecraft:subtitle_overlay";

    private VanillaHudLayers() {
    }
}
