package com.yourorg.kingdomcore.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Spawns particles visible to all nearby players (force=true bypasses client particle settings).
 */
public final class ParticleBroadcast {

    private static final double VIEW_DISTANCE_SQ = 64.0 * 64.0;

    private ParticleBroadcast() {
    }

    public static void particle(World world, Location location, Particle particle, int count,
                                double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || location == null) {
            return;
        }
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, null, true);
    }

    public static void particle(World world, Location location, Particle particle, int count,
                                double offsetX, double offsetY, double offsetZ, double extra,
                                Object data) {
        if (world == null || location == null) {
            return;
        }
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data, true);
    }

    public static void particleForNearby(World world, Location location, Particle particle, int count,
                                         double offsetX, double offsetY, double offsetZ, double extra) {
        particle(world, location, particle, count, offsetX, offsetY, offsetZ, extra);
    }

    public static boolean isNearAnyPlayer(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= VIEW_DISTANCE_SQ) {
                return true;
            }
        }
        return false;
    }
}
