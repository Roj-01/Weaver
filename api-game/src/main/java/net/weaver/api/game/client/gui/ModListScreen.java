package net.weaver.api.game.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.weaver.api.WeaverApi;
import net.weaver.api.WeaverMods;

/**
 * The in-game list of loaded mods, opened from the "Mods" button on the title
 * screen and the pause menu: each mod's name, version, authors, license,
 * homepage, and description from its {@code weaver.mod.json}, a search box, and a
 * button to its settings screen ({@link ConfigScreens}).
 */
public final class ModListScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int GOLD = 0xFFFFD27F;

    private final Screen parent;
    private EditBox search;
    private ModList list;
    private Button configButton;
    private WeaverMods.Mod selected;

    public ModListScreen(Screen parent) {
        super(Component.translatableWithFallback("weaver.mods.title", "Mods"));
        this.parent = parent;
    }

    /** Opens the mod list over the current screen. */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreenAndShow(new ModListScreen(minecraft.gui.screen()));
    }

    /** The entries shown: Minecraft and Weaver first, then every loaded mod in load order. */
    public static List<WeaverMods.Mod> entries() {
        List<WeaverMods.Mod> out = new ArrayList<>();
        out.add(new WeaverMods.Mod("minecraft", WeaverApi.GAME_VERSION, "Minecraft", "both",
                "The game.", List.of("Mojang Studios"), "", "https://www.minecraft.net"));
        out.add(new WeaverMods.Mod("weaver", WeaverApi.VERSION, "Weaver", "both",
                "The mod loader: events, registries, configuration, networking, and client APIs.",
                List.of(), "LGPL-2.1-only", ""));
        out.addAll(WeaverMods.loaded());
        return out;
    }

    /** The selected entry, or null. */
    public WeaverMods.Mod selected() {
        return selected;
    }

    /** The entries the list shows now (after the search filter). */
    public int shownCount() {
        return list == null ? 0 : list.children().size();
    }

    /** Types {@code text} into the search box (after {@code init}). */
    public void search(String text) {
        if (search != null) {
            search.setValue(text);
            refill();
        }
    }

    /** Selects the mod with {@code id} when it is shown; returns whether it was. */
    public boolean select(String id) {
        if (list != null) {
            for (ModEntry entry : list.children()) {
                if (entry.mod.id().equals(id)) {
                    select(entry);
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the settings button is enabled for the selected mod. */
    public boolean settingsAvailable() {
        return configButton != null && configButton.active;
    }

    @Override
    protected void init() {
        int listWidth = Math.max(120, Math.min(200, width / 3));
        search = new EditBox(font, 8, 24, listWidth, 18, Component.translatableWithFallback("weaver.mods.search", "Search"));
        search.setHint(Component.translatableWithFallback("weaver.mods.search", "Search"));
        search.setResponder(text -> refill());
        addRenderableWidget(search);
        list = new ModList(minecraft, listWidth, Math.max(40, height - 48 - 36), 48, 24);
        list.setX(8);
        addRenderableWidget(list);
        int detailX = listWidth + 20;
        configButton = Button.builder(Component.translatableWithFallback("weaver.mods.config", "Settings"),
                button -> openConfig()).bounds(detailX, height - 28, 100, 20).build();
        addRenderableWidget(configButton);
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.done", "Done"), button -> onClose())
                .bounds(width - 108, height - 28, 100, 20).build());
        refill();
        if (selected == null && !list.children().isEmpty()) {
            select(list.children().get(0));
        } else {
            updateButtons();
        }
    }

    private void refill() {
        String filter = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<ModEntry> entries = new ArrayList<>();
        for (WeaverMods.Mod mod : entries()) {
            if (filter.isEmpty() || mod.id().toLowerCase(Locale.ROOT).contains(filter)
                    || mod.name().toLowerCase(Locale.ROOT).contains(filter)) {
                entries.add(new ModEntry(mod));
            }
        }
        list.replaceEntries(entries);
        for (ModEntry entry : entries) {
            if (selected != null && entry.mod.id().equals(selected.id())) {
                list.setSelected(entry);
            }
        }
    }

    private void select(ModEntry entry) {
        list.setSelected(entry);
        selected = entry == null ? null : entry.mod;
        updateButtons();
    }

    private void updateButtons() {
        if (configButton != null) {
            configButton.active = selected != null && ConfigScreens.has(selected.id());
        }
    }

    private void openConfig() {
        if (selected != null) {
            ConfigScreens.create(selected.id(), this).ifPresent(screen -> minecraft.setScreenAndShow(screen));
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 8, WHITE);
        if (selected == null) {
            return;
        }
        int x = list.getRight() + 12;
        int textWidth = Math.max(60, width - x - 12);
        int y = 26;
        graphics.text(font, Component.literal(selected.name()), x, y, WHITE);
        y += 14;
        y = line(graphics, "weaver.mods.version", "Version", selected.version(), x, y);
        y = line(graphics, "weaver.mods.id", "ID", selected.id(), x, y);
        y = line(graphics, "weaver.mods.side", "Side", selected.side(), x, y);
        if (!selected.authors().isEmpty()) {
            y = line(graphics, "weaver.mods.authors", "Authors", String.join(", ", selected.authors()), x, y);
        }
        if (!selected.license().isEmpty()) {
            y = line(graphics, "weaver.mods.license", "License", selected.license(), x, y);
        }
        if (!selected.homepage().isEmpty()) {
            y = line(graphics, "weaver.mods.homepage", "Homepage", selected.homepage(), x, y);
        }
        if (!selected.description().isEmpty()) {
            y += 6;
            graphics.textWithWordWrap(font, FormattedText.of(selected.description()), x, y, textWidth, WHITE);
        }
    }

    private int line(GuiGraphicsExtractor graphics, String key, String fallback, String value, int x, int y) {
        Component label = Component.translatableWithFallback(key, fallback);
        graphics.text(font, label, x, y, GOLD);
        graphics.text(font, Component.literal(value), x + font.width(label) + 6, y, GREY);
        return y + 11;
    }

    private final class ModList extends ObjectSelectionList<ModEntry> {
        ModList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 12;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }

        @Override
        public void replaceEntries(java.util.Collection<ModEntry> entries) {
            super.replaceEntries(entries);
        }
    }

    private final class ModEntry extends ObjectSelectionList.Entry<ModEntry> {
        private final WeaverMods.Mod mod;

        ModEntry(WeaverMods.Mod mod) {
            this.mod = mod;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.text(font, Component.literal(font.plainSubstrByWidth(mod.name(), getContentWidth())),
                    getContentX(), getContentY() + 1, WHITE);
            graphics.text(font, Component.literal(mod.version()), getContentX(), getContentY() + 12, GREY);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
            select(this);
            if (doubled && ConfigScreens.has(mod.id())) {
                openConfig();
            }
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(mod.name() + " " + mod.version());
        }
    }
}
