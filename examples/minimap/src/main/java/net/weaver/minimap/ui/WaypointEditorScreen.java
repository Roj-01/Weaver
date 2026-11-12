package net.weaver.minimap.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.MinimapClient;
import net.weaver.minimap.render.IconAtlas;
import net.weaver.minimap.render.MapMarkerRenderer;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Colors;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.ui.theme.ThemeManager;
import net.weaver.minimap.ui.widget.UiButton;
import net.weaver.minimap.ui.widget.UiColorPicker;
import net.weaver.minimap.ui.widget.UiIconPicker;
import net.weaver.minimap.ui.widget.UiTextField;
import net.weaver.minimap.ui.widget.UiToggle;
import net.weaver.minimap.waypoint.Waypoint;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Modal waypoint editor used by the fullscreen map and the waypoint list.
 *
 * <p>The editor keeps the original waypoint identifier, death flag, creation
 * time, and extension fields. New waypoints are created in the current
 * dimension at the supplied block position.</p>
 */
public final class WaypointEditorScreen extends Screen
{
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 560;

    private final MinimapClient client;
    private final Screen parent;
    private final Waypoint existing;
    private final int blockX;
    private final int blockY;
    private final int blockZ;

    private UiTextField nameField;
    private UiTextField groupField;
    private UiColorPicker colorPicker;
    private UiIconPicker iconPicker;
    private UiToggle visibleToggle;
    private UiToggle temporaryToggle;
    private UiButton deleteButton;
    private int color;
    private String iconId;

    /**
     * Creates the editor.
     *
     * @param client   the mod client, never {@code null}
     * @param parent   the screen to return to, possibly {@code null}
     * @param existing the waypoint to edit, or {@code null} for a new waypoint
     * @param x        the block X
     * @param y        the block Y
     * @param z        the block Z
     */
    public WaypointEditorScreen(MinimapClient client, Screen parent, Waypoint existing,
                                int x, int y, int z)
    {
        super(Component.literal(existing == null ? "New Waypoint" : "Edit Waypoint"));
        this.client = client;
        this.parent = parent;
        this.existing = existing;
        this.blockX = existing == null ? x : existing.x();
        this.blockY = existing == null ? y : existing.y();
        this.blockZ = existing == null ? z : existing.z();
        this.color = existing == null ? client.config().waypointColor() : existing.color();
        this.iconId = existing == null ? "pin" : existing.icon();
    }

    @Override
    protected void init()
    {
        super.init();
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int x = panelX + 24;
        int width = PANEL_WIDTH - 48;

        nameField = new UiTextField(x, panelY + 78, width, 26,
                existing == null ? "" : existing.name(), null);
        nameField.setHint("Waypoint name");
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);

        colorPicker = new UiColorPicker(x, panelY + 128, width, 40, color,
                value -> color = value);
        addRenderableWidget(colorPicker);

        IconAtlas.Icon selectedIcon = MapMarkerRenderer.iconFor(iconId);
        iconPicker = new UiIconPicker(x, panelY + 190, width, 140, selectedIcon,
                icon -> iconId = icon.name().toLowerCase(Locale.ROOT));
        addRenderableWidget(iconPicker);

        groupField = new UiTextField(x, panelY + 352, width, 26,
                existing == null ? "" : existing.group(), null);
        groupField.setHint("Group (optional)");
        groupField.setMaxLength(48);
        addRenderableWidget(groupField);

        visibleToggle = new UiToggle(x + width - 42, panelY + 392, 38, 18,
                existing == null || existing.visible(), null);
        addRenderableWidget(visibleToggle);

        temporaryToggle = new UiToggle(x + width - 42, panelY + 424, 38, 18,
                existing != null && existing.temporary(), null);
        addRenderableWidget(temporaryToggle);

        deleteButton = new UiButton(panelX + 24, panelY + PANEL_HEIGHT - 44, 96, 26,
                Component.literal("Delete"), this::delete);
        deleteButton.setIcon(IconAtlas.Icon.TRASH);
        deleteButton.setVariant(UiButton.Variant.DANGER);
        deleteButton.active = existing != null;
        addRenderableWidget(deleteButton);

        UiButton cancel = new UiButton(panelX + PANEL_WIDTH - 196, panelY + PANEL_HEIGHT - 44,
                82, 26, Component.literal("Cancel"), this::close);
        cancel.setVariant(UiButton.Variant.GHOST);
        addRenderableWidget(cancel);

        UiButton save = new UiButton(panelX + PANEL_WIDTH - 106, panelY + PANEL_HEIGHT - 44,
                82, 26, Component.literal("Save"), this::save);
        save.setVariant(UiButton.Variant.PRIMARY);
        save.setIcon(IconAtlas.Icon.CHECK);
        addRenderableWidget(save);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick)
    {
        Theme theme = ThemeManager.get().current();
        graphics.fill(0, 0, width, height, Colors.withAlpha(theme.overlay(), 0xE8));
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        UiDraw.shadow(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 18,
                theme.shadow(), 4);
        UiDraw.roundRect(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 18,
                theme.surface());
        UiDraw.roundRectBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 18, 1,
                theme.borderStrong());
        int x = panelX + 24;
        UiDraw.roundRect(graphics, x, panelY + 22, 4, 16, 2, theme.accent());
        graphics.text(font, title, x + 12, panelY + 22, theme.text(), false);
        String hint = "Changes are saved to the current world and dimension.";
        graphics.text(font, hint, x, panelY + 42, theme.textFaint(), false);

        drawLabel(graphics, theme, "Name", x, panelY + 66);
        drawLabel(graphics, theme, "Color", x, panelY + 112);
        drawLabel(graphics, theme, "Icon", x, panelY + 166);
        drawLabel(graphics, theme, "Group", x, panelY + 338);
        drawLabel(graphics, theme, "Visible", x, panelY + 396);
        drawLabel(graphics, theme, "Temporary", x, panelY + 428);
        String coordinates = "X " + blockX + "   Y " + blockY + "   Z " + blockZ;
        graphics.text(font, coordinates, x, panelY + 460, theme.textMuted(), false);
        String dimension = client.maps() == null ? "-" : client.maps().dimensionId();
        graphics.text(font, "Dimension  " + dimension, x, panelY + 476,
                theme.textFaint(), false);

        String iconName = iconId == null ? "pin" : iconId;
        UiDraw.circle(graphics, panelX + PANEL_WIDTH - 44, panelY + 30, 16,
                Colors.withAlpha(color, 0x33));
        UiDraw.circle(graphics, panelX + PANEL_WIDTH - 44, panelY + 30, 12, color);
        IconAtlas.draw(graphics, MapMarkerRenderer.iconFor(iconName),
                panelX + PANEL_WIDTH - 50, panelY + 24, 12,
                Colors.contrastText(color));

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!renderedOnce)
        {
            renderedOnce = true;
            System.out.println("[weaver-minimap] WAYPOINT EDITOR rendered");
        }
    }

    private boolean renderedOnce;

    private void drawLabel(GuiGraphicsExtractor graphics, Theme theme, String label, int x,
                           int y)
    {
        graphics.text(font, label, x, y, theme.textMuted(), false);
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (nameField != null && nameField.isFocused())
        {
            return super.keyPressed(event);
        }
        if (groupField != null && groupField.isFocused())
        {
            return super.keyPressed(event);
        }
        if (event.key() == 256)
        {
            close();
            return true;
        }
        if (event.key() == 257 || event.key() == 335)
        {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    private void save()
    {
        String name = nameField == null ? "" : nameField.value().trim();
        if (name.isEmpty())
        {
            name = "Waypoint";
        }
        String group = groupField == null ? "" : groupField.value().trim();
        String dimension = client.maps() == null ? null : client.maps().dimensionId();
        if (dimension == null)
        {
            close();
            return;
        }
        long now = System.currentTimeMillis();
        UUID id = existing == null ? UUID.randomUUID() : existing.id();
        long createdAt = existing == null ? now : existing.createdAt();
        Map<String, com.google.gson.JsonElement> extensions = existing == null ? Map.of()
                : existing.extensions();
        boolean death = existing != null && existing.death();
        int finalColor = colorPicker == null ? color : colorPicker.color();
        Waypoint waypoint = new Waypoint(id, name, dimension, blockX, blockY, blockZ,
                finalColor, iconId, group,
                visibleToggle == null || visibleToggle.value(),
                temporaryToggle != null && temporaryToggle.value(), death, createdAt, now,
                extensions);
        client.saveWaypoint(waypoint);
        close();
    }

    private void delete()
    {
        if (existing != null)
        {
            client.removeWaypoint(existing);
        }
        close();
    }

    private void close()
    {
        if (parent != null && minecraft != null)
        {
            minecraft.setScreenAndShow(parent);
        }
        else
        {
            onClose();
        }
    }

    @Override
    public void onClose()
    {
        if (parent != null && minecraft != null)
        {
            minecraft.setScreenAndShow(parent);
            return;
        }
        client.saveConfig();
        super.onClose();
    }
}
