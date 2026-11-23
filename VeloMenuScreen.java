package com.velo.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class VeloMenuScreen extends Screen {
    private final Screen parent;

    private enum Category {
        FINDER("Finder & ESP", "Glowing ESP, Base & Spawner"),
        MOVEMENT("Movement", "Player & Freecam"),
        HAND("Hand", "Slow Swing & Viewmodel"),
        VISUALS("Visuals", "HUD & Overlay"),
        THEME("Theme", "Blue Appearance");

        final String label;
        final String subLabel;

        Category(String label, String subLabel) {
            this.label = label;
            this.subLabel = subLabel;
        }
    }

    private enum RowType {
        TOGGLE, SLIDER, MODE, ACTION
    }

    private static class SettingRow {
        final Category category;
        final String name;
        final String subtitle;
        final RowType type;

        Supplier<Boolean> boolGetter;
        Consumer<Boolean> boolSetter;
        Supplier<Float> floatGetter;
        Consumer<Float> floatSetter;
        float minVal, maxVal;
        String suffix;
        Supplier<String> modeGetter;
        Runnable action;

        int rowX, rowY, rowW, rowH;
        boolean visibleOnScreen = false;
        float toggleAnim = 0f;

        static SettingRow toggle(Category cat, String name, String subtitle, Supplier<Boolean> get, Consumer<Boolean> set) {
            SettingRow r = new SettingRow(cat, name, subtitle, RowType.TOGGLE);
            r.boolGetter = get;
            r.boolSetter = set;
            r.toggleAnim = get.get() ? 1f : 0f;
            return r;
        }

        static SettingRow slider(Category cat, String name, String subtitle, float min, float max, String suffix, Supplier<Float> get, Consumer<Float> set) {
            SettingRow r = new SettingRow(cat, name, subtitle, RowType.SLIDER);
            r.minVal = min;
            r.maxVal = max;
            r.suffix = suffix;
            r.floatGetter = get;
            r.floatSetter = set;
            return r;
        }

        static SettingRow mode(Category cat, String name, String subtitle, Supplier<String> get, Runnable cycle) {
            SettingRow r = new SettingRow(cat, name, subtitle, RowType.MODE);
            r.modeGetter = get;
            r.action = cycle;
            return r;
        }

        static SettingRow action(Category cat, String name, String subtitle, Supplier<String> btnLabel, Runnable run) {
            SettingRow r = new SettingRow(cat, name, subtitle, RowType.ACTION);
            r.modeGetter = btnLabel;
            r.action = run;
            return r;
        }

        private SettingRow(Category category, String name, String subtitle, RowType type) {
            this.category = category;
            this.name = name;
            this.subtitle = subtitle;
            this.type = type;
        }
    }

    private Category selectedCategory = Category.FINDER;
    private TextFieldWidget searchField;
    private final List<SettingRow> allRows = new ArrayList<>();
    private SettingRow draggingSlider = null;

    private int winX, winY, winW, winH;
    private final int sidebarW = 132;
    private int scrollOffset = 0;

    public VeloMenuScreen() {
        this(null);
    }

    public VeloMenuScreen(Screen parent) {
        super(Text.literal("VELO Client Meny"));
        this.parent = parent;
        buildRows();
    }

    private void buildRows() {
        allRows.clear();

        // Finder & ESP
        allRows.add(SettingRow.toggle(Category.FINDER, "Block ESP", "Glowing outline runt valda block",
                () -> VeloWorldFeatures.blockEspEnabled, v -> VeloWorldFeatures.blockEspEnabled = v));
        allRows.add(SettingRow.action(Category.FINDER, "Välj ESP-block (Alla)", "Sök bland alla block i Minecraft",
                () -> "Välj (" + VeloWorldFeatures.selectedEspBlocks.size() + ") →",
                () -> {
                    if (this.client != null) this.client.setScreen(new VeloBlockPickerScreen(this));
                }));
        allRows.add(SettingRow.slider(Category.FINDER, "ESP Radie", "Sökradie i block",
                16f, 80f, "m", () -> (float) VeloWorldFeatures.blockEspRange, v -> VeloWorldFeatures.blockEspRange = Math.round(v)));

        allRows.add(SettingRow.toggle(Category.FINDER, "Base Finder", "Glowing outline genom marken + varning",
                () -> VeloWorldFeatures.baseFinderEnabled, v -> {
                    VeloWorldFeatures.baseFinderEnabled = v;
                    if (v) VeloWorldFeatures.clearAlerts();
                }));
        allRows.add(SettingRow.slider(Category.FINDER, "Bas Kistor/Shulkers", "Antal för bas-varning",
                2f, 20f, "", () -> (float) VeloWorldFeatures.baseChestThreshold, v -> VeloWorldFeatures.baseChestThreshold = Math.round(v)));
        allRows.add(SettingRow.slider(Category.FINDER, "Redstone Gräns", "Block för redstone-farm varning",
                8f, 60f, "", () -> (float) VeloWorldFeatures.redstoneFarmThreshold, v -> VeloWorldFeatures.redstoneFarmThreshold = Math.round(v)));

        allRows.add(SettingRow.toggle(Category.FINDER, "Spawner Finder", "Glowing outline runt monster spawners",
                () -> VeloWorldFeatures.spawnerFinderEnabled, v -> {
                    VeloWorldFeatures.spawnerFinderEnabled = v;
                    if (v) VeloWorldFeatures.clearAlerts();
                }));
        allRows.add(SettingRow.mode(Category.FINDER, "Spawner Typ", "Välj vilken mob-spawner som söks",
                VeloWorldFeatures::getSelectedSpawnerLabel,
                () -> {
                    VeloWorldFeatures.selectedSpawnerTypeIndex = (VeloWorldFeatures.selectedSpawnerTypeIndex + 1) % VeloWorldFeatures.SPAWNER_TYPES.length;
                    VeloWorldFeatures.clearAlerts();
                }));

        // Movement
        allRows.add(SettingRow.toggle(Category.MOVEMENT, "Freecam (F6)", "Flyg fritt med kameran",
                FreecamManager::isEnabled, FreecamManager::setEnabled));
        allRows.add(SettingRow.slider(Category.MOVEMENT, "Freecam Speed", "Hastighet i Freecam",
                0.2f, 3.0f, "x", () -> FreecamManager.speed, v -> FreecamManager.speed = v));
        allRows.add(SettingRow.toggle(Category.MOVEMENT, "Toggle Sprint", "Automatisk sprint framåt",
                () -> VeloClient.toggleSprint, v -> VeloClient.toggleSprint = v));

        // Hand & Slow Swing
        allRows.add(SettingRow.toggle(Category.HAND, "Slow Swing", "Gör hand-svingningen mjuk & långsam",
                () -> VeloClient.slowSwing, v -> VeloClient.slowSwing = v));
        allRows.add(SettingRow.slider(Category.HAND, "Swing Speed", "Hastighet (6 normal – 30 långsam)",
                6f, 30f, "", () -> (float) VeloClient.swingDuration, v -> VeloClient.swingDuration = Math.round(v)));
        allRows.add(SettingRow.toggle(Category.HAND, "Slim Hand", "Gör handen smalare och renare",
                () -> VeloClient.slimHand, v -> VeloClient.slimHand = v));
        allRows.add(SettingRow.slider(Category.HAND, "Hand Scale", "Storlek på hand/vapen",
                0.40f, 1.00f, "", () -> VeloClient.handScale, v -> VeloClient.handScale = v));
        allRows.add(SettingRow.slider(Category.HAND, "Hand X-Offset", "Flytta i sidled",
                -0.25f, 0.25f, "", () -> VeloClient.handOffsetX, v -> VeloClient.handOffsetX = v));
        allRows.add(SettingRow.slider(Category.HAND, "Hand Y-Offset", "Flytta upp/ner",
                -0.25f, 0.25f, "", () -> VeloClient.handOffsetY, v -> VeloClient.handOffsetY = v));

        // Visuals
        allRows.add(SettingRow.toggle(Category.VISUALS, "VELO HUD", "Visar blå VELO-panel i hörnet",
                () -> VeloClient.hudEnabled, v -> VeloClient.hudEnabled = v));
        allRows.add(SettingRow.toggle(Category.VISUALS, "FPS Counter", "Visar FPS i HUD",
                () -> VeloClient.showFps, v -> VeloClient.showFps = v));
        allRows.add(SettingRow.toggle(Category.VISUALS, "Coordinates", "Visar XYZ i HUD",
                () -> VeloClient.showCoords, v -> VeloClient.showCoords = v));

        // Theme
        allRows.add(SettingRow.mode(Category.THEME, "Blue Accent", "Välj blå färgnyans",
                VeloClient::getThemeName, () -> VeloClient.blueThemeIndex = (VeloClient.blueThemeIndex + 1) % 3));
    }

    @Override
    protected void init() {
        this.winW = Math.min(488, this.width - 24);
        this.winH = Math.min(292, this.height - 24);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        int contentX = this.winX + this.sidebarW;
        int contentW = this.winW - this.sidebarW;

        String prevSearch = (searchField != null) ? searchField.getText() : "";
        searchField = new TextFieldWidget(this.textRenderer, contentX + 18, this.winY + 12, contentW - 52, 16, Text.literal("Sök"));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Sök inställning..."));
        searchField.setText(prevSearch);
        this.addDrawableChild(searchField);
    }

    private List<SettingRow> getFilteredRows() {
        String query = (searchField != null) ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<SettingRow> list = new ArrayList<>();
        for (SettingRow row : allRows) {
            if (query.isEmpty()) {
                if (row.category == selectedCategory) list.add(row);
            } else if (row.name.toLowerCase(Locale.ROOT).contains(query) || row.subtitle.toLowerCase(Locale.ROOT).contains(query)) {
                list.add(row);
            }
        }
        return list;
    }

    private void updateRowLayout(List<SettingRow> visibleRows) {
        for (SettingRow r : allRows) r.visibleOnScreen = false;

        int contentX = winX + sidebarW;
        int contentW = winW - sidebarW;
        int listY = winY + 62;
        int rowHeight = 40;
        int maxBottom = winY + winH - 10;

        int maxScroll = Math.max(0, visibleRows.size() * rowHeight - (maxBottom - listY));
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

        for (int i = 0; i < visibleRows.size(); i++) {
            SettingRow row = visibleRows.get(i);
            int ry = listY + i * rowHeight - scrollOffset;
            if (ry >= listY - 4 && ry + rowHeight - 6 <= maxBottom) {
                row.rowX = contentX + 12;
                row.rowY = ry;
                row.rowW = contentW - 24;
                row.rowH = rowHeight - 6;
                row.visibleOnScreen = true;
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88040914);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int primary = VeloClient.getPrimaryBlue();
        int accent = VeloClient.getAccentColor();

        // Anti-aliased modernt huvudfönster
        VeloUiRenderer.drawSmoothPanel(context, winX, winY, winW, winH, 0xF20B1324, 0xFF1E3A6E);
        VeloUiRenderer.drawSmoothRect(context, winX + 1, winY + 1, sidebarW - 1, winH - 2, 0xF2070D1B);
        context.fill(winX + sidebarW, winY + 2, winX + sidebarW + 1, winY + winH - 2, 0xFF1B2B4B);

        context.drawTextWithShadow(this.textRenderer, "§lVELO §r§bCLIENT", winX + 16, winY + 16, accent);
        context.drawTextWithShadow(this.textRenderer, "§7Clean v1.8", winX + 16, winY + 28, 0xFF64748B);

        // Sidopanelens kategorier
        Category[] categories = Category.values();
        int catStartY = winY + 50;
        int catH = 28;
        for (int i = 0; i < categories.length; i++) {
            Category cat = categories[i];
            int cy = catStartY + i * (catH + 4);
            int cx = winX + 8;
            int cw = sidebarW - 16;
            boolean selected = (cat == selectedCategory) && (searchField == null || searchField.getText().isBlank());
            boolean hovered = mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + catH;

            if (selected) {
                VeloUiRenderer.drawSmoothRect(context, cx, cy, cw, catH, 0xFF14233F);
                context.fill(cx + 2, cy + 6, cx + 4, cy + catH - 6, accent);
            } else if (hovered) {
                VeloUiRenderer.drawSmoothRect(context, cx, cy, cw, catH, 0x8014233F);
            }
            context.drawTextWithShadow(this.textRenderer, cat.label, cx + 12, cy + 10, selected ? 0xFFFFFFFF : 0xFF94A3B8);
        }

        // Höger innehållsyta
        int contentX = winX + sidebarW;
        int contentW = winW - sidebarW;
        VeloUiRenderer.drawSmoothPanel(context, contentX + 12, winY + 8, contentW - 40, 20, 0xFF101B33, 0xFF1E3A6E);

        int closeX = winX + winW - 22;
        int closeY = winY + 8;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 20;
        context.drawTextWithShadow(this.textRenderer, "✕", closeX + 4, closeY + 6, closeHover ? accent : 0xFF94A3B8);

        context.drawTextWithShadow(this.textRenderer, "§l" + selectedCategory.label + "  §b→  §7" + selectedCategory.subLabel,
                contentX + 14, winY + 40, 0xFFFFFFFF);

        List<SettingRow> visibleRows = getFilteredRows();
        updateRowLayout(visibleRows);

        for (SettingRow row : visibleRows) {
            if (!row.visibleOnScreen) continue;
            boolean hovered = mouseX >= row.rowX && mouseX <= row.rowX + row.rowW && mouseY >= row.rowY && mouseY <= row.rowY + row.rowH;
            VeloUiRenderer.drawSmoothPanel(context, row.rowX, row.rowY, row.rowW, row.rowH,
                    hovered ? 0xFF152440 : 0xCC101A30, hovered ? accent : 0xFF1A2946);

            context.drawTextWithShadow(this.textRenderer, row.name, row.rowX + 10, row.rowY + 6, 0xFFF8FAFC);
            context.drawTextWithShadow(this.textRenderer, "§7" + row.subtitle, row.rowX + 10, row.rowY + 18, 0xFF64748B);

            if (row.type == RowType.TOGGLE) {
                boolean active = row.boolGetter.get();
                row.toggleAnim += ((active ? 1f : 0f) - row.toggleAnim) * 0.3f;

                int swW = 34, swH = 16;
                int swX = row.rowX + row.rowW - swW - 10;
                int swY = row.rowY + (row.rowH - swH) / 2;
                int swBg = VeloUiRenderer.lerpColor(0xFF1E293B, primary, row.toggleAnim);
                VeloUiRenderer.drawSmoothRect(context, swX, swY, swW, swH, swBg);

                int knobX = swX + 2 + (int) ((swW - 16) * row.toggleAnim);
                VeloUiRenderer.drawSmoothRect(context, knobX, swY + 2, 12, 12, 0xFFFFFFFF);
            } else if (row.type == RowType.SLIDER) {
                float val = row.floatGetter.get();
                float pct = MathHelper.clamp((val - row.minVal) / (row.maxVal - row.minVal), 0f, 1f);
                int trackW = 84, trackH = 6;
                int trackX = row.rowX + row.rowW - trackW - 12;
                int trackY = row.rowY + (row.rowH - trackH) / 2 + 1;

                String valStr = (row.maxVal - row.minVal >= 5f)
                        ? String.format(Locale.ROOT, "%d%s", Math.round(val), row.suffix)
                        : String.format(Locale.ROOT, "%.2f%s", val, row.suffix);
                context.drawTextWithShadow(this.textRenderer, valStr, trackX - this.textRenderer.getWidth(valStr) - 8, trackY - 2, accent);
                VeloUiRenderer.drawSmoothRect(context, trackX, trackY, trackW, trackH, 0xFF1E293B);
                int fillW = Math.max(4, (int) (trackW * pct));
                VeloUiRenderer.drawSmoothRect(context, trackX, trackY, fillW, trackH, accent);
                VeloUiRenderer.drawSmoothRect(context, trackX + (int) (trackW * pct) - 4, trackY - 2, 8, 10, 0xFFFFFFFF);
            } else if (row.type == RowType.MODE || row.type == RowType.ACTION) {
                String modeVal = row.modeGetter.get();
                int pillW = this.textRenderer.getWidth(modeVal) + 16;
                int pillH = 18;
                int pillX = row.rowX + row.rowW - pillW - 10;
                int pillY = row.rowY + (row.rowH - pillH) / 2;
                int pillFill = (row.type == RowType.ACTION && hovered) ? primary : 0xFF162542;
                VeloUiRenderer.drawSmoothPanel(context, pillX, pillY, pillW, pillH, pillFill, accent);
                context.drawTextWithShadow(this.textRenderer, modeVal, pillX + 8, pillY + 5, 0xFFFFFFFF);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (click.button() == 0) {
            int closeX = winX + winW - 22;
            int closeY = winY + 8;
            if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 20) {
                if (this.client != null) this.client.setScreen(this.parent);
                return true;
            }

            Category[] categories = Category.values();
            int catStartY = winY + 50;
            int catH = 28;
            for (int i = 0; i < categories.length; i++) {
                int cy = catStartY + i * (catH + 4);
                int cx = winX + 8;
                int cw = sidebarW - 16;
                if (mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + catH) {
                    selectedCategory = categories[i];
                    scrollOffset = 0;
                    if (searchField != null) searchField.setText("");
                    return true;
                }
            }

            List<SettingRow> visibleRows = getFilteredRows();
            updateRowLayout(visibleRows);

            for (SettingRow row : visibleRows) {
                if (!row.visibleOnScreen) continue;
                if (mouseX >= row.rowX && mouseX <= row.rowX + row.rowW && mouseY >= row.rowY && mouseY <= row.rowY + row.rowH) {
                    if (searchField != null) searchField.setFocused(false);
                    if (row.type == RowType.TOGGLE) {
                        row.boolSetter.accept(!row.boolGetter.get());
                        return true;
                    } else if (row.type == RowType.MODE || row.type == RowType.ACTION) {
                        row.action.run();
                        return true;
                    } else if (row.type == RowType.SLIDER) {
                        draggingSlider = row;
                        updateSlider(row, mouseX);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (click.button() == 0 && draggingSlider != null) {
            updateSlider(draggingSlider, click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && draggingSlider != null) {
            draggingSlider = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * 20);
        return true;
    }

    private void updateSlider(SettingRow row, double mouseX) {
        int trackW = 84;
        int trackX = row.rowX + row.rowW - trackW - 12;
        float pct = MathHelper.clamp((float) (mouseX - trackX) / (float) trackW, 0.0f, 1.0f);
        row.floatSetter.accept(row.minVal + pct * (row.maxVal - row.minVal));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
