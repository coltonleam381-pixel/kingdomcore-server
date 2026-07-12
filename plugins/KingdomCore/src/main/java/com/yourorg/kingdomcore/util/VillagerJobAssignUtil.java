package com.yourorg.kingdomcore.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helps unemployed villagers claim nearby free job-site POIs instead of staying stuck
 * on unreachable reservations (MC-257069).
 */
public final class VillagerJobAssignUtil {
    private static volatile boolean warningLogged;
    private static Object jobSitePredicate;
    private static Object occupancyHasSpace;
    private static Object occupancyAny;
    private static Class<?> blockPosClass;
    private static Class<?> occupancyClass;
    private static Class<?> memoryModuleTypeClass;
    private static Object potentialJobSiteKey;

    private VillagerJobAssignUtil() {
    }

    public static boolean isUnemployed(Villager villager) {
        if (!villager.isAdult()) {
            return false;
        }
        return villager.getProfession() == Villager.Profession.NONE;
    }

    /**
     * If a closer free job site exists, drop stale locks and reserve the nearby block.
     * Also steals reservations from villagers farther away when this one is adjacent.
     */
    public static boolean preferNearbyFreeJob(
            Villager villager,
            int searchRadius,
            int claimRadius,
            boolean taxicab,
            Logger logger) {
        if (!isUnemployed(villager)) {
            return false;
        }

        Location nearestFree = findNearestFreeJobSite(villager, searchRadius, logger);
        if (nearestFree == null) {
            nearestFree = findNearestOccupiedJobSite(villager, Math.min(searchRadius, claimRadius + 2), logger);
            if (nearestFree != null) {
                releaseCompetingReservations(villager, nearestFree, claimRadius, taxicab, logger);
                nearestFree = findNearestFreeJobSite(villager, searchRadius, logger);
            }
        }

        if (nearestFree == null) {
            return false;
        }

        double seekerDist = distance(villager.getLocation(), nearestFree, taxicab);
        if (seekerDist > claimRadius) {
            return false;
        }

        releaseCompetingReservations(villager, nearestFree, claimRadius, taxicab, logger);

        Location currentLock = readPotentialJobSite(villager);
        if (currentLock == null) {
            currentLock = villager.getMemory(MemoryKey.JOB_SITE);
        }

        if (currentLock != null && sameBlock(currentLock, nearestFree)) {
            return false;
        }

        if (currentLock != null) {
            double lockDist = distance(villager.getLocation(), currentLock, taxicab);
            if (lockDist <= seekerDist) {
                return false;
            }
        }

        VillagerBedPoiUtil.releasePotentialJobSite(villager, logger);
        VillagerBedPoiUtil.releaseJobSite(villager, logger);
        return assignPotentialJobSite(villager, nearestFree, searchRadius, logger);
    }

    public static void releaseCompetingReservations(
            Villager seeker,
            Location jobSite,
            int claimRadius,
            boolean taxicab,
            Logger logger) {
        World world = seeker.getWorld();
        if (world == null || jobSite.getWorld() == null || !world.equals(jobSite.getWorld())) {
            return;
        }

        double seekerDist = distance(seeker.getLocation(), jobSite, taxicab);
        if (seekerDist > claimRadius) {
            return;
        }

        VillagerBedPoiUtil.releasePoiAtBlock(jobSite, logger);

        for (Villager other : world.getEntitiesByClass(Villager.class)) {
            if (other.getUniqueId().equals(seeker.getUniqueId())) {
                continue;
            }

            Location potential = readPotentialJobSite(other);
            Location linkedJob = other.getMemory(MemoryKey.JOB_SITE);
            double otherDist = distance(other.getLocation(), jobSite, taxicab);

            if (potential != null && sameBlock(potential, jobSite)
                    && (seekerDist < otherDist || otherDist > claimRadius)) {
                VillagerBedPoiUtil.releasePotentialJobSite(other, logger);
            }

            if (isUnemployed(other) && linkedJob != null && sameBlock(linkedJob, jobSite)
                    && (seekerDist < otherDist || otherDist > claimRadius)) {
                VillagerBedPoiUtil.releaseJobSite(other, logger);
            }
        }
    }

    public static Location findNearestFreeJobSite(Villager villager, int searchRadius, Logger logger) {
        return findNearestJobSite(villager, searchRadius, occupancyHasSpace, logger);
    }

    private static Location findNearestOccupiedJobSite(Villager villager, int searchRadius, Logger logger) {
        return findNearestJobSite(villager, searchRadius, occupancyAny, logger);
    }

    private static Location findNearestJobSite(
            Villager villager,
            int searchRadius,
            Object occupancy,
            Logger logger) {
        try {
            ensureReflection();

            Object nmsVillager = villager.getClass().getMethod("getHandle").invoke(villager);
            Object level = nmsVillager.getClass().getMethod("level").invoke(nmsVillager);
            Object poiManager = level.getClass().getMethod("getPoiManager").invoke(level);

            Object villagerPos = blockPosClass.getMethod("containing", double.class, double.class, double.class)
                    .invoke(null, villager.getLocation().getX(), villager.getLocation().getY(), villager.getLocation().getZ());

            Method findClosest = poiManager.getClass().getMethod(
                    "findClosest",
                    java.util.function.Predicate.class,
                    blockPosClass,
                    int.class,
                    occupancyClass);

            Object optionalPos = findClosest.invoke(poiManager, jobSitePredicate, villagerPos, searchRadius, occupancy);
            if (optionalPos == null || !(boolean) optionalPos.getClass().getMethod("isPresent").invoke(optionalPos)) {
                return null;
            }

            Object blockPos = optionalPos.getClass().getMethod("get").invoke(optionalPos);
            return blockPosToLocation(villager.getWorld(), blockPos);
        } catch (ReflectiveOperationException ex) {
            logOnce(logger, "Finding nearby villager job sites failed.", ex);
            return null;
        }
    }

    public static Location readPotentialJobSite(Villager villager) {
        try {
            ensureReflection();

            Object nmsVillager = villager.getClass().getMethod("getHandle").invoke(villager);
            Object brain = nmsVillager.getClass().getMethod("getBrain").invoke(nmsVillager);
            Object optional = brain.getClass().getMethod("getMemory", memoryModuleTypeClass).invoke(brain, potentialJobSiteKey);
            if (optional == null || !(boolean) optional.getClass().getMethod("isPresent").invoke(optional)) {
                return null;
            }
            Object globalPos = optional.getClass().getMethod("get").invoke(optional);
            Object blockPos = globalPos.getClass().getMethod("pos").invoke(globalPos);
            return blockPosToLocation(villager.getWorld(), blockPos);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static Location readJobSite(Villager villager) {
        Location jobSite = villager.getMemory(MemoryKey.JOB_SITE);
        if (jobSite != null) {
            return jobSite;
        }
        return readPotentialJobSite(villager);
    }

    private static boolean assignPotentialJobSite(
            Villager villager,
            Location jobSite,
            int searchRadius,
            Logger logger) {
        try {
            ensureReflection();

            Object nmsVillager = villager.getClass().getMethod("getHandle").invoke(villager);
            Object level = nmsVillager.getClass().getMethod("level").invoke(nmsVillager);
            Object poiManager = level.getClass().getMethod("getPoiManager").invoke(level);
            Object blockPos = blockPosClass.getMethod("containing", double.class, double.class, double.class)
                    .invoke(null, jobSite.getX(), jobSite.getY(), jobSite.getZ());

            Object alwaysTrue = Proxy.newProxyInstance(
                    VillagerJobAssignUtil.class.getClassLoader(),
                    new Class[]{java.util.function.BiPredicate.class},
                    (proxy, method, args) -> true);

            Method take = poiManager.getClass().getMethod(
                    "take",
                    java.util.function.Predicate.class,
                    java.util.function.BiPredicate.class,
                    blockPosClass,
                    int.class);
            Object taken = take.invoke(poiManager, jobSitePredicate, alwaysTrue, blockPos, searchRadius);
            if (taken instanceof Optional<?> optional && optional.isEmpty()) {
                return false;
            }

            Object dimension = level.getClass().getMethod("dimension").invoke(level);
            Class<?> globalPosClass = Class.forName("net.minecraft.core.GlobalPos");
            Object globalPos = globalPosClass.getMethod("of", dimension.getClass(), blockPosClass)
                    .invoke(null, dimension, blockPos);

            Object brain = nmsVillager.getClass().getMethod("getBrain").invoke(nmsVillager);
            Method setMemory = brain.getClass().getMethod("setMemory", memoryModuleTypeClass, Object.class);
            // Brain stores GlobalPos directly; Optional here breaks ValidateNearbyPoi and kills villager AI.
            setMemory.invoke(brain, potentialJobSiteKey, globalPos);
            return true;
        } catch (ReflectiveOperationException ex) {
            logOnce(logger, "Assigning nearby villager job site failed.", ex);
            return false;
        }
    }

    private static Location blockPosToLocation(World world, Object blockPos) throws ReflectiveOperationException {
        int x = (int) blockPosClass.getMethod("getX").invoke(blockPos);
        int y = (int) blockPosClass.getMethod("getY").invoke(blockPos);
        int z = (int) blockPosClass.getMethod("getZ").invoke(blockPos);
        return new Location(world, x, y, z);
    }

    private static void ensureReflection() throws ReflectiveOperationException {
        if (jobSitePredicate != null) {
            return;
        }
        Class<?> villagerProfessionClass = Class.forName("net.minecraft.world.entity.npc.VillagerProfession");
        jobSitePredicate = villagerProfessionClass.getField("ALL_ACQUIRABLE_JOBS").get(null);

        occupancyClass = Class.forName("net.minecraft.world.entity.ai.village.poi.PoiManager$Occupancy");
        occupancyHasSpace = occupancyClass.getField("HAS_SPACE").get(null);
        occupancyAny = occupancyClass.getField("ANY").get(null);

        blockPosClass = Class.forName("net.minecraft.core.BlockPos");
        memoryModuleTypeClass = Class.forName("net.minecraft.world.entity.ai.memory.MemoryModuleType");
        potentialJobSiteKey = memoryModuleTypeClass.getField("POTENTIAL_JOB_SITE").get(null);
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ()
                && a.getWorld().equals(b.getWorld());
    }

    public static double distance(Location from, Location to, boolean taxicab) {
        if (taxicab) {
            return Math.abs(from.getBlockX() - to.getBlockX())
                    + Math.abs(from.getBlockY() - to.getBlockY())
                    + Math.abs(from.getBlockZ() - to.getBlockZ());
        }
        return from.distance(to);
    }

    private static void logOnce(Logger logger, String message, ReflectiveOperationException ex) {
        if (!warningLogged) {
            warningLogged = true;
            logger.log(Level.WARNING, message, ex);
        }
    }
}
