package com.findgame.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface INPC {
    void spawn(Location loc);
    void destroy();
    Entity getEntity();
    int getEntityId();
    void setSpeed(double speed);
    void playSweat();
    void playJump();
    void playSprint();
    void lookAt(Player player);
    void runAwayFrom(Player player);

    Location getLocation();
}