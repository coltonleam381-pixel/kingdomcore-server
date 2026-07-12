package com.yourorg.kingdomcore.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;

import java.util.logging.Logger;

public class WorldGuard7Hook implements WorldGuardHook {
    private final Logger logger;
    private RegionContainer container;

    public WorldGuard7Hook(Logger logger) {
        this.logger = logger;
        this.container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isInRegion(Location location, String regionName) {
        if (location == null || location.getWorld() == null || regionName == null) {
            return false;
        }
        
        try {
            RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));
            if (regions == null) {
                return false;
            }
            
            BlockVector3 vector = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            ApplicableRegionSet set = regions.getApplicableRegions(vector);
            
            for (ProtectedRegion region : set) {
                if (region.getId().equalsIgnoreCase(regionName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.warning("[KingdomCore] WorldGuard Region check failed: " + e.getMessage());
        }
        
        return false;
    }
}
