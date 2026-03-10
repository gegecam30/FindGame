package com.findgame.managers;

import com.findgame.FindGamePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ArenaManager {

    private final FindGamePlugin plugin;
    private final Map<String, Arena> arenas = new HashMap<>();
    private File arenasFile;
    private FileConfiguration arenasConfig;

    // ✅ NUEVO: Lobby global del servidor
    private Location globalLobby = null;
    private File lobbyFile;
    private FileConfiguration lobbyConfig;

    public ArenaManager(FindGamePlugin plugin) {
        this.plugin = plugin;
        loadLobby();
        loadArenas();
    }

    // =========================================================
    // LOBBY GLOBAL
    // =========================================================

    /**
     * Guarda el lobby global en lobby.yml
     */
    public void setGlobalLobby(Location loc) {
        this.globalLobby = loc;
        lobbyConfig.set("lobby.world", loc.getWorld().getName());
        lobbyConfig.set("lobby.x", loc.getX());
        lobbyConfig.set("lobby.y", loc.getY());
        lobbyConfig.set("lobby.z", loc.getZ());
        lobbyConfig.set("lobby.yaw", loc.getYaw());
        lobbyConfig.set("lobby.pitch", loc.getPitch());
        saveLobby();
    }

    public Location getGlobalLobby() {
        return globalLobby;
    }

    public boolean hasGlobalLobby() {
        return globalLobby != null;
    }

    private void loadLobby() {
        lobbyFile = new File(plugin.getDataFolder(), "lobby.yml");
        if (!lobbyFile.exists()) {
            try { lobbyFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        lobbyConfig = YamlConfiguration.loadConfiguration(lobbyFile);

        if (lobbyConfig.contains("lobby.world")) {
            World world = Bukkit.getWorld(lobbyConfig.getString("lobby.world"));
            if (world != null) {
                globalLobby = new Location(
                        world,
                        lobbyConfig.getDouble("lobby.x"),
                        lobbyConfig.getDouble("lobby.y"),
                        lobbyConfig.getDouble("lobby.z"),
                        (float) lobbyConfig.getDouble("lobby.yaw"),
                        (float) lobbyConfig.getDouble("lobby.pitch")
                );
            }
        }
    }

    private void saveLobby() {
        try { lobbyConfig.save(lobbyFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // =========================================================
    // ARENA MANAGEMENT
    // =========================================================

    public boolean deleteArena(String name) {
        if (!arenas.containsKey(name)) return false;
        arenas.remove(name);
        arenasConfig.set("arenas." + name, null);
        saveArenas();
        return true;
    }

    public boolean removeNearestNpcSpawn(String arenaName, Player player) {
        Arena arena = getArena(arenaName);
        if (arena == null) return false;

        Location pLoc = player.getLocation();
        Location nearest = null;
        double minDesc = Double.MAX_VALUE;

        for (Location loc : arena.getNpcSpawns()) {
            if (!loc.getWorld().equals(pLoc.getWorld())) continue;
            double dist = loc.distanceSquared(pLoc);
            if (dist < 4.0 && dist < minDesc) {
                minDesc = dist;
                nearest = loc;
            }
        }

        if (nearest != null) {
            arena.getNpcSpawns().remove(nearest);
            saveArena(arena);
            return true;
        }
        return false;
    }

    public boolean createArena(String name) {
        if (arenas.containsKey(name)) return false;
        arenas.put(name, new Arena(name));
        return true;
    }

    public Arena getArena(String name) {
        return arenas.get(name);
    }

    public Arena getRandomArena() {
        if (arenas.isEmpty()) return null;
        List<Arena> validArenas = new ArrayList<>();
        for (Arena a : arenas.values()) {
            if (a.isComplete()) validArenas.add(a);
        }
        if (validArenas.isEmpty()) return null;
        return validArenas.get(new Random().nextInt(validArenas.size()));
    }

    public Set<String> getArenaNames() {
        return arenas.keySet();
    }

    public void setPlayerSpawn(String arenaName, Location loc) {
        Arena arena = getArena(arenaName);
        if (arena != null) {
            arena.setPlayerSpawn(loc);
            saveArena(arena);
        }
    }

    public boolean addNpcSpawn(String arenaName, Location loc) {
        Arena arena = getArena(arenaName);
        if (arena != null) {
            arena.addNpcSpawn(loc);
            saveArena(arena);
            return true;
        }
        return false;
    }

    /**
     * ✅ NUEVO: Establece el radio de confinamiento de una arena.
     */
    public boolean setArenaRadius(String arenaName, double radius) {
        Arena arena = getArena(arenaName);
        if (arena == null) return false;
        arena.setRadius(radius);
        saveArena(arena);
        return true;
    }

    private void loadArenas() {
        arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!arenasFile.exists()) {
            try { arenasFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);

        if (arenasConfig.contains("arenas")) {
            for (String key : arenasConfig.getConfigurationSection("arenas").getKeys(false)) {
                String path = "arenas." + key;
                Arena arena = new Arena(key);

                if (arenasConfig.contains(path + ".player-spawn")) {
                    arena.setPlayerSpawn(deserializeLoc(arenasConfig.getConfigurationSection(path + ".player-spawn")));
                }

                if (arenasConfig.contains(path + ".npc-spawns")) {
                    List<Location> locs = new ArrayList<>();
                    for (String i : arenasConfig.getConfigurationSection(path + ".npc-spawns").getKeys(false)) {
                        locs.add(deserializeLoc(arenasConfig.getConfigurationSection(path + ".npc-spawns." + i)));
                    }
                    arena.setNpcSpawns(locs);
                }

                // ✅ NUEVO: Cargar radio guardado
                if (arenasConfig.contains(path + ".radius")) {
                    arena.setRadius(arenasConfig.getDouble(path + ".radius"));
                }

                arenas.put(key, arena);
            }
        }
    }

    private void saveArena(Arena arena) {
        String path = "arenas." + arena.getName();

        if (arena.getPlayerSpawn() != null) {
            serializeLoc(arena.getPlayerSpawn(), path + ".player-spawn");
        }

        // ✅ NUEVO: Guardar radio
        if (arena.getRadius() > 0) {
            arenasConfig.set(path + ".radius", arena.getRadius());
        }

        arenasConfig.set(path + ".npc-spawns", null);
        List<Location> spawns = arena.getNpcSpawns();
        for (int i = 0; i < spawns.size(); i++) {
            serializeLoc(spawns.get(i), path + ".npc-spawns." + i);
        }

        saveArenas();
    }

    public void saveArenas() {
        try { arenasConfig.save(arenasFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void serializeLoc(Location loc, String path) {
        arenasConfig.set(path + ".world", loc.getWorld().getName());
        arenasConfig.set(path + ".x", loc.getX());
        arenasConfig.set(path + ".y", loc.getY());
        arenasConfig.set(path + ".z", loc.getZ());
        arenasConfig.set(path + ".yaw", loc.getYaw());
        arenasConfig.set(path + ".pitch", loc.getPitch());
    }

    private Location deserializeLoc(ConfigurationSection section) {
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) return null;
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    // =========================================================
    // ARENA INNER CLASS
    // =========================================================

    public static class Arena {
        private final String name;
        private Location playerSpawn;
        private List<Location> npcSpawns = new ArrayList<>();

        // ✅ NUEVO: Radio de confinamiento (-1 = sin límite)
        private double radius = -1;

        public Arena(String name) { this.name = name; }

        public String getName() { return name; }
        public Location getPlayerSpawn() { return playerSpawn; }
        public void setPlayerSpawn(Location playerSpawn) { this.playerSpawn = playerSpawn; }
        public List<Location> getNpcSpawns() { return npcSpawns; }
        public void setNpcSpawns(List<Location> npcSpawns) { this.npcSpawns = npcSpawns; }
        public void addNpcSpawn(Location loc) { this.npcSpawns.add(loc); }

        // ✅ NUEVO: Radio
        public double getRadius() { return radius; }
        public void setRadius(double radius) { this.radius = radius; }
        public boolean hasRadius() { return radius > 0; }

        public boolean isComplete() { return playerSpawn != null && !npcSpawns.isEmpty(); }
    }
}
