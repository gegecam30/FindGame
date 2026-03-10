package com.findgame.managers;

import com.findgame.FindGamePlugin;
import com.findgame.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Manages config.yml (settings/visuals/rewards) and messages.yml (all text).
 *
 * Usage:
 *   configManager.get("settings.max-lives")          → from config.yml
 *   configManager.msg("game.joined")                 → from messages.yml (with prefix)
 *   configManager.msg("game.joined", false)          → without prefix
 *   configManager.msg("arena.created", "name","test")→ with placeholder replacement
 */
public class ConfigManager {

    private final FindGamePlugin plugin;

    private FileConfiguration config;
    private FileConfiguration messages;
    private File messagesFile;

    private String prefix;

    public ConfigManager(FindGamePlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    // =========================================================
    // LOAD
    // =========================================================

    public void loadConfig() {
        // --- config.yml ---
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // --- messages.yml ---
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Merge any missing keys from the bundled default
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }

        // Resolve prefix: messages.yml overrides config.yml if non-blank
        String msgPrefix = messages.getString("prefix", "").trim();
        prefix = msgPrefix.isEmpty()
                ? config.getString("settings.prefix", "{#DC143C}&l[FindGame] {#B0B0B0}")
                : msgPrefix;
    }

    // =========================================================
    // CONFIG ACCESSORS  (config.yml)
    // =========================================================

    /** Raw access to config.yml values. */
    public FileConfiguration getConfig() { return config; }

    /** Raw access to messages.yml values (e.g. for reading list entries). */
    public FileConfiguration getMessages() { return messages; }

    public int getMaxLives() {
        return config.getInt("settings.max-lives", 3);
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("settings.debug", false);
    }

    public String getPrefix() { return prefix; }

    // =========================================================
    // MESSAGE ACCESSORS  (messages.yml)
    // =========================================================

    /**
     * Returns a colorized message with the prefix prepended.
     * Path is dot-separated inside messages.yml, e.g. "game.joined".
     */
    public String msg(String path) {
        return msg(path, true);
    }

    /**
     * @param withPrefix whether to prepend the server prefix.
     */
    public String msg(String path, boolean withPrefix) {
        String raw = messages.getString(path, "[MSG NOT FOUND: " + path + "]");
        if (withPrefix) raw = prefix + raw;
        return ColorUtil.colorize(raw);
    }

    /**
     * Returns a colorized message with placeholder substitution.
     * Placeholders are passed as alternating key/value pairs:
     *   msg("arena.created", "name", "lobby")
     *   msg("stats.top-line", "position","1", "player","Steve", "wins","42")
     */
    public String msg(String path, String... placeholders) {
        return msg(path, true, placeholders);
    }

    public String msg(String path, boolean withPrefix, String... placeholders) {
        String raw = messages.getString(path, "[MSG NOT FOUND: " + path + "]");
        if (withPrefix) raw = prefix + raw;

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return ColorUtil.colorize(raw);
    }

    // =========================================================
    // LEGACY BRIDGE
    // =========================================================
    // These methods keep backward-compat with any existing callers
    // that still use the old API. They delegate to the new msg() methods.

    /** @deprecated Use {@link #msg(String)} instead. */
    @Deprecated
    public String getMessage(String path) {
        return msg(path);
    }

    /** @deprecated Use {@link #msg(String, boolean)} instead. */
    @Deprecated
    public String getMessage(String path, boolean includePrefix) {
        return msg(path, includePrefix);
    }

    /** @deprecated Use {@link #msg(String, String...)} instead. */
    @Deprecated
    public String getMessageWithPlaceholders(String path, String... placeholders) {
        return msg(path, placeholders);
    }
}
