package com.findgame.managers;

import com.findgame.FindGamePlugin;
import com.findgame.data.ImpostorType;
import com.findgame.managers.ArenaManager.Arena;
import com.findgame.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class NPCManager {

    private final FindGamePlugin plugin;

    private final Map<UUID, List<Entity>>  activeNPCs    = new HashMap<>();
    private final Map<UUID, Integer>       impostors     = new HashMap<>();
    private final Map<UUID, BukkitTask>    behaviorTasks = new HashMap<>();
    private final Map<UUID, ImpostorType>  impostorTypes = new HashMap<>();

    // ── Appearance pools ──────────────────────────────────────────────────

    private static final Villager.Profession[] PROFESSIONS = {
            Villager.Profession.ARMORER,      Villager.Profession.BUTCHER,
            Villager.Profession.CARTOGRAPHER, Villager.Profession.CLERIC,
            Villager.Profession.FARMER,       Villager.Profession.FISHERMAN,
            Villager.Profession.FLETCHER,     Villager.Profession.LEATHERWORKER,
            Villager.Profession.LIBRARIAN,    Villager.Profession.MASON,
            Villager.Profession.SHEPHERD,     Villager.Profession.TOOLSMITH,
            Villager.Profession.WEAPONSMITH
            // NITWIT excluded: has no trade clothes, too easy to spot
    };

    /** Biome types available across all MC versions we support (1.16+). */
    private static final String[] SKIN_TYPES = {
            "plains", "desert", "jungle", "swamp", "snow", "taiga", "savanna"
    };

    /** Villager levels 1-5 change the badge/belt on their outfit. */
    private static final int[] LEVELS = { 1, 2, 3, 4, 5 };

    // ─────────────────────────────────────────────────────────────────────

    public NPCManager(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    // VISIBILITY
    // =========================================================

    public void hideAllExistingNPCsFrom(Player newPlayer) {
        for (UUID ownerId : activeNPCs.keySet()) {
            if (ownerId.equals(newPlayer.getUniqueId())) continue;
            List<Entity> entities = activeNPCs.get(ownerId);
            if (entities == null) continue;
            for (Entity npc : entities) {
                if (npc != null && !npc.isDead()) newPlayer.hideEntity(plugin, npc);
            }
        }
    }

    // =========================================================
    // SPAWN
    // =========================================================

    public void spawnNPCs(Arena arena, Player player, int count) {
        cleanup(player);

        List<Location> allSpawns = arena.getNpcSpawns();
        if (allSpawns.isEmpty()) return;

        List<Location> shuffled = new ArrayList<>(allSpawns);
        Collections.shuffle(shuffled);

        int actual = Math.min(count, shuffled.size());
        List<Entity> spawned = new ArrayList<>();

        // Prepare name pool for this round (no repeats if possible)
        List<String> namePool = buildNamePool(actual);

        for (int i = 0; i < actual; i++) {
            Location loc = shuffled.get(i);
            if (!loc.getChunk().isLoaded()) loc.getChunk().load();

            Villager npc = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
            npc.setCollidable(false);
            npc.setSilent(true);
            npc.setAgeLock(true);
            npc.setGlowing(true);
            npc.setAI(true);

            if (npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.23);
            }

            // ✅ Varied appearance: biome skin + profession + level
            applyRandomAppearance(npc);

            // ✅ Random name from pool
            if (i < namePool.size()) {
                npc.setCustomName(namePool.get(i));
                npc.setCustomNameVisible(true);
            }

            // Hide from all other players
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(player.getUniqueId())) {
                    other.hideEntity(plugin, npc);
                }
            }

            spawned.add(npc);
        }

        if (spawned.isEmpty()) return;

        // Pick impostor
        Random random = new Random();
        Entity impostorEntity = spawned.get(random.nextInt(spawned.size()));
        ImpostorType type = ImpostorType.values()[random.nextInt(ImpostorType.values().length)];

        activeNPCs.put(player.getUniqueId(), spawned);
        impostors.put(player.getUniqueId(), impostorEntity.getEntityId());
        impostorTypes.put(player.getUniqueId(), type);

        // Send hint
        String typeName = plugin.getConfigManager().getMessages()
                .getString("impostor.type-names." + type.name().toLowerCase(),
                        type.getDisplayName());
        player.sendMessage(plugin.getConfigManager().msg("impostor.hint", "type", typeName));

        startImpostorAI(player, (Villager) impostorEntity, type);
    }

    // =========================================================
    // ✅ APPEARANCE: biome skin + profession + level
    // =========================================================

    private void applyRandomAppearance(Villager npc) {
        Random rand = new Random();

        // 1. Biome skin (safe fallback for all versions)
        String skinKey = SKIN_TYPES[rand.nextInt(SKIN_TYPES.length)];
        try {
            // 1.19.4+ Paper API via Registry
            Villager.Type type = Registry.VILLAGER_TYPE.get(
                    NamespacedKey.minecraft(skinKey));
            if (type != null) {
                npc.setVillagerType(type);
            }
        } catch (Throwable e) {
            // 1.16–1.19.3 fallback: direct enum lookup
            try {
                npc.setVillagerType(Villager.Type.valueOf(skinKey.toUpperCase()));
            } catch (Exception ignored) { }
        }

        // 2. Random profession (gives different outfit/badge color)
        npc.setProfession(PROFESSIONS[rand.nextInt(PROFESSIONS.length)]);

        // 3. Random level 1-5 (changes the belt/badge detail on the outfit)
        npc.setVillagerLevel(LEVELS[rand.nextInt(LEVELS.length)]);
    }

    // =========================================================
    // ✅ NAMES: shuffle list, no repeats within a round
    // =========================================================

    private List<String> buildNamePool(int needed) {
        List<String> configured = plugin.getConfigManager().getMessages()
                .getStringList("npc-names");

        // Fallback names if messages.yml list is empty
        if (configured.isEmpty()) {
            configured = Arrays.asList(
                    "§fAlice", "§fBob", "§fCarlos", "§fDiana", "§fEthan",
                    "§fFiona", "§fGeorge", "§fHanna", "§fIvan", "§fJulia",
                    "§fKevin", "§fLena", "§fMarco", "§fNora", "§fOscar"
            );
        }

        List<String> pool = new ArrayList<>(configured);
        Collections.shuffle(pool);

        // If we need more names than available, allow repeats
        while (pool.size() < needed) {
            pool.addAll(configured);
        }
        return pool.subList(0, needed);
    }

    // =========================================================
    // AI DISPATCH
    // =========================================================

    private void startImpostorAI(Player player, Villager impostor, ImpostorType type) {

        // SHY requires AI=false so manual movement isn't overridden by pathfinding
        if (type == ImpostorType.SHY) {
            impostor.setAI(false);
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (impostor.isDead() || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                Random rand = new Random();
                int chance = rand.nextInt(100);

                switch (type) {

                    // ── Original types ──────────────────────────────────

                    case NERVOUS:
                        // Sweats often, jumps sometimes, rarely kills
                        if (chance < 40)      playSweat(impostor);
                        else if (chance < 65) playJump(impostor);
                        else if (chance > 97) killNearestInnocent(player, impostor, 4.0);
                        break;

                    case KILLER:
                        // Stares at player, kills nearby innocents frequently
                        if (chance < 15)      playSweat(impostor);
                        else if (chance < 55) lookAtPlayer(player, impostor);
                        else if (chance > 65) killNearestInnocent(player, impostor, 8.0);
                        break;

                    case SHY:
                        // ✅ FIXED: AI is off, so we teleport manually each tick
                        shyStep(player, impostor);
                        break;

                    case SPEEDRUNNER:
                        // Frequent speed bursts, rare kills
                        if (chance < 45)      playSprint(impostor);
                        else if (chance > 92) killNearestInnocent(player, impostor, 3.0);
                        break;

                    // ── New types ────────────────────────────────────────

                    case DRUNK:
                        // Random erratic movement in a random direction
                        drunkStep(impostor);
                        if (chance > 85) playSweat(impostor);
                        break;

                    case PARANOID:
                        // Always snaps head toward the player, and sweats a lot
                        lookAtPlayer(player, impostor);
                        if (chance < 50) playSweat(impostor);
                        break;

                    case ASSASSIN:
                        // Kills very frequently, moves toward nearest innocent
                        if (chance < 60)      killNearestInnocent(player, impostor, 10.0);
                        else if (chance < 80) lookAtPlayer(player, impostor);
                        break;

                    case FREEZER:
                        // Stands completely still — only occasionally twitches
                        if (chance > 95) {
                            // Rare subtle twitch: tiny position jitter
                            freezerTwitch(impostor);
                        }
                        break;
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        behaviorTasks.put(player.getUniqueId(), task);
    }

    // =========================================================
    // EFFECTS & MOVEMENT
    // =========================================================

    private void playSweat(Villager npc) {
        if (ParticleUtil.SWEAT != null) {
            npc.getWorld().spawnParticle(
                    ParticleUtil.SWEAT,
                    npc.getLocation().add(0, 2.2, 0),
                    8, 0.3, 0.1, 0.3, 0.01);
        }
    }

    private void playJump(Villager npc) {
        if (npc.isOnGround()) {
            npc.setVelocity(new Vector(0, 0.5, 0));
        }
    }

    private void lookAtPlayer(Player player, Villager npc) {
        Vector dir = player.getLocation().toVector()
                .subtract(npc.getLocation().toVector()).normalize();
        Location look = npc.getLocation().clone();
        look.setDirection(dir);
        npc.teleport(look);
    }

    /**
     * ✅ FIXED SHY: AI is disabled, so we manually teleport the NPC
     * one small step away from the player every tick.
     * This guarantees it actually moves — pathfinding can't override it.
     */
    private void shyStep(Player player, Villager npc) {
        Location npcLoc    = npc.getLocation();
        Location playerLoc = player.getLocation();

        double dist = npcLoc.distance(playerLoc);

        if (dist < 8.0) {
            // Direction away from player
            Vector away = npcLoc.toVector()
                    .subtract(playerLoc.toVector())
                    .normalize()
                    .multiply(0.35); // step size per tick

            Location next = npcLoc.clone().add(away.getX(), 0, away.getZ());
            next.setY(npcLoc.getY()); // keep on ground

            // Face away from player
            next.setDirection(away);
            npc.teleport(next);

            // Sweat when very close (panicking)
            if (dist < 4.0) playSweat(npc);
        } else {
            // Player is far — stand still and face them suspiciously
            lookAtPlayer(player, npc);
        }
    }

    private void playSprint(Villager npc) {
        if (npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) == null) return;
        npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.6);
        if (ParticleUtil.CLOUD != null) {
            npc.getWorld().spawnParticle(
                    ParticleUtil.CLOUD,
                    npc.getLocation(), 5, 0.2, 0.1, 0.2, 0.05);
        }
        new BukkitRunnable() {
            @Override public void run() {
                if (!npc.isDead() && npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                    npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.23);
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    /**
     * DRUNK: teleports the NPC a small random step in a random XZ direction.
     * AI stays on so it also pathfinds, making movement look chaotic.
     */
    private void drunkStep(Villager npc) {
        Random rand = new Random();
        // Random angle, short step
        double angle  = rand.nextDouble() * 2 * Math.PI;
        double step   = 0.3 + rand.nextDouble() * 0.5;
        double dx     = Math.cos(angle) * step;
        double dz     = Math.sin(angle) * step;

        Location next = npc.getLocation().clone().add(dx, 0, dz);
        next.setDirection(new Vector(dx, 0, dz));
        npc.teleport(next);

        // Randomly change speed to stagger movement
        if (npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            double speed = 0.1 + rand.nextDouble() * 0.4;
            npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
        }
    }

    /**
     * FREEZER: rare subtle twitch — tiny position jitter so it's not 100% a statue.
     */
    private void freezerTwitch(Villager npc) {
        Random rand = new Random();
        double jitter = 0.05;
        Location twitch = npc.getLocation().clone()
                .add((rand.nextDouble() - 0.5) * jitter,
                        0,
                        (rand.nextDouble() - 0.5) * jitter);
        npc.teleport(twitch);
    }

    private void killNearestInnocent(Player player, Villager impostor, double range) {
        List<Entity> npcs = activeNPCs.get(player.getUniqueId());
        if (npcs == null || npcs.size() <= 2) return;

        Entity victim = null;
        double min = range;
        for (Entity e : npcs) {
            if (e.getEntityId() == impostor.getEntityId() || e.isDead()) continue;
            double d = e.getLocation().distance(impostor.getLocation());
            if (d < min) { min = d; victim = e; }
        }

        if (victim != null) {
            if (ParticleUtil.DEATH_POOF != null) {
                victim.getWorld().spawnParticle(
                        ParticleUtil.DEATH_POOF,
                        victim.getLocation().add(0, 1, 0),
                        15, 0.2, 0.2, 0.2, 0.05);
            }
            victim.getWorld().playSound(
                    victim.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            victim.remove();
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    public void cleanup(Player player) {
        UUID id = player.getUniqueId();

        if (behaviorTasks.containsKey(id)) {
            behaviorTasks.get(id).cancel();
            behaviorTasks.remove(id);
        }

        if (activeNPCs.containsKey(id)) {
            for (Entity e : activeNPCs.get(id)) {
                if (e != null && !e.isDead()) {
                    // Re-enable AI before removing (good practice)
                    if (e instanceof Villager) ((Villager) e).setAI(true);
                    e.remove();
                }
            }
            activeNPCs.remove(id);
        }

        impostors.remove(id);
        impostorTypes.remove(id);
    }

    public void cleanupAll() {
        for (BukkitTask t : behaviorTasks.values()) t.cancel();
        behaviorTasks.clear();

        for (List<Entity> list : activeNPCs.values()) {
            for (Entity e : list) {
                if (e != null && !e.isDead()) {
                    if (e instanceof Villager) ((Villager) e).setAI(true);
                    e.remove();
                }
            }
        }
        activeNPCs.clear();
        impostors.clear();
        impostorTypes.clear();
    }

    // =========================================================
    // QUERIES
    // =========================================================

    public boolean isImpostor(Player player, int entityId) {
        return impostors.getOrDefault(player.getUniqueId(), -1) == entityId;
    }

    public boolean isGameNPC(Player player, Entity entity) {
        List<Entity> list = activeNPCs.get(player.getUniqueId());
        return list != null && list.contains(entity);
    }
}
