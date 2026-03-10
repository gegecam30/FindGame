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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {

    private final FindGamePlugin plugin;

    private final Set<UUID>           activePlayers     = new HashSet<>();
    private final Map<UUID, Integer>  playerLevels      = new HashMap<>();
    private final Map<UUID, Integer>  playerLives       = new HashMap<>();
    private final Map<UUID, BossBar>  activeBars        = new HashMap<>();
    private final Map<UUID, BukkitTask> timerTasks      = new HashMap<>();
    private final Map<UUID, Arena>    playerArenas      = new HashMap<>();
    private final Set<UUID>           inObservation     = new HashSet<>();
    private final Map<UUID, Long>      boundaryWarnTime  = new HashMap<>();

    /** Compatible helper — SLOWNESS was SLOW before 1.20.5 */
    private static final PotionEffectType SLOWNESS;
    static {
        PotionEffectType t = PotionEffectType.getByName("SLOWNESS");
        SLOWNESS = (t != null) ? t : PotionEffectType.getByName("SLOW");
    }

    public GameManager(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    // JOIN
    // =========================================================

    public boolean joinGame(Player player, String arenaName) {
        UUID uuid = player.getUniqueId();

        if (activePlayers.contains(uuid)) {
            player.sendMessage(plugin.getConfigManager().msg("game.already-in"));
            return false;
        }

        Arena arena = (arenaName != null)
                ? plugin.getArenaManager().getArena(arenaName)
                : plugin.getArenaManager().getRandomArena();

        if (arena == null || !arena.isComplete()) {
            player.sendMessage(plugin.getConfigManager().msg("arena.not-found",
                    "name", arenaName != null ? arenaName : "?"));
            return false;
        }

        activePlayers.add(uuid);
        playerLevels.put(uuid, 1);
        playerLives.put(uuid, plugin.getConfig().getInt("settings.max-lives", 3));
        playerArenas.put(uuid, arena);

        plugin.getStatsManager().addGamePlayed(uuid);
        applyPlayingGameMode(player);

        player.sendMessage(plugin.getConfigManager().msg("game.joined"));
        startRound(player, arena, true);
        return true;
    }

    // =========================================================
    // ROUND
    // =========================================================

    private void startRound(Player player, Arena arena, boolean teleport) {
        if (!isPlaying(player)) return;
        if (teleport) player.teleport(arena.getPlayerSpawn());

        int level = playerLevels.getOrDefault(player.getUniqueId(), 1);
        int obsSecs = plugin.getConfig().getInt("settings.observation-phase", 7);
        startObservationPhase(player, arena, level, obsSecs);
    }

    // =========================================================
    // OBSERVATION PHASE
    // =========================================================

    private void startObservationPhase(Player player, Arena arena, int level, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        inObservation.add(uuid);

        // Spawn NPCs immediately so the player can study them
        plugin.getNpcManager().spawnNPCs(arena, player, 3 + level);

        int ticks = durationSeconds * 20 + 10;
        if (SLOWNESS != null) {
            player.addPotionEffect(new PotionEffect(SLOWNESS, ticks, 4, false, false, false));
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, ticks, 0, false, false, false));

        // Titles
        player.sendTitle(
                plugin.getConfigManager().msg("titles.level-title", false, "level", String.valueOf(level)),
                plugin.getConfigManager().msg("observation.subtitle", false),
                10, 60, 10);

        player.sendMessage(plugin.getConfigManager().msg("observation.start",
                "seconds", String.valueOf(durationSeconds)));

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);

        // Observation BossBar
        String obsFormat = plugin.getConfigManager().msg("observation.bossbar", false);
        BossBar obsBar = Bukkit.createBossBar(
                ColorUtil.colorize(obsFormat.replace("{time}", String.valueOf(durationSeconds))),
                BarColor.BLUE, BarStyle.SEGMENTED_6);
        obsBar.addPlayer(player);
        activeBars.put(uuid, obsBar);

        BukkitTask obsTask = new BukkitRunnable() {
            int timeLeft = durationSeconds;

            @Override
            public void run() {
                if (!isPlaying(player)) {
                    obsBar.removeAll();
                    activeBars.remove(uuid);
                    inObservation.remove(uuid);
                    this.cancel();
                    return;
                }

                timeLeft--;

                BossBar bar = activeBars.get(uuid);
                if (bar != null) {
                    bar.setTitle(ColorUtil.colorize(
                            obsFormat.replace("{time}", String.valueOf(timeLeft))));
                    bar.setProgress(Math.max(0.0, (double) timeLeft / durationSeconds));
                }

                if (timeLeft <= 3 && timeLeft > 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    player.sendTitle("", ColorUtil.colorize("&e" + timeLeft + "..."), 0, 25, 5);
                }

                if (timeLeft <= 0) {
                    if (bar != null) { bar.removeAll(); activeBars.remove(uuid); }
                    inObservation.remove(uuid);
                    this.cancel();
                    clearObservationEffects(player);
                    beginHuntPhase(player, arena, level);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        timerTasks.put(uuid, obsTask);
    }

    private void beginHuntPhase(Player player, Arena arena, int level) {
        if (!isPlaying(player)) return;

        player.sendTitle(
                plugin.getConfigManager().msg("titles.level-title",   false, "level", String.valueOf(level)),
                plugin.getConfigManager().msg("titles.level-subtitle", false),
                5, 40, 10);

        player.sendMessage(plugin.getConfigManager().msg("game.start", "level", String.valueOf(level)));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        startHuntTimer(player, 30 + (level * 2));
    }

    // =========================================================
    // GUESS / VICTORY / DEFEAT
    // =========================================================

    public void processGuess(Player player, Entity target) {
        if (!isPlaying(player)) return;

        if (inObservation.contains(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().msg("observation.no-attack"));
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
        inObservation.remove(player.getUniqueId());

        player.sendMessage(plugin.getConfigManager().msg("game.win"));
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
                Arena arena = playerArenas.get(player.getUniqueId());
                if (arena != null) startRound(player, arena, false);
            }
        }, 40L);
    }

    private void handleDefeat(Player player, Entity innocentEntity) {
        UUID uuid = player.getUniqueId();
        inObservation.remove(uuid);

        int lives = playerLives.getOrDefault(uuid, 1) - 1;
        playerLives.put(uuid, lives);

        if (innocentEntity != null) {
            innocentEntity.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        if (lives <= 0) {
            player.sendMessage(plugin.getConfigManager().msg("game.game-over"));
            player.sendTitle(
                    plugin.getConfigManager().msg("titles.game-over-title", false),
                    "", 10, 60, 20);
            leaveGame(player);
        } else {
            player.sendMessage(plugin.getConfigManager().msg("game.life-lost",
                    "lives", String.valueOf(lives)));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

            if (innocentEntity == null) {
                stopTimer(player);
                plugin.getNpcManager().cleanup(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isPlaying(player)) {
                        Arena arena = playerArenas.get(player.getUniqueId());
                        if (arena != null) startRound(player, arena, false);
                    }
                }, 20L);
            }
        }
    }

    // =========================================================
    // RADIUS CHECK
    // =========================================================

    public void checkRadiusBreach(Player player) {
        if (!isPlaying(player)) return;

        Arena arena = playerArenas.get(player.getUniqueId());
        if (arena == null || !arena.hasRadius() || arena.getPlayerSpawn() == null) return;

        Location center    = arena.getPlayerSpawn();
        Location playerLoc = player.getLocation();
        if (!center.getWorld().equals(playerLoc.getWorld())) return;

        double distance = center.distance(playerLoc);
        double radius   = arena.getRadius();
        if (distance <= radius) return;

        // How far past the boundary the player is (0.0 = just at edge, 1.0 = one full radius beyond)
        double overflow = (distance - radius) / radius;

        // Direction pointing back toward center
        org.bukkit.util.Vector toCenter = center.toVector()
                .subtract(playerLoc.toVector())
                .normalize();

        // Push strength scales with how far they went past the edge:
        //   - Just past the line: gentle nudge (0.2)
        //   - Far past the line: stronger push (capped at 0.8)
        double strength = Math.min(0.2 + overflow * 1.2, 0.8);
        org.bukkit.util.Vector push = toCenter.multiply(strength);

        // Keep Y neutral — don't fling the player into the air
        push.setY(0.05);
        player.setVelocity(push);

        // Warning message — throttled: only show once every 2 seconds
        // (PlayerMoveEvent fires every block, so we avoid chat spam)
        UUID uuid = player.getUniqueId();
        Long lastWarn = boundaryWarnTime.get(uuid);
        long now = System.currentTimeMillis();
        if (lastWarn == null || now - lastWarn > 2000) {
            boundaryWarnTime.put(uuid, now);
            player.sendMessage(plugin.getConfigManager().msg("game.out-of-bounds"));
            player.playSound(playerLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
        }
    }

    // =========================================================
    // HUNT TIMER
    // =========================================================

    private void startHuntTimer(Player player, int seconds) {
        stopTimer(player);

        String fmt = plugin.getConfigManager().msg("hunt.bossbar", false);
        BossBar bar = Bukkit.createBossBar(
                ColorUtil.colorize(fmt.replace("{time}", String.valueOf(seconds))),
                BarColor.YELLOW, BarStyle.SOLID);
        bar.addPlayer(player);
        activeBars.put(player.getUniqueId(), bar);

        BukkitTask task = new BukkitRunnable() {
            double timeLeft = seconds;
            final double total = seconds;

            @Override
            public void run() {
                if (!isPlaying(player)) { stopTimer(player); this.cancel(); return; }

                timeLeft--;
                BossBar b = activeBars.get(player.getUniqueId());
                if (b != null) {
                    b.setTitle(ColorUtil.colorize(fmt.replace("{time}", String.valueOf((int) timeLeft))));
                    b.setProgress(Math.max(0.0, Math.min(1.0, timeLeft / total)));
                    if (timeLeft <= 5) {
                        b.setColor(BarColor.RED);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                }
                if (timeLeft <= 0) { this.cancel(); handleDefeat(player, null); }
            }
        }.runTaskTimer(plugin, 20L, 20L);

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
        clearObservationEffects(player);

        UUID uuid = player.getUniqueId();
        activePlayers.remove(uuid);
        playerLevels.remove(uuid);
        playerLives.remove(uuid);
        playerArenas.remove(uuid);
        inObservation.remove(uuid);
        boundaryWarnTime.remove(uuid);

        player.sendMessage(plugin.getConfigManager().msg("game.left"));

        // Teleport to global lobby if set
        if (plugin.getArenaManager().hasGlobalLobby()) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    player.teleport(plugin.getArenaManager().getGlobalLobby()), 2L);
        }

        applyLobbyGameMode(player);
        return true;
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void clearObservationEffects(Player player) {
        if (SLOWNESS != null) player.removePotionEffect(SLOWNESS);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    private void applyPlayingGameMode(Player player) {
        if (!plugin.getConfig().getBoolean("settings.gamemode-management", true)) return;
        String name = plugin.getConfig().getString("settings.gamemode-playing", "ADVENTURE");
        try { player.setGameMode(GameMode.valueOf(name)); }
        catch (IllegalArgumentException e) { player.setGameMode(GameMode.ADVENTURE); }
    }

    private void applyLobbyGameMode(Player player) {
        if (!plugin.getConfig().getBoolean("settings.gamemode-management", true)) return;
        String name = plugin.getConfig().getString("settings.gamemode-lobby", "SURVIVAL");
        try { player.setGameMode(GameMode.valueOf(name)); }
        catch (IllegalArgumentException e) { player.setGameMode(GameMode.SURVIVAL); }
    }

    public boolean isPlaying(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public boolean isInObservationPhase(Player player) {
        return inObservation.contains(player.getUniqueId());
    }

    public void clearAllPlayers() {
        for (UUID uuid : new HashSet<>(activePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) leaveGame(p);
        }
    }
}
