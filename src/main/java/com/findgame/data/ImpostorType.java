package com.findgame.data;

public enum ImpostorType {

    // --- Original types ---
    NERVOUS("The Nervous One"),     // Sweats + jumps randomly
    KILLER("The Killer"),           // Stalks and eliminates innocents
    SHY("The Shy One"),             // Flees when player gets close
    SPEEDRUNNER("The Speedster"),   // Bursts of high speed

    // --- New types ---
    DRUNK("The Drunkard"),          // Erratic random movement, stumbles around
    PARANOID("The Paranoid One"),   // Constantly stares at the player
    ASSASSIN("The Assassin"),       // Kills nearby innocents very frequently
    FREEZER("The Statue");          // Stands completely still — hardest to spot

    private final String displayName;

    ImpostorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
