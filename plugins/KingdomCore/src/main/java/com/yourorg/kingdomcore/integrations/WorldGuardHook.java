package com.yourorg.kingdomcore.integrations;

import org.bukkit.Location;

import java.util.List;

public interface WorldGuardHook {
    boolean isAvailable();

    boolean isInRegion(Location location, String regionName);

    default boolean isInAnyRegion(Location location, List<String> regionNames) {
        if (location == null || regionNames == null || regionNames.isEmpty()) {
            return false;
        }
        for (String regionName : regionNames) {
            if (isInRegion(location, regionName)) {
                return true;
            }
        }
        return false;
    }
}
