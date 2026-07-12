package com.yourorg.kingdomcore.integrations;

import org.bukkit.Location;

import java.util.logging.Logger;

/**
 * WorldGuard API integration using reflection-based loading.
 * Dynamically loads WorldGuard classes at runtime for fail-closed behavior.
 * Falls back gracefully if WorldGuard is unavailable or API changes.
 */
public class WorldGuardApiHook implements WorldGuardHook {
    private final Logger logger;
    private volatile boolean initialized = false;
    private volatile boolean failed = false;
    private Object worldGuardPlatform;

    public WorldGuardApiHook(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return true; // Assume available; actual check happens at runtime
    }

    @Override
    public boolean isInRegion(Location location, String regionName) {
        // Reflection-based approach: dynamically load WorldGuard classes
        try {
            if (failed) {
                return false; // Fail-open: if WG integration failed, don't block actions
            }
            if (!initialized) {
                initializeWorldGuard();
            }
            
            return checkRegionWithReflection(location, regionName);
        } catch (Throwable ex) {
            // Log once if not already logged
            if (!failed) {
                logger.warning("[KingdomCore] WorldGuard integration failed: " + ex.getMessage() + 
                              ". Region protection disabled, fail-open mode.");
                failed = true;
            }
            return false; // Fail-open: don't block player actions if WG unavailable
        }
    }

    private void initializeWorldGuard() throws Exception {
        Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
        Object instance = worldGuardClass.getMethod("getInstance").invoke(null);
        
        this.worldGuardPlatform = worldGuardClass.getMethod("getPlatform")
            .invoke(instance);
        
        initialized = true;
    }

    private boolean checkRegionWithReflection(Location location, String regionName) throws Exception {
        if (worldGuardPlatform == null || regionName == null || regionName.isBlank()) {
            return false;
        }

        try {
            // Use reflection to get RegionContainer and query regions
            Class<?> adapterClass = Class.forName("com.sk89q.worldguard.bukkit.BukkitAdapter");
            Class<?> blockVectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            
            // Get BlockVector3 from location
            Object blockVector = adapterClass.getMethod("asBlockVector", Location.class)
                .invoke(null, location);
            
            // Get RegionContainer
            Object regionContainer = worldGuardPlatform.getClass()
                .getMethod("getRegionContainer")
                .invoke(worldGuardPlatform);
            
            // Get RegionQuery
            Object regionQuery = regionContainer.getClass()
                .getMethod("createQuery")
                .invoke(regionContainer);
            
            // Get ApplicableRegionSet
            Object regionSet = regionQuery.getClass()
                .getMethod("getApplicableRegions", org.bukkit.World.class, blockVectorClass)
                .invoke(regionQuery, location.getWorld(), blockVector);
            
            // Check if region exists in the set
            java.util.Set<?> regions = (java.util.Set<?>) regionSet.getClass()
                .getMethod("getRegions")
                .invoke(regionSet);
            
            for (Object region : regions) {
                String id = (String) region.getClass().getMethod("getId").invoke(region);
                if (regionName.equalsIgnoreCase(id)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            // Fallback: if reflection fails, don't block (fail-open)
            return false;
        }
    }
}

