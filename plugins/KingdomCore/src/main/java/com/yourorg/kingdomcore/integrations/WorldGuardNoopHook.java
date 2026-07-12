package com.yourorg.kingdomcore.integrations;

import org.bukkit.Location;

public class WorldGuardNoopHook implements WorldGuardHook {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isInRegion(Location location, String regionName) {
        return false;
    }
}
