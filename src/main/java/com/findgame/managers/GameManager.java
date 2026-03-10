package com.findgame.managers;

import com.findgame.FindGamePlugin;
import com.findgame.managers.ArenaManager.Arena;
import com.findgame.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {

    private final FindGamePlugin plugin;

    // Estado de partida
    private final Set<UUID> activePlayers       = new HashSet<>();
    private final Map<UUID, Integer>  playerLevels  = new HashMap<>();
    private final Map<UUID, Integer>  playerLives   = new HashMap<>();
    private final Map<UUID, BossBar>  activeBars    = new HashMap<>();
    private final Map<UUID, BukkitTask> timerTasks  = new HashMap<>();
    private final Map<UUID, Arena>    playerArenas  = new HashMap<>();

    // ✅ NUEVO: jugadores en fase de observación (no pueden atacar ni moverse libremente)
    private final Set<UUID> inObservationPhase = new HashSet<>();

    public GameManager(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    // JOIN
    // =========================================================

    public boolean joinGame(Player player, String arenaName) {
        UUID uuid = player.getUniqueId();

        if (activePlayers.contains(uuid)) {
            player.sendMessage(plugin.getConfigManager().getMessage("already-playing"));
            return false;
        }

        Arena arena = (arenaName != null)
                ? plugin.getArenaManager().getArena(arenaName)
                : plugin.getArenaManager().getRandomArena();

        if (arena == null || !arena.isComplete()) {
            player.sendMessage(plugin.getConfigManager().getMessage("arena-not-found"));
            return false;
        }

        activePlayers.add(uuid);
        playerLevels.put(uuid, 1);
        playerLives.put(uuid, plugin.getConfig().getInt("settings.max-lives", 3));
        playerArenas.put(uuid, arena);

        plugin.getStatsManager().addGamePlayed(uuid);

        if (plugin.getConfig().getBoolean("settings.gamemode-management", true)) {
            String modeName = plugin.getConfig().getString("settings.gamemode-playing", "ADVENTURE");
            try { player.setGameMode(GameMode.valueOf(modeName)); }
            catch (IllegalArgumentException e) { player.setGameMode(GameMode.ADVENTURE); }
        }

        player.sendMessage(plugin.getConfigManager().getMessage("game-joined"));
        startRound(player, arena, true);
        return true;
    }

    // =========================================================
    // ROUND
    // =========================================================

    /**
     * @param teleportPlayer true solo en la primera ronda.
     */
    private void startRound(Player player, Arena arena, boolean teleportPlayer) {
        if (!isPlaying(player)) return;

        UUID uuid = player.getUniqueId();
        int level = playerLevels.getOrDefault(uuid, 1);

        if (teleportPlayer) {
            player.teleport(arena.getPlayerSpawn());
        }

        // ✅ NUEVO: Fase de observación antes de que el juego empiece
        int obsDuration = plugin.getConfig().getInt("settings.observation-phase", 5);
        startObservationPhase(player, arena, level, obsDuration);
    }

    // =========================================================
    // ✅ NUEVO: FASE DE OBSERVACIÓN
    // =========================================================

    /**
     * Aplica slowness + night-vision para simular "zoom de análisis".
     * El jugador no puede atacar durante este tiempo (chequeado en processGuess).
     * Al terminar, arranca el round normal con timer.
     */
    private void startObservationPhase(Player player, Arena arena, int level, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        inObservationPhase.add(uuid);

        // Spawn NPCs ahora para que el jugador pueda observarlos
        int npcCount = 3 + level;
        plugin.getNpcManager().spawnNPCs(arena, player, npcCount);

        // Efectos visuales de la fase de observación
        int ticks = durationSeconds * 20 + 10; // +10 por margen

        // Slowness IV = casi sin movimiento (simula zoom)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW,
                ticks, 4, false, false, false));

        // Night Vision para ver bien los aldeanos
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION,
                ticks, 0, false, false, false));

        // Título de la fase
        String title    = plugin.getConfigManager().getMessageWithPlaceholders(
                "level-title", "level", String.valueOf(level));
        String subtitle = plugin.getConfigManager().getMessage("obs-subtitle");
        player.sendTitle(title, subtitle, 10, 60, 10);

        player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                "obs-start", "seconds", String.valueOf(durationSeconds)));

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);

        // BossBar de cuenta regresiva de observación
        String obsBarFormat = plugin.getConfig().getString(
                "messages.obs-bossbar-text", "&bObservation Phase: &f{time}s");
        BossBar obsBar = Bukkit.createBossBar(
                ColorUtil.colorize(obsBarFormat.replace("{time}", String.valueOf(durationSeconds))),
                BarColor.BLUE, BarStyle.SEGMENTED_6);
        obsBar.addPlayer(player);
        activeBars.put(uuid, obsBar);

        // Countdown y transición al juego
        BukkitTask obsTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int timeLeft = durationSeconds;

            @Override
            public void run() {
                if (!isPlaying(player)) {
                    obsBar.removeAll();
                    activeBars.remove(uuid);
                    inObservationPhase.remove(uuid);
                    this.cancel();
                    return;
                }

                timeLeft--;

                BossBar bar = activeBars.get(uuid);
                if (bar != null) {
                    bar.setTitle(ColorUtil.colorize(
                            obsBarFormat.replace("{time}", String.valueOf(timeLeft))));
                    bar.setProgress(Math.max(0.0, (double) timeLeft / durationSeconds));
                }

                if (timeLeft <= 3 && timeLeft > 0) {
                    player.playSound(player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    player.sendTitle("", ColorUtil.colorize("&e" + timeLeft + "..."), 0, 25, 5);
                }

                if (timeLeft <= 0) {
                    // Quitar bossbar de observación
                    if (bar != null) { bar.removeAll(); activeBars.remove(uuid); }
                    inObservationPhase.remove(uuid);
                    this.cancel();

                    // Quitar efectos y arrancar ronda real
                    player.removePotionEffect(PotionEffectType.SLOW);
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);

                    beginHuntPhase(player, arena, level);
                }
            }
        }, 20L, 20L);

        timerTasks.put(uuid, obsTask); // guardamos para poder cancelar si sale
    }

    /**
     * Arranca la fase de caza real (BossBar amarilla + timer de juego).
     */
    private void beginHuntPhase(Player player, Arena arena, int level) {
        if (!isPlaying(player)) return;

        // Título de ¡A JUGAR!
        String huntTitle    = plugin.getConfigManager().getMessageWithPlaceholders(
                "level-title", "level", String.valueOf(level));
        String huntSubtitle = plugin.getConfigManager().getMessage("level-subtitle");
        player.sendTitle(huntTitle, huntSubtitle, 5, 40, 10);

        player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                "game-start", "level", String.valueOf(level)));

        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        int time = 30 + (level * 2);
        startTimer(player, time);
    }

    // =========================================================
    // GUESS / VICTORY / DEFEAT
    // =========================================================

    public void processGuess(Player player, Entity target) {
        if (!isPlaying(player)) return;

        // ✅ NUEVO: Bloquear golpes durante observación
        if (inObservationPhase.contains(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("obs-no-attack"));
            return;
        }

        if (plugin.getNpcManager().isImpostor(player, target.getEntityId())) {
            handleVictory(player);
        } else {
            handleDefeat(player, target);
        }
    }

    private void handleVictory(Player player) {
        stopTimer(player);
        inObservationPhase.remove(player.getUniqueId());

        player.sendMessage(plugin.getConfigManager().getMessage("game-win"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        plugin.getRewardManager().giveWinRewards(player);
        plugin.getStatsManager().addWin(player.getUniqueId());
        plugin.getNpcManager().cleanup(player);

        UUID uuid = player.getUniqueId();
        int nextLevel = playerLevels.getOrDefault(uuid, 1) + 1;
        playerLevels.put(uuid, nextLevel);
        plugin.getStatsManager().updateMaxLevel(uuid, nextLevel);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isPlaying(player)) {
                Arena currentArena = playerArenas.get(player.getUniqueId());
                if (currentArena != null) startRound(player, currentArena, false);
            }
        }, 40L);
    }

    private void handleDefeat(Player player, Entity innocentEntity) {
        UUID uuid = player.getUniqueId();
        inObservationPhase.remove(uuid);

        int currentLives = playerLives.getOrDefault(uuid, 1) - 1;
        playerLives.put(uuid, currentLives);

        if (innocentEntity != null) {
            innocentEntity.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        if (currentLives <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("game-over"));
            player.sendTitle(plugin.getConfigManager().getMessage("game-over"), "", 10, 60, 20);
            leaveGame(player);
        } else {
            player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                    "game-life-lost", "lives", String.valueOf(currentLives)));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

            if (innocentEntity == null) {
                stopTimer(player);
                plugin.getNpcManager().cleanup(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isPlaying(player)) {
                        Arena currentArena = playerArenas.get(player.getUniqueId());
                        if (currentArena != null) startRound(player, currentArena, false);
                    }
                }, 20L);
            }
        }
    }

    // =========================================================
    // ✅ NUEVO: RADIO - verificar si el jugador salió del límite
    // =========================================================

    /**
     * Llamado desde GameListener en PlayerMoveEvent.
     * Si el jugador sale del radio, se le empuja de vuelta al spawn de la arena.
     */
    public void checkRadiusBreach(Player player) {
        if (!isPlaying(player)) return;

        Arena arena = playerArenas.get(player.getUniqueId());
        if (arena == null || !arena.hasRadius() || arena.getPlayerSpawn() == null) return;

        Location center = arena.getPlayerSpawn();
        Location playerLoc = player.getLocation();

        // Solo comparar si están en el mismo mundo
        if (!center.getWorld().equals(playerLoc.getWorld())) return;

        double distance = center.distance(playerLoc);
        if (distance > arena.getRadius()) {
            // Teleportar de vuelta al borde del radio (dirección hacia el centro)
            double ratio = (arena.getRadius() - 0.5) / distance;
            double newX = center.getX() + (playerLoc.getX() - center.getX()) * ratio;
            double newZ = center.getZ() + (playerLoc.getZ() - center.getZ()) * ratio;

            Location pushBack = new Location(
                    center.getWorld(), newX, playerLoc.getY(), newZ,
                    playerLoc.getYaw(), playerLoc.getPitch());

            player.teleport(pushBack);
            player.sendMessage(plugin.getConfigManager().getMessage("arena-boundary"));
            player.playSound(pushBack, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
    }

    // =========================================================
    // TIMER
    // =========================================================

    private void startTimer(Player player, int seconds) {
        stopTimer(player);

        String bossFormat = plugin.getConfig().getString("messages.bossbar-text", "&eTime: {time}s");
        BossBar bar = Bukkit.createBossBar(
                ColorUtil.colorize(bossFormat.replace("{time}", String.valueOf(seconds))),
                BarColor.YELLOW, BarStyle.SOLID);
        bar.addPlayer(player);
        activeBars.put(player.getUniqueId(), bar);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            double timeLeft = seconds;
            final double totalTime = seconds;

            @Override
            public void run() {
                if (!isPlaying(player)) { stopTimer(player); return; }

                timeLeft -= 1.0;
                BossBar currentBar = activeBars.get(player.getUniqueId());
                if (currentBar != null) {
                    currentBar.setTitle(ColorUtil.colorize(
                            bossFormat.replace("{time}", String.valueOf((int) timeLeft))));
                    double progress = timeLeft / totalTime;
                    currentBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                    if (timeLeft <= 5) {
                        currentBar.setColor(BarColor.RED);
                        player.playSound(player.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }

                if (timeLeft <= 0) handleDefeat(player, null);
            }
        }, 20L, 20L);

        timerTasks.put(player.getUniqueId(), task);
    }

    public void stopTimer(Player player) {
        UUID uuid = player.getUniqueId();
        if (timerTasks.containsKey(uuid)) { timerTasks.get(uuid).cancel(); timerTasks.remove(uuid); }
        if (activeBars.containsKey(uuid))  { activeBars.get(uuid).removeAll(); activeBars.remove(uuid); }
    }

    // =========================================================
    // LEAVE
    // =========================================================

    public boolean leaveGame(Player player) {
        if (!isPlaying(player)) return false;

        stopTimer(player);
        plugin.getNpcManager().cleanup(player);

        UUID uuid = player.getUniqueId();
        activePlayers.remove(uuid);
        playerLevels.remove(uuid);
        playerLives.remove(uuid);
        playerArenas.remove(uuid);
        inObservationPhase.remove(uuid);

        // Limpiar efectos de observación si salió a mitad
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        player.sendMessage(plugin.getConfigManager().getMessage("game-left"));

        // ✅ NUEVO: Teleportar al lobby global si está configurado
        if (plugin.getArenaManager().hasGlobalLobby()) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    player.teleport(plugin.getArenaManager().getGlobalLobby()), 2L);
        } else if (plugin.getConfig().getBoolean("settings.gamemode-management", true)) {
            // Fallback: solo restaurar gamemode
            String mode = plugin.getConfig().getString("settings.gamemode-lobby", "SURVIVAL");
            try { player.setGameMode(GameMode.valueOf(mode)); }
            catch (Exception e) { player.setGameMode(GameMode.SURVIVAL); }
        }

        // Restaurar gamemode lobby siempre
        if (plugin.getConfig().getBoolean("settings.gamemode-management", true)) {
            String mode = plugin.getConfig().getString("settings.gamemode-lobby", "SURVIVAL");
            try { player.setGameMode(GameMode.valueOf(mode)); }
            catch (Exception e) { player.setGameMode(GameMode.SURVIVAL); }
        }

        return true;
    }

    public boolean isPlaying(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public boolean isInObservationPhase(Player player) {
        return inObservationPhase.contains(player.getUniqueId());
    }

    public void clearAllPlayers() {
        for (UUID uuid : new HashSet<>(activePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) leaveGame(p);
        }
    }
}
