package com.yourorg.kingdomcore.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SpawnSelector {
    private SpawnSelector() {
    }

    public static Location selectSpawn(Player player) {
        if (player == null) {
            return null;
        }
        Location bed = player.getBedSpawnLocation();
        if (bed != null) {
            return bed;
        }
        World world = player.getWorld();
        return world != null ? world.getSpawnLocation() : null;
    }
}
