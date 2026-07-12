package com.yourorg.kingdomcore.util;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.service.SpawnProtectionService;
import org.bukkit.Location;

import java.util.List;

/**
 * Spawn ability blocking follows /spawnprotect — abilities only blocked in spawn when protection is ON.
 */
public final class SpawnRegionPolicy {

    private final SpawnProtectionService spawnProtectionService;
    private final WorldGuardHook worldGuardHook;
    private final List<String> spawnRegions;

    public SpawnRegionPolicy(SpawnProtectionService spawnProtectionService,
                             WorldGuardHook worldGuardHook,
                             List<String> spawnRegions) {
        this.spawnProtectionService = spawnProtectionService;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegions = spawnRegions;
    }

    public boolean blocksAbilities(Location location) {
        if (!spawnProtectionService.isEnabled()) {
            return false;
        }
        return worldGuardHook.isInAnyRegion(location, spawnRegions);
    }

    public boolean isInSpawnArea(Location location) {
        return worldGuardHook.isInAnyRegion(location, spawnRegions);
    }
}
