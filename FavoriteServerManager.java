package com.velo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FavoriteServerManager {
    public static class ServerEntry {
        public String name;
        public String address;

        public ServerEntry(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("velo-client-servers.json");

    private static List<ServerEntry> servers = new ArrayList<>();

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    Type listType = new TypeToken<ArrayList<ServerEntry>>() {}.getType();
                    List<ServerEntry> loaded = GSON.fromJson(reader, listType);
                    if (loaded != null) {
                        servers = loaded;
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        servers = new ArrayList<>();
        servers.add(new ServerEntry("Hypixel", "mc.hypixel.net"));
        servers.add(new ServerEntry("Minemen Club", "na.minemen.club"));
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(servers, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<ServerEntry> getServers() {
        if (servers.isEmpty() && !Files.exists(CONFIG_PATH)) {
            load();
        }
        return servers;
    }

    public static void addServer(String name, String address) {
        if (name == null || name.isBlank()) {
            name = address;
        }
        if (address != null && !address.isBlank()) {
            servers.add(new ServerEntry(name.trim(), address.trim()));
            save();
        }
    }

    public static void removeServer(int index) {
        if (index >= 0 && index < servers.size()) {
            servers.remove(index);
            save();
        }
    }
}
