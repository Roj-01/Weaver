package net.weaver.minimap.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.MinimapClient;
import net.weaver.minimap.render.IconAtlas;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Colors;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.ui.theme.ThemeManager;
import net.weaver.minimap.ui.widget.UiButton;
import net.weaver.minimap.ui.widget.UiTextField;
import net.weaver.minimap.waypoint.WaypointManager;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Waypoint import/export screen with clipboard and file paths.
 *
 * <p>Exports are UTF-8 JSON in the version 2 format. Imports merge by stable
 * identifiers by default; importing the same file twice is idempotent.</p>
 */
public final class ImportExportScreen extends Screen
{
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 406;

    private final MinimapClient client;
    private final Screen parent;

    private UiTextField pathField;
    private String status = "Use the clipboard for quick transfers, or export to a file.";
    private int statusColor = 0xFFA3B0BF;

    /**
     * Creates the screen.
     *
     * @param client the mod client, never {@code null}
     * @param parent the screen to return to, possibly {@code null}
     */
    public ImportExportScreen(MinimapClient client, Screen parent)
    {
        super(Component.literal("Waypoint Import / Export"));
        this.client = client;
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        super.init();
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int x = panelX + 24;
        int width = PANEL_WIDTH - 48;
        String defaultPath = minecraft == null ? "weaver-waypoints.json"
                : new java.io.File(minecraft.gameDirectory, "weaver-waypoints.json")
                .getAbsolutePath();
        pathField = new UiTextField(x, panelY + 238, width, 26, defaultPath, null);
        pathField.setHint("Path to a waypoint JSON file");
        pathField.setMaxLength(512);
        addRenderableWidget(pathField);

        int buttonY = panelY + 104;
        int half = (width - 8) / 2;
        UiButton exportClipboard = new UiButton(x, buttonY, half, 28,
                Component.literal("Copy JSON"), this::exportToClipboard);
        exportClipboard.setIcon(IconAtlas.Icon.CHECK);
        exportClipboard.setVariant(UiButton.Variant.PRIMARY);
        addRenderableWidget(exportClipboard);

        UiButton importClipboard = new UiButton(x + half + 8, buttonY, half, 28,
                Component.literal("Import JSON"), this::importFromClipboard);
        importClipboard.setIcon(IconAtlas.Icon.PLUS);
        addRenderableWidget(importClipboard);

        int fileY = panelY + 276;
        UiButton exportFile = new UiButton(x, fileY, half, 28,
                Component.literal("Export file"), this::exportToFile);
        exportFile.setIcon(IconAtlas.Icon.WORLD);
        addRenderableWidget(exportFile);

        UiButton importFile = new UiButton(x + half + 8, fileY, half, 28,
                Component.literal("Import file"), this::importFromFile);
        importFile.setIcon(IconAtlas.Icon.WORLD);
        addRenderableWidget(importFile);

        UiButton back = new UiButton(panelX + PANEL_WIDTH - 104,
                panelY + PANEL_HEIGHT - 44, 80, 26, Component.literal("Back"), this::close);
        back.setVariant(UiButton.Variant.GHOST);
        addRenderableWidget(back);
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
        UiDraw.textClipped(graphics, font,
                "Waypoints are stored per world and dimension. Export produces version 2 JSON.",
                x, panelY + 42, PANEL_WIDTH - 48, theme.textFaint(), false);
        WaypointManager manager = client.waypoints();
        String count = manager == null ? "No world loaded"
                : manager.count() + " waypoints in " + manager.groups().size() + " groups";
        graphics.text(font, count, x, panelY + 60, theme.textMuted(), false);
        graphics.text(font, "Clipboard", x, panelY + 88, theme.textFaint(), false);
        graphics.text(font, "File", x, panelY + 218, theme.textFaint(), false);
        graphics.text(font, "Path", x, panelY + 226, theme.textFaint(), false);
        int statusY = panelY + 322;
        UiDraw.roundRect(graphics, x, statusY, PANEL_WIDTH - 48, 34, 8,
                Colors.withAlpha(theme.surfaceAlt(), 0xCC));
        UiDraw.textClipped(graphics, font, status, x + 10, statusY + 12,
                PANEL_WIDTH - 68, statusColor, false);
        String hint = "Tip: copy the JSON to share a waypoint set, or export a file and edit it.";
        UiDraw.textClipped(graphics, font, hint, x, panelY + 366, PANEL_WIDTH - 48,
                theme.textFaint(), false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void exportToClipboard()
    {
        WaypointManager manager = client.waypoints();
        if (manager == null)
        {
            setStatus("No world is loaded.", true);
            return;
        }
        String json = manager.exportJson();
        if (minecraft != null && minecraft.keyboardHandler != null)
        {
            minecraft.keyboardHandler.setClipboard(json);
            setStatus("Copied " + manager.count() + " waypoints to the clipboard.", false);
            System.out.println("[weaver-minimap] WAYPOINT EXPORT clipboard");
        }
    }

    private void importFromClipboard()
    {
        WaypointManager manager = client.waypoints();
        if (manager == null)
        {
            setStatus("No world is loaded.", true);
            return;
        }
        String json = minecraft == null || minecraft.keyboardHandler == null ? ""
                : minecraft.keyboardHandler.getClipboard();
        if (json == null || json.isBlank())
        {
            setStatus("The clipboard does not contain JSON.", true);
            return;
        }
        importJson(manager, json, "clipboard");
    }

    private void exportToFile()
    {
        WaypointManager manager = client.waypoints();
        if (manager == null)
        {
            setStatus("No world is loaded.", true);
            return;
        }
        try
        {
            Path path = Path.of(pathField.value().trim());
            manager.exportFile(path);
            setStatus("Exported " + manager.count() + " waypoints to " + path + ".", false);
            System.out.println("[weaver-minimap] WAYPOINT EXPORT file=" + path);
        }
        catch (IOException | RuntimeException failure)
        {
            setStatus("Export failed: " + failure.getMessage(), true);
        }
    }

    private void importFromFile()
    {
        WaypointManager manager = client.waypoints();
        if (manager == null)
        {
            setStatus("No world is loaded.", true);
            return;
        }
        try
        {
            Path path = Path.of(pathField.value().trim());
            int imported = manager.importFile(path, WaypointManager.ImportMode.MERGE);
            manager.save();
            setStatus("Imported " + imported + " waypoints from " + path + ".", false);
            System.out.println("[weaver-minimap] WAYPOINT IMPORT file=" + path);
        }
        catch (IOException | RuntimeException failure)
        {
            setStatus("Import failed: " + failure.getMessage(), true);
        }
    }

    private void importJson(WaypointManager manager, String json, String source)
    {
        try
        {
            int imported = manager.importJson(json, WaypointManager.ImportMode.MERGE);
            manager.save();
            setStatus("Imported " + imported + " waypoints from the " + source + ".", false);
            System.out.println("[weaver-minimap] WAYPOINT IMPORT source=" + source);
        }
        catch (IOException | RuntimeException failure)
        {
            setStatus("Import failed: " + failure.getMessage(), true);
        }
    }

    private void setStatus(String message, boolean error)
    {
        status = message == null ? "" : message;
        statusColor = error ? ThemeManager.get().current().danger()
                : ThemeManager.get().current().success();
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
        super.onClose();
    }
}
