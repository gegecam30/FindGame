package com.findgame.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final Pattern HEX_PATTERN = Pattern.compile("\\{#([A-Fa-f0-9]{6})\\}");

    /**
     * Convierte texto con formato {#RRGGBB} o &c a String Legacy compatible con Spigot.
     */
    public static String colorize(String text) {
        if (text == null) return "";

        // 1. Soporte para formato {#FFFFFF} (Nuestro formato config)
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            // Convertimos {#FFFFFF} a formato Bungee/Spigot &x&F&F&F&F&F&F
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        text = buffer.toString();

        // 2. Soporte para formato clásico &c
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Método auxiliar si alguna vez necesitamos un Component de Adventure
     */
    public static Component asComponent(String text) {
        return SERIALIZER.deserialize(colorize(text));
    }
}