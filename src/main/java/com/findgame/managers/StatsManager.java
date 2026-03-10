package com.findgame.managers;

import com.findgame.FindGamePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class StatsManager {

    private final FindGamePlugin plugin;
    private File statsFile;
    private FileConfiguration statsConfig;

    public StatsManager(FindGamePlugin plugin) {
        this.plugin = plugin;
        setupStatsFile();
    }

    private void setupStatsFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        statsFile = new File(plugin.getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create stats.yml!");
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void addWin(UUID uuid) {
        int wins = getWins(uuid) + 1;
        statsConfig.set(uuid.toString() + ".wins", wins);
        saveStats();
    }

    public int getWins(UUID uuid) {
        return statsConfig.getInt(uuid.toString() + ".wins", 0);
    }

    public void addGamePlayed(UUID uuid) {
        int played = getGamesPlayed(uuid) + 1;
        statsConfig.set(uuid.toString() + ".played", played);
        saveStats();
    }

    public int getGamesPlayed(UUID uuid) {
        return statsConfig.getInt(uuid.toString() + ".played", 0);
    }

    public void updateMaxLevel(UUID uuid, int level) {
        int currentMax = getMaxLevel(uuid);
        if (level > currentMax) {
            statsConfig.set(uuid.toString() + ".max-level", level);
            saveStats();
        }
    }

    public int getMaxLevel(UUID uuid) {
        return statsConfig.getInt(uuid.toString() + ".max-level", 0);
    }

    public Map<String, Integer> getTopPlayers(int limit) {
        Map<String, Integer> allWins = new HashMap<>();
        
        for (String uuidStr : statsConfig.getKeys(false)) {
            int wins = statsConfig.getInt(uuidStr + ".wins", 0);
            if (wins > 0) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                    String name = (p.getName() != null) ? p.getName() : "Unknown";
                    allWins.put(name, wins);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return allWins.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue, 
                        (e1, e2) -> e1, 
                        LinkedHashMap::new
                ));
    }
    
    // CORRECCIÓN: Método añadido para compatibilidad con FindGamePlugin
    public void saveAllStats() {
        saveStats();
    }

    private void saveStats() {
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save stats.yml!");
        }
    }
}