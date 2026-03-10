package com.findgame.commands;

import com.findgame.FindGamePlugin;
import com.findgame.managers.ArenaManager;
import com.findgame.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class FindGameCommand implements CommandExecutor, TabCompleter {

    private final FindGamePlugin plugin;

    public FindGameCommand(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();

        // ── RELOAD (console or player) ─────────────────────────────────────
        if (sub.equals("reload")) {
            if (!sender.hasPermission("findgame.admin")) {
                sender.sendMessage(plugin.getConfigManager().msg("general.no-permission")); return true;
            }
            plugin.getConfigManager().loadConfig();
            sender.sendMessage(plugin.getConfigManager().msg("general.reload-success"));
            return true;
        }

        // ── Player-only gate ──────────────────────────────────────────────
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().msg("general.players-only")); return true;
        }
        Player player = (Player) sender;

        switch (sub) {

            // ── JOIN ──────────────────────────────────────────────────────
            case "join":
                if (args.length == 1) {
                    plugin.getGameManager().joinGame(player, null);
                } else if (args.length == 2) {
                    plugin.getGameManager().joinGame(player, args[1]);
                } else if (args.length == 3) {
                    if (!requireAdmin(player)) return true;
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage(plugin.getConfigManager().msg("general.player-not-found"));
                        return true;
                    }
                    boolean ok = plugin.getGameManager().joinGame(target, args[1]);
                    player.sendMessage(ok
                            ? ColorUtil.colorize("&aForced &e" + target.getName()
                                    + " &ato join arena &e" + args[1])
                            : ColorUtil.colorize("&cCould not force-join that player."));
                }
                break;

            // ── LEAVE ─────────────────────────────────────────────────────
            case "leave":
                if (!plugin.getGameManager().leaveGame(player))
                    player.sendMessage(plugin.getConfigManager().msg("game.not-in"));
                break;

            // ── STATS ─────────────────────────────────────────────────────
            case "stats":
                showStats(player);
                break;

            // ── TOP ───────────────────────────────────────────────────────
            case "top":
                showTop(player);
                break;

            // ── LIST ──────────────────────────────────────────────────────
            case "list":
                if (!requireAdmin(player)) return true;
                player.sendMessage(plugin.getConfigManager().msg("arena.list-header"));
                Set<String> names = plugin.getArenaManager().getArenaNames();
                if (names.isEmpty()) {
                    player.sendMessage(plugin.getConfigManager().msg("arena.list-empty"));
                } else {
                    for (String name : names) {
                        ArenaManager.Arena a = plugin.getArenaManager().getArena(name);
                        String radiusStr = a.hasRadius()
                                ? " &7| &eRadius: &f" + (int) a.getRadius() + "b" : "";
                        player.sendMessage(plugin.getConfigManager().msg("arena.list-item",
                                "name", name,
                                "info", "NPCs: " + a.getNpcSpawns().size() + radiusStr));
                    }
                }
                break;

            // ── CREATE ────────────────────────────────────────────────────
            case "create":
                if (!requireAdmin(player)) return true;
                if (args.length < 2) { usage(player, "/fg create <name>"); return true; }
                if (plugin.getArenaManager().createArena(args[1])) {
                    player.sendMessage(plugin.getConfigManager().msg("arena.created", "name", args[1]));
                    giveWand(player, args[1]);
                } else {
                    player.sendMessage(plugin.getConfigManager().msg("arena.already-exists", "name", args[1]));
                }
                break;

            // ── DELETE ────────────────────────────────────────────────────
            case "delete":
                if (!requireAdmin(player)) return true;
                if (args.length < 2) { usage(player, "/fg delete <name>"); return true; }
                if (plugin.getArenaManager().deleteArena(args[1])) {
                    player.sendMessage(plugin.getConfigManager().msg("arena.deleted", "name", args[1]));
                } else {
                    player.sendMessage(plugin.getConfigManager().msg("arena.not-found", "name", args[1]));
                }
                break;

            // ── SETSPAWN ──────────────────────────────────────────────────
            case "setspawn":
                if (!requireAdmin(player)) return true;
                if (args.length < 2) { usage(player, "/fg setspawn <arena>"); return true; }
                if (plugin.getArenaManager().getArena(args[1]) == null) {
                    player.sendMessage(plugin.getConfigManager().msg("arena.not-found", "name", args[1]));
                    return true;
                }
                plugin.getArenaManager().setPlayerSpawn(args[1], player.getLocation());
                player.sendMessage(plugin.getConfigManager().msg("arena.spawn-set", "name", args[1]));
                break;

            // ── SETRADIUS ─────────────────────────────────────────────────
            case "setradius":
                if (!requireAdmin(player)) return true;
                if (args.length < 3) { usage(player, "/fg setradius <arena> <radius>"); return true; }
                double radius;
                try {
                    radius = Double.parseDouble(args[2]);
                    if (radius <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorUtil.colorize("&cRadius must be a positive number."));
                    return true;
                }
                if (plugin.getArenaManager().setArenaRadius(args[1], radius)) {
                    player.sendMessage(plugin.getConfigManager().msg("arena.radius-set",
                            "name", args[1], "radius", String.valueOf((int) radius)));
                } else {
                    player.sendMessage(plugin.getConfigManager().msg("arena.not-found", "name", args[1]));
                }
                break;

            // ── SETLOBBY ──────────────────────────────────────────────────
            case "setlobby":
                if (!requireAdmin(player)) return true;
                plugin.getArenaManager().setGlobalLobby(player.getLocation());
                player.sendMessage(plugin.getConfigManager().msg("lobby.set"));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                break;

            // ── REMOVENPC ─────────────────────────────────────────────────
            case "removenpc":
                if (!requireAdmin(player)) return true;
                if (args.length < 2) { usage(player, "/fg removenpc <arena>"); return true; }
                if (plugin.getArenaManager().removeNearestNpcSpawn(args[1], player)) {
                    player.sendMessage(ColorUtil.colorize(
                            "&aNearby NPC spawn removed from arena &e" + args[1]));
                } else {
                    player.sendMessage(ColorUtil.colorize(
                            "&cNo NPC spawn found within 2 blocks."));
                }
                break;

            // ── WAND ──────────────────────────────────────────────────────
            case "wand":
                if (!requireAdmin(player)) return true;
                if (args.length < 2) { usage(player, "/fg wand <arena>"); return true; }
                giveWand(player, args[1]);
                break;

            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    // =========================================================
    // STATS / TOP
    // =========================================================

    private void showStats(Player player) {
        int wins    = plugin.getStatsManager().getWins(player.getUniqueId());
        int played  = plugin.getStatsManager().getGamesPlayed(player.getUniqueId());
        int winRate = (played == 0) ? 0 : (wins * 100 / played);

        player.sendMessage(plugin.getConfigManager().msg("stats.header"));
        player.sendMessage(plugin.getConfigManager().msg("stats.played",  "played",  String.valueOf(played)));
        player.sendMessage(plugin.getConfigManager().msg("stats.won",     "won",     String.valueOf(wins)));
        player.sendMessage(plugin.getConfigManager().msg("stats.winrate", "winrate", String.valueOf(winRate)));
    }

    private void showTop(Player player) {
        player.sendMessage(plugin.getConfigManager().msg("stats.top-header"));
        Map<String, Integer> top = plugin.getStatsManager().getTopPlayers(10);
        int pos = 1;
        for (Map.Entry<String, Integer> entry : top.entrySet()) {
            player.sendMessage(plugin.getConfigManager().msg("stats.top-line",
                    "position", String.valueOf(pos),
                    "player",   entry.getKey(),
                    "wins",     String.valueOf(entry.getValue())));
            pos++;
        }
        if (top.isEmpty()) player.sendMessage(ColorUtil.colorize("&7No data yet."));
    }

    // =========================================================
    // WAND
    // =========================================================

    private void giveWand(Player player, String arenaName) {
        if (plugin.getArenaManager().getArena(arenaName) == null) {
            player.sendMessage(plugin.getConfigManager().msg("arena.not-found", "name", arenaName));
            return;
        }
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta  = wand.getItemMeta();

        meta.setDisplayName(plugin.getConfigManager().msg("wand.name", false, "arena", arenaName));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfigManager().getMessages().getStringList("wand.lore")) {
            lore.add(ColorUtil.colorize(line));
        }
        meta.setLore(lore);

        NamespacedKey key = new NamespacedKey(plugin, "arena_wand");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, arenaName);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
    }

    // =========================================================
    // HELP
    // =========================================================

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().msg("help.header"));
        sender.sendMessage(plugin.getConfigManager().msg("help.join"));
        sender.sendMessage(plugin.getConfigManager().msg("help.leave"));
        sender.sendMessage(plugin.getConfigManager().msg("help.stats"));
        sender.sendMessage(plugin.getConfigManager().msg("help.top"));

        if (sender.hasPermission("findgame.admin")) {
            sender.sendMessage(plugin.getConfigManager().msg("help.admin-header"));
            sender.sendMessage(plugin.getConfigManager().msg("help.create"));
            sender.sendMessage(plugin.getConfigManager().msg("help.delete"));
            sender.sendMessage(plugin.getConfigManager().msg("help.setspawn"));
            sender.sendMessage(plugin.getConfigManager().msg("help.setradius"));
            sender.sendMessage(plugin.getConfigManager().msg("help.setlobby"));
            sender.sendMessage(plugin.getConfigManager().msg("help.removenpc"));
            sender.sendMessage(plugin.getConfigManager().msg("help.wand"));
            sender.sendMessage(plugin.getConfigManager().msg("help.list"));
            sender.sendMessage(plugin.getConfigManager().msg("help.reload"));
        }
    }

    // =========================================================
    // UTILITIES
    // =========================================================

    private boolean requireAdmin(Player player) {
        if (player.hasPermission("findgame.admin")) return true;
        player.sendMessage(plugin.getConfigManager().msg("general.no-permission"));
        return false;
    }

    private void usage(Player player, String syntax) {
        player.sendMessage(ColorUtil.colorize("&cUsage: " + syntax));
    }

    // =========================================================
    // TAB COMPLETE
    // =========================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList("join", "leave", "stats", "top"));
            if (sender.hasPermission("findgame.admin")) {
                opts.addAll(Arrays.asList("create", "delete", "setspawn", "setradius",
                        "setlobby", "removenpc", "wand", "list", "reload"));
            }
            return opts;
        }
        if (args.length == 2) {
            List<String> arenaArgs = Arrays.asList(
                    "join", "delete", "setspawn", "setradius", "removenpc", "wand");
            if (arenaArgs.contains(args[0].toLowerCase())) {
                return new ArrayList<>(plugin.getArenaManager().getArenaNames());
            }
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setradius")) {
                return Arrays.asList("10", "15", "20", "30", "50");
            }
            if (args[0].equalsIgnoreCase("join") && sender.hasPermission("findgame.admin")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                return players;
            }
        }
        return Collections.emptyList();
    }
}
