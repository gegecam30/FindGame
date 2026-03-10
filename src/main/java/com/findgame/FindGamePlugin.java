package com.findgame;

import com.findgame.commands.FindGameCommand;
import com.findgame.managers.*;
import com.findgame.utils.FindGameExpansion;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

// --- IMPORTS DE BSTATS ---
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

public class FindGamePlugin extends JavaPlugin {
    
    private static FindGamePlugin instance;
    
    // --- ID DE BSTATS ---
    // ¡IMPORTANTE! Reemplaza '12345' con el ID que te dio la página de bStats
    private static final int BSTATS_ID = 28732; 
    
    private ConfigManager configManager;
    private ArenaManager arenaManager;
    private NPCManager npcManager;
    private GameManager gameManager;
    private StatsManager statsManager;
    private RewardManager rewardManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Inicializar Managers
        this.configManager = new ConfigManager(this);
        this.statsManager = new StatsManager(this);
        this.rewardManager = new RewardManager(this);
        this.arenaManager = new ArenaManager(this);
        this.npcManager = new NPCManager(this);
        this.gameManager = new GameManager(this);
        
        // Comandos y Eventos con TabCompleter
        FindGameCommand commandExecutor = new FindGameCommand(this);
        PluginCommand cmd = getCommand("findgame");
        if (cmd != null) {
            cmd.setExecutor(commandExecutor);
            cmd.setTabCompleter(commandExecutor); 
        }
        
        getServer().getPluginManager().registerEvents(new com.findgame.listeners.GameListener(this), this);
        
        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FindGameExpansion(this).register();
        }
        
        // --- INICIALIZAR BSTATS ---
        // Esto conecta tu plugin con bStats
        Metrics metrics = new Metrics(this, BSTATS_ID);
        
        // Gráfico personalizado: Cantidad de arenas creadas
        metrics.addCustomChart(new SimplePie("arenas_created", () -> {
            int count = arenaManager.getArenaNames().size();
            return String.valueOf(count);
        }));
        // --------------------------
        
        // --- BANNER DE INICIO (Estilo SkinsRestorer) ---
        String version = getDescription().getVersion();
        
        // Usamos Bukkit.getConsoleSender() para poder usar colores
        Bukkit.getConsoleSender().sendMessage("§b+---------------------------------------+");
        Bukkit.getConsoleSender().sendMessage("§b|             §f§lFIND GAME             §b|");
        Bukkit.getConsoleSender().sendMessage("§b+---------------------------------------+");
        Bukkit.getConsoleSender().sendMessage("§b|                                       |");
        Bukkit.getConsoleSender().sendMessage("§b|  §7Version: §f" + version + "                   §b|");
        Bukkit.getConsoleSender().sendMessage("§b|  §7Status:  §aEnabled successfully      §b|");
        Bukkit.getConsoleSender().sendMessage("§b|                                       |");
        Bukkit.getConsoleSender().sendMessage("§b|  §eThanks for using this plugin!      §b|");
        Bukkit.getConsoleSender().sendMessage("§b|                                       |");
        Bukkit.getConsoleSender().sendMessage("§b+---------------------------------------+");
    }
    
    @Override
    public void onDisable() {
        if (npcManager != null) npcManager.cleanupAll();
        if (arenaManager != null) arenaManager.saveArenas();
        if (statsManager != null) statsManager.saveAllStats();
        
        // Mensaje de apagado simple pero limpio
        Bukkit.getConsoleSender().sendMessage("§b[FindGame] §cPlugin disabled correctly.");
    }
    
    public static FindGamePlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public NPCManager getNpcManager() { return npcManager; }
    public GameManager getGameManager() { return gameManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public RewardManager getRewardManager() { return rewardManager; }
}