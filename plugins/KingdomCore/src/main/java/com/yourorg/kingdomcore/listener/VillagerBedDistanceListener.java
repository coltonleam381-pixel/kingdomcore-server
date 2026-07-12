package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.util.VillagerBedPoiUtil;
import com.yourorg.kingdomcore.util.VillagerJobAssignUtil;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Releases unreachable bed/job POI claims and steers unemployed villagers toward nearby free job blocks.
 */
public final class VillagerBedDistanceListener implements Listener {
    private final JavaPlugin plugin;

    public VillagerBedDistanceListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        if (!plugin.getConfig().getBoolean("villager-beds.enabled", true)) {
            return;
        }

        int bedMaxDistance = plugin.getConfig().getInt("villager-beds.max-distance", 17);
        boolean babiesOnly = plugin.getConfig().getBoolean("villager-beds.babies-only", true);
        boolean taxicab = plugin.getConfig().getBoolean("villager-beds.use-taxicab-distance", true);
        boolean releaseJobSites = plugin.getConfig().getBoolean("villager-beds.release-job-sites", true);
        int jobSiteMaxDistance = plugin.getConfig().getInt("villager-beds.job-site-max-distance", 4);
        int potentialJobMaxDistance = plugin.getConfig().getInt("villager-beds.potential-job-max-distance", 2);
        boolean preferNearbyFreeJobs = plugin.getConfig().getBoolean("villager-beds.prefer-nearby-free-jobs", true);
        int freeJobSearchRadius = plugin.getConfig().getInt("villager-beds.free-job-search-radius", 16);
        int freeJobClaimRadius = plugin.getConfig().getInt("villager-beds.free-job-claim-radius", 3);
        int freeJobCheckInterval = Math.max(1, plugin.getConfig().getInt("villager-beds.free-job-check-interval-ticks", 20));
        boolean runFreeJobPass = preferNearbyFreeJobs && event.getTickNumber() % freeJobCheckInterval == 0;

        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            for (Entity entity : world.getEntitiesByClass(Villager.class)) {
                Villager villager = (Villager) entity;
                if (!babiesOnly || !villager.isAdult()) {
                    maybeReleaseHome(villager, bedMaxDistance, taxicab);
                }
                if (releaseJobSites) {
                    maybeReleaseJobSite(villager, jobSiteMaxDistance, taxicab);
                    maybeReleasePotentialJobSite(villager, potentialJobMaxDistance, taxicab);
                }
                if (runFreeJobPass) {
                    VillagerJobAssignUtil.preferNearbyFreeJob(
                            villager, freeJobSearchRadius, freeJobClaimRadius, taxicab, plugin.getLogger());
                }
            }
        }
    }

    private void maybeReleaseHome(Villager villager, int maxDistance, boolean taxicab) {
        Location home = villager.getMemory(MemoryKey.HOME);
        if (home == null || home.getWorld() == null) {
            return;
        }
        if (!home.getWorld().equals(villager.getWorld())) {
            VillagerBedPoiUtil.releaseHome(villager, plugin.getLogger());
            return;
        }
        if (distance(villager.getLocation(), home, taxicab) > maxDistance) {
            VillagerBedPoiUtil.releaseHome(villager, plugin.getLogger());
        }
    }

    private void maybeReleaseJobSite(Villager villager, int maxDistance, boolean taxicab) {
        if (VillagerJobAssignUtil.isUnemployed(villager)) {
            Location jobSite = villager.getMemory(MemoryKey.JOB_SITE);
            if (jobSite != null) {
                VillagerBedPoiUtil.releaseJobSite(villager, plugin.getLogger());
            }
            return;
        }

        Location jobSite = villager.getMemory(MemoryKey.JOB_SITE);
        if (jobSite == null || jobSite.getWorld() == null) {
            return;
        }
        if (!jobSite.getWorld().equals(villager.getWorld())) {
            VillagerBedPoiUtil.releaseJobSite(villager, plugin.getLogger());
            return;
        }
        if (distance(villager.getLocation(), jobSite, taxicab) > maxDistance) {
            VillagerBedPoiUtil.releaseJobSite(villager, plugin.getLogger());
        }
    }

    private void maybeReleasePotentialJobSite(Villager villager, int maxDistance, boolean taxicab) {
        Location potential = VillagerJobAssignUtil.readPotentialJobSite(villager);
        if (potential == null || potential.getWorld() == null) {
            return;
        }
        if (!potential.getWorld().equals(villager.getWorld())) {
            VillagerBedPoiUtil.releasePotentialJobSite(villager, plugin.getLogger());
            return;
        }
        if (distance(villager.getLocation(), potential, taxicab) > maxDistance) {
            VillagerBedPoiUtil.releasePotentialJobSite(villager, plugin.getLogger());
        }
    }

    private static double distance(Location from, Location to, boolean taxicab) {
        return VillagerJobAssignUtil.distance(from, to, taxicab);
    }
}
