package com.velo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class VeloConfigManager {

    public static class Waypoint {
        public String name;
        public int x, y, z;
        public int color;
        public boolean enabled;

        public Waypoint(String name, int x, int y, int z, int color) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.enabled = true;
        }
    }

    public static class HudPos {
        public int x, y;
        public boolean enabled;

        public HudPos(int x, int y, boolean enabled) {
            this.x = x;
            this.y = y;
            this.enabled = enabled;
        }
    }

    public static class ConfigData {
        public HudPos watermarkHud = new HudPos(10, 10, true);
        public HudPos coordsHud = new HudPos(10, 62, true);
        public HudPos arrayListHud = new HudPos(-1, 10, true); // -1 = auto right-align
        public HudPos armorHud = new HudPos(10, 106, true);
        public HudPos inventoryHud = new HudPos(10, 152, false);
        public HudPos targetHud = new HudPos(-2, 160, true);   // -2 = auto center-right
        public HudPos compassHud = new HudPos(-3, 8, true);    // -3 = auto top-center

        public List<Waypoint> waypoints = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("velo-client-v2.json");

    public static ConfigData data = new ConfigData();

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    ConfigData loaded = GSON.fromJson(r, ConfigData.class);
                    if (loaded != null) {
                        data = loaded;
                        if (data.watermarkHud == null) data.watermarkHud = new HudPos(10, 10, true);
                        if (data.coordsHud == null) data.coordsHud = new HudPos(10, 62, true);
                        if (data.arrayListHud == null) data.arrayListHud = new HudPos(-1, 10, true);
                        if (data.armorHud == null) data.armorHud = new HudPos(10, 106, true);
                        if (data.inventoryHud == null) data.inventoryHud = new HudPos(10, 152, false);
                        if (data.targetHud == null) data.targetHud = new HudPos(-2, 160, true);
                        if (data.compassHud == null) data.compassHud = new HudPos(-3, 8, true);
                        if (data.waypoints == null) data.waypoints = new ArrayList<>();
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        data = new ConfigData();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(data, w);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void resetHudPositions() {
        data.watermarkHud = new HudPos(10, 10, true);
        data.coordsHud = new HudPos(10, 62, true);
        data.arrayListHud = new HudPos(-1, 10, true);
        data.armorHud = new HudPos(10, 106, true);
        data.inventoryHud = new HudPos(10, 152, false);
        data.targetHud = new HudPos(-2, 160, true);
        data.compassHud = new HudPos(-3, 8, true);
        save();
    }
}
