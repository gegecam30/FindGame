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
    
    // Mapas de gestión
    private final Map<UUID, List<Entity>> activeNPCs = new HashMap<>();
    private final Map<UUID, Integer> impostors = new HashMap<>();
    private final Map<UUID, BukkitTask> behaviorTasks = new HashMap<>();
    private final Map<UUID, ImpostorType> impostorTypes = new HashMap<>();

    // Profesiones seguras (sin menú de trabajo molesto)
    private static final Villager.Profession[] SAFE_PROFESSIONS = {
            Villager.Profession.ARMORER, Villager.Profession.BUTCHER, Villager.Profession.CARTOGRAPHER,
            Villager.Profession.CLERIC, Villager.Profession.FARMER, Villager.Profession.FISHERMAN,
            Villager.Profession.FLETCHER, Villager.Profession.LEATHERWORKER, Villager.Profession.LIBRARIAN,
            Villager.Profession.MASON, Villager.Profession.NITWIT, Villager.Profession.SHEPHERD,
            Villager.Profession.TOOLSMITH, Villager.Profession.WEAPONSMITH
    };

    public NPCManager(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Oculta los NPCs de otras arenas al jugador que entra.
     */
    public void hideAllExistingNPCsFrom(Player newPlayer) {
        for (UUID ownerId : activeNPCs.keySet()) {
            if (ownerId.equals(newPlayer.getUniqueId())) continue;
            List<Entity> entities = activeNPCs.get(ownerId);
            if (entities != null) {
                for (Entity npc : entities) {
                    if (npc != null && !npc.isDead()) {
                        newPlayer.hideEntity(plugin, npc);
                    }
                }
            }
        }
    }

    /**
     * Spawnea los NPCs para una ronda.
     */
    public void spawnNPCs(Arena arena, Player player, int count) {
        cleanup(player); // Limpiar ronda anterior

        List<Location> allSpawns = arena.getNpcSpawns();
        if (allSpawns.isEmpty()) return;

        List<Location> shuffledSpawns = new ArrayList<>(allSpawns);
        Collections.shuffle(shuffledSpawns);

        int actualCount = Math.min(count, shuffledSpawns.size());
        List<Entity> spawnedEntities = new ArrayList<>();
        
        boolean skinsEnabled = plugin.getConfig().getBoolean("visuals.villager-skins-enabled", true);
        List<String> allowedSkins = plugin.getConfig().getStringList("visuals.allowed-skins");

        for (int i = 0; i < actualCount; i++) {
            Location loc = shuffledSpawns.get(i);
            if (!loc.getChunk().isLoaded()) loc.getChunk().load();

            Villager npc = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
            
            // --- Configuración Física y Visual ---
            npc.setAI(true);
            npc.setCollidable(false); 
            npc.setSilent(true);
            npc.setAgeLock(true);
            npc.setProfession(getRandomProfession());
            npc.setGlowing(true); // Glow siempre activo para visibilidad
            
            // Velocidad base
            if (npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.23); 
            }

            // Sistema de Instancia Privada (Packet-based hiding)
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(player.getUniqueId())) {
                    other.hideEntity(plugin, npc);
                }
            }
            
            // Aplicar Skin (Compatible 1.16 - 1.21)
            if (skinsEnabled && !allowedSkins.isEmpty()) {
                applyRandomSkin(npc, allowedSkins);
            }
            
            spawnedEntities.add(npc);
        }

        if (!spawnedEntities.isEmpty()) {
            // Seleccionar Impostor
            Random random = new Random();
            Entity impostor = spawnedEntities.get(random.nextInt(spawnedEntities.size()));
            
            activeNPCs.put(player.getUniqueId(), spawnedEntities);
            impostors.put(player.getUniqueId(), impostor.getEntityId());
            
            // Seleccionar Tipo de IA
            ImpostorType type = ImpostorType.values()[random.nextInt(ImpostorType.values().length)];
            impostorTypes.put(player.getUniqueId(), type);
            
            // Mensaje de Pista Configurable
            String typeName = plugin.getConfig().getString("messages.impostor-names." + type.name().toLowerCase(), type.name());
            String hintMsg = plugin.getConfig().getString("messages.impostor-hint", "&7Pista: Busca al &b{type}");
            player.sendMessage(ColorUtil.colorize(hintMsg.replace("{type}", typeName)));
            
            // Iniciar Comportamiento
            startImpostorAI(player, (Villager) impostor, type);
        }
    }
    
    /**
     * Aplica skins de forma segura detectando la versión del servidor.
     */
    private void applyRandomSkin(Villager npc, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) return;
        
        String skinName = allowed.get(new Random().nextInt(allowed.size())).toUpperCase();
        
        try {
            // INTENTO 1: Método Moderno (1.19+ Registry)
            // Esto arregla el crash en 1.20.1+
            Villager.Type type = Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(skinName.toLowerCase()));
            if (type != null) npc.setVillagerType(type);
            
        } catch (Throwable t) {
            // INTENTO 2: Método Legacy (1.16 - 1.18)
            // Si el Registry no existe (NoClassDefFoundError), usamos el Enum clásico
            try {
                npc.setVillagerType(Villager.Type.valueOf(skinName));
            } catch (Exception ignored) {
                // Si la skin no existe en esta versión, ignoramos.
            }
        }
    }

    /**
     * IA Agresiva del Impostor
     */
private void startImpostorAI(Player player, Villager impostor, ImpostorType type) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (impostor == null || impostor.isDead() || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                Random rand = new Random();
                int chance = rand.nextInt(100);

                switch (type) {
                    case NERVOUS:
                        if (chance < 30) playSweat(impostor);
                        else if (chance > 80) playJump(impostor);
                        else if (chance > 98) killNearestInnocent(player, impostor, 4.0);
                        break;
                        
                    case KILLER:
                        if (chance < 10) playSweat(impostor);
                        else if (chance > 70) killNearestInnocent(player, impostor, 8.0);
                        else if (chance > 50 && chance < 70) lookAtPlayer(player, impostor);
                        break;
                        
                    case SHY:
                        double dist = player.getLocation().distance(impostor.getLocation());
                        if (dist < 7.0) {
                            runAwayFrom(player, impostor);
                        } else if (chance < 20) {
                            playSweat(impostor);
                        }
                        break;

                    // --- NUEVO: SPEEDRUNNER ---
                    case SPEEDRUNNER:
                        // 40% de probabilidad de pegar un sprint (correr muy rápido)
                        if (chance < 40) playSprint(impostor);
                        // También puede matar si tiene a alguien muy cerca (10% prob)
                        else if (chance > 90) killNearestInnocent(player, impostor, 3.0);
                        break;
                }
            }
        }.runTaskTimer(plugin, 40L, 20L); 
        
        behaviorTasks.put(player.getUniqueId(), task);
    }
    
    // --- EFECTOS VISUALES Y FÍSICOS ---
    
    private void playSweat(Villager npc) {
        if (ParticleUtil.SWEAT != null) {
            npc.getWorld().spawnParticle(ParticleUtil.SWEAT, npc.getLocation().add(0, 2.2, 0), 8, 0.3, 0.1, 0.3, 0.01);
        }
    }
    
    private void playJump(Villager npc) {
        if (npc.isOnGround()) {
            npc.setVelocity(new Vector(0, 0.5, 0)); // Salto visible
        }
    }
    
    private void lookAtPlayer(Player player, Villager npc) {
        Location npcLoc = npc.getLocation();
        Vector dir = player.getLocation().toVector().subtract(npcLoc.toVector()).normalize();
        Location lookLoc = npcLoc.clone();
        lookLoc.setDirection(dir);
        npc.teleport(lookLoc);
    }
    
    private void runAwayFrom(Player player, Villager npc) {
        Vector dir = npc.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
        npc.setVelocity(dir.multiply(0.7).setY(0.3)); // Empuje con salto
        
        Location lookLoc = npc.getLocation().clone();
        lookLoc.setDirection(dir);
        npc.teleport(lookLoc);
    }

    private void killNearestInnocent(Player player, Villager impostor, double range) {
        List<Entity> npcs = activeNPCs.get(player.getUniqueId());
        if (npcs == null || npcs.size() <= 2) return; // No vaciar la arena

        Entity victim = null;
        double minDistance = range; 

        for (Entity e : npcs) {
            if (e.getEntityId() == impostor.getEntityId() || e.isDead()) continue;
            
            double dist = e.getLocation().distance(impostor.getLocation());
            if (dist < minDistance) {
                minDistance = dist;
                victim = e;
            }
        }

        if (victim != null) {
            // Efectos de asesinato
            if (ParticleUtil.DEATH_POOF != null) {
                victim.getWorld().spawnParticle(ParticleUtil.DEATH_POOF, victim.getLocation().add(0,1,0), 15, 0.2, 0.2, 0.2, 0.05);
            }
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            victim.remove(); 
        }
    }
    private void playSprint(Villager npc) {
        if (npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            // Aumentar velocidad a 0.6 (Lo normal es 0.23, así que es casi x3)
            npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.6);
            
            // Efecto visual: Partículas de nube en los pies
            if (ParticleUtil.CLOUD != null) {
                npc.getWorld().spawnParticle(ParticleUtil.CLOUD, npc.getLocation(), 5, 0.2, 0.1, 0.2, 0.05);
            }
            
            // Restaurar velocidad normal después de 2 segundos (40 ticks)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!npc.isDead()) {
                        Objects.requireNonNull(npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(0.23);
                    }
                }
            }.runTaskLater(plugin, 40L);
        }
    }

    // --- LIMPIEZA Y UTILIDADES ---

    public void cleanup(Player player) {
        UUID playerId = player.getUniqueId();
        if (behaviorTasks.containsKey(playerId)) {
            behaviorTasks.get(playerId).cancel();
            behaviorTasks.remove(playerId);
        }
        if (activeNPCs.containsKey(playerId)) {
            for (Entity entity : activeNPCs.get(playerId)) {
                if (entity != null && !entity.isDead()) entity.remove();
            }
            activeNPCs.remove(playerId);
        }
        impostors.remove(playerId);
        impostorTypes.remove(playerId);
    }
    
    public void cleanupAll() {
        for (BukkitTask task : behaviorTasks.values()) task.cancel();
        behaviorTasks.clear();
        for (List<Entity> entities : activeNPCs.values()) {
            for (Entity entity : entities) if (entity != null && !entity.isDead()) entity.remove();
        }
        activeNPCs.clear();
        impostors.clear();
        impostorTypes.clear();
    }

    public boolean isImpostor(Player player, int entityId) {
        return impostors.getOrDefault(player.getUniqueId(), -1) == entityId;
    }
    
    public boolean isGameNPC(Player player, Entity entity) {
        List<Entity> list = activeNPCs.get(player.getUniqueId());
        return list != null && list.contains(entity);
    }
    
    private Villager.Profession getRandomProfession() {
        return SAFE_PROFESSIONS[new Random().nextInt(SAFE_PROFESSIONS.length)];
    }
}