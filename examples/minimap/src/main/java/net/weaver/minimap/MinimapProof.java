package net.weaver.minimap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.weaver.minimap.config.MinimapConfig;
import net.weaver.minimap.waypoint.Waypoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Deterministic screenshot and marker proof driver.
 *
 * <p>The proof is enabled with {@code -Dweaver.minimap.proof=<mode>} or the
 * {@code WEAVER_MINIMAP_PROOF} environment variable. Supported modes are
 * {@code hud}, {@code map}, {@code settings}, {@code cave},
 * {@code waypoint-editor}, and {@code all}. Screenshots are written to
 * {@code -Dweaver.minimap.proofDir=...} or {@code <gameDir>/weaver-screenshots}.
 * The game is never closed by the proof; the final screen stays open for a
 * human inspection.</p>
 */
final class MinimapProof
{
    private static final int HUD_DELAY = 120;
    private static final int SCREEN_DELAY = 40;
    private static final int CAVE_DELAY = 160;

    private static String mode = "";
    private static long startTick;
    private static int step;
    private static boolean started;
    private static boolean finished;

    private MinimapProof()
    {
    }

    static String mode()
    {
        String property = System.getProperty("weaver.minimap.proof", "");
        if (!property.isBlank())
        {
            return property.trim().toLowerCase();
        }
        return System.getenv().getOrDefault("WEAVER_MINIMAP_PROOF", "").trim().toLowerCase();
    }

    static void start(MinimapClient client)
    {
        if (started)
        {
            return;
        }
        started = true;
        mode = mode();
        startTick = -1L;
        step = 0;
        finished = false;
        System.out.println("[weaver-minimap] PROOF start mode=" + mode);
    }

    static void tick(MinimapClient client, Minecraft minecraft)
    {
        if (!started || finished || mode.isBlank())
        {
            return;
        }
        if (startTick < 0L)
        {
            if (!minecraft.isGameLoadFinished() || minecraft.player == null
                    || minecraft.level == null
                    || (minecraft.gui != null && minecraft.gui.screen() != null))
            {
                return;
            }
            startTick = minecraft.level.getGameTime();
            begin(client);
            return;
        }
        if (minecraft.gui != null && minecraft.gui.screen() != null
                && !"map".equals(mode) && !"settings".equals(mode) && !"cave".equals(mode)
                && !"waypoint-editor".equals(mode) && !"editor".equals(mode)
                && !"all".equals(mode))
        {
            return;
        }
        long tick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        long elapsed = tick - startTick;
        switch (mode)
        {
            case "hud" -> {
                if (elapsed >= HUD_DELAY)
                {
                    screenshot(client, minecraft, "01-hud.png");
                    finish("hud");
                }
            }
            case "map" -> {
                if (elapsed >= SCREEN_DELAY + 20)
                {
                    screenshot(client, minecraft, "02-fullscreen.png");
                    finish("map");
                }
            }
            case "settings" -> {
                if (elapsed >= SCREEN_DELAY + 10)
                {
                    screenshot(client, minecraft, "03-settings.png");
                    finish("settings");
                }
            }
            case "cave" -> {
                if (elapsed >= CAVE_DELAY)
                {
                    screenshot(client, minecraft, "04-cave.png");
                    finish("cave");
                }
            }
            case "waypoint-editor", "editor" -> {
                if (elapsed >= SCREEN_DELAY - 15)
                {
                    Waypoint waypoint = client.waypoints() == null ? null
                            : client.waypoints().all().stream().findFirst().orElse(null);
                    if (waypoint != null && step == 0)
                    {
                        step = 1;
                        client.openWaypointEditor(waypoint, waypoint.x(), waypoint.y(),
                                waypoint.z());
                    }
                }
                if (elapsed >= SCREEN_DELAY + 20 && step == 1)
                {
                    screenshot(client, minecraft, "05-waypoint-editor.png");
                    finish("waypoint-editor");
                }
            }
            case "all" -> tickAll(client, minecraft, elapsed);
            default -> finish("unknown");
        }
    }

    private static void begin(MinimapClient client)
    {
        System.out.println("[weaver-minimap] PROOF world ready mode=" + mode);
        if (!"cave".equals(mode))
        {
            client.config().setCaveEnabled(false);
            client.saveConfigSoon();
        }
        if ("map".equals(mode))
        {
            client.openMap();
        }
        else if ("settings".equals(mode))
        {
            client.openSettings();
        }
        else if ("cave".equals(mode))
        {
            client.config().setCaveEnabled(true);
            client.config().setCaveAuto(true);
            client.saveConfigSoon();
            client.openMap();
        }
        else if ("waypoint-editor".equals(mode) || "editor".equals(mode))
        {
            client.addWaypointAtPlayer();
            client.openMap();
        }
    }

    private static void tickAll(MinimapClient client, Minecraft minecraft, long elapsed)
    {
        if (step == 0 && elapsed >= HUD_DELAY)
        {
            screenshot(client, minecraft, "01-hud.png");
            step = 1;
            client.openMap();
        }
        else if (step == 1 && elapsed >= HUD_DELAY + 400)
        {
            screenshot(client, minecraft, "02-fullscreen.png");
            step = 2;
            client.openSettings();
        }
        else if (step == 2 && elapsed >= HUD_DELAY + 440)
        {
            screenshot(client, minecraft, "03-settings.png");
            step = 3;
            client.config().setCaveEnabled(true);
            client.config().setCaveAuto(true);
            client.saveConfigSoon();
            client.openMap();
        }
        else if (step == 3 && elapsed >= HUD_DELAY + 700)
        {
            screenshot(client, minecraft, "04-cave.png");
            step = 4;
            client.addWaypointAtPlayer();
            Waypoint waypoint = client.waypoints() == null ? null
                    : client.waypoints().all().stream().findFirst().orElse(null);
            if (waypoint != null)
            {
                client.openWaypointEditor(waypoint, waypoint.x(), waypoint.y(), waypoint.z());
            }
        }
        else if (step == 4 && elapsed >= HUD_DELAY + 720)
        {
            screenshot(client, minecraft, "05-waypoint-editor.png");
            step = 5;
            client.config().setCaveEnabled(false);
            client.saveConfigSoon();
            client.openMap();
        }
        else if (step == 5 && elapsed >= HUD_DELAY + 1100)
        {
            // The map keeps scanning while it is open, so this frame shows a
            // populated surface instead of the first few chunks around spawn.
            screenshot(client, minecraft, "06-map-explored.png");
            step = 6;
            minecraft.setScreenAndShow(null);
        }
        else if (step == 6 && elapsed >= HUD_DELAY + 1160)
        {
            screenshot(client, minecraft, "07-hud-late.png");
            step = 7;
            finish("all");
        }
    }

    private static void screenshot(MinimapClient client, Minecraft minecraft, String name)
    {
        if (minecraft == null || minecraft.gameRenderer == null)
        {
            return;
        }
        Path directory = proofDirectory(minecraft);
        try
        {
            Files.createDirectories(directory);
        }
        catch (IOException failure)
        {
            client.log("PROOF screenshot directory failed: " + failure);
            return;
        }
        Path target = directory.resolve(name);
        try
        {
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image ->
                    write(client, image, target));
            System.out.println("[weaver-minimap] PROOF screenshot " + target);
        }
        catch (Throwable failure)
        {
            client.log("PROOF screenshot failed: " + failure);
        }
    }

    private static void write(MinimapClient client, NativeImage image, Path target)
    {
        if (image == null)
        {
            return;
        }
        try
        {
            image.writeToFile(target);
        }
        catch (IOException failure)
        {
            client.log("PROOF screenshot write failed: " + failure);
        }
        finally
        {
            image.close();
        }
    }

    private static Path proofDirectory(Minecraft minecraft)
    {
        String configured = System.getProperty("weaver.minimap.proofDir", "");
        if (!configured.isBlank())
        {
            return Path.of(configured);
        }
        return Path.of(minecraft.gameDirectory.getAbsolutePath(), "weaver-screenshots");
    }

    private static void finish(String label)
    {
        if (finished)
        {
            return;
        }
        finished = true;
        System.out.println("[weaver-minimap] PROOF done mode=" + label);
    }
}
