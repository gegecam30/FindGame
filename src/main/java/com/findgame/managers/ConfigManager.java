package com.findgame.managers;

import com.findgame.FindGamePlugin;
import com.findgame.utils.ColorUtil;
// Ya no necesitamos importar Component de Adventure
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    
    private final FindGamePlugin plugin;
    private FileConfiguration config;
    private String prefix;
    
    public ConfigManager(FindGamePlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.prefix = config.getString("settings.prefix", "{#00FFFF}[FindGame]{#FFFFFF} ");
    }
    
    public int getMaxLives() {
        return config.getInt("settings.max-lives", 3);
    }
    
    public String getPrefix() {
        return prefix;
    }

    // CAMBIO: Ahora devuelve String, compatible con Spigot y ColorUtil actualizado
    public String getMessage(String path) {
        return getMessage(path, true);
    }
    
    public String getMessage(String path, boolean includePrefix) {
        String message = config.getString("messages." + path, "Mensaje no encontrado: " + path);
        if (includePrefix) message = prefix + message;
        // ColorUtil.colorize ahora devuelve String, así que esto es correcto:
        return ColorUtil.colorize(message);
    }
    
    public String getMessageWithPlaceholders(String path, String... placeholders) {
        String message = config.getString("messages." + path, "Mensaje no encontrado: " + path);
        message = prefix + message;
        
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String key = "{" + placeholders[i] + "}";
            String value = placeholders[i + 1];
            message = message.replace(key, value);
        }
        
        return ColorUtil.colorize(message);
    }
    
    public boolean isDebugEnabled() {
        return config.getBoolean("settings.debug", false);
    }
    
    public FileConfiguration getConfig() {
        return config;
    }
}