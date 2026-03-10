package com.findgame.npc;

import com.findgame.FindGamePlugin;
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
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class VillagerNPC implements INPC {

    private final FindGamePlugin plugin;
    private Villager entity;
    private final Player owner;

    private static final Villager.Profession[] SAFE_PROFESSIONS = {
            Villager.Profession.ARMORER, Villager.Profession.BUTCHER, Villager.Profession.CARTOGRAPHER,
            Villager.Profession.CLERIC, Villager.Profession.FARMER, Villager.Profession.FISHERMAN,
            Villager.Profession.FLETCHER, Villager.Profession.LEATHERWORKER, Villager.Profession.LIBRARIAN,
            Villager.Profession.MASON, Villager.Profession.NITWIT, Villager.Profession.SHEPHERD,
            Villager.Profession.TOOLSMITH, Villager.Profession.WEAPONSMITH
    };

    public VillagerNPC(FindGamePlugin plugin, Player owner) {
        this.plugin = plugin;
        this.owner = owner;
    }

    @Override
    public void spawn(Location loc) {
        if (!loc.getChunk().isLoaded()) loc.getChunk().load();
        
        entity = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        entity.setAI(true);
        entity.setCollidable(false);
        entity.setSilent(true);
        entity.setAgeLock(true);
        entity.setProfession(SAFE_PROFESSIONS[new Random().nextInt(SAFE_PROFESSIONS.length)]);
        entity.setGlowing(true);

        setSpeed(0.23);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(owner.getUniqueId())) {
                other.hideEntity(plugin, entity);
            }
        }

        boolean skinsEnabled = plugin.getConfig().getBoolean("visuals.villager-skins-enabled", true);
        List<String> allowedSkins = plugin.getConfig().getStringList("visuals.allowed-skins");
        if (skinsEnabled && !allowedSkins.isEmpty()) {
            applyRandomSkin(allowedSkins);
        }
    }

    private void applyRandomSkin(List<String> allowed) {
        String skinName = allowed.get(new Random().nextInt(allowed.size())).toUpperCase();
        try {
            Villager.Type type = Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(skinName.toLowerCase()));
            if (type != null) entity.setVillagerType(type);
        } catch (Throwable t) {
            try { entity.setVillagerType(Villager.Type.valueOf(skinName)); } catch (Exception ignored) {}
        }
    }

    @Override
    public void destroy() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    @Override
    public Entity getEntity() { return entity; }

    @Override
    public int getEntityId() { return entity != null ? entity.getEntityId() : -1; }

    @Override
    public void setSpeed(double speed) {
        if (entity != null && entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
        }
    }

    @Override
    public void playSweat() {
        if (ParticleUtil.SWEAT != null && entity != null) {
            entity.getWorld().spawnParticle(ParticleUtil.SWEAT, entity.getLocation().add(0, 2.2, 0), 8, 0.3, 0.1, 0.3, 0.01);
        }
    }

    @Override
    public void playJump() {
        if (entity != null && entity.isOnGround()) {
            entity.setVelocity(new Vector(0, 0.5, 0));
        }
    }

    @Override
    public void playSprint() {
        setSpeed(0.6);
        if (ParticleUtil.CLOUD != null && entity != null) {
            entity.getWorld().spawnParticle(ParticleUtil.CLOUD, entity.getLocation(), 5, 0.2, 0.1, 0.2, 0.05);
        }
        new BukkitRunnable() {
            @Override public void run() {
                if (entity != null && !entity.isDead()) setSpeed(0.23);
            }
        }.runTaskLater(plugin, 40L);
    }

    @Override
    public void lookAt(Player player) {
        if (entity == null) return;
        Vector dir = player.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        Location lookLoc = entity.getLocation().clone();
        lookLoc.setDirection(dir);
        entity.teleport(lookLoc);
    }

    @Override
    public void runAwayFrom(Player player) {
        if (entity == null) return;
        Vector dir = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
        entity.setVelocity(dir.multiply(0.7).setY(0.3));
        Location lookLoc = entity.getLocation().clone();
        lookLoc.setDirection(dir);
        entity.teleport(lookLoc);
    }
    
    @Override 
    public Location getLocation() { return entity.getLocation(); }
}