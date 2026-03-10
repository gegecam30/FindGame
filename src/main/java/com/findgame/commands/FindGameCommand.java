package com.findgame.commands;

import com.findgame.FindGamePlugin;
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

        String subCommand = args[0].toLowerCase();

        // RELOAD
        if (subCommand.equals("reload")) {
            if (!sender.hasPermission("findgame.admin")) return noPerm(sender);
            plugin.getConfigManager().loadConfig();
            sender.sendMessage(plugin.getConfigManager().getMessage("admin-reload"));
            return true;
        }

        // Validar jugador
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtil.colorize("&cOnly players."));
            return true;
        }
        Player player = (Player) sender;

        switch (subCommand) {

            // --- JOIN ---
            case "join":
                if (args.length == 1) {
                    plugin.getGameManager().joinGame(player, null);
                } else if (args.length == 2) {
                    plugin.getGameManager().joinGame(player, args[1]);
                } else if (args.length == 3) {
                    if (!checkPerm(player)) return true;
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage(ColorUtil.colorize("&cPlayer not found.")); return true;
                    }
                    boolean success = plugin.getGameManager().joinGame(target, args[1]);
                    if (success) {
                        player.sendMessage(ColorUtil.colorize(
                                "&aYou forced &e" + target.getName() + " &ato join arena &e" + args[1]));
                    } else {
                        player.sendMessage(ColorUtil.colorize("&cCould not force join."));
                    }
                }
                break;

            case "leave":
                if (!plugin.getGameManager().leaveGame(player)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("not-playing"));
                }
                break;

            case "stats":
                showStats(player);
                break;

            case "top":
                showTop(player);
                break;

            case "list":
                if (!checkPerm(player)) return true;
                player.sendMessage(plugin.getConfigManager().getMessage("arena-list-header"));
                Set<String> arenas = plugin.getArenaManager().getArenaNames();
                if (arenas.isEmpty()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("arena-list-empty"));
                } else {
                    for (String name : arenas) {
                        var arena = plugin.getArenaManager().getArena(name);
                        int npcs = arena.getNpcSpawns().size();
                        String radius = arena.hasRadius()
                                ? " &7| &eRadio: &f" + (int) arena.getRadius() + "b" : "";
                        String info = "NPCs: " + npcs + radius;
                        player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                                "arena-list-item", "name", name, "info", info));
                    }
                }
                break;

            // --- ADMIN ---
            case "create":
                if (!checkPerm(player)) return true;
                if (args.length < 2) { player.sendMessage(ColorUtil.colorize("&cUsage: /fg create <name>")); return true; }
                if (plugin.getArenaManager().createArena(args[1])) {
                    player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("arena-created", "name", args[1]));
                    giveWand(player, args[1]);
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("arena-already-exists", "name", args[1]));
                }
                break;

            case "delete":
                if (!checkPerm(player)) return true;
                if (args.length < 2) { player.sendMessage(ColorUtil.colorize("&cUsage: /fg delete <name>")); return true; }
                if (plugin.getArenaManager().deleteArena(args[1])) {
                    player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("arena-deleted", "name", args[1]));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("arena-not-found", "name", args[1]));
                }
                break;

            case "setspawn":
                if (!checkPerm(player)) return true;
                if (args.length < 2) { player.sendMessage(ColorUtil.colorize("&cUsage: /fg setspawn <arena>")); return true; }
                if (plugin.getArenaManager().getArena(args[1]) == null) {
                    player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("arena-not-found", "name", args[1]));
                    return true;
                }
                plugin.getArenaManager().setPlayerSpawn(args[1], player.getLocation());
                player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("arena-spawn-set", "name", args[1]));
                break;

            // ✅ NUEVO: /fg setradius <arena> <radio>
            case "setradius":
                if (!checkPerm(player)) return true;
                if (args.length < 3) {
                    player.sendMessage(ColorUtil.colorize("&cUsage: /fg setradius <arena> <radius>"));
                    return true;
                }
                double radius;
                try {
                    radius = Double.parseDouble(args[2]);
                    if (radius <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorUtil.colorize("&cInvalid radius. Must be a positive number."));
                    return true;
                }
                if (plugin.getArenaManager().setArenaRadius(args[1], radius)) {
                    player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                            "arena-radius-set", "name", args[1], "radius", String.valueOf((int) radius)));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                            "arena-not-found", "name", args[1]));
                }
                break;

            // ✅ NUEVO: /fg setlobby
            case "setlobby":
                if (!checkPerm(player)) return true;
                plugin.getArenaManager().setGlobalLobby(player.getLocation());
                player.sendMessage(plugin.getConfigManager().getMessage("lobby-set"));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                break;

            case "removenpc":
                if (!checkPerm(player)) return true;
                if (args.length < 2) { player.sendMessage(ColorUtil.colorize("&cUsage: /fg removenpc <arena>")); return true; }
                if (plugin.getArenaManager().removeNearestNpcSpawn(args[1], player)) {
                    player.sendMessage(ColorUtil.colorize("&aNearest NPC removed from arena &e" + args[1]));
                } else {
                    player.sendMessage(ColorUtil.colorize("&cNo NPC spawn point found near you (Radius 2 blocks)."));
                }
                break;

            case "wand":
                if (!checkPerm(player)) return true;
                if (args.length < 2) { player.sendMessage(ColorUtil.colorize("&cUsage: /fg wand <arena>")); return true; }
                giveWand(player, args[1]);
                break;

            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void showStats(Player player) {
        int wins    = plugin.getStatsManager().getWins(player.getUniqueId());
        int played  = plugin.getStatsManager().getGamesPlayed(player.getUniqueId());
        int winRate = (played == 0) ? 0 : (wins * 100 / played);

        player.sendMessage(plugin.getConfigManager().getMessage("stats-header"));
        player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("stats-played",  "played",  String.valueOf(played)));
        player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("stats-won",     "won",     String.valueOf(wins)));
        player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("stats-winrate", "winrate", String.valueOf(winRate)));
    }

    private void showTop(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("top-header"));
        Map<String, Integer> top = plugin.getStatsManager().getTopPlayers(10);
        int pos = 1;
        for (Map.Entry<String, Integer> entry : top.entrySet()) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders(
                    "top-line", "position", String.valueOf(pos),
                    "player", entry.getKey(), "wins", String.valueOf(entry.getValue())));
            pos++;
        }
        if (top.isEmpty()) player.sendMessage(ColorUtil.colorize("&7No data yet."));
    }

    private void giveWand(Player player, String arenaName) {
        if (plugin.getArenaManager().getArena(arenaName) == null) {
            player.sendMessage(plugin.getConfigManager().getMessageWithPlaceholders("arena-not-found", "name", arenaName));
            return;
        }
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(plugin.getConfigManager().getMessageWithPlaceholders("wand-name", "arena", arenaName));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("messages.wand-lore")) {
            lore.add(ColorUtil.colorize(line));
        }
        meta.setLore(lore);

        NamespacedKey key = new NamespacedKey(plugin, "arena_wand");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, arenaName);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
    }

    private boolean checkPerm(Player player) {
        if (!player.hasPermission("findgame.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return false;
        }
        return true;
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&cYou do not have permission."));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getMessage("help-header"));
        sender.sendMessage(plugin.getConfigManager().getMessage("help-start"));
        sender.sendMessage(plugin.getConfigManager().getMessage("help-leave"));
        sender.sendMessage(plugin.getConfigManager().getMessage("help-stats"));
        sender.sendMessage(plugin.getConfigManager().getMessage("help-top"));

        if (sender.hasPermission("findgame.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("help-admin-header"));
            sender.sendMessage(plugin.getConfigManager().getMessage("help-create"));
            sender.sendMessage(plugin.getConfigManager().getMessage("help-delete"));
            sender.sendMessage(ColorUtil.colorize("&e/fg setradius <arena> <radius> &7- Set boundary radius"));
            sender.sendMessage(ColorUtil.colorize("&e/fg setlobby &7- Set global lobby (return point)"));
            sender.sendMessage(ColorUtil.colorize("&e/fg removenpc <arena> &7- Remove nearby NPC spawn"));
            sender.sendMessage(plugin.getConfigManager().getMessage("help-setspawn"));
            sender.sendMessage(plugin.getConfigManager().getMessage("help-list"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList("join", "leave", "stats", "top"));
            if (sender.hasPermission("findgame.admin")) {
                opts.addAll(Arrays.asList("create", "delete", "removenpc", "setspawn",
                        "setradius", "setlobby", "wand", "reload", "list"));
            }
            return opts;
        }
        if (args.length == 2) {
            if (Arrays.asList("join", "wand", "delete", "removenpc", "setspawn", "setradius")
                    .contains(args[0].toLowerCase())) {
                return new ArrayList<>(plugin.getArenaManager().getArenaNames());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setradius")) {
            return Arrays.asList("10", "15", "20", "30", "50");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("join")
                && sender.hasPermission("findgame.admin")) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
            return players;
        }
        return Collections.emptyList();
    }
}
