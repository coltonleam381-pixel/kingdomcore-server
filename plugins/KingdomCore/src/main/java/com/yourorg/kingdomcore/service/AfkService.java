package com.yourorg.kingdomcore.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface AfkService {
    void startWindup(Player player);
    void cancelWindup(Player player, String reason);
    void enterAfk(Player player);
    void exitAfk(Player player, String reason);
    boolean isAfk(Player player);
    boolean isWindup(Player player);
    boolean canUseAfkCommand(Player player);
    long getCooldownRemaining(Player player);
    Location getAnchor(Player player);
    void snapToAnchor(Player player);
}
