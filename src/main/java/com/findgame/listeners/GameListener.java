package com.findgame.listeners;

import com.findgame.FindGamePlugin;
import com.findgame.managers.GameManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GameListener implements Listener {

    private final FindGamePlugin plugin;

    public GameListener(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Muerte del Jugador.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getGameManager().isPlaying(player)) {
            event.getDrops().clear();
            event.setDeathMessage(null);
            plugin.getGameManager().leaveGame(player);
        }
    }

    /**
     * Varita de setup.
     */
    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;
        NamespacedKey key = new NamespacedKey(plugin, "arena_wand");
        if (!item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;

        event.setCancelled(true);
        String arenaName = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (event.getClickedBlock() == null) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Location loc = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
            loc.setYaw(player.getLocation().getYaw());
            loc.setPitch(player.getLocation().getPitch());
            plugin.getArenaManager().setPlayerSpawn(arenaName, loc);
            player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                    "wand-set-spawn", "name", arenaName));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);

        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block targetBlock = event.getClickedBlock().getRelative(event.getBlockFace());
            Location loc = targetBlock.getLocation().add(0.5, 0, 0.5);
            loc.setYaw(player.getLocation().getYaw() + 180);
            boolean success = plugin.getArenaManager().addNpcSpawn(arenaName, loc);
            if (success) {
                int count = plugin.getArenaManager().getArena(arenaName).getNpcSpawns().size();
                player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                        "wand-success", "number", String.valueOf(count), "name", arenaName));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
            } else {
                player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                        "wand-error", "name", arenaName));
            }
        }
    }

    /**
     * Ataque a aldeano → procesar intento.
     */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Villager)) return;

        Player player = (Player) event.getDamager();
        if (!plugin.getGameManager().isPlaying(player)) return;

        event.setCancelled(true);
        plugin.getGameManager().processGuess(player, event.getEntity());
    }

    /**
     * Bloquear interacción con NPCs del juego.
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameManager().isPlaying(player)
                && plugin.getNpcManager().isGameNPC(player, event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * ✅ NUEVO: Verificar radio de confinamiento en cada movimiento.
     * Usamos MONITOR para no interferir con otros listeners,
     * y solo chequeamos cuando el bloque cambia (optimización).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Optimización: solo actuar si el jugador cambió de bloque
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        plugin.getGameManager().checkRadiusBreach(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getNpcManager().hideAllExistingNPCsFrom(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getGameManager().isPlaying(event.getPlayer())) {
            plugin.getGameManager().leaveGame(event.getPlayer());
        }
    }
}
