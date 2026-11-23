package com.velo.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Helt omarbetad Lunar-inspirerad VELO Client-meny:
 * - Mörkt frostat glas-fönster med toppflikar, glidande understrykning och sökfält
 * - Kategori-pills (ALL, RENDER, PLAYER, MOVEMENT, FINDER, VIEWMODEL)
 * - 3-kolumns kort-grid med Minecraft-ikoner, ENABLED/DISABLED-knappar och inställnings-kugghjul
 * - 100% framerate-oberoende mjuka animationer (System.nanoTime())
 */
public class VeloMenuScreen extends Screen {
    private final Screen parent;

    private enum TopTab {
        MODS("MODS"),
        SETTINGS("SETTINGS"),
        WAYPOINTS("WAYPOINTS"),
        HUD("HUD EDITOR");

        final String label;
        TopTab(String label) { this.label = label; }
    }

    private enum ModCategory {
        ALL("ALL"),
        RENDER("RENDER"),
        PLAYER("PLAYER"),
        MOVEMENT("MOVEMENT"),
        FINDER("FINDER"),
        VIEWMODEL("VIEWMODEL");

        final String label;
        ModCategory(String label) { this.label = label; }
    }

    private enum SettingType { TOGGLE, SLIDER, MODE, ACTION, DANGER_ACTION }

    private static class ModSetting {
        final String name;
        final String subtitle;
        final SettingType type;
        Supplier<Boolean> boolGet;
        Consumer<Boolean> boolSet;
        Supplier<Float> floatGet;
        Consumer<Float> floatSet;
        float minVal, maxVal;
        String suffix;
        Supplier<String> modeGet;
        Runnable action;
        float toggleAnim = 0f;
        int rx, ry, rw, rh;
        boolean visible = false;

        ModSetting(String name, String subtitle, SettingType type) {
            this.name = name;
            this.subtitle = subtitle;
            this.type = type;
        }

        static ModSetting toggle(String name, String sub, Supplier<Boolean> g, Consumer<Boolean> s) {
            ModSetting m = new ModSetting(name, sub, SettingType.TOGGLE);
            m.boolGet = g; m.boolSet = s; m.toggleAnim = g.get() ? 1f : 0f;
            return m;
        }

        static ModSetting slider(String name, String sub, float min, float max, String suf, Supplier<Float> g, Consumer<Float> s) {
            ModSetting m = new ModSetting(name, sub, SettingType.SLIDER);
            m.minVal = min; m.maxVal = max; m.suffix = suf; m.floatGet = g; m.floatSet = s;
            return m;
        }

        static ModSetting mode(String name, String sub, Supplier<String> g, Runnable r) {
            ModSetting m = new ModSetting(name, sub, SettingType.MODE);
            m.modeGet = g; m.action = r;
            return m;
        }

        static ModSetting action(String name, String sub, Supplier<String> g, Runnable r) {
            ModSetting m = new ModSetting(name, sub, SettingType.ACTION);
            m.modeGet = g; m.action = r;
            return m;
        }

        static ModSetting danger(String name, String sub, Supplier<String> g, Runnable r) {
            ModSetting m = new ModSetting(name, sub, SettingType.DANGER_ACTION);
            m.modeGet = g; m.action = r;
            return m;
        }
    }

    private static class ModuleCard {
        final String name;
        final String description;
        final ModCategory category;
        final Item icon;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;
        final List<ModSetting> settings = new ArrayList<>();

        float hoverAnim = 0f;
        float enableAnim = 0f;
        int cx, cy, cw, ch;
        boolean visible = false;

        ModuleCard(String name, String description, ModCategory category, Item icon,
                   Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.icon = icon;
            this.getter = getter;
            this.setter = setter;
            this.enableAnim = getter.get() ? 1f : 0f;
        }

        ModuleCard add(ModSetting s) {
            settings.add(s);
            return this;
        }
    }

    private TopTab activeTopTab = TopTab.MODS;
    private ModCategory activeCategory = ModCategory.ALL;
    private ModuleCard inspectingModule = null;

    private final List<ModuleCard> modules = new ArrayList<>();
    private final List<ModSetting> globalSettings = new ArrayList<>();

    private TextFieldWidget searchField;
    private ModSetting draggingSlider = null;

    private int winX, winY, winW, winH;

    // Ultra-smooth animation state
    private float openAnim = 0.0f;
    private float currentScroll = 0.0f;
    private float targetScroll = 0.0f;
    private float tabLineX = -1.0f;
    private float tabLineW = 40.0f;
    private long lastFrameNanos = System.nanoTime();

    public VeloMenuScreen() {
        this(null);
    }

    public VeloMenuScreen(Screen parent) {
        super(Text.literal("VELO Client"));
        this.parent = parent;
        buildModules();
    }

    private void buildModules() {
        modules.clear();
        globalSettings.clear();

        // --- RENDER ---
        modules.add(new ModuleCard("FULLBRIGHT", "Se perfekt i mörker & grottor", ModCategory.RENDER, Items.GLOWSTONE_DUST,
                () -> VeloUtilityModules.fullbright, v -> VeloUtilityModules.fullbright = v));

        modules.add(new ModuleCard("NO HURT CAM", "Ingen skärmskakning när du tar skada", ModCategory.RENDER, Items.SHIELD,
                () -> VeloClient.noHurtCam, v -> VeloClient.noHurtCam = v));

        modules.add(new ModuleCard("LOW FIRE", "Sänker elden så du ser klart", ModCategory.RENDER, Items.BLAZE_POWDER,
                () -> VeloClient.lowFire, v -> VeloClient.lowFire = v)
                .add(ModSetting.slider("Fire Height", "Hur långt ner elden sänks", 0.15f, 0.55f, "",
                        () -> VeloClient.lowFireOffset, v -> VeloClient.lowFireOffset = v)));

        modules.add(new ModuleCard("PLAYER ESP", "Glowing hörnhakar + HP på spelare", ModCategory.RENDER, Items.PLAYER_HEAD,
                () -> VeloWorldFeatures.playerEsp, v -> VeloWorldFeatures.playerEsp = v));

        modules.add(new ModuleCard("MOB ESP", "Glowing hörnhakar runt fientliga mobs", ModCategory.RENDER, Items.ZOMBIE_HEAD,
                () -> VeloWorldFeatures.mobEsp, v -> VeloWorldFeatures.mobEsp = v));

        modules.add(new ModuleCard("ITEM ESP", "Visar namn & box på items på marken", ModCategory.RENDER, Items.DIAMOND,
                () -> VeloWorldFeatures.itemEsp, v -> VeloWorldFeatures.itemEsp = v));

        modules.add(new ModuleCard("TRACERS", "Linjer från skärmens mitt till spelare", ModCategory.RENDER, Items.SPECTRAL_ARROW,
                () -> VeloWorldFeatures.tracers, v -> VeloWorldFeatures.tracers = v));

        modules.add(new ModuleCard("BREADCRUMBS", "Glödande blått spår bakom dig", ModCategory.RENDER, Items.BLUE_DYE,
                () -> VeloWorldFeatures.breadcrumbs, v -> VeloWorldFeatures.breadcrumbs = v));

        modules.add(new ModuleCard("LOGOUT SPOTS", "Markerar var spelare loggade ut", ModCategory.RENDER, Items.SKELETON_SKULL,
                () -> VeloWorldFeatures.logoutSpots, v -> VeloWorldFeatures.logoutSpots = v));

        modules.add(new ModuleCard("WAYPOINTS", "Visa dina sparade waypoints genom väggar", ModCategory.RENDER, Items.COMPASS,
                () -> VeloWorldFeatures.waypointsEnabled, v -> VeloWorldFeatures.waypointsEnabled = v)
                .add(ModSetting.action("Hantera Waypoints", "Skapa, visa/dölj eller ta bort waypoints",
                        () -> "Öppna (" + VeloConfigManager.data.waypoints.size() + ") →",
                        () -> { if (this.client != null) this.client.setScreen(new VeloWaypointScreen(this)); })));

        // --- FINDER ---
        modules.add(new ModuleCard("BLOCK ESP", "Glowing hörnhakar runt valda block", ModCategory.FINDER, Items.DIAMOND_ORE,
                () -> VeloWorldFeatures.blockEspEnabled, v -> VeloWorldFeatures.blockEspEnabled = v)
                .add(ModSetting.action("Välj Block (Alla)", "Sök & välj bland alla Minecraft-block",
                        () -> "Välj (" + VeloWorldFeatures.selectedEspBlocks.size() + ") →",
                        () -> { if (this.client != null) this.client.setScreen(new VeloBlockPickerScreen(this)); }))
                .add(ModSetting.danger("Reset Block ESP", "Stänger av och rensar alla valda block direkt",
                        () -> "Reset Alla",
                        () -> {
                            VeloWorldFeatures.selectedEspBlocks.clear();
                            VeloWorldFeatures.blockEspEnabled = false;
                        }))
                .add(ModSetting.slider("Sökradie", "Hur långt bort Block ESP letar", 16f, 80f, "m",
                        () -> (float) VeloWorldFeatures.blockEspRange, v -> VeloWorldFeatures.blockEspRange = Math.round(v))));

        modules.add(new ModuleCard("BASE FINDER", "Top-popup med storlek, kistor & redstone", ModCategory.FINDER, Items.CHEST,
                () -> VeloWorldFeatures.baseFinderEnabled, v -> {
                    VeloWorldFeatures.baseFinderEnabled = v;
                    if (v) VeloWorldFeatures.clearAlerts();
                })
                .add(ModSetting.slider("Kistor / Shulkers", "Minsta antal för bas-popup", 2f, 20f, "",
                        () -> (float) VeloWorldFeatures.baseChestThreshold, v -> VeloWorldFeatures.baseChestThreshold = Math.round(v)))
                .add(ModSetting.slider("Redstone Gräns", "Minsta antal för redstone-farm popup", 8f, 60f, "",
                        () -> (float) VeloWorldFeatures.redstoneFarmThreshold, v -> VeloWorldFeatures.redstoneFarmThreshold = Math.round(v))));

        modules.add(new ModuleCard("SPAWNER FINDER", "Top-popup + lila outline runt spawners", ModCategory.FINDER, Items.SPAWNER,
                () -> VeloWorldFeatures.spawnerFinderEnabled, v -> {
                    VeloWorldFeatures.spawnerFinderEnabled = v;
                    if (v) VeloWorldFeatures.clearAlerts();
                })
                .add(ModSetting.mode("Spawner Typ", "Filtrera vilken mob-spawner som söks",
                        VeloWorldFeatures::getSelectedSpawnerLabel, () -> {
                            VeloWorldFeatures.selectedSpawnerTypeIndex = (VeloWorldFeatures.selectedSpawnerTypeIndex + 1) % VeloWorldFeatures.SPAWNER_TYPES.length;
                            VeloWorldFeatures.clearAlerts();
                        })));

        // --- PLAYER & CHEST ---
        modules.add(new ModuleCard("TOTEM WARNING", "Varnar om totem ligger i inv men ej offhand", ModCategory.PLAYER, Items.TOTEM_OF_UNDYING,
                () -> VeloClient.totemWarning, v -> VeloClient.totemWarning = v));

        modules.add(new ModuleCard("CHESTCLEANER", "Kastar bort skräp-items ur öppna kistor", ModCategory.PLAYER, Items.BARREL,
                () -> VeloUtilityModules.chestCleaner, v -> VeloUtilityModules.chestCleaner = v)
                .add(ModSetting.slider("Chest Delay", "Ticks mellan varje klick", 1f, 10f, "t",
                        () -> (float) VeloUtilityModules.chestDelayTicks, v -> VeloUtilityModules.chestDelayTicks = Math.round(v))));

        modules.add(new ModuleCard("CHESTSTEALER", "Plockar snabbt allt värdefullt ur kistor", ModCategory.PLAYER, Items.ENDER_CHEST,
                () -> VeloUtilityModules.chestStealer, v -> VeloUtilityModules.chestStealer = v)
                .add(ModSetting.slider("Chest Delay", "Ticks mellan varje klick", 1f, 10f, "t",
                        () -> (float) VeloUtilityModules.chestDelayTicks, v -> VeloUtilityModules.chestDelayTicks = Math.round(v))));

        modules.add(new ModuleCard("AUTO TOOL", "Byter automatiskt till bästa verktyget", ModCategory.PLAYER, Items.NETHERITE_PICKAXE,
                () -> VeloUtilityModules.autoTool, v -> VeloUtilityModules.autoTool = v));

        modules.add(new ModuleCard("AUTO REPLENISH", "Fyller på hotbar från inventoryt", ModCategory.PLAYER, Items.COBBLESTONE,
                () -> VeloUtilityModules.autoReplenish, v -> VeloUtilityModules.autoReplenish = v));

        modules.add(new ModuleCard("MIDDLE PEARL", "Kastar Ender Pearl direkt med mushjulet", ModCategory.PLAYER, Items.ENDER_PEARL,
                () -> VeloUtilityModules.middleClickPearl, v -> VeloUtilityModules.middleClickPearl = v));

        modules.add(new ModuleCard("AUTO RESPAWN", "Hoppar över dödsskärm + visar coords", ModCategory.PLAYER, Items.RED_BED,
                () -> VeloUtilityModules.autoRespawn, v -> VeloUtilityModules.autoRespawn = v));

        modules.add(new ModuleCard("ANTI AFK", "Undvik AFK-kick med små rörelser", ModCategory.PLAYER, Items.CLOCK,
                () -> VeloUtilityModules.antiAfk, v -> VeloUtilityModules.antiAfk = v));

        // --- MOVEMENT & CAMERA ---
        modules.add(new ModuleCard("FREECAM", "Flyg fritt med kameran (F6)", ModCategory.MOVEMENT, Items.ELYTRA,
                FreecamManager::isEnabled, FreecamManager::setEnabled)
                .add(ModSetting.slider("Freecam Speed", "Flyghastighet", 0.2f, 3.0f, "x",
                        () -> FreecamManager.speed, v -> FreecamManager.speed = v)));

        modules.add(new ModuleCard("FREELOOK", "360° kamera runt dig (Left Alt)", ModCategory.MOVEMENT, Items.ENDER_EYE,
                () -> FreecamManager.freeLookEnabled, v -> FreecamManager.freeLookEnabled = v));

        modules.add(new ModuleCard("SMOOTH ZOOM", "Mjuk inzoomning när du håller C", ModCategory.MOVEMENT, Items.SPYGLASS,
                () -> VeloUtilityModules.zoomEnabled, v -> VeloUtilityModules.zoomEnabled = v)
                .add(ModSetting.slider("Zoom FOV", "Hur långt in den zoomar", 30f, 60f, "°",
                        () -> (float) VeloUtilityModules.zoomFov, v -> VeloUtilityModules.zoomFov = Math.round(v))));

        modules.add(new ModuleCard("TOGGLE SPRINT", "Automatisk sprint framåt", ModCategory.MOVEMENT, Items.DIAMOND_BOOTS,
                () -> VeloClient.toggleSprint, v -> VeloClient.toggleSprint = v));

        // --- VIEWMODEL ---
        modules.add(new ModuleCard("VIEWMODEL", "Storlek, position & rotation på handen", ModCategory.VIEWMODEL, Items.DIAMOND_SWORD,
                () -> VeloClient.slimHand, v -> VeloClient.slimHand = v)
                .add(ModSetting.slider("Hand Scale", "Storlek på hand & vapen", 0.25f, 1.20f, "",
                        () -> VeloClient.handScale, v -> VeloClient.handScale = v))
                .add(ModSetting.slider("Offset X", "Flytta i sidled", -0.35f, 0.35f, "",
                        () -> VeloClient.handOffsetX, v -> VeloClient.handOffsetX = v))
                .add(ModSetting.slider("Offset Y", "Flytta upp/ner", -0.35f, 0.35f, "",
                        () -> VeloClient.handOffsetY, v -> VeloClient.handOffsetY = v))
                .add(ModSetting.slider("Offset Z", "Avstånd från kameran", -0.50f, 0.25f, "",
                        () -> VeloClient.handOffsetZ, v -> VeloClient.handOffsetZ = v))
                .add(ModSetting.slider("Rotation X (Pitch)", "Vinkla upp/ner", -45f, 45f, "°",
                        () -> VeloClient.handRotX, v -> VeloClient.handRotX = v))
                .add(ModSetting.slider("Rotation Y (Yaw)", "Vrid i sidled", -45f, 45f, "°",
                        () -> VeloClient.handRotY, v -> VeloClient.handRotY = v))
                .add(ModSetting.slider("Rotation Z (Roll)", "Luta vapnet/handen", -45f, 45f, "°",
                        () -> VeloClient.handRotZ, v -> VeloClient.handRotZ = v)));

        modules.add(new ModuleCard("SLOW SWING", "Gör hand-svingningen mjuk & långsam", ModCategory.VIEWMODEL, Items.FEATHER,
                () -> VeloClient.slowSwing, v -> VeloClient.slowSwing = v)
                .add(ModSetting.slider("Swing Speed", "Hastighet (6 normal – 30 långsam)", 6f, 30f, "",
                        () -> (float) VeloClient.swingDuration, v -> VeloClient.swingDuration = Math.round(v))));

        // --- GLOBAL SETTINGS TAB ---
        globalSettings.add(ModSetting.toggle("VELO HUD", "Visa/dölj hela HUD-systemet",
                () -> VeloClient.hudEnabled, v -> VeloClient.hudEnabled = v));
        globalSettings.add(ModSetting.toggle("Hotbar Durability HUD", "Visa durability på alla hotbar-items i hörnet",
                () -> VeloClient.hotbarDurabilityHud, v -> VeloClient.hotbarDurabilityHud = v));
        globalSettings.add(ModSetting.mode("Accent Theme", "Byt blå färgton på hela clienten",
                VeloClient::getThemeName, () -> VeloClient.blueThemeIndex = (VeloClient.blueThemeIndex + 1) % 3));
        globalSettings.add(ModSetting.action("Öppna HUD Editor (H)", "Dra & släpp alla 7 HUD-element fritt",
                () -> "Öppna Editor →", () -> { if (this.client != null) this.client.setScreen(new VeloHudEditorScreen(this)); }));
        globalSettings.add(ModSetting.action("Hantera Waypoints", "Skapa och hantera dina sparade waypoints",
                () -> "Öppna (" + VeloConfigManager.data.waypoints.size() + ") →",
                () -> { if (this.client != null) this.client.setScreen(new VeloWaypointScreen(this)); }));
        globalSettings.add(ModSetting.danger("Reset Block ESP", "Rensar och stänger av alla valda ESP-block",
                () -> "Reset Alla", () -> {
                    VeloWorldFeatures.selectedEspBlocks.clear();
                    VeloWorldFeatures.blockEspEnabled = false;
                }));
    }

    @Override
    protected void init() {
        this.winW = Math.min(556, this.width - 24);
        this.winH = Math.min(338, this.height - 24);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        String prev = (searchField != null) ? searchField.getText() : "";
        int searchW = 114;
        int searchX = winX + winW - searchW - 34;
        searchField = new TextFieldWidget(this.textRenderer, searchX + 8, winY + 15, searchW - 14, 14, Text.literal("Search"));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Search..."));
        searchField.setText(prev);
        this.addDrawableChild(searchField);

        this.lastFrameNanos = System.nanoTime();
    }

    private List<ModuleCard> getFilteredModules() {
        String q = (searchField != null) ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<ModuleCard> out = new ArrayList<>();
        for (ModuleCard m : modules) {
            if (q.isEmpty()) {
                if (activeCategory == ModCategory.ALL || m.category == activeCategory) {
                    out.add(m);
                }
            } else {
                if (m.name.toLowerCase(Locale.ROOT).contains(q)
                        || m.description.toLowerCase(Locale.ROOT).contains(q)
                        || m.category.label.toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        int alpha = Math.clamp(Math.round(openAnim * 135.0f), 0, 135);
        context.fill(0, 0, this.width, this.height, (alpha << 24) | 0x030712);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;

        float fastSmooth = 1.0f - (float) Math.exp(-16.0f * dt);
        float medSmooth = 1.0f - (float) Math.exp(-12.0f * dt);
        openAnim += (1.0f - openAnim) * fastSmooth;

        int accent = VeloClient.getAccentColor();
        int primary = VeloClient.getPrimaryBlue();
        int slideY = Math.round((1.0f - openAnim) * 16.0f);
        int wy = winY + slideY;

        // Huvudfönster (Lunar-stil frostat mörkt glas)
        VeloUiRenderer.drawSmoothPanel(ctx, winX, wy, winW, winH, 12, 0xE80A0F1A, 0x6638BDF8, true);

        // Top Header divider
        ctx.fill(winX + 12, wy + 38, winX + winW - 12, wy + 39, 0x3394A3B8);

        // Vänster logga: VELO-emblem + "VELO"
        VeloUiRenderer.drawSmoothPanel(ctx, winX + 14, wy + 10, 20, 20, 6, primary, accent, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§lV", winX + 24, wy + 16, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, "§lVELO", winX + 40, wy + 16, 0xFFFFFFFF);

        // Centrerade Top-Tabs (MODS | SETTINGS | WAYPOINTS | HUD EDITOR)
        TopTab[] tabs = TopTab.values();
        int tabStartX = winX + 94;
        int curTabX = tabStartX;
        for (TopTab tab : tabs) {
            int tw = this.textRenderer.getWidth(tab.label);
            boolean active = (tab == activeTopTab && inspectingModule == null);
            boolean hov = mouseX >= curTabX - 6 && mouseX <= curTabX + tw + 6 && mouseY >= wy + 8 && mouseY <= wy + 34;

            ctx.drawTextWithShadow(ctx.getClass() != null ? this.textRenderer : this.textRenderer,
                    tab.label, curTabX, wy + 16, active ? 0xFFFFFFFF : (hov ? 0xFFCBD5E1 : 0xFF8896AB));

            if (active) {
                if (tabLineX < 0) {
                    tabLineX = curTabX;
                    tabLineW = tw;
                } else {
                    tabLineX += (curTabX - tabLineX) * fastSmooth;
                    tabLineW += (tw - tabLineW) * fastSmooth;
                }
            }
            curTabX += tw + 20;
        }

        // Glidande vit/blå understrykning under aktiv flik
        if (inspectingModule == null && tabLineX >= 0) {
            VeloUiRenderer.drawSmoothRect(ctx, Math.round(tabLineX), wy + 36, Math.round(tabLineW), 2, 1, accent);
        }

        // Sökfält-pill uppe till höger
        int searchW = 114;
        int searchX = winX + winW - searchW - 34;
        VeloUiRenderer.drawSmoothPanel(ctx, searchX, wy + 10, searchW, 20, 8, 0xCC121A2B, 0x4494A3B8, false);

        // Stäng-knapp (X)
        int closeX = winX + winW - 26;
        int closeY = wy + 10;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 20;
        VeloUiRenderer.drawSmoothPanel(ctx, closeX, closeY, 18, 20, 6, closeHov ? 0x88EF4444 : 0x441E293B, 0x33FFFFFF, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "✕", closeX + 9, closeY + 6, closeHov ? 0xFFFFFFFF : 0xFF94A3B8);

        // --- INNEHÅLL ---
        if (inspectingModule != null) {
            renderModuleSettings(ctx, inspectingModule, wy, mouseX, mouseY, fastSmooth, medSmooth, accent, primary);
        } else if (activeTopTab == TopTab.MODS) {
            renderModsGrid(ctx, wy, mouseX, mouseY, fastSmooth, medSmooth, accent, primary);
        } else if (activeTopTab == TopTab.SETTINGS) {
            renderSettingsList(ctx, "CLIENT SETTINGS", globalSettings, wy, mouseX, mouseY, fastSmooth, medSmooth, accent, primary, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderModsGrid(DrawContext ctx, int wy, int mouseX, int mouseY,
                                float fastSmooth, float medSmooth, int accent, int primary) {
        // Kategori-pills under headern (ALL | RENDER | PLAYER | MOVEMENT | FINDER | VIEWMODEL)
        ModCategory[] cats = ModCategory.values();
        int px = winX + 14;
        int py = wy + 46;
        for (ModCategory cat : cats) {
            int pw = this.textRenderer.getWidth(cat.label) + 16;
            boolean sel = (cat == activeCategory) && (searchField == null || searchField.getText().isBlank());
            boolean hov = mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + 18;
            int fill = sel ? 0xDD1C2E4F : (hov ? 0x99162238 : 0x660F1726);
            int border = sel ? accent : 0x33475569;
            VeloUiRenderer.drawSmoothPanel(ctx, px, py, pw, 18, 6, fill, border, false);
            ctx.drawCenteredTextWithShadow(this.textRenderer, cat.label, px + pw / 2, py + 5, sel ? 0xFFFFFFFF : 0xFF94A3B8);
            px += pw + 6;
        }

        // Snabb Reset-knapp när man står i FINDER-kategorin!
        if (activeCategory == ModCategory.FINDER) {
            int rw = 94;
            int rx = winX + winW - rw - 14;
            boolean rHov = mouseX >= rx && mouseX <= rx + rw && mouseY >= py && mouseY <= py + 18;
            VeloUiRenderer.drawSmoothPanel(ctx, rx, py, rw, 18, 6, rHov ? 0xFFDC2626 : 0xBB3B1219, 0xAAEF4444, false);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Reset ESP Blocks", rx + rw / 2, py + 5, 0xFFFFFFFF);
        }

        // 3-kolumns Modul-kort (Lunar-layout)
        List<ModuleCard> list = getFilteredModules();
        for (ModuleCard m : modules) m.visible = false;

        int gridTop = wy + 72;
        int gridBottom = wy + winH - 12;
        int cols = 3;
        int gap = 10;
        int cardW = (winW - 28 - gap * (cols - 1)) / cols;
        int cardH = 84;

        int rows = (list.size() + cols - 1) / cols;
        float maxScroll = Math.max(0.0f, rows * (cardH + gap) - (gridBottom - gridTop));
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll);
        currentScroll += (targetScroll - currentScroll) * fastSmooth;

        ctx.enableScissor(winX + 10, gridTop, winX + winW - 10, gridBottom);

        for (int i = 0; i < list.size(); i++) {
            ModuleCard m = list.get(i);
            int row = i / cols;
            int col = i % cols;
            int cx = winX + 14 + col * (cardW + gap);
            int cy = gridTop + row * (cardH + gap) - Math.round(currentScroll);

            if (cy + cardH < gridTop || cy > gridBottom) continue;

            m.cx = cx; m.cy = cy; m.cw = cardW; m.ch = cardH; m.visible = true;

            boolean enabled = m.getter.get();
            boolean hovered = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH
                    && mouseY >= gridTop && mouseY <= gridBottom;

            m.hoverAnim += ((hovered ? 1.0f : 0.0f) - m.hoverAnim) * fastSmooth;
            m.enableAnim += ((enabled ? 1.0f : 0.0f) - m.enableAnim) * medSmooth;

            int cardFill = VeloUiRenderer.lerpColor(0xB80E1626, 0xD815233D, m.hoverAnim);
            int baseBorder = VeloUiRenderer.lerpColor(0x44334155, (0xAA << 24) | (accent & 0x00FFFFFF), m.enableAnim);
            int cardBorder = VeloUiRenderer.lerpColor(baseBorder, 0xDDFFFFFF, m.hoverAnim * 0.35f);

            VeloUiRenderer.drawSmoothPanel(ctx, cx, cy, cardW, cardH, 9, cardFill, cardBorder, false);

            // Kugghjul uppe till höger om modulen har inställningar
            if (!m.settings.isEmpty()) {
                int gx = cx + cardW - 22;
                int gy = cy + 6;
                boolean gHov = mouseX >= gx && mouseX <= gx + 16 && mouseY >= gy && mouseY <= gy + 16;
                VeloUiRenderer.drawSmoothPanel(ctx, gx, gy, 16, 16, 5, gHov ? 0x9938BDF8 : 0x551E293B, 0x44FFFFFF, false);
                ctx.drawCenteredTextWithShadow(this.textRenderer, "⚙", gx + 8, gy + 4, gHov ? 0xFFFFFFFF : 0xFF94A3B8);
            }

            // Centrerad ikon-badge
            int iconBoxX = cx + (cardW - 24) / 2;
            int iconBoxY = cy + 10;
            int iconBg = VeloUiRenderer.lerpColor(0x66162238, (0x33 << 24) | (accent & 0x00FFFFFF), m.enableAnim);
            VeloUiRenderer.drawSmoothRect(ctx, iconBoxX, iconBoxY, 24, 24, 7, iconBg);
            ctx.drawItem(new ItemStack(m.icon), iconBoxX + 4, iconBoxY + 4);

            // Modulnamn
            ctx.drawCenteredTextWithShadow(this.textRenderer, "§l" + m.name, cx + cardW / 2, cy + 40, 0xFFF8FAFC);

            // ENABLED / DISABLED-knapp längst ner på kortet (Lunar-stil!)
            int bx = cx + 8;
            int by = cy + cardH - 24;
            int bw = cardW - 16;
            int bh = 18;
            int btnFill = VeloUiRenderer.lerpColor(0xCC1E293B, 0xEE10B981, m.enableAnim);
            int btnBorder = VeloUiRenderer.lerpColor(0x44475569, 0xFF34D399, m.enableAnim);
            VeloUiRenderer.drawSmoothPanel(ctx, bx, by, bw, bh, 6, btnFill, btnBorder, false);

            String btnText = enabled ? "ENABLED" : "DISABLED";
            int textCol = VeloUiRenderer.lerpColor(0xFF94A3B8, 0xFFFFFFFF, m.enableAnim);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "§l" + btnText, bx + bw / 2, by + 5, textCol);
        }

        ctx.disableScissor();
    }

    private void renderModuleSettings(DrawContext ctx, ModuleCard mod, int wy, int mouseX, int mouseY,
                                      float fastSmooth, float medSmooth, int accent, int primary) {
        renderSettingsList(ctx, mod.name + "  §7•  INSTÄLLNINGAR", mod.settings, wy, mouseX, mouseY, fastSmooth, medSmooth, accent, primary, true);
    }

    private void renderSettingsList(DrawContext ctx, String headerTitle, List<ModSetting> settings, int wy,
                                    int mouseX, int mouseY, float fastSmooth, float medSmooth, int accent, int primary, boolean showBack) {
        int topY = wy + 48;
        if (showBack) {
            int bx = winX + 14;
            boolean bHov = mouseX >= bx && mouseX <= bx + 74 && mouseY >= topY && mouseY <= topY + 20;
            VeloUiRenderer.drawSmoothPanel(ctx, bx, topY, 74, 20, 6, bHov ? primary : 0xCC15223B, accent, false);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "← Tillbaka", bx + 37, topY + 6, 0xFFFFFFFF);
            ctx.drawTextWithShadow(this.textRenderer, "§l" + headerTitle, bx + 86, topY + 6, 0xFFFFFFFF);
        } else {
            ctx.drawTextWithShadow(this.textRenderer, "§l" + headerTitle, winX + 16, topY + 6, accent);
        }

        int listTop = wy + 76;
        int listBottom = wy + winH - 12;
        int rowH = 40;

        float maxScroll = Math.max(0.0f, settings.size() * rowH - (listBottom - listTop));
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll);
        currentScroll += (targetScroll - currentScroll) * fastSmooth;

        ctx.enableScissor(winX + 10, listTop, winX + winW - 10, listBottom);

        for (int i = 0; i < settings.size(); i++) {
            ModSetting s = settings.get(i);
            s.visible = false;
            int ry = listTop + i * rowH - Math.round(currentScroll);
            if (ry + rowH < listTop || ry > listBottom) continue;

            s.rx = winX + 14;
            s.ry = ry;
            s.rw = winW - 28;
            s.rh = rowH - 6;
            s.visible = true;

            boolean hov = mouseX >= s.rx && mouseX <= s.rx + s.rw && mouseY >= s.ry && mouseY <= s.ry + s.rh;
            VeloUiRenderer.drawSmoothPanel(ctx, s.rx, s.ry, s.rw, s.rh, 8,
                    hov ? 0xD815233D : 0xB80E1626, hov ? accent : 0x44334155, false);

            ctx.drawTextWithShadow(this.textRenderer, "§l" + s.name, s.rx + 12, s.ry + 6, 0xFFF8FAFC);
            ctx.drawTextWithShadow(this.textRenderer, "§7" + s.subtitle, s.rx + 12, s.ry + 19, 0xFF94A3B8);

            if (s.type == SettingType.TOGGLE) {
                boolean on = s.boolGet.get();
                s.toggleAnim += ((on ? 1.0f : 0.0f) - s.toggleAnim) * fastSmooth;
                int swW = 36, swH = 16;
                int swX = s.rx + s.rw - swW - 12;
                int swY = s.ry + (s.rh - swH) / 2;
                int bg = VeloUiRenderer.lerpColor(0xFF1E293B, 0xFF10B981, s.toggleAnim);
                VeloUiRenderer.drawSmoothRect(ctx, swX, swY, swW, swH, 8, bg);
                int knobX = swX + 2 + Math.round((swW - 16) * s.toggleAnim);
                VeloUiRenderer.drawSmoothRect(ctx, knobX, swY + 2, 12, 12, 6, 0xFFFFFFFF);
            } else if (s.type == SettingType.SLIDER) {
                float val = s.floatGet.get();
                float pct = MathHelper.clamp((val - s.minVal) / (s.maxVal - s.minVal), 0f, 1f);
                int trackW = 110, trackH = 6;
                int trackX = s.rx + s.rw - trackW - 14;
                int trackY = s.ry + (s.rh - trackH) / 2 + 1;

                String valStr = (s.maxVal - s.minVal >= 5f)
                        ? String.format(Locale.ROOT, "%d%s", Math.round(val), s.suffix)
                        : String.format(Locale.ROOT, "%.2f%s", val, s.suffix);
                ctx.drawTextWithShadow(this.textRenderer, valStr, trackX - this.textRenderer.getWidth(valStr) - 10, trackY - 2, accent);
                VeloUiRenderer.drawSmoothRect(ctx, trackX, trackY, trackW, trackH, 3, 0xFF1E293B);
                VeloUiRenderer.drawSmoothRect(ctx, trackX, trackY, Math.max(4, Math.round(trackW * pct)), trackH, 3, accent);
                VeloUiRenderer.drawSmoothRect(ctx, trackX + Math.round(trackW * pct) - 5, trackY - 2, 10, 10, 5, 0xFFFFFFFF);
            } else if (s.type == SettingType.MODE || s.type == SettingType.ACTION || s.type == SettingType.DANGER_ACTION) {
                String label = s.modeGet.get();
                int pillW = Math.max(76, this.textRenderer.getWidth(label) + 18);
                int pillH = 20;
                int pillX = s.rx + s.rw - pillW - 12;
                int pillY = s.ry + (s.rh - pillH) / 2;
                int pillFill = (s.type == SettingType.DANGER_ACTION)
                        ? (hov ? 0xFFDC2626 : 0xCC45121B)
                        : (hov ? primary : 0xCC162542);
                int pillBorder = (s.type == SettingType.DANGER_ACTION) ? 0xFFEF4444 : accent;
                VeloUiRenderer.drawSmoothPanel(ctx, pillX, pillY, pillW, pillH, 6, pillFill, pillBorder, false);
                ctx.drawCenteredTextWithShadow(this.textRenderer, label, pillX + pillW / 2, pillY + 6, 0xFFFFFFFF);
            }
        }

        ctx.disableScissor();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();

        // Stäng-knapp
        int closeX = winX + winW - 26, closeY = winY + 10;
        if (click.button() == 0 && mx >= closeX && mx <= closeX + 18 && my >= closeY && my <= closeY + 20) {
            if (this.client != null) this.client.setScreen(this.parent);
            return true;
        }

        // Top Tabs
        if (click.button() == 0 && my >= winY + 8 && my <= winY + 34) {
            int curTabX = winX + 94;
            for (TopTab tab : TopTab.values()) {
                int tw = this.textRenderer.getWidth(tab.label);
                if (mx >= curTabX - 6 && mx <= curTabX + tw + 6) {
                    if (tab == TopTab.WAYPOINTS) {
                        if (this.client != null) this.client.setScreen(new VeloWaypointScreen(this));
                        return true;
                    } else if (tab == TopTab.HUD) {
                        if (this.client != null) this.client.setScreen(new VeloHudEditorScreen(this));
                        return true;
                    }
                    activeTopTab = tab;
                    inspectingModule = null;
                    targetScroll = currentScroll = 0f;
                    return true;
                }
                curTabX += tw + 20;
            }
        }

        // Modul-inställningsvy
        if (inspectingModule != null) {
            int bx = winX + 14, by = winY + 48;
            if (click.button() == 0 && mx >= bx && mx <= bx + 74 && my >= by && my <= by + 20) {
                inspectingModule = null;
                targetScroll = currentScroll = 0f;
                return true;
            }
            if (handleSettingsClick(inspectingModule.settings, click)) return true;
            return super.mouseClicked(click, doubled);
        }

        // Global Settings tab
        if (activeTopTab == TopTab.SETTINGS) {
            if (handleSettingsClick(globalSettings, click)) return true;
            return super.mouseClicked(click, doubled);
        }

        // MODS tab: Kategori-pills + Reset ESP Blocks i Finder
        if (activeTopTab == TopTab.MODS && click.button() == 0) {
            int px = winX + 14;
            int py = winY + 46;
            for (ModCategory cat : ModCategory.values()) {
                int pw = this.textRenderer.getWidth(cat.label) + 16;
                if (mx >= px && mx <= px + pw && my >= py && my <= py + 18) {
                    activeCategory = cat;
                    targetScroll = currentScroll = 0f;
                    if (searchField != null) searchField.setText("");
                    return true;
                }
                px += pw + 6;
            }

            if (activeCategory == ModCategory.FINDER) {
                int rw = 94, rx = winX + winW - rw - 14;
                if (mx >= rx && mx <= rx + rw && my >= py && my <= py + 18) {
                    VeloWorldFeatures.selectedEspBlocks.clear();
                    VeloWorldFeatures.blockEspEnabled = false;
                    return true;
                }
            }
        }

        // MODS tab: Kortklick (Vänsterklick = toggle / kugghjul = inställningar, Högerklick = inställningar)
        if (activeTopTab == TopTab.MODS) {
            for (ModuleCard m : getFilteredModules()) {
                if (!m.visible) continue;
                if (mx >= m.cx && mx <= m.cx + m.cw && my >= m.cy && my <= m.cy + m.ch) {
                    if (searchField != null) searchField.setFocused(false);
                    if (!m.settings.isEmpty()) {
                        int gx = m.cx + m.cw - 22, gy = m.cy + 6;
                        if (click.button() == 1 || (click.button() == 0 && mx >= gx && mx <= gx + 16 && my >= gy && my <= gy + 16)) {
                            inspectingModule = m;
                            targetScroll = currentScroll = 0f;
                            return true;
                        }
                    }
                    if (click.button() == 0) {
                        m.setter.accept(!m.getter.get());
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private boolean handleSettingsClick(List<ModSetting> settings, Click click) {
        if (click.button() != 0) return false;
        double mx = click.x(), my = click.y();
        for (ModSetting s : settings) {
            if (!s.visible) continue;
            if (mx >= s.rx && mx <= s.rx + s.rw && my >= s.ry && my <= s.ry + s.rh) {
                if (s.type == SettingType.TOGGLE) {
                    s.boolSet.accept(!s.boolGet.get());
                    return true;
                } else if (s.type == SettingType.MODE || s.type == SettingType.ACTION || s.type == SettingType.DANGER_ACTION) {
                    s.action.run();
                    return true;
                } else if (s.type == SettingType.SLIDER) {
                    draggingSlider = s;
                    updateSlider(s, mx);
                    return true;
                }
            }
        }
        return false;
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
        targetScroll -= (float) (verticalAmount * 38.0);
        return true;
    }

    private void updateSlider(ModSetting s, double mouseX) {
        int trackW = 110;
        int trackX = s.rx + s.rw - trackW - 14;
        float pct = MathHelper.clamp((float) (mouseX - trackX) / (float) trackW, 0.0f, 1.0f);
        s.floatSet.accept(s.minVal + pct * (s.maxVal - s.minVal));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
