package com.chillzone.spawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class SpawnConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("chillzone-spawn-protection.json");
    private State state = new State();

    static final class State {
        boolean protectionEnabled = false;
        SpawnPoint spawn;
        Region region;
    }

    static final class SpawnPoint {
        String dimension;
        double x, y, z;
        float yaw, pitch;
    }

    static final class Region {
        String dimension;
        double centerX, centerZ;
        double edgeX, edgeZ;
        double radius;
        boolean hasCenter;
        boolean hasEdge;
    }

    void load() {
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                State loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), State.class);
                if (loaded != null) state = loaded;
            }
            save();
        } catch (Exception e) {
            System.err.println("[ChillZoneSpawn] Failed to load config: " + e.getMessage());
            state = new State();
        }
    }

    State state() { return state; }

    void save() {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupported) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneSpawn] Failed to save config: " + e.getMessage());
        }
    }
}
