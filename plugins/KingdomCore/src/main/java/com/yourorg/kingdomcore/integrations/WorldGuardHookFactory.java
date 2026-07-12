package com.yourorg.kingdomcore.integrations;

import org.bukkit.Bukkit;

import java.util.logging.Logger;

public final class WorldGuardHookFactory {
    private WorldGuardHookFactory() {
    }

    public static WorldGuardHook create(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            logger.info("[KingdomCore] WorldGuard not found, using no-op region checks");
            return new WorldGuardNoopHook();
        }
        
        try {
            logger.info("[KingdomCore] WorldGuard found, initializing hook...");
            return new WorldGuard7Hook(logger);
        } catch (Throwable ex) {
            logger.warning("[KingdomCore] WorldGuard integration failed, using no-op: " + ex.getMessage());
            return new WorldGuardNoopHook();
        }
    }
}

