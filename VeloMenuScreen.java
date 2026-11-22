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
        MOVEMENT("Movement", "Player & Camera"),
        VISUALS("Visuals", "HUD & Overlay"),
        HAND("Hand", "Viewmodel"),
        THEME("Theme", "Blue Appearance");

        final String label;
        final String subLabel;

        Category(String label, String subLabel) {
            this.label = label;
            this.subLabel = subLabel;
        }
    }

    private enum RowType {
        TOGGLE,
        SLIDER,
        MODE
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
        Runnable modeCycler;

        int rowX, rowY, rowW, rowH;

        static SettingRow toggle(Category cat, String name, String subtitle, Supplier<Boolean> get, Consumer<Boolean> set) {
            SettingRow r = new SettingRow(cat, name, subtitle, RowType.TOGGLE);
            r.boolGetter = get;
            r.boolSetter = set;
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
            r.modeCycler = cycle;
            return r;
        }

        private SettingRow(Category category, String name, String subtitle, RowType type) {
            this.category = category;
            this.name = name;
            this.subtitle = subtitle;
            this.type = type;
        }
    }

    private Category selectedCategory = Category.MOVEMENT;
    private TextFieldWidget searchField;
    private final List<SettingRow> allRows = new ArrayList<>();
    private SettingRow draggingSlider = null;

    // Layout-koordinater
    private int winX, winY, winW, winH;
    private int sidebarW = 128;

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

        // Movement & Camera
        allRows.add(SettingRow.toggle(
                Category.MOVEMENT,
                "Freecam (F6)",
                "Flyg fritt med kameran utan att flytta spelaren",
                FreecamManager::isEnabled,
                FreecamManager::setEnabled
        ));
        allRows.add(SettingRow.slider(
                Category.MOVEMENT,
                "Freecam Speed",
                "Hastighet när du flyger i Freecam",
                0.2f, 3.0f, "x",
                () -> FreecamManager.speed,
                val -> FreecamManager.speed = val
        ));
        allRows.add(SettingRow.toggle(
                Category.MOVEMENT,
                "Toggle Sprint",
                "Håller sprint aktiv automatiskt när du går framåt",
                () -> VeloClient.toggleSprint,
                val -> VeloClient.toggleSprint = val
        ));

        // Visuals (HUD)
        allRows.add(SettingRow.toggle(
                Category.VISUALS,
                "VELO HUD",
                "Visar den blå VELO-panelen uppe till vänster",
                () -> VeloClient.hudEnabled,
                val -> VeloClient.hudEnabled = val
        ));
        allRows.add(SettingRow.toggle(
                Category.VISUALS,
                "FPS Counter",
                "Visar nuvarande bildfrekvens (FPS) i HUD",
                () -> VeloClient.showFps,
                val -> VeloClient.showFps = val
        ));
        allRows.add(SettingRow.toggle(
                Category.VISUALS,
                "Coordinates",
                "Visar dina XYZ-koordinater i HUD",
                () -> VeloClient.showCoords,
                val -> VeloClient.showCoords = val
        ));

        // Hand / Viewmodel (så handen inte är fet)
        allRows.add(SettingRow.toggle(
                Category.HAND,
                "Slim Hand",
                "Gör handen och vapen smalare och snyggare",
                () -> VeloClient.slimHand,
                val -> VeloClient.slimHand = val
        ));
        allRows.add(SettingRow.slider(
                Category.HAND,
                "Hand Scale",
                "Storlek på din hand och dina föremål",
                0.40f, 1.00f, "",
                () -> VeloClient.handScale,
                val -> VeloClient.handScale = val
        ));
        allRows.add(SettingRow.slider(
                Category.HAND,
                "Hand X-Offset",
                "Flytta handen i sidled",
                -0.25f, 0.25f, "",
                () -> VeloClient.handOffsetX,
                val -> VeloClient.handOffsetX = val
        ));
        allRows.add(SettingRow.slider(
                Category.HAND,
                "Hand Y-Offset",
                "Flytta handen uppåt eller nedåt",
                -0.25f, 0.25f, "",
                () -> VeloClient.handOffsetY,
                val -> VeloClient.handOffsetY = val
        ));

        // Theme (Blå nyanser)
        allRows.add(SettingRow.mode(
                Category.THEME,
                "Blue Accent",
                "Välj blå färgnyans för hela VELO Client",
                VeloClient::getThemeName,
                () -> VeloClient.blueThemeIndex = (VeloClient.blueThemeIndex + 1) % 3
        ));
    }

    @Override
    protected void init() {
        this.winW = Math.min(460, this.width - 24);
        this.winH = Math.min(265, this.height - 24);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        int contentX = this.winX + this.sidebarW;
        int contentW = this.winW - this.sidebarW;

        String prevSearch = (searchField != null) ? searchField.getText() : "";
        searchField = new TextFieldWidget(
                this.textRenderer,
                contentX + 30,
                this.winY + 12,
                contentW - 64,
                16,
                Text.literal("Search element")
        );
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Search element..."));
        searchField.setText(prevSearch);
        this.addDrawableChild(searchField);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.client != null && this.client.world == null) {
            context.fillGradient(0, 0, this.width, this.height, 0xFF050B18, 0xFF0C1E3E);
        } else {
            context.fill(0, 0, this.width, this.height, 0x99040914);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int primary = VeloClient.getPrimaryBlue();
        int accent = VeloClient.getAccentColor();

        int bgSidebar = 0xF2080E1D;
        int bgMain = 0xF20D1528;
        int bgCard = 0xFF131F38;
        int borderLine = 0xFF1B2B4B;

        // Yttre fönsterskugga & ram
        context.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x66000000);
        context.fill(winX - 1, winY - 1, winX + winW + 1, winY + winH + 1, borderLine);

        // Vänster sidopanel & höger huvudyta
        context.fill(winX, winY, winX + sidebarW, winY + winH, bgSidebar);
        context.fill(winX + sidebarW, winY, winX + winW, winY + winH, bgMain);
        context.fill(winX + sidebarW, winY, winX + sidebarW + 1, winY + winH, borderLine);

        // Blå topp-accentlinje
        context.fill(winX, winY, winX + winW, winY + 2, accent);

        // --- VÄNSTER SIDOPANEL: BLADE-INSPIRERAD VELO-LOGGA ---
        drawVeloLogo(context, winX + 16, winY + 15, primary, accent);

        // Kategorier i sidopanelen
        Category[] categories = Category.values();
        int catStartY = winY + 52;
        int catH = 26;

        for (int i = 0; i < categories.length; i++) {
            Category cat = categories[i];
            int cy = catStartY + i * (catH + 4);
            int cx = winX + 8;
            int cw = sidebarW - 16;

            boolean isSelected = (cat == selectedCategory) && (searchField == null || searchField.getText().isBlank());
            boolean isHovered = mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + catH;

            if (isSelected) {
                context.fill(cx, cy, cx + cw, cy + catH, bgCard);
                // Vertikal blå indikator-pill till vänster (som i BLADE-inspo)
                context.fill(cx, cy + 5, cx + 3, cy + catH - 5, accent);
            } else if (isHovered) {
                context.fill(cx, cy, cx + cw, cy + catH, 0x80131F38);
            }

            drawCategoryIcon(context, cat, cx + 10, cy + 8, isSelected ? accent : 0xFF64748B);
            context.drawTextWithShadow(
                    this.textRenderer,
                    cat.label,
                    cx + 26,
                    cy + 9,
                    isSelected ? 0xFFFFFFFF : 0xFF94A3B8
            );
        }

        // Liten hjälptext längst ner i sidopanelen
        context.drawTextWithShadow(this.textRenderer, "§7R-Shift: Meny", winX + 14, winY + winH - 24, 0xFF64748B);
        context.drawTextWithShadow(this.textRenderer, "§7F6: Freecam", winX + 14, winY + winH - 13, 0xFF64748B);

        // --- HÖGER PANEL: SÖKFÄLT + BREADCRUMB + INSTÄLLNINGAR ---
        int contentX = winX + sidebarW;
        int contentW = winW - sidebarW;

        // Sökruta högst upp
        int searchBoxX = contentX + 12;
        int searchBoxY = winY + 8;
        int searchBoxW = contentW - 38;
        int searchBoxH = 20;
        context.fill(searchBoxX, searchBoxY, searchBoxX + searchBoxW, searchBoxY + searchBoxH, bgCard);
        context.fill(searchBoxX, searchBoxY + searchBoxH - 1, searchBoxX + searchBoxW, searchBoxY + searchBoxH, borderLine);

        // Förstoringsglas-ikon
        drawSearchIcon(context, searchBoxX + 7, searchBoxY + 6, 0xFF64748B);

        // Stäng-knapp (X) uppe till höger
        int closeX = winX + winW - 22;
        int closeY = winY + 8;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 20;
        context.drawTextWithShadow(this.textRenderer, "✕", closeX + 4, closeY + 6, closeHover ? accent : 0xFF94A3B8);

        // Breadcrumb: "Movement  ->  Player & Camera"
        String query = (searchField != null) ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        int breadY = winY + 36;
        drawCategoryIcon(context, selectedCategory, contentX + 14, breadY, accent);
        if (query.isEmpty()) {
            context.drawTextWithShadow(
                    this.textRenderer,
                    "§f" + selectedCategory.label + "  §b→  §7" + selectedCategory.subLabel,
                    contentX + 30,
                    breadY + 1,
                    0xFFFFFFFF
            );
        } else {
            context.drawTextWithShadow(
                    this.textRenderer,
                    "§fSearch  §b→  §7"" + searchField.getText() + """,
                    contentX + 30,
                    breadY + 1,
                    0xFFFFFFFF
            );
        }

        // Sektionsrubrik (som "Rotation settings" i BLADE-inspo)
        int secY = winY + 54;
        context.drawTextWithShadow(this.textRenderer, "§lGeneral settings", contentX + 14, secY, 0xFFE2E8F0);

        // Filtrera och rita inställningsrader
        List<SettingRow> visibleRows = new ArrayList<>();
        for (SettingRow row : allRows) {
            if (query.isEmpty()) {
                if (row.category == selectedCategory) visibleRows.add(row);
            } else {
                if (row.name.toLowerCase(Locale.ROOT).contains(query) || row.subtitle.toLowerCase(Locale.ROOT).contains(query)) {
                    visibleRows.add(row);
                }
            }
        }

        int listY = winY + 70;
        int rowHeight = 38;

        for (int i = 0; i < visibleRows.size(); i++) {
            SettingRow row = visibleRows.get(i);
            int ry = listY + i * rowHeight;
            if (ry + rowHeight > winY + winH - 6) break;

            int rx = contentX + 12;
            int rw = contentW - 24;
            row.rowX = rx;
            row.rowY = ry;
            row.rowW = rw;
            row.rowH = rowHeight - 4;

            boolean hovered = mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + row.rowH;
            context.fill(rx, ry, rx + rw, ry + row.rowH, hovered ? 0xFF15233E : 0x99111B30);
            context.fill(rx, ry + row.rowH, rx + rw, ry + row.rowH + 1, borderLine);

            // Titel + undertext till vänster
            context.drawTextWithShadow(this.textRenderer, row.name, rx + 10, ry + 7, 0xFFF1F5F9);
            context.drawTextWithShadow(this.textRenderer, "§7" + row.subtitle, rx + 10, ry + 19, 0xFF64748B);

            // Kontroll till höger beroende på typ
            if (row.type == RowType.TOGGLE) {
                boolean active = row.boolGetter.get();
                int swW = 34;
                int swH = 16;
                int swX = rx + rw - swW - 10;
                int swY = ry + (row.rowH - swH) / 2;

                context.fill(swX, swY, swX + swW, swY + swH, active ? primary : 0xFF1E293B);
                context.fill(swX, swY, swX + swW, swY + 1, active ? accent : 0xFF334155);

                int knobSize = 12;
                int knobX = active ? (swX + swW - knobSize - 2) : (swX + 2);
                int knobY = swY + 2;
                context.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, active ? 0xFFFFFFFF : 0xFF94A3B8);

            } else if (row.type == RowType.SLIDER) {
                float val = row.floatGetter.get();
                float pct = MathHelper.clamp((val - row.minVal) / (row.maxVal - row.minVal), 0.0f, 1.0f);

                int trackW = 86;
                int trackH = 4;
                int trackX = rx + rw - trackW - 12;
                int trackY = ry + (row.rowH - trackH) / 2 + 2;

                String valStr = String.format(Locale.ROOT, "%.2f%s", val, row.suffix);
                int valW = this.textRenderer.getWidth(valStr);
                context.drawTextWithShadow(this.textRenderer, valStr, trackX - valW - 8, trackY - 3, accent);

                // Bakgrundsspår + blå fyllnad + vit knob (som i BLADE-inspo)
                context.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF1E293B);
                int fillW = (int) (trackW * pct);
                context.fill(trackX, trackY, trackX + fillW, trackY + trackH, accent);

                int thumbX = trackX + fillW - 4;
                int thumbY = trackY - 3;
                context.fill(thumbX, thumbY, thumbX + 8, thumbY + 10, 0xFFFFFFFF);

            } else if (row.type == RowType.MODE) {
                String modeVal = row.modeGetter.get() + "  ⌄";
                int pillW = this.textRenderer.getWidth(modeVal) + 16;
                int pillH = 18;
                int pillX = rx + rw - pillW - 10;
                int pillY = ry + (row.rowH - pillH) / 2;

                context.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0xFF1B2B4B);
                context.fill(pillX, pillY, pillX + pillW, pillY + 1, accent);
                context.drawTextWithShadow(this.textRenderer, modeVal, pillX + 8, pillY + 5, 0xFFFFFFFF);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawVeloLogo(DrawContext context, int x, int y, int primary, int accent) {
        // Skarp geometrisk blixt/V-ikon i blått (inspo från BLADE-ikonen)
        context.fill(x, y + 2, x + 12, y + 5, accent);
        context.fill(x + 3, y + 5, x + 10, y + 8, primary);
        context.fill(x + 1, y + 8, x + 11, y + 11, accent);
        context.fill(x + 4, y + 11, x + 8, y + 15, primary);

        context.drawTextWithShadow(this.textRenderer, "§lVELO", x + 18, y + 4, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§bCLIENT", x + 50, y + 4, accent);
    }

    private void drawCategoryIcon(DrawContext context, Category cat, int x, int y, int color) {
        switch (cat) {
            case MOVEMENT -> {
                // Dubbelpil / vinge
                context.fill(x + 1, y + 2, x + 8, y + 4, color);
                context.fill(x + 3, y + 5, x + 10, y + 7, color);
                context.fill(x + 1, y + 8, x + 8, y + 10, color);
            }
            case VISUALS -> {
                // Cirkel / öga
                context.fill(x + 2, y + 1, x + 8, y + 2, color);
                context.fill(x + 1, y + 2, x + 2, y + 8, color);
                context.fill(x + 8, y + 2, x + 9, y + 8, color);
                context.fill(x + 2, y + 8, x + 8, y + 9, color);
                context.fill(x + 4, y + 4, x + 6, y + 6, color);
            }
            case HAND -> {
                // Kamera / hand-kub
                context.fill(x + 1, y + 3, x + 7, y + 8, color);
                context.fill(x + 8, y + 4, x + 10, y + 7, color);
            }
            case THEME -> {
                // Palett / rader
                context.fill(x + 1, y + 2, x + 9, y + 3, color);
                context.fill(x + 1, y + 5, x + 9, y + 6, color);
                context.fill(x + 1, y + 8, x + 9, y + 9, color);
            }
        }
    }

    private void drawSearchIcon(DrawContext context, int x, int y, int color) {
        context.fill(x + 1, y, x + 5, y + 1, color);
        context.fill(x, y + 1, x + 1, y + 5, color);
        context.fill(x + 5, y + 1, x + 6, y + 5, color);
        context.fill(x + 1, y + 5, x + 5, y + 6, color);
        context.fill(x + 5, y + 5, x + 8, y + 8, color);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0) {
            // Stäng-kryss uppe till höger
            int closeX = winX + winW - 22;
            int closeY = winY + 8;
            if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 20) {
                if (this.client != null) this.client.setScreen(this.parent);
                return true;
            }

            // Klick på kategori i sidopanelen
            Category[] categories = Category.values();
            int catStartY = winY + 52;
            int catH = 26;
            for (int i = 0; i < categories.length; i++) {
                int cy = catStartY + i * (catH + 4);
                int cx = winX + 8;
                int cw = sidebarW - 16;
                if (mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + catH) {
                    selectedCategory = categories[i];
                    if (searchField != null) searchField.setText("");
                    return true;
                }
            }

            // Klick på inställningsrad
            String query = (searchField != null) ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
            for (SettingRow row : allRows) {
                boolean visible = query.isEmpty()
                        ? (row.category == selectedCategory)
                        : (row.name.toLowerCase(Locale.ROOT).contains(query) || row.subtitle.toLowerCase(Locale.ROOT).contains(query));
                if (!visible) continue;

                if (mouseX >= row.rowX && mouseX <= row.rowX + row.rowW && mouseY >= row.rowY && mouseY <= row.rowY + row.rowH) {
                    if (row.type == RowType.TOGGLE) {
                        row.boolSetter.accept(!row.boolGetter.get());
                        return true;
                    } else if (row.type == RowType.MODE) {
                        row.modeCycler.run();
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

    private void updateSlider(SettingRow row, double mouseX) {
        int trackW = 86;
        int trackX = row.rowX + row.rowW - trackW - 12;
        float pct = MathHelper.clamp((float) (mouseX - trackX) / (float) trackW, 0.0f, 1.0f);
        float newVal = row.minVal + pct * (row.maxVal - row.minVal);
        row.floatSetter.accept(newVal);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
