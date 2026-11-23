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
 * VELO Client v4.0 — Stor, minimalistisk, skarpkantad arkitektonisk meny i marinblått & skiffergrått.
 * - Superstor arbetsyta (upp till 820 x 480) med vänstersidopanel, 3-kolumns modulgrid och omdesignad Settings-vy.
 * - 100% skarpa 90°-kanter, tunna 1px linjer och framerate-oberoende mjuka animationer.
 */
public class VeloMenuScreen extends Screen {
    private final Screen parent;

    private enum NavItem {
        ALL("ALL MODULES", "MODULES"),
        RENDER("RENDER", "MODULES"),
        FINDER("FINDER", "MODULES"),
        PLAYER("PLAYER", "MODULES"),
        MOVEMENT("MOVEMENT", "MODULES"),
        VIEWMODEL("VIEWMODEL", "MODULES"),
        SETTINGS("SETTINGS", "WORKSPACE"),
        WAYPOINTS("WAYPOINTS", "WORKSPACE"),
        HUD("HUD EDITOR", "WORKSPACE");

        final String label;
        final String group;
        NavItem(String label, String group) {
            this.label = label;
            this.group = group;
        }
    }

    private enum SettingType { TOGGLE, SLIDER, MODE, ACTION, DANGER }

    private static class SettingRow {
        final String section;
        final String name;
        final String desc;
        final SettingType type;

        Supplier<Boolean> boolGet;
        Consumer<Boolean> boolSet;
        Supplier<Float> floatGet;
        Consumer<Float> floatSet;
        float minVal, maxVal;
        String suffix;
        Supplier<String> textGet;
        Runnable action;

        float anim = 0f;
        int rx, ry, rw, rh;
        boolean visible = false;

        SettingRow(String section, String name, String desc, SettingType type) {
            this.section = section;
            this.name = name;
            this.desc = desc;
            this.type = type;
        }

        static SettingRow toggle(String sec, String name, String desc, Supplier<Boolean> g, Consumer<Boolean> s) {
            SettingRow r = new SettingRow(sec, name, desc, SettingType.TOGGLE);
            r.boolGet = g;
            r.boolSet = s;
            r.anim = g.get() ? 1f : 0f;
            return r;
        }

        static SettingRow slider(String sec, String name, String desc, float min, float max, String suf, Supplier<Float> g, Consumer<Float> s) {
            SettingRow r = new SettingRow(sec, name, desc, SettingType.SLIDER);
            r.minVal = min;
            r.maxVal = max;
            r.suffix = suf;
            r.floatGet = g;
            r.floatSet = s;
            return r;
        }

        static SettingRow mode(String sec, String name, String desc, Supplier<String> g, Runnable a) {
            SettingRow r = new SettingRow(sec, name, desc, SettingType.MODE);
            r.textGet = g;
            r.action = a;
            return r;
        }

        static SettingRow action(String sec, String name, String desc, Supplier<String> g, Runnable a) {
            SettingRow r = new SettingRow(sec, name, desc, SettingType.ACTION);
            r.textGet = g;
            r.action = a;
            return r;
        }

        static SettingRow danger(String sec, String name, String desc, Supplier<String> g, Runnable a) {
            SettingRow r = new SettingRow(sec, name, desc, SettingType.DANGER);
            r.textGet = g;
            r.action = a;
            return r;
        }
    }

    private static class ModuleCard {
        final String name;
        final String description;
        final NavItem category;
        final Item icon;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;
        final List<SettingRow> settings = new ArrayList<>();

        float hoverAnim = 0f;
        float enableAnim = 0f;
        int cx, cy, cw, ch;
        boolean visible = false;

        ModuleCard(String name, String description, NavItem category, Item icon,
                   Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.icon = icon;
            this.getter = getter;
            this.setter = setter;
            this.enableAnim = getter.get() ? 1f : 0f;
        }

        ModuleCard add(SettingRow r) {
            settings.add(r);
            return this;
        }
    }

    // Färgpalett: Marinblå & Skiffergrå
    private static final int BG_WINDOW     = 0xF40B0E14;
    private static final int BG_SIDEBAR    = 0xFF0E121A;
    private static final int BG_HEADER     = 0xFF0E121A;
    private static final int BG_CARD       = 0xFF111622;
    private static final int BG_CARD_HOVER = 0xFF161D2C;
    private static final int BORDER_HAIR   = 0xFF222A3A;
    private static final int BORDER_STRONG = 0xFF303B52;
    private static final int TEXT_PRIMARY  = 0xFFF1F5F9;
    private static final int TEXT_SEC      = 0xFF94A3B8;
    private static final int TEXT_MUTED    = 0xFF52607A;

    private NavItem activeNav = NavItem.ALL;
    private ModuleCard inspectingModule = null;

    private final List<ModuleCard> modules = new ArrayList<>();
    private final List<SettingRow> minimalSettings = new ArrayList<>();

    private TextFieldWidget searchField;
    private SettingRow draggingSlider = null;

    private int winX, winY, winW, winH;
    private static final int SIDEBAR_W = 152;
    private static final int HEADER_H = 38;

    private float openAnim = 0.0f;
    private float currentScroll = 0.0f;
    private float targetScroll = 0.0f;
    private float navIndicatorY = -1.0f;
    private long lastFrameNanos = System.nanoTime();

    public VeloMenuScreen() {
        this(null);
    }

    public VeloMenuScreen(Screen parent) {
        super(Text.literal("VELO Client"));
        this.parent = parent;
        buildData();
    }

    private void buildData() {
        modules.clear();
        minimalSettings.clear();

        // --- RENDER ---
        modules.add(new ModuleCard("FULLBRIGHT", "Maximal ljusstyrka i mörker & grottor", NavItem.RENDER, Items.GLOWSTONE_DUST,
                () -> VeloUtilityModules.fullbright, v -> VeloUtilityModules.fullbright = v));

        modules.add(new ModuleCard("NO HURT CAM", "Tar bort skärmskakning vid skada", NavItem.RENDER, Items.SHIELD,
                () -> VeloClient.noHurtCam, v -> VeloClient.noHurtCam = v));

        modules.add(new ModuleCard("LOW FIRE", "Sänker elden längst ner på skärmen", NavItem.RENDER, Items.BLAZE_POWDER,
                () -> VeloClient.lowFire, v -> VeloClient.lowFire = v)
                .add(SettingRow.slider("LOW FIRE", "Fire Height", "Hur långt ner elden sänks på skärmen", 0.15f, 0.55f, "",
                        () -> VeloClient.lowFireOffset, v -> VeloClient.lowFireOffset = v)));

        modules.add(new ModuleCard("PLAYER ESP", "Fast 3D glowing outline + HP på spelare", NavItem.RENDER, Items.PLAYER_HEAD,
                () -> VeloWorldFeatures.playerEsp, v -> VeloWorldFeatures.playerEsp = v));

        modules.add(new ModuleCard("MOB ESP", "Fast 3D glowing outline runt mobs", NavItem.RENDER, Items.ZOMBIE_HEAD,
                () -> VeloWorldFeatures.mobEsp, v -> VeloWorldFeatures.mobEsp = v));

        modules.add(new ModuleCard("ITEM ESP", "Markerar tappade items på marken", NavItem.RENDER, Items.DIAMOND,
                () -> VeloWorldFeatures.itemEsp, v -> VeloWorldFeatures.itemEsp = v));

        modules.add(new ModuleCard("TRACERS", "Riktningslinjer till spelare i närheten", NavItem.RENDER, Items.SPECTRAL_ARROW,
                () -> VeloWorldFeatures.tracers, v -> VeloWorldFeatures.tracers = v));

        modules.add(new ModuleCard("BREADCRUMBS", "Diskret linjespår där du gått", NavItem.RENDER, Items.BLUE_DYE,
                () -> VeloWorldFeatures.breadcrumbs, v -> VeloWorldFeatures.breadcrumbs = v));

        modules.add(new ModuleCard("LOGOUT SPOTS", "Markerar koordinater där spelare loggat ut", NavItem.RENDER, Items.SKELETON_SKULL,
                () -> VeloWorldFeatures.logoutSpots, v -> VeloWorldFeatures.logoutSpots = v));

        modules.add(new ModuleCard("WAYPOINTS", "Visar dina sparade waypoints i världen", NavItem.RENDER, Items.COMPASS,
                () -> VeloWorldFeatures.waypointsEnabled, v -> VeloWorldFeatures.waypointsEnabled = v)
                .add(SettingRow.action("WAYPOINTS", "Hantera Waypoints", "Skapa, visa eller radera sparade koordinater",
                        () -> "OPEN (" + VeloConfigManager.data.waypoints.size() + ")",
                        () -> { if (this.client != null) this.client.setScreen(new VeloWaypointScreen(this)); })));

        // --- FINDER ---
        modules.add(new ModuleCard("BLOCK ESP", "Fast 3D outline runt valda block (Key: R)", NavItem.FINDER, Items.DIAMOND_ORE,
                () -> VeloWorldFeatures.blockEspEnabled, v -> VeloWorldFeatures.blockEspEnabled = v)
                .add(SettingRow.action("BLOCK ESP", "Välj Blocktyper", "Sök och välj bland alla block i spelet",
                        () -> "SELECT (" + VeloWorldFeatures.selectedEspBlocks.size() + ")",
                        () -> { if (this.client != null) this.client.setScreen(new VeloBlockPickerScreen(this)); }))
                .add(SettingRow.slider("BLOCK ESP", "Sökradie", "Maximal räckvidd i block", 16f, 80f, "m",
                        () -> (float) VeloWorldFeatures.blockEspRange, v -> VeloWorldFeatures.blockEspRange = Math.round(v)))
                .add(SettingRow.danger("BLOCK ESP", "Återställ Block ESP", "Stänger av och tömmer listan med valda block",
                        () -> "RESET ALL",
                        () -> {
                            VeloWorldFeatures.selectedEspBlocks.clear();
                            VeloWorldFeatures.blockEspEnabled = false;
                        })));

        modules.add(new ModuleCard("BASE FINDER", "Klustrad 3D outline runt kistor & redstone", NavItem.FINDER, Items.CHEST,
                () -> VeloWorldFeatures.baseFinderEnabled, v -> {
                    VeloWorldFeatures.baseFinderEnabled = v;
                    if (v) VeloWorldFeatures.clearAlerts();
                })
                .add(SettingRow.slider("BASE FINDER", "Kist-tröskel", "Minsta antal kistor/shulkers för bas-kluster", 2f, 20f, "",
                        () -> (float) VeloWorldFeatures.baseChestThreshold, v -> VeloWorldFeatures.baseChestThreshold = Math.round(v)))
                .add(SettingRow.slider("BASE FINDER", "Redstone-tröskel", "Minsta antal komponenter för redstone-kluster", 8f, 60f, "",
                        () -> (float) VeloWorldFeatures.redstoneFarmThreshold, v -> VeloWorldFeatures.redstoneFarmThreshold = Math.round(v))));

        modules.add(new ModuleCard("SPAWNER FINDER", "Hittar och markerar mob-spawners i chunks", NavItem.FINDER, Items.SPAWNER,
                () -> VeloWorldFeatures.spawnerFinderEnabled, v -> {
                    VeloWorldFeatures.spawnerFinderEnabled = v;
                    if (v) VeloWorldFeatures.clearAlerts();
                })
                .add(SettingRow.mode("SPAWNER FINDER", "Spawner Filter", "Välj specifik mob-spawner att söka efter",
                        VeloWorldFeatures::getSelectedSpawnerLabel, () -> {
                            VeloWorldFeatures.selectedSpawnerTypeIndex = (VeloWorldFeatures.selectedSpawnerTypeIndex + 1) % VeloWorldFeatures.SPAWNER_TYPES.length;
                            VeloWorldFeatures.clearAlerts();
                        })));

        // --- PLAYER ---
        modules.add(new ModuleCard("TOTEM WARNING", "Varnar om totem saknas i offhand", NavItem.PLAYER, Items.TOTEM_OF_UNDYING,
                () -> VeloClient.totemWarning, v -> VeloClient.totemWarning = v));

        modules.add(new ModuleCard("CHESTCLEANER", "Rensar skräp-items ur öppna kistor", NavItem.PLAYER, Items.BARREL,
                () -> VeloUtilityModules.chestCleaner, v -> VeloUtilityModules.chestCleaner = v)
                .add(SettingRow.slider("CHEST", "Action Delay", "Ticks mellan varje kist-klick", 1f, 10f, "t",
                        () -> (float) VeloUtilityModules.chestDelayTicks, v -> VeloUtilityModules.chestDelayTicks = Math.round(v))));

        modules.add(new ModuleCard("CHESTSTEALER", "Plockar värdefulla items ur kistor", NavItem.PLAYER, Items.ENDER_CHEST,
                () -> VeloUtilityModules.chestStealer, v -> VeloUtilityModules.chestStealer = v)
                .add(SettingRow.slider("CHEST", "Action Delay", "Ticks mellan varje kist-klick", 1f, 10f, "t",
                        () -> (float) VeloUtilityModules.chestDelayTicks, v -> VeloUtilityModules.chestDelayTicks = Math.round(v))));

        modules.add(new ModuleCard("AUTO TOOL", "Byter automatiskt till rätt verktyg", NavItem.PLAYER, Items.NETHERITE_PICKAXE,
                () -> VeloUtilityModules.autoTool, v -> VeloUtilityModules.autoTool = v));

        modules.add(new ModuleCard("AUTO REPLENISH", "Fyller på block i hotbaren automatiskt", NavItem.PLAYER, Items.COBBLESTONE,
                () -> VeloUtilityModules.autoReplenish, v -> VeloUtilityModules.autoReplenish = v));

        modules.add(new ModuleCard("MIDDLE PEARL", "Kastar Ender Pearl med mushjulet", NavItem.PLAYER, Items.ENDER_PEARL,
                () -> VeloUtilityModules.middleClickPearl, v -> VeloUtilityModules.middleClickPearl = v));

        modules.add(new ModuleCard("AUTO RESPAWN", "Hoppar över dödsskärmen direkt", NavItem.PLAYER, Items.RED_BED,
                () -> VeloUtilityModules.autoRespawn, v -> VeloUtilityModules.autoRespawn = v));

        modules.add(new ModuleCard("ANTI AFK", "Förhindrar AFK-kick med små rörelser", NavItem.PLAYER, Items.CLOCK,
                () -> VeloUtilityModules.antiAfk, v -> VeloUtilityModules.antiAfk = v));

        // --- MOVEMENT ---
        modules.add(new ModuleCard("FREECAM", "Fri kamera i världen (Key: F6)", NavItem.MOVEMENT, Items.ELYTRA,
                FreecamManager::isEnabled, FreecamManager::setEnabled)
                .add(SettingRow.slider("FREECAM", "Fly Speed", "Kamerans rörelsehastighet", 0.2f, 3.0f, "x",
                        () -> FreecamManager.speed, v -> FreecamManager.speed = v)));

        modules.add(new ModuleCard("FREELOOK", "360° kamera runt spelaren (Left Alt)", NavItem.MOVEMENT, Items.ENDER_EYE,
                () -> FreecamManager.freeLookEnabled, v -> FreecamManager.freeLookEnabled = v));

        modules.add(new ModuleCard("SMOOTH ZOOM", "Silkeslen inzoomning (Key: C)", NavItem.MOVEMENT, Items.SPYGLASS,
                () -> VeloUtilityModules.zoomEnabled, v -> VeloUtilityModules.zoomEnabled = v)
                .add(SettingRow.slider("SMOOTH ZOOM", "Target FOV", "Synfält vid full inzoomning", 30f, 60f, "°",
                        () -> (float) VeloUtilityModules.zoomFov, v -> VeloUtilityModules.zoomFov = Math.round(v))));

        modules.add(new ModuleCard("TOGGLE SPRINT", "Håller sprint aktivt när du går framåt", NavItem.MOVEMENT, Items.DIAMOND_BOOTS,
                () -> VeloClient.toggleSprint, v -> VeloClient.toggleSprint = v));

        // --- VIEWMODEL ---
        modules.add(new ModuleCard("VIEWMODEL", "Anpassa handens storlek, position & vinkel", NavItem.VIEWMODEL, Items.DIAMOND_SWORD,
                () -> VeloClient.slimHand, v -> VeloClient.slimHand = v)
                .add(SettingRow.slider("VIEWMODEL", "Scale", "Storlek på hand och vapen", 0.25f, 1.20f, "",
                        () -> VeloClient.handScale, v -> VeloClient.handScale = v))
                .add(SettingRow.slider("VIEWMODEL", "Offset X", "Position i sidled", -0.35f, 0.35f, "",
                        () -> VeloClient.handOffsetX, v -> VeloClient.handOffsetX = v))
                .add(SettingRow.slider("VIEWMODEL", "Offset Y", "Position i höjdled", -0.35f, 0.35f, "",
                        () -> VeloClient.handOffsetY, v -> VeloClient.handOffsetY = v))
                .add(SettingRow.slider("VIEWMODEL", "Offset Z", "Avstånd från kameran", -0.50f, 0.25f, "",
                        () -> VeloClient.handOffsetZ, v -> VeloClient.handOffsetZ = v))
                .add(SettingRow.slider("VIEWMODEL", "Pitch (X)", "Rotation upp/ner", -45f, 45f, "°",
                        () -> VeloClient.handRotX, v -> VeloClient.handRotX = v))
                .add(SettingRow.slider("VIEWMODEL", "Yaw (Y)", "Rotation i sidled", -45f, 45f, "°",
                        () -> VeloClient.handRotY, v -> VeloClient.handRotY = v))
                .add(SettingRow.slider("VIEWMODEL", "Roll (Z)", "Lutning på hand/vapen", -45f, 45f, "°",
                        () -> VeloClient.handRotZ, v -> VeloClient.handRotZ = v)));

        modules.add(new ModuleCard("SLOW SWING", "Mjukare, långsammare sving-animation", NavItem.VIEWMODEL, Items.FEATHER,
                () -> VeloClient.slowSwing, v -> VeloClient.slowSwing = v)
                .add(SettingRow.slider("SLOW SWING", "Swing Duration", "Antal ticks per sving (6–30)", 6f, 30f, "",
                        () -> (float) VeloClient.swingDuration, v -> VeloClient.swingDuration = Math.round(v))));

        // --- ULTRA-MINIMALIST SETTINGS TAB (Grupperad i rena sektioner) ---
        minimalSettings.add(SettingRow.mode("INTERFACE", "Accent Palette", "Marinblå eller skiffergrå färgton i hela klienten",
                VeloClient::getThemeName, () -> VeloClient.blueThemeIndex = (VeloClient.blueThemeIndex + 1) % 3));
        minimalSettings.add(SettingRow.toggle("INTERFACE", "Master HUD Overlay", "Visar watermark, koordinater, arraylist och kompass",
                () -> VeloClient.hudEnabled, v -> VeloClient.hudEnabled = v));
        minimalSettings.add(SettingRow.toggle("INTERFACE", "Hotbar Durability", "Visar hållbarhet i procent för alla hotbar-items i hörnet",
                () -> VeloClient.hotbarDurabilityHud, v -> VeloClient.hotbarDurabilityHud = v));
        minimalSettings.add(SettingRow.toggle("INTERFACE", "Totem Offhand Alert", "Diskret varning om totem ligger i inventoryt men inte i offhand",
                () -> VeloClient.totemWarning, v -> VeloClient.totemWarning = v));

        minimalSettings.add(SettingRow.toggle("VISUALS & CAMERA", "No Hurt Cam", "Eliminerar kameraskakning helt vid skada",
                () -> VeloClient.noHurtCam, v -> VeloClient.noHurtCam = v));
        minimalSettings.add(SettingRow.slider("VISUALS & CAMERA", "Low Fire Offset", "Hur långt ner elden sänks vid brand", 0.15f, 0.55f, "",
                () -> VeloClient.lowFireOffset, v -> VeloClient.lowFireOffset = v));
        minimalSettings.add(SettingRow.slider("VISUALS & CAMERA", "Zoom Target FOV", "Synfält när du håller inne zoom-knappen (C)", 30f, 60f, "°",
                () -> (float) VeloUtilityModules.zoomFov, v -> VeloUtilityModules.zoomFov = Math.round(v)));

        minimalSettings.add(SettingRow.slider("FINDER & ESP", "Block ESP Range", "Sökradie i block för valda ESP-block (Key: R)", 16f, 80f, "m",
                () -> (float) VeloWorldFeatures.blockEspRange, v -> VeloWorldFeatures.blockEspRange = Math.round(v)));
        minimalSettings.add(SettingRow.slider("FINDER & ESP", "Base Chest Threshold", "Minsta antal kistor/shulkers för att klustra en bas", 2f, 20f, "",
                () -> (float) VeloWorldFeatures.baseChestThreshold, v -> VeloWorldFeatures.baseChestThreshold = Math.round(v)));
        minimalSettings.add(SettingRow.danger("FINDER & ESP", "Reset Selected ESP Blocks", "Stänger av Block ESP och rensar alla valda block",
                () -> "RESET (" + VeloWorldFeatures.selectedEspBlocks.size() + ")",
                () -> {
                    VeloWorldFeatures.selectedEspBlocks.clear();
                    VeloWorldFeatures.blockEspEnabled = false;
                }));

        minimalSettings.add(SettingRow.action("WORKSPACE", "HUD Layout Editor", "Flytta och anpassa positionen på alla HUD-paneler (Key: H)",
                () -> "OPEN EDITOR",
                () -> { if (this.client != null) this.client.setScreen(new VeloHudEditorScreen(this)); }));
        minimalSettings.add(SettingRow.action("WORKSPACE", "Waypoints Manager", "Hantera dina sparade koordinater och markörer",
                () -> "OPEN (" + VeloConfigManager.data.waypoints.size() + ")",
                () -> { if (this.client != null) this.client.setScreen(new VeloWaypointScreen(this)); }));
    }

    @Override
    protected void init() {
        // SUPERSTOR meny med generöst utrymme
        this.winW = Math.min(820, this.width - 24);
        this.winH = Math.min(480, this.height - 20);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        String prev = (searchField != null) ? searchField.getText() : "";
        int searchW = 166;
        int searchX = winX + winW - searchW - 36;
        searchField = new TextFieldWidget(this.textRenderer, searchX + 8, winY + 14, searchW - 14, 14, Text.literal("Search"));
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
                if (activeNav == NavItem.ALL || m.category == activeNav) {
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

    private int getActiveModuleCount() {
        int c = 0;
        for (ModuleCard m : modules) {
            if (m.getter.get()) c++;
        }
        return c;
    }

    private int getCategoryCount(NavItem nav) {
        if (nav == NavItem.ALL) return modules.size();
        int c = 0;
        for (ModuleCard m : modules) {
            if (m.category == nav) c++;
        }
        return c;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        int alpha = Math.clamp(Math.round(openAnim * 155.0f), 0, 155);
        context.fill(0, 0, this.width, this.height, (alpha << 24) | 0x05070B);
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
        int slideY = Math.round((1.0f - openAnim) * 12.0f);
        int wy = winY + slideY;

        // Huvudfönster: Skarpa 90°-kanter, marinblå & skiffergrå bas
        VeloUiRenderer.drawSharpPanel(ctx, winX, wy, winW, winH, BG_WINDOW, BORDER_STRONG, true);
        VeloUiRenderer.drawCornerTicks(ctx, winX, wy, winW, winH, 8, accent);

        // Vänster sidopanel (Sidebar)
        VeloUiRenderer.drawSharpRect(ctx, winX + 1, wy + 1, SIDEBAR_W - 1, winH - 2, BG_SIDEBAR);
        VeloUiRenderer.drawSharpRect(ctx, winX + SIDEBAR_W, wy + 1, 1, winH - 2, BORDER_HAIR);

        // Top Header Bar
        int contentX = winX + SIDEBAR_W + 1;
        int contentW = winW - SIDEBAR_W - 2;
        VeloUiRenderer.drawSharpRect(ctx, contentX, wy + 1, contentW, HEADER_H - 1, BG_HEADER);
        VeloUiRenderer.drawSharpRect(ctx, winX + 1, wy + HEADER_H, winW - 2, 1, BORDER_HAIR);

        // Sidebar Brand Block (top-left)
        VeloUiRenderer.drawSharpPanel(ctx, winX + 14, wy + 10, 18, 18, primary, accent, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§lV", winX + 23, wy + 15, TEXT_PRIMARY);
        ctx.drawTextWithShadow(this.textRenderer, "§lVELO", winX + 39, wy + 15, TEXT_PRIMARY);
        ctx.drawTextWithShadow(this.textRenderer, "4.0", winX + 70, wy + 15, TEXT_MUTED);

        // Render Sidebar Navigation
        renderSidebar(ctx, wy, mouseX, mouseY, fastSmooth, accent, primary);

        // Header Breadcrumb & Status
        String crumb = (inspectingModule != null)
                ? ("MODULES  /  " + inspectingModule.name)
                : (activeNav.group + "  /  " + activeNav.label);
        ctx.drawTextWithShadow(this.textRenderer, crumb, contentX + 16, wy + 15, TEXT_SEC);

        int activeCount = getActiveModuleCount();
        String activeBadge = activeCount + " ACTIVE";
        int badgeW = this.textRenderer.getWidth(activeBadge) + 12;
        int badgeX = winX + winW - 216 - badgeW;
        if (badgeX > contentX + 170) {
            VeloUiRenderer.drawSharpPanel(ctx, badgeX, wy + 10, badgeW, 18, 0xFF121A2B, BORDER_STRONG, false);
            ctx.drawCenteredTextWithShadow(this.textRenderer, activeBadge, badgeX + badgeW / 2, wy + 15, accent);
        }

        // Sharp Search Input Box
        int searchW = 166;
        int searchX = winX + winW - searchW - 36;
        boolean searchFocused = searchField != null && searchField.isFocused();
        VeloUiRenderer.drawSharpPanel(ctx, searchX, wy + 9, searchW, 20, 0xFF0A0D13, searchFocused ? accent : BORDER_HAIR, false);

        // Sharp Close Button (X)
        int closeX = winX + winW - 28;
        int closeY = wy + 9;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 20 && mouseY >= closeY && mouseY <= closeY + 20;
        VeloUiRenderer.drawSharpPanel(ctx, closeX, closeY, 20, 20, closeHov ? 0xFF7F1D1D : 0xFF131823, closeHov ? 0xFFEF4444 : BORDER_HAIR, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "✕", closeX + 10, closeY + 6, closeHov ? TEXT_PRIMARY : TEXT_SEC);

        // Main Workspace Area
        int bodyY = wy + HEADER_H + 1;
        int bodyH = winH - HEADER_H - 2;

        if (inspectingModule != null) {
            renderInspectorView(ctx, inspectingModule, contentX, bodyY, contentW, bodyH, mouseX, mouseY, fastSmooth, accent, primary);
        } else if (activeNav == NavItem.SETTINGS) {
            renderMinimalSettingsView(ctx, contentX, bodyY, contentW, bodyH, mouseX, mouseY, fastSmooth, accent, primary);
        } else {
            renderModulesGrid(ctx, contentX, bodyY, contentW, bodyH, mouseX, mouseY, fastSmooth, medSmooth, accent, primary);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderSidebar(DrawContext ctx, int wy, int mouseX, int mouseY, float fastSmooth, int accent, int primary) {
        int curY = wy + HEADER_H + 14;
        String lastGroup = "";

        for (NavItem nav : NavItem.values()) {
            if (!nav.group.equals(lastGroup)) {
                if (!lastGroup.isEmpty()) curY += 12;
                ctx.drawTextWithShadow(this.textRenderer, nav.group, winX + 14, curY, TEXT_MUTED);
                curY += 14;
                lastGroup = nav.group;
            }

            int itemH = 24;
            boolean selected = (nav == activeNav && inspectingModule == null);
            boolean hov = mouseX >= winX + 6 && mouseX <= winX + SIDEBAR_W - 6 && mouseY >= curY && mouseY <= curY + itemH;

            if (selected) {
                VeloUiRenderer.drawSharpRect(ctx, winX + 6, curY, SIDEBAR_W - 12, itemH, 0xFF151D2E);
                if (navIndicatorY < 0) {
                    navIndicatorY = curY;
                } else {
                    navIndicatorY += (curY - navIndicatorY) * fastSmooth;
                }
            } else if (hov) {
                VeloUiRenderer.drawSharpRect(ctx, winX + 6, curY, SIDEBAR_W - 12, itemH, 0x99131926);
            }

            int textCol = selected ? TEXT_PRIMARY : (hov ? TEXT_SEC : 0xFF7B89A3);
            ctx.drawTextWithShadow(this.textRenderer, nav.label, winX + 16, curY + 8, textCol);

            if (nav.group.equals("MODULES")) {
                String cnt = String.valueOf(getCategoryCount(nav));
                ctx.drawTextWithShadow(this.textRenderer, cnt, winX + SIDEBAR_W - 14 - this.textRenderer.getWidth(cnt), curY + 8,
                        selected ? accent : TEXT_MUTED);
            }

            curY += itemH + 2;
        }

        // Glidande 2px vertikal marinblå indikator på aktiv sidopanel-flik
        if (inspectingModule == null && navIndicatorY >= 0) {
            VeloUiRenderer.drawSharpRect(ctx, winX + 6, Math.round(navIndicatorY), 2, 24, accent);
        }

        // Footer keybind-info längst ner i sidopanelen
        int footerY = wy + winH - 34;
        VeloUiRenderer.drawSharpRect(ctx, winX + 12, footerY - 6, SIDEBAR_W - 24, 1, BORDER_HAIR);
        ctx.drawTextWithShadow(this.textRenderer, "R  Block ESP", winX + 14, footerY, TEXT_MUTED);
        ctx.drawTextWithShadow(this.textRenderer, "H  HUD Layout", winX + 14, footerY + 12, TEXT_MUTED);
    }

    private void renderModulesGrid(DrawContext ctx, int areaX, int areaY, int areaW, int areaH,
                                   int mouseX, int mouseY, float fastSmooth, float medSmooth, int accent, int primary) {
        List<ModuleCard> list = getFilteredModules();
        for (ModuleCard m : modules) m.visible = false;

        int pad = 14;
        int gridTop = areaY + pad;
        int gridBottom = areaY + areaH - pad;
        int cols = 3;
        int gap = 10;
        int cardW = (areaW - pad * 2 - gap * (cols - 1)) / cols;
        int cardH = 82;

        int rows = (list.size() + cols - 1) / cols;
        float maxScroll = Math.max(0.0f, rows * (cardH + gap) - (gridBottom - gridTop));
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll);
        currentScroll += (targetScroll - currentScroll) * fastSmooth;

        ctx.enableScissor(areaX + 4, gridTop, areaX + areaW - 4, gridBottom);

        for (int i = 0; i < list.size(); i++) {
            ModuleCard m = list.get(i);
            int row = i / cols;
            int col = i % cols;
            int cx = areaX + pad + col * (cardW + gap);
            int cy = gridTop + row * (cardH + gap) - Math.round(currentScroll);

            if (cy + cardH < gridTop || cy > gridBottom) continue;

            m.cx = cx;
            m.cy = cy;
            m.cw = cardW;
            m.ch = cardH;
            m.visible = true;

            boolean enabled = m.getter.get();
            boolean hovered = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH
                    && mouseY >= gridTop && mouseY <= gridBottom;

            m.hoverAnim += ((hovered ? 1.0f : 0.0f) - m.hoverAnim) * fastSmooth;
            m.enableAnim += ((enabled ? 1.0f : 0.0f) - m.enableAnim) * medSmooth;

            int cardFill = VeloUiRenderer.lerpColor(BG_CARD, BG_CARD_HOVER, m.hoverAnim);
            int cardBorder = VeloUiRenderer.lerpColor(BORDER_HAIR, accent, Math.max(m.enableAnim * 0.75f, m.hoverAnim * 0.45f));

            VeloUiRenderer.drawSharpPanel(ctx, cx, cy, cardW, cardH, cardFill, cardBorder, false);
            if (enabled) {
                VeloUiRenderer.drawCornerTicks(ctx, cx, cy, cardW, cardH, 5, accent);
            }

            // Vänsterställd ikon + rubrik
            int iconX = cx + 10;
            int iconY = cy + 10;
            VeloUiRenderer.drawSharpPanel(ctx, iconX, iconY, 22, 22, 0xFF0B0E14, enabled ? accent : BORDER_HAIR, false);
            ctx.drawItem(new ItemStack(m.icon), iconX + 3, iconY + 3);

            int titleMaxW = cardW - (m.settings.isEmpty() ? 44 : 64);
            String trimmedTitle = this.textRenderer.trimToWidth(m.name, titleMaxW);
            ctx.drawTextWithShadow(this.textRenderer, "§l" + trimmedTitle, iconX + 28, iconY + 7, TEXT_PRIMARY);

            // Kugghjul om inställningar finns
            if (!m.settings.isEmpty()) {
                int gx = cx + cardW - 24;
                int gy = cy + 11;
                boolean gHov = mouseX >= gx && mouseX <= gx + 16 && mouseY >= gy && mouseY <= gy + 16;
                VeloUiRenderer.drawSharpPanel(ctx, gx, gy, 16, 16, gHov ? primary : 0xFF0E121A, gHov ? accent : BORDER_HAIR, false);
                ctx.drawCenteredTextWithShadow(this.textRenderer, "⚙", gx + 8, gy + 4, gHov ? TEXT_PRIMARY : TEXT_SEC);
            }

            // Beskrivning (1 rad i dämpad skiffergrå)
            String desc = this.textRenderer.trimToWidth(m.description, cardW - 20);
            ctx.drawTextWithShadow(this.textRenderer, desc, cx + 10, cy + 38, TEXT_MUTED);

            // Skarp status-bar längst ner på kortet
            int bx = cx + 10;
            int by = cy + cardH - 22;
            int bw = cardW - 20;
            int bh = 14;
            int barFill = VeloUiRenderer.lerpColor(0xFF0B0E14, primary, m.enableAnim);
            int barBorder = VeloUiRenderer.lerpColor(BORDER_HAIR, accent, m.enableAnim);
            VeloUiRenderer.drawSharpPanel(ctx, bx, by, bw, bh, barFill, barBorder, false);

            String stateTxt = enabled ? "ACTIVE" : "OFF";
            int stateCol = VeloUiRenderer.lerpColor(TEXT_MUTED, TEXT_PRIMARY, m.enableAnim);
            ctx.drawCenteredTextWithShadow(this.textRenderer, stateTxt, bx + bw / 2, by + 3, stateCol);
        }

        ctx.disableScissor();
    }

    /**
     * Helt omdesignad, ultra-minimalistisk Settings-vy:
     * - Rena typografiska sektioner med 1px avdelare (inga tunga boxar runt varje rad)
     * - Skarpa rektangulära switchar, hårstråtunna sliders och platta action-knappar
     */
    private void renderMinimalSettingsView(DrawContext ctx, int areaX, int areaY, int areaW, int areaH,
                                           int mouseX, int mouseY, float fastSmooth, int accent, int primary) {
        int padX = 24;
        int listTop = areaY + 14;
        int listBottom = areaY + areaH - 14;
        int rowW = areaW - padX * 2;

        // Räkna total höjd inklusive sektionsrubriker
        int totalH = 0;
        String prevSec = "";
        for (SettingRow r : minimalSettings) {
            if (!r.section.equals(prevSec)) {
                totalH += (prevSec.isEmpty() ? 22 : 34);
                prevSec = r.section;
            }
            totalH += 34;
        }

        float maxScroll = Math.max(0.0f, totalH - (listBottom - listTop));
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll);
        currentScroll += (targetScroll - currentScroll) * fastSmooth;

        ctx.enableScissor(areaX + 4, listTop, areaX + areaW - 4, listBottom);

        int y = listTop - Math.round(currentScroll);
        String curSec = "";

        for (SettingRow r : minimalSettings) {
            r.visible = false;

            if (!r.section.equals(curSec)) {
                if (!curSec.isEmpty()) y += 12;
                curSec = r.section;
                if (y + 20 >= listTop && y <= listBottom) {
                    ctx.drawTextWithShadow(this.textRenderer, curSec, areaX + padX, y + 4, accent);
                    VeloUiRenderer.drawSharpRect(ctx, areaX + padX, y + 16, rowW, 1, BORDER_HAIR);
                }
                y += 22;
            }

            int rowH = 34;
            if (y + rowH >= listTop && y <= listBottom) {
                r.rx = areaX + padX;
                r.ry = y;
                r.rw = rowW;
                r.rh = rowH;
                r.visible = true;

                renderMinimalRow(ctx, r, mouseX, mouseY, fastSmooth, accent, primary);
            }
            y += rowH;
        }

        ctx.disableScissor();
    }

    private void renderInspectorView(DrawContext ctx, ModuleCard mod, int areaX, int areaY, int areaW, int areaH,
                                     int mouseX, int mouseY, float fastSmooth, int accent, int primary) {
        int padX = 24;
        int topY = areaY + 14;

        // Skarp "← BACK"-knapp
        int backW = 68;
        boolean backHov = mouseX >= areaX + padX && mouseX <= areaX + padX + backW && mouseY >= topY && mouseY <= topY + 20;
        VeloUiRenderer.drawSharpPanel(ctx, areaX + padX, topY, backW, 20, backHov ? primary : BG_CARD, backHov ? accent : BORDER_STRONG, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "← BACK", areaX + padX + backW / 2, topY + 6, TEXT_PRIMARY);

        // Modulrubrik & beskrivning
        ctx.drawTextWithShadow(this.textRenderer, "§l" + mod.name, areaX + padX + backW + 14, topY + 2, TEXT_PRIMARY);
        ctx.drawTextWithShadow(this.textRenderer, mod.description, areaX + padX + backW + 14, topY + 13, TEXT_MUTED);

        // Master Toggle uppe till höger i inspectorn
        boolean enabled = mod.getter.get();
        int togW = 86;
        int togX = areaX + areaW - padX - togW;
        VeloUiRenderer.drawSharpPanel(ctx, togX, topY, togW, 20, enabled ? primary : BG_CARD, enabled ? accent : BORDER_STRONG, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, enabled ? "ENABLED" : "DISABLED", togX + togW / 2, topY + 6,
                enabled ? TEXT_PRIMARY : TEXT_SEC);

        VeloUiRenderer.drawSharpRect(ctx, areaX + padX, topY + 28, areaW - padX * 2, 1, BORDER_HAIR);

        // Minimalistiska inställningsrader för vald modul
        int listTop = topY + 36;
        int listBottom = areaY + areaH - 14;
        int rowW = areaW - padX * 2;
        int rowH = 36;

        float maxScroll = Math.max(0.0f, mod.settings.size() * rowH - (listBottom - listTop));
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll);
        currentScroll += (targetScroll - currentScroll) * fastSmooth;

        ctx.enableScissor(areaX + 4, listTop, areaX + areaW - 4, listBottom);

        for (int i = 0; i < mod.settings.size(); i++) {
            SettingRow r = mod.settings.get(i);
            r.visible = false;
            int ry = listTop + i * rowH - Math.round(currentScroll);
            if (ry + rowH < listTop || ry > listBottom) continue;

            r.rx = areaX + padX;
            r.ry = ry;
            r.rw = rowW;
            r.rh = rowH;
            r.visible = true;

            renderMinimalRow(ctx, r, mouseX, mouseY, fastSmooth, accent, primary);
        }

        ctx.disableScissor();
    }

    private void renderMinimalRow(DrawContext ctx, SettingRow r, int mouseX, int mouseY,
                                  float fastSmooth, int accent, int primary) {
        boolean hov = mouseX >= r.rx && mouseX <= r.rx + r.rw && mouseY >= r.ry && mouseY <= r.ry + r.rh;
        if (hov) {
            VeloUiRenderer.drawSharpRect(ctx, r.rx - 6, r.ry + 2, r.rw + 12, r.rh - 4, 0x88131926);
        }

        // Tunn 1px avdelare under raden
        VeloUiRenderer.drawSharpRect(ctx, r.rx, r.ry + r.rh - 1, r.rw, 1, 0x55222A3A);

        // Vänster: Titel + dämpad beskrivning
        ctx.drawTextWithShadow(this.textRenderer, r.name, r.rx, r.ry + 6, TEXT_PRIMARY);
        ctx.drawTextWithShadow(this.textRenderer, r.desc, r.rx, r.ry + 18, TEXT_MUTED);

        // Höger: Minimalistisk kontroll med skarpa kanter
        if (r.type == SettingType.TOGGLE) {
            boolean on = r.boolGet.get();
            r.anim += ((on ? 1.0f : 0.0f) - r.anim) * fastSmooth;

            int swW = 32, swH = 14;
            int swX = r.rx + r.rw - swW;
            int swY = r.ry + (r.rh - swH) / 2;

            int trackFill = VeloUiRenderer.lerpColor(0xFF0A0D13, primary, r.anim);
            int trackBorder = VeloUiRenderer.lerpColor(BORDER_STRONG, accent, r.anim);
            VeloUiRenderer.drawSharpPanel(ctx, swX, swY, swW, swH, trackFill, trackBorder, false);

            int thumbX = swX + 2 + Math.round((swW - 14) * r.anim);
            int thumbCol = VeloUiRenderer.lerpColor(TEXT_SEC, TEXT_PRIMARY, r.anim);
            VeloUiRenderer.drawSharpRect(ctx, thumbX, swY + 2, 10, 10, thumbCol);

        } else if (r.type == SettingType.SLIDER) {
            float val = r.floatGet.get();
            float pct = MathHelper.clamp((val - r.minVal) / (r.maxVal - r.minVal), 0f, 1f);

            int trackW = 140, trackH = 2;
            int trackX = r.rx + r.rw - trackW;
            int trackY = r.ry + (r.rh - trackH) / 2;

            String valStr = (r.maxVal - r.minVal >= 5f)
                    ? String.format(Locale.ROOT, "%d%s", Math.round(val), r.suffix)
                    : String.format(Locale.ROOT, "%.2f%s", val, r.suffix);
            ctx.drawTextWithShadow(this.textRenderer, valStr, trackX - this.textRenderer.getWidth(valStr) - 12, trackY - 3, accent);

            VeloUiRenderer.drawSharpRect(ctx, trackX, trackY, trackW, trackH, BORDER_STRONG);
            VeloUiRenderer.drawSharpRect(ctx, trackX, trackY, Math.max(2, Math.round(trackW * pct)), trackH, accent);
            // Skarp rektangulär slider-markör
            int knobX = trackX + Math.round(trackW * pct) - 3;
            VeloUiRenderer.drawSharpPanel(ctx, knobX, trackY - 5, 6, 12, TEXT_PRIMARY, accent, false);

        } else if (r.type == SettingType.MODE || r.type == SettingType.ACTION || r.type == SettingType.DANGER) {
            String label = r.textGet.get();
            int btnW = Math.max(88, this.textRenderer.getWidth(label) + 20);
            int btnH = 20;
            int btnX = r.rx + r.rw - btnW;
            int btnY = r.ry + (r.rh - btnH) / 2;

            int fill = (r.type == SettingType.DANGER)
                    ? (hov ? 0xFF7F1D1D : 0xFF261218)
                    : (hov ? primary : BG_CARD);
            int border = (r.type == SettingType.DANGER) ? 0xFFEF4444 : (hov ? accent : BORDER_STRONG);
            VeloUiRenderer.drawSharpPanel(ctx, btnX, btnY, btnW, btnH, fill, border, false);
            ctx.drawCenteredTextWithShadow(this.textRenderer, label, btnX + btnW / 2, btnY + 6, TEXT_PRIMARY);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();

        // Close button (X)
        int closeX = winX + winW - 28, closeY = winY + 9;
        if (click.button() == 0 && mx >= closeX && mx <= closeX + 20 && my >= closeY && my <= closeY + 20) {
            if (this.client != null) this.client.setScreen(this.parent);
            return true;
        }

        // Sidebar clicks
        if (click.button() == 0 && mx >= winX + 6 && mx <= winX + SIDEBAR_W - 6) {
            int curY = winY + HEADER_H + 14;
            String lastGroup = "";
            for (NavItem nav : NavItem.values()) {
                if (!nav.group.equals(lastGroup)) {
                    if (!lastGroup.isEmpty()) curY += 12;
                    curY += 14;
                    lastGroup = nav.group;
                }
                if (my >= curY && my <= curY + 24) {
                    if (nav == NavItem.WAYPOINTS) {
                        if (this.client != null) this.client.setScreen(new VeloWaypointScreen(this));
                        return true;
                    } else if (nav == NavItem.HUD) {
                        if (this.client != null) this.client.setScreen(new VeloHudEditorScreen(this));
                        return true;
                    }
                    activeNav = nav;
                    inspectingModule = null;
                    targetScroll = currentScroll = 0f;
                    return true;
                }
                curY += 26;
            }
        }

        int contentX = winX + SIDEBAR_W + 1;
        int contentW = winW - SIDEBAR_W - 2;
        int bodyY = winY + HEADER_H + 1;

        // Inspector view clicks
        if (inspectingModule != null) {
            int padX = 24;
            int topY = bodyY + 14;
            if (click.button() == 0 && mx >= contentX + padX && mx <= contentX + padX + 68 && my >= topY && my <= topY + 20) {
                inspectingModule = null;
                targetScroll = currentScroll = 0f;
                return true;
            }
            int togW = 86;
            int togX = contentX + contentW - padX - togW;
            if (click.button() == 0 && mx >= togX && mx <= togX + togW && my >= topY && my <= topY + 20) {
                inspectingModule.setter.accept(!inspectingModule.getter.get());
                return true;
            }
            if (handleSettingsClick(inspectingModule.settings, click)) return true;
            return super.mouseClicked(click, doubled);
        }

        // Settings tab clicks
        if (activeNav == NavItem.SETTINGS) {
            if (handleSettingsClick(minimalSettings, click)) return true;
            return super.mouseClicked(click, doubled);
        }

        // Module cards clicks
        for (ModuleCard m : getFilteredModules()) {
            if (!m.visible) continue;
            if (mx >= m.cx && mx <= m.cx + m.cw && my >= m.cy && my <= m.cy + m.ch) {
                if (searchField != null) searchField.setFocused(false);
                if (!m.settings.isEmpty()) {
                    int gx = m.cx + m.cw - 24, gy = m.cy + 11;
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

        return super.mouseClicked(click, doubled);
    }

    private boolean handleSettingsClick(List<SettingRow> rows, Click click) {
        if (click.button() != 0) return false;
        double mx = click.x(), my = click.y();
        for (SettingRow r : rows) {
            if (!r.visible) continue;
            if (mx >= r.rx && mx <= r.rx + r.rw && my >= r.ry && my <= r.ry + r.rh) {
                if (r.type == SettingType.TOGGLE) {
                    r.boolSet.accept(!r.boolGet.get());
                    return true;
                } else if (r.type == SettingType.MODE || r.type == SettingType.ACTION || r.type == SettingType.DANGER) {
                    r.action.run();
                    return true;
                } else if (r.type == SettingType.SLIDER) {
                    draggingSlider = r;
                    updateSlider(r, mx);
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
        targetScroll -= (float) (verticalAmount * 40.0);
        return true;
    }

    private void updateSlider(SettingRow r, double mouseX) {
        int trackW = 140;
        int trackX = r.rx + r.rw - trackW;
        float pct = MathHelper.clamp((float) (mouseX - trackX) / (float) trackW, 0.0f, 1.0f);
        r.floatSet.accept(r.minVal + pct * (r.maxVal - r.minVal));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
