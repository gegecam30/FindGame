package com.findgame.managers;

import com.findgame.FindGamePlugin;
import com.findgame.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Random;

public class RewardManager {
    private final FindGamePlugin plugin;
    private final Random random = new Random();

    public RewardManager(FindGamePlugin plugin) { this.plugin = plugin; }

    public void giveWinRewards(Player player) {
        // --- Comandos de Victoria ---
        List<String> commands = plugin.getConfig().getStringList("rewards.win-commands");
        for (String cmd : commands) {
            // Protección contra comandos mal escritos en la config
            try {
                // Reemplazo seguro de variables
                String finalCmd = cmd.replace("%player%", player.getName());
                
                // Ejecutar comando desde la consola
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ColorUtil.colorize(finalCmd));
                
            } catch (Exception e) {
                // Si falla, avisamos en consola pero NO detenemos el juego
                plugin.getLogger().warning("[FindGame] Error ejecutando recompensa: '" + cmd + "'. Causa: " + e.getMessage());
            }
        }

        // --- Lógica de Bonus (20%) ---
        int chance = plugin.getConfig().getInt("rewards.bonus-chance", 20);
        if (random.nextInt(100) < chance) {
            try {
                String bonusRaw = plugin.getConfig().getString("rewards.bonus-command", "give %player% diamond 1");
                String bonusCmd = bonusRaw.replace("%player%", player.getName());
                
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bonusCmd);
                player.sendMessage(plugin.getConfigManager().getMessage("game-win-bonus"));
                
            } catch (Exception e) {
                plugin.getLogger().warning("[FindGame] Error ejecutando bonus: " + e.getMessage());
            }
        }
    }
}