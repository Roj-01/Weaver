package net.weaver.nf;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.ClientTickEvent;

/**
 * Adds a mod's own {@link KeyMapping}s to the game's controls, as NeoForge's
 * {@code RegisterKeyMappingsEvent}. Call it from {@code onClientSetup}; the keys
 * then show in the controls screen, are saved with the options, and report
 * presses through {@code isDown()} and {@code consumeClick()}.
 */
public final class ClientKeys {
    private static final List<KeyMapping> PENDING = new ArrayList<>();
    private static boolean listening;

    private ClientKeys() {
    }

    /**
     * Adds {@code mappings} to the controls. Weaver calls {@code onClientSetup}
     * before the game has made its options, so the mappings are added as soon as
     * the options exist (at once if they do, else on the next client tick). Each
     * mapping's category is registered, and a binding the player chose before
     * (saved in {@code options.txt}, which the game read before the mapping
     * existed) is put back.
     */
    public static void register(KeyMapping... mappings) {
        synchronized (ClientKeys.class) {
            PENDING.addAll(Arrays.asList(mappings));
            if (!listening) {
                listening = true;
                EventBus.GAME.addListener(ClientTickEvent.Post.class, event -> tick());
            }
        }
        tick();
    }

    /** Adds the mappings still waiting for the game's options; safe to call every client tick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        KeyMapping[] due;
        synchronized (ClientKeys.class) {
            if (PENDING.isEmpty()) {
                return;
            }
            due = PENDING.toArray(new KeyMapping[0]);
            PENDING.clear();
        }
        add(minecraft, due);
    }

    private static void add(Minecraft minecraft, KeyMapping[] mappings) {
        for (KeyMapping mapping : mappings) {
            try {
                KeyMapping.Category.register(mapping.getCategory().id());
            } catch (RuntimeException alreadyRegistered) {
                // vanilla's or registered by an earlier mapping
            }
        }
        Options options = minecraft.options;
        try {
            Field field = Options.class.getDeclaredField("keyMappings");
            field.setAccessible(true);
            KeyMapping[] current = (KeyMapping[]) field.get(options);
            List<KeyMapping> known = Arrays.asList(current);
            KeyMapping[] extended = Arrays.copyOf(current, current.length + mappings.length);
            int size = current.length;
            for (KeyMapping mapping : mappings) {
                if (!known.contains(mapping)) {
                    extended[size++] = mapping;
                }
            }
            field.set(options, Arrays.copyOf(extended, size));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            System.err.println("[weaver-nf] KEY MAPPINGS NOT ADDED reason=" + failure);
            return;
        }
        Map<String, String> saved = savedBindings(minecraft);
        for (KeyMapping mapping : mappings) {
            String value = saved.get(mapping.getName());
            if (value == null) {
                continue;
            }
            try {
                mapping.setKey(InputConstants.getKey(value));
            } catch (RuntimeException unknownKey) {
                // keep the default
            }
        }
        KeyMapping.resetMapping();
    }

    /** The {@code key_<name>:<key>} lines of {@code options.txt}. */
    private static Map<String, String> savedBindings(Minecraft minecraft) {
        Map<String, String> saved = new LinkedHashMap<>();
        try {
            Path file = minecraft.gameDirectory.toPath().resolve("options.txt");
            if (Files.isRegularFile(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int colon = line.indexOf(':');
                    if (line.startsWith("key_") && colon > 4) {
                        saved.put(line.substring(4, colon), line.substring(colon + 1));
                    }
                }
            }
        } catch (Exception unreadable) {
            // no saved bindings
        }
        return saved;
    }
}
