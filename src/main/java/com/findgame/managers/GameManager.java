package com.findgame.managers;

import com.findgame.FindGamePlugin;
import com.findgame.managers.ArenaManager.Arena;
import com.findgame.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class GameManager {

    private final FindGamePlugin plugin;

    // Almacenamiento de estado
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Map<UUID, Integer> playerLevels = new HashMap<>();
    private final Map<UUID, Integer> playerLives = new HashMap<>();
    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private final Map<UUID, BukkitTask> timerTasks = new HashMap<>();

    // ✅ FIX 1: Guardar la arena asignada al jugador durante toda su sesión
    private final Map<UUID, Arena> playerArenas = new HashMap<>();

    public GameManager(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Une a un jugador a una partida específica o aleatoria.
     */
    public boolean joinGame(Player player, String arenaName) {
        UUID uuid = player.getUniqueId();

        if (activePlayers.contains(uuid)) {
            player.sendMessage(plugin.getConfigManager().getMessage("already-playing"));
            return false;
        }

        // Buscar arena
        Arena arena = (arenaName != null)
                ? plugin.getArenaManager().getArena(arenaName)
                : plugin.getArenaManager().getRandomArena();

        if (arena == null || !arena.isComplete()) {
            player.sendMessage(plugin.getConfigManager().getMessage("arena-not-found"));
            return false;
        }

        // 1. Inicializar Datos del Jugador
        activePlayers.add(uuid);
        playerLevels.put(uuid, 1);
        playerLives.put(uuid, plugin.getConfig().getInt("settings.max-lives", 3));

        // ✅ FIX 1: Guardar la arena del jugador para toda la sesión
        playerArenas.put(uuid, arena);

        // 2. Registrar "Played" Stat (PAPI)
        plugin.getStatsManager().addGamePlayed(uuid);

        // 3. Gestionar Modo de Juego (Configurable)
        if (plugin.getConfig().getBoolean("settings.gamemode-management", true)) {
            String modeName = plugin.getConfig().getString("settings.gamemode-playing", "ADVENTURE");
            try {
                player.setGameMode(GameMode.valueOf(modeName));
            } catch (IllegalArgumentException e) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }

        player.sendMessage(plugin.getConfigManager().getMessage("game-joined"));

        // ✅ FIX 2: La primera ronda SÍ teleporta (es el join inicial)
        startRound(player, arena, true);
        return true;
    }

    /**
     * Inicia una ronda.
     *
     * @param teleportPlayer true solo en la primera ronda (join). En rondas
     *                       subsecuentes es false para no mover al jugador.
     */
    private void startRound(Player player, Arena arena, boolean teleportPlayer) {
        if (!isPlaying(player)) return;

        UUID uuid = player.getUniqueId();
        int level = playerLevels.getOrDefault(uuid, 1);

        // ✅ FIX 2: Solo teleportar en la ronda inicial
        if (teleportPlayer) {
            player.teleport(arena.getPlayerSpawn());
        }

        // Títulos desde Config
        String title = plugin.getConfigManager().getMessageWithPlaceholders(
                "level-title", "level", String.valueOf(level));
        String subtitle = plugin.getConfigManager().getMessage("level-subtitle");
        player.sendTitle(title, subtitle, 10, 40, 10);

        // Mensaje de inicio
        player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                "game-start", "level", String.valueOf(level)));

        // Spawn NPCs (Dificultad progresiva: 3 base + nivel)
        int npcCount = 3 + level;
        plugin.getNpcManager().spawnNPCs(arena, player, npcCount);

        // Iniciar Temporizador (Tiempo dinámico)
        int time = 30 + (level * 2);
        startTimer(player, time);
    }

    /**
     * Procesa el golpe del jugador.
     */
    public void processGuess(Player player, Entity target) {
        if (!isPlaying(player)) return;

        if (plugin.getNpcManager().isImpostor(player, target.getEntityId())) {
            handleVictory(player);
        } else {
            handleDefeat(player, target);
        }
    }

    /**
     * Lógica de Victoria.
     */
    private void handleVictory(Player player) {
        stopTimer(player);
        player.sendMessage(plugin.getConfigManager().getMessage("game-win"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        // Recompensas y Stats
        plugin.getRewardManager().giveWinRewards(player);
        plugin.getStatsManager().addWin(player.getUniqueId());

        // Limpieza de NPCs (los elimina del mundo, el jugador no se mueve)
        plugin.getNpcManager().cleanup(player);

        // Subir de Nivel
        UUID uuid = player.getUniqueId();
        int nextLevel = playerLevels.getOrDefault(uuid, 1) + 1;
        playerLevels.put(uuid, nextLevel);
        plugin.getStatsManager().updateMaxLevel(uuid, nextLevel);

        // ✅ FIX 1+2: Usar la arena guardada del jugador, sin teleportar
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isPlaying(player)) {
                // Recuperar la arena guardada en lugar de buscar una aleatoria
                Arena currentArena = playerArenas.get(player.getUniqueId());
                if (currentArena != null) {
                    startRound(player, currentArena, false); // false = no teleportar
                }
            }
        }, 40L);
    }

    /**
     * Lógica de Derrota (Vidas/Tiempo).
     */
    private void handleDefeat(Player player, Entity innocentEntity) {
        UUID uuid = player.getUniqueId();
        int currentLives = playerLives.getOrDefault(uuid, 1) - 1;
        playerLives.put(uuid, currentLives);

        if (innocentEntity != null) {
            innocentEntity.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        if (currentLives <= 0) {
            // GAME OVER FINAL
            player.sendMessage(plugin.getConfigManager().getMessage("game-over"));
            player.sendTitle(plugin.getConfigManager().getMessage("game-over"), "", 10, 60, 20);
            leaveGame(player);
        } else {
            // Pierde una vida
            player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                    "game-life-lost", "lives", String.valueOf(currentLives)));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

            // Si falló por tiempo (sin golpear), reiniciamos ronda sin mover al jugador
            if (innocentEntity == null) {
                stopTimer(player);
                plugin.getNpcManager().cleanup(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isPlaying(player)) {
                        // ✅ FIX 1+2: Misma arena, sin teleportar
                        Arena currentArena = playerArenas.get(player.getUniqueId());
                        if (currentArena != null) {
                            startRound(player, currentArena, false); // false = no teleportar
                        }
                    }
                }, 20L);
            }
        }
    }

    /**
     * Inicia la BossBar configurable.
     */
    private void startTimer(Player player, int seconds) {
        stopTimer(player);

        String bossFormat = plugin.getConfig().getString("messages.bossbar-text", "&eTime: {time}s");
        String initialTitle = ColorUtil.colorize(bossFormat.replace("{time}", String.valueOf(seconds)));

        BossBar bar = Bukkit.createBossBar(initialTitle, BarColor.YELLOW, BarStyle.SOLID);
        bar.addPlayer(player);
        activeBars.put(player.getUniqueId(), bar);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            double timeLeft = seconds;
            final double totalTime = seconds;

            @Override
            public void run() {
                if (!isPlaying(player)) {
                    stopTimer(player);
                    return;
                }

                timeLeft -= 1.0;
                BossBar currentBar = activeBars.get(player.getUniqueId());
                if (currentBar != null) {
                    String currentTitle = ColorUtil.colorize(
                            bossFormat.replace("{time}", String.valueOf((int) timeLeft)));
                    currentBar.setTitle(currentTitle);

                    double progress = timeLeft / totalTime;
                    currentBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

                    if (timeLeft <= 5) {
                        currentBar.setColor(BarColor.RED);
                        player.playSound(player.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }

                if (timeLeft <= 0) {
                    handleDefeat(player, null);
                }
            }
        }, 20L, 20L);

        timerTasks.put(player.getUniqueId(), task);
    }

    public void stopTimer(Player player) {
        UUID uuid = player.getUniqueId();
        if (timerTasks.containsKey(uuid)) {
            timerTasks.get(uuid).cancel();
            timerTasks.remove(uuid);
        }
        if (activeBars.containsKey(uuid)) {
            activeBars.get(uuid).removeAll();
            activeBars.remove(uuid);
        }
    }

    public boolean leaveGame(Player player) {
        if (!isPlaying(player)) return false;

        stopTimer(player);
        plugin.getNpcManager().cleanup(player);

        UUID uuid = player.getUniqueId();
        activePlayers.remove(uuid);
        playerLevels.remove(uuid);
        playerLives.remove(uuid);

        // ✅ FIX 1: Limpiar la arena guardada al salir
        playerArenas.remove(uuid);

        player.sendMessage(plugin.getConfigManager().getMessage("game-left"));

        // Restaurar modo lobby
        if (plugin.getConfig().getBoolean("settings.gamemode-management", true)) {
            String mode = plugin.getConfig().getString("settings.gamemode-lobby", "SURVIVAL");
            try {
                player.setGameMode(GameMode.valueOf(mode));
            } catch (Exception e) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        return true;
    }

    public boolean isPlaying(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    /**
     * Limpia todas las partidas activas (usado en onDisable).
     */
    public void clearAllPlayers() {
        for (UUID uuid : new HashSet<>(activePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) leaveGame(p);
        }
    }
}
