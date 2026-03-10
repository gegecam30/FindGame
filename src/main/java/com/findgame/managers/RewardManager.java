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

    public RewardManager(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    public void giveWinRewards(Player player) {
        List<String> commands = plugin.getConfig().getStringList("rewards.win-commands");
        for (String cmd : commands) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        ColorUtil.colorize(cmd.replace("%player%", player.getName())));
            } catch (Exception e) {
                plugin.getLogger().warning("[FindGame] Failed to run reward command: '"
                        + cmd + "' — " + e.getMessage());
            }
        }

        int chance = plugin.getConfig().getInt("rewards.bonus-chance", 20);
        if (random.nextInt(100) < chance) {
            try {
                String bonus = plugin.getConfig().getString("rewards.bonus-command",
                        "give %player% diamond 1");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        bonus.replace("%player%", player.getName()));
                player.sendMessage(plugin.getConfigManager().msg("game.win-bonus"));
            } catch (Exception e) {
                plugin.getLogger().warning("[FindGame] Failed to run bonus command: "
                        + e.getMessage());
            }
        }
    }
}
