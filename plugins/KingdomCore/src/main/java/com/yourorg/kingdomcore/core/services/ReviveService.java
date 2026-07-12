package com.yourorg.kingdomcore.core.services;

import org.bukkit.entity.Player;

public interface ReviveService {
    boolean revive(Player reviver, String targetName);

    void unblockPlayer(String targetName);

    void handleJoin(Player player);
}
