package com.findgame.data;

public enum ImpostorType {
    NERVOUS("El Nervioso"),  // Salta y suda
    KILLER("El Asesino"),    // Mata rápido
    SHY("El Tímido"),        // Huye de ti
    SPEEDRUNNER("El Velocista"); // NUEVO: Corre muy rápido

    private final String displayName;

    ImpostorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}