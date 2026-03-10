package com.findgame.utils;

import org.bukkit.Particle;

public class ParticleUtil {

    public static final Particle SWEAT;
    public static final Particle DEATH_POOF;
    public static final Particle CLOUD; // <-- La variable que faltaba

    static {
        // Busca partículas compatibles según la versión del servidor
        
        // 1. Efecto de sudor (Gotas de agua)
        SWEAT = findParticle("WATER_SPLASH", "WATER_DROP", "SPLASH");
        
        // 2. Efecto de muerte (Humo/Explosión)
        // "POOF" es para 1.20+, "EXPLOSION_NORMAL" para anteriores
        DEATH_POOF = findParticle("POOF", "EXPLOSION_NORMAL", "CLOUD");
        
        // 3. Efecto de velocidad (Nubes/Humo en los pies)
        CLOUD = findParticle("CLOUD", "EXPLOSION_NORMAL", "SMOKE_NORMAL");
    }

    /**
     * Intenta encontrar una partícula válida probando varios nombres.
     * Esto evita errores si Mojang cambia los nombres en futuras versiones.
     */
    private static Particle findParticle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Si no existe en esta versión, probamos el siguiente nombre
            }
        }
        return null; // Si devuelve null, el código principal lo ignora para no crashear
    }
}