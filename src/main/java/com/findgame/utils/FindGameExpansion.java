package com.findgame.utils;

import com.findgame.FindGamePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class FindGameExpansion extends PlaceholderExpansion {

    private final FindGamePlugin plugin;

    public FindGameExpansion(FindGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "findgame";
    }

    @Override
    public @NotNull String getAuthor() {
        return "FindGameTeam";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Importante para que no se desconecte al recargar PAPI
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // %findgame_wins%
        if (params.equalsIgnoreCase("wins")) {
            return String.valueOf(plugin.getStatsManager().getWins(player.getUniqueId()));
        }

        // %findgame_played%
        if (params.equalsIgnoreCase("played")) {
            return String.valueOf(plugin.getStatsManager().getGamesPlayed(player.getUniqueId()));
        }

        // %findgame_max_level%
        if (params.equalsIgnoreCase("max_level")) {
            return String.valueOf(plugin.getStatsManager().getMaxLevel(player.getUniqueId()));
        }
        
        // %findgame_lives% (Solo si está jugando)
        if (params.equalsIgnoreCase("lives")) {
            // Nota: Necesitarías un getter público en GameManager para las vidas si quieres mostrar esto.
            // Por defecto devolvemos 0 o podrías implementar getPlayerLives en GameManager.
            return "N/A"; 
        }

        return null; // Placeholder no válido
    }
}
