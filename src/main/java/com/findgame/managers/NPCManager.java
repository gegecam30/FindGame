package com.findgame.managers;

import com.findgame.FindGamePlugin;
import com.findgame.data.ImpostorType;
import com.findgame.managers.ArenaManager.Arena;
import com.findgame.utils.ColorUtil;
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

    private final Map<UUID, List<Entity>>   activeNPCs     = new HashMap<>();
    private final Map<UUID, Integer>        impostors      = new HashMap<>();
    private final Map<UUID, BukkitTask>     behaviorTasks  = new HashMap<>();
    private final Map<UUID, ImpostorType>   impostorTypes  = new HashMap<>();

    private static final Villager.Profession[] SAFE_PROFESSIONS = {
            Villager.Profession.ARMORER,      Villager.Profession.BUTCHER,
            Villager.Profession.CARTOGRAPHER, Villager.Profession.CLERIC,
            Villager.Profession.FARMER,       Villager.Profession.FISHERMAN,
            Villager.Profession.FLETCHER,     Villager.Profession.LEATHERWORKER,
            Villager.Profession.LIBRARIAN,    Villager.Profession.MASON,
            Villager.Profession.NITWIT,       Villager.Profession.SHEPHERD,
            Villager.Profession.TOOLSMITH,    Villager.Profession.WEAPONSMITH
    };

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

        boolean skinsEnabled = plugin.getConfig().getBoolean("visuals.villager-skins-enabled", true);
        List<String> allowedSkins = plugin.getConfig().getStringList("visuals.allowed-skins");

        for (int i = 0; i < actual; i++) {
            Location loc = shuffled.get(i);
            if (!loc.getChunk().isLoaded()) loc.getChunk().load();

            Villager npc = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
            npc.setAI(true);
            npc.setCollidable(false);
            npc.setSilent(true);
            npc.setAgeLock(true);
            npc.setProfession(randomProfession());
            npc.setGlowing(true);

            if (npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.23);
            }

            // Hide from all other players
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(player.getUniqueId())) {
                    other.hideEntity(plugin, npc);
                }
            }

            if (skinsEnabled && !allowedSkins.isEmpty()) applyRandomSkin(npc, allowedSkins);
            spawned.add(npc);
        }

        if (spawned.isEmpty()) return;

        Random random = new Random();
        Entity impostor = spawned.get(random.nextInt(spawned.size()));
        ImpostorType type = ImpostorType.values()[random.nextInt(ImpostorType.values().length)];

        activeNPCs.put(player.getUniqueId(), spawned);
        impostors.put(player.getUniqueId(), impostor.getEntityId());
        impostorTypes.put(player.getUniqueId(), type);

        // Send impostor hint using new message path
        String typeName = plugin.getConfigManager().getMessages()
                .getString("impostor.type-names." + type.name().toLowerCase(), type.name());
        player.sendMessage(plugin.getConfigManager().msg("impostor.hint", "type", typeName));

        startImpostorAI(player, (Villager) impostor, type);
    }

    // =========================================================
    // SKIN
    // =========================================================

    private void applyRandomSkin(Villager npc, List<String> allowed) {
        String skinName = allowed.get(new Random().nextInt(allowed.size())).toUpperCase();
        try {
            Villager.Type type = Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(skinName.toLowerCase()));
            if (type != null) npc.setVillagerType(type);
        } catch (Throwable t) {
            try { npc.setVillagerType(Villager.Type.valueOf(skinName)); }
            catch (Exception ignored) {}
        }
    }

    // =========================================================
    // AI
    // =========================================================

    private void startImpostorAI(Player player, Villager impostor, ImpostorType type) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (impostor == null || impostor.isDead() || !player.isOnline()) {
                    this.cancel(); return;
                }
                Random rand = new Random();
                int chance = rand.nextInt(100);

                switch (type) {
                    case NERVOUS:
                        if (chance < 30)       playSweat(impostor);
                        else if (chance > 80)  playJump(impostor);
                        else if (chance > 98)  killNearestInnocent(player, impostor, 4.0);
                        break;
                    case KILLER:
                        if (chance < 10)            playSweat(impostor);
                        else if (chance > 70)       killNearestInnocent(player, impostor, 8.0);
                        else if (chance > 50)       lookAtPlayer(player, impostor);
                        break;
                    case SHY:
                        double dist = player.getLocation().distance(impostor.getLocation());
                        if (dist < 7.0)        runAwayFrom(player, impostor);
                        else if (chance < 20)  playSweat(impostor);
                        break;
                    case SPEEDRUNNER:
                        if (chance < 40)       playSprint(impostor);
                        else if (chance > 90)  killNearestInnocent(player, impostor, 3.0);
                        break;
                }
            }
        }.runTaskTimer(plugin, 40L, 20L);

        behaviorTasks.put(player.getUniqueId(), task);
    }

    // =========================================================
    // EFFECTS
    // =========================================================

    private void playSweat(Villager npc) {
        if (ParticleUtil.SWEAT != null) {
            npc.getWorld().spawnParticle(ParticleUtil.SWEAT,
                    npc.getLocation().add(0, 2.2, 0), 8, 0.3, 0.1, 0.3, 0.01);
        }
    }

    private void playJump(Villager npc) {
        if (npc.isOnGround()) npc.setVelocity(new Vector(0, 0.5, 0));
    }

    private void lookAtPlayer(Player player, Villager npc) {
        Vector dir = player.getLocation().toVector()
                .subtract(npc.getLocation().toVector()).normalize();
        Location look = npc.getLocation().clone();
        look.setDirection(dir);
        npc.teleport(look);
    }

    private void runAwayFrom(Player player, Villager npc) {
        Vector dir = npc.getLocation().toVector()
                .subtract(player.getLocation().toVector()).normalize();
        npc.setVelocity(dir.multiply(0.7).setY(0.3));
        Location look = npc.getLocation().clone();
        look.setDirection(dir);
        npc.teleport(look);
    }

    private void playSprint(Villager npc) {
        if (npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) == null) return;
        npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.6);
        if (ParticleUtil.CLOUD != null) {
            npc.getWorld().spawnParticle(ParticleUtil.CLOUD,
                    npc.getLocation(), 5, 0.2, 0.1, 0.2, 0.05);
        }
        new BukkitRunnable() {
            @Override public void run() {
                if (!npc.isDead()) {
                    Objects.requireNonNull(npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED))
                            .setBaseValue(0.23);
                }
            }
        }.runTaskLater(plugin, 40L);
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
                victim.getWorld().spawnParticle(ParticleUtil.DEATH_POOF,
                        victim.getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.05);
            }
            victim.getWorld().playSound(victim.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            victim.remove();
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    public void cleanup(Player player) {
        UUID id = player.getUniqueId();
        if (behaviorTasks.containsKey(id)) { behaviorTasks.get(id).cancel(); behaviorTasks.remove(id); }
        if (activeNPCs.containsKey(id)) {
            for (Entity e : activeNPCs.get(id)) if (e != null && !e.isDead()) e.remove();
            activeNPCs.remove(id);
        }
        impostors.remove(id);
        impostorTypes.remove(id);
    }

    public void cleanupAll() {
        for (BukkitTask t : behaviorTasks.values()) t.cancel();
        behaviorTasks.clear();
        for (List<Entity> list : activeNPCs.values())
            for (Entity e : list) if (e != null && !e.isDead()) e.remove();
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

    private Villager.Profession randomProfession() {
        return SAFE_PROFESSIONS[new Random().nextInt(SAFE_PROFESSIONS.length)];
    }
}
