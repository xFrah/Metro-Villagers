package com.fdimo.metrovillagers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/metro_villagers.json");

    public static ConfigData DATA = new ConfigData();

    public static class ConfigData {
        public int pathfindingRadius = 96;
        public int maxPathfindingNodes = 5000;
        public double gossipRadius = 5.0;
        public boolean enableDebugBeams = true;
        public boolean enableDebugLogs = true;
        public int occupancyCacheDurationSeconds = 30; // Default: 30 seconds
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                DATA = GSON.fromJson(reader, ConfigData.class);
            } catch (Exception e) {
                Constants.LOG.error("Failed to read config file", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        CONFIG_FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(DATA, writer);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save config file", e);
        }
    }
}
