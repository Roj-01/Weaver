package net.weaver.api.game.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigType;
import net.weaver.api.config.ConfigValidationException;
import net.weaver.api.config.ConfigValue;

/**
 * A settings screen generated from a mod's {@link ConfigManager}: one row per
 * value, with an on/off button for booleans, a cycling button for enums, and a
 * text box for numbers, strings, and lists (comma-separated). Rows are named by
 * the translation {@code config.<modid>.<key>} (tooltip {@code config.<modid>.<key>.tooltip}),
 * or by the key made readable when there is none. A text box turns
 * red while its text is invalid (out of range, not a number, too long), with the
 * reason as its tooltip. "Save" applies and writes every value only when all
 * are valid; "Reset" puts every row back to its default; "Cancel" leaves the file
 * as it was. While the server's values are in effect the rows are read-only.
 */
public final class ConfigScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private final ConfigManager manager;
    private final Map<String, String> edits = new LinkedHashMap<>();
    private final boolean serverValues;
    private ValueList list;
    private Button saveButton;
    private Component status = Component.empty();

    public ConfigScreen(Screen parent, ConfigManager manager) {
        super(Component.literal(manager.spec().modId()));
        this.parent = parent;
        this.manager = manager;
        this.serverValues = manager.syncResult().isPresent();
        for (ConfigValue<?> value : manager.spec().values()) {
            edits.put(value.key(), value.text());
        }
    }

    /** The manager this screen edits. */
    public ConfigManager manager() {
        return manager;
    }

    /** The edited text of every value, by key, as the rows hold it now. */
    public Map<String, String> edits() {
        return java.util.Collections.unmodifiableMap(edits);
    }

    /** Sets the edited text of one row, as typing in it would. */
    public void edit(String key, String text) {
        if (edits.containsKey(key)) {
            edits.put(key, text);
            if (list != null) {
                rebuildRows();
            }
        }
    }

    /** The values whose edited text is invalid, with the reason. */
    public Map<String, String> problems() {
        Map<String, String> out = new LinkedHashMap<>();
        for (ConfigValue<?> value : manager.spec().values()) {
            String text = edits.get(value.key());
            // An untouched row keeps its value exactly; only edited text is parsed.
            String reason = value.text().equals(text) ? null : check(value, text);
            if (reason != null) {
                out.put(value.key(), reason);
            }
        }
        return out;
    }

    /**
     * Applies and writes every value when all are valid.
     *
     * @return true when saved; false (with the reason shown) otherwise
     */
    public boolean save() {
        if (serverValues) {
            status = Component.translatableWithFallback("weaver.config.server", "These values are set by the server");
            return false;
        }
        Map<String, String> problems = problems();
        if (!problems.isEmpty()) {
            Map.Entry<String, String> first = problems.entrySet().iterator().next();
            status = Component.literal(first.getKey() + ": " + first.getValue());
            return false;
        }
        try {
            // Only edited rows are applied: re-parsing an untouched list could change it
            // (an element with a comma in it, or surrounding spaces).
            for (ConfigValue<?> value : manager.spec().values()) {
                String text = edits.get(value.key());
                if (!value.text().equals(text)) {
                    value.setText(text);
                }
            }
            manager.save();
            status = Component.translatableWithFallback("weaver.config.saved", "Saved");
            return true;
        } catch (RuntimeException failure) {
            status = Component.literal(String.valueOf(failure.getMessage()));
            System.out.println("[weaver] CONFIG SAVE FAILED mod=" + manager.spec().modId() + " reason=" + failure);
            return false;
        }
    }

    /** Puts every row back to its default value (not saved until "Save"). */
    public void resetToDefaults() {
        for (ConfigValue<?> value : manager.spec().values()) {
            edits.put(value.key(), value.defaultText());
        }
        if (list != null) {
            rebuildRows();
        }
    }

    /**
     * The row's name: the translation of {@code config.<modid>.<key>} when the mod has
     * one, otherwise the last part of the key made readable ({@code dualWield} and
     * {@code dual_wield} become "Dual Wield").
     */
    static Component label(ConfigValue<?> value) {
        return Component.translatableWithFallback("config." + value.modId() + "." + value.key(), readable(value.key()));
    }

    /** The row's tooltip: {@code config.<modid>.<key>.tooltip}, otherwise the spec's description. */
    static Component description(ConfigValue<?> value) {
        return Component.translatableWithFallback("config." + value.modId() + "." + value.key() + ".tooltip",
                value.description());
    }

    /** {@code general.dualWield} to "Dual Wield". */
    static String readable(String key) {
        String last = key.substring(key.lastIndexOf('.') + 1);
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < last.length(); i++) {
            char c = last.charAt(i);
            if (c == '_' || c == '-') {
                upper = true;
                continue;
            }
            if (Character.isUpperCase(c) && out.length() > 0 && !Character.isUpperCase(last.charAt(i - 1))) {
                out.append(' ');
            } else if (upper && out.length() > 0) {
                out.append(' ');
            }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.length() == 0 ? key : out.toString();
    }

    private static String check(ConfigValue<?> value, String text) {
        try {
            value.parseText(text == null ? "" : text);
            return null;
        } catch (ConfigValidationException invalid) {
            return invalid.reason();
        } catch (RuntimeException invalid) {
            return String.valueOf(invalid.getMessage());
        }
    }

    @Override
    protected void init() {
        list = new ValueList(minecraft, width, Math.max(ROW_HEIGHT, height - 32 - 40), 32, ROW_HEIGHT);
        addRenderableWidget(list);
        rebuildRows();
        int y = height - 28;
        int center = width / 2;
        saveButton = Button.builder(Component.translatableWithFallback("weaver.config.save", "Save"), button -> {
            if (save()) {
                minecraft.setScreenAndShow(parent);
            }
        }).bounds(center - 154, y, 100, 20).build();
        saveButton.active = !serverValues;
        addRenderableWidget(saveButton);
        Button reset = Button.builder(Component.translatableWithFallback("weaver.config.reset", "Reset"),
                button -> resetToDefaults()).bounds(center - 50, y, 100, 20).build();
        reset.active = !serverValues;
        addRenderableWidget(reset);
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.cancel", "Cancel"),
                button -> onClose()).bounds(center + 54, y, 100, 20).build());
        if (serverValues) {
            status = Component.translatableWithFallback("weaver.config.server", "These values are set by the server");
        }
    }

    private void rebuildRows() {
        List<Row> rows = new ArrayList<>();
        for (ConfigValue<?> value : manager.spec().values()) {
            rows.add(new Row(value, widgetFor(value)));
        }
        list.replaceEntries(rows);
    }

    private AbstractWidget widgetFor(ConfigValue<?> value) {
        String key = value.key();
        String text = edits.get(key);
        Component label = label(value);
        AbstractWidget widget;
        if (value.type() == ConfigType.BOOLEAN) {
            widget = CycleButton.onOffBuilder(Boolean.parseBoolean(text)).displayOnlyValue()
                    .create(0, 0, 120, 20, label, (button, on) -> edits.put(key, String.valueOf(on)));
        } else if (value.type() == ConfigType.ENUM && !value.allowedValues().isEmpty()) {
            String initial = value.allowedValues().contains(text) ? text : value.allowedValues().get(0);
            widget = CycleButton.builder((String name) -> Component.literal(name), initial)
                    .withValues(value.allowedValues()).displayOnlyValue()
                    .create(0, 0, 120, 20, label, (button, name) -> edits.put(key, name));
        } else {
            EditBox box = new EditBox(font, 0, 0, 120, 20, label);
            box.setMaxLength(4096);
            box.setValue(text == null ? "" : text);
            box.setResponder(typed -> {
                edits.put(key, typed);
                colour(box, value, typed);
            });
            colour(box, value, box.getValue());
            widget = box;
        }
        widget.active = !serverValues;
        if (!value.description().isEmpty() && !(widget instanceof EditBox)) {
            widget.setTooltip(Tooltip.create(description(value)));
        }
        return widget;
    }

    private void colour(EditBox box, ConfigValue<?> value, String text) {
        String reason = check(value, text);
        box.setTextColor(reason == null ? 0xFFE0E0E0 : RED);
        String description = description(value).getString();
        String tip = reason == null ? description : (description.isEmpty() ? reason : description + "\n" + reason);
        box.setTooltip(tip.isEmpty() ? null : Tooltip.create(Component.literal(tip)));
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String modId = manager.spec().modId();
        String name = net.weaver.api.WeaverMods.find(modId).map(net.weaver.api.WeaverMods.Mod::name).orElse(modId);
        graphics.centeredText(font, Component.translatableWithFallback("weaver.config.title", "%s settings", name),
                width / 2, 8, WHITE);
        if (!manager.spec().description().isEmpty()) {
            graphics.centeredText(font, Component.literal(manager.spec().description()), width / 2, 19, GREY);
        }
        graphics.centeredText(font, status, width / 2, height - 40, problems().isEmpty() ? GREY : RED);
    }

    private final class ValueList extends ContainerObjectSelectionList<Row> {
        ValueList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, getWidth() - 24);
        }

        @Override
        public void replaceEntries(java.util.Collection<Row> entries) {
            super.replaceEntries(entries);
        }
    }

    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final ConfigValue<?> value;
        private final AbstractWidget widget;

        Row(ConfigValue<?> value, AbstractWidget widget) {
            this.value = value;
            this.widget = widget;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int widgetWidth = Math.min(160, getContentWidth() / 2);
            widget.setWidth(widgetWidth);
            widget.setX(getContentRight() - widgetWidth);
            widget.setY(getContentY());
            String label = font.plainSubstrByWidth(label(value).getString(), getContentWidth() - widgetWidth - 8);
            boolean changed = !value.text().equals(edits.get(value.key()));
            graphics.text(font, Component.literal(changed ? label + " *" : label), getContentX(),
                    getContentY() + 6, check(value, edits.get(value.key())) == null ? WHITE : RED);
            widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(widget);
        }
    }
}
