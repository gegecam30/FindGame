package com.findgame.utils;

import org.bukkit.Sound;

public class SoundUtil {

    /**
     * Intenta obtener un sonido de forma segura.
     */
    public static Sound get(String modernName, String legacyName) {
        try {
            return Sound.valueOf(modernName);
        } catch (IllegalArgumentException e) {
            try {
                return Sound.valueOf(legacyName);
            } catch (IllegalArgumentException ex) {
                return null; 
            }
        }
    }

    // Sonidos Universales
    public static final Sound PLING = get("BLOCK_NOTE_BLOCK_PLING", "NOTE_PLING");
    public static final Sound LEVEL_UP = get("ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
    public static final Sound VILLAGER_NO = get("ENTITY_VILLAGER_NO", "VILLAGER_NO");
    public static final Sound ITEM_BREAK = get("ENTITY_ITEM_BREAK", "ITEM_BREAK");
    public static final Sound ATTACK_STRONG = get("ENTITY_PLAYER_ATTACK_STRONG", "IRONGOLEM_HIT");
    public static final Sound DRAGON_GROWL = get("ENTITY_ENDER_DRAGON_GROWL", "ENDERDRAGON_GROWL");
    public static final Sound BEACON_DEACTIVATE = get("BLOCK_BEACON_DEACTIVATE", "BEACON_DEACTIVATE");
}