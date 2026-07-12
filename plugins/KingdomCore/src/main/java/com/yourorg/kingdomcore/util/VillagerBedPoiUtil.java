package com.yourorg.kingdomcore.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clears villager POI brain memory and releases POI tickets on bed/job blocks.
 */
public final class VillagerBedPoiUtil {
    private static volatile boolean poiWarningLogged;

    private VillagerBedPoiUtil() {
    }

    public static void releaseHome(Villager villager, Logger logger) {
        releaseMemory(villager, "HOME", MemoryKey.HOME, logger);
    }

    public static void releaseJobSite(Villager villager, Logger logger) {
        releaseMemory(villager, "JOB_SITE", MemoryKey.JOB_SITE, logger);
    }

    /** Clears a stuck "walking to job" reservation that blocks other villagers (MC-257069). */
    public static void releasePotentialJobSite(Villager villager, Logger logger) {
        releaseMemory(villager, "POTENTIAL_JOB_SITE", null, logger);
    }

    public static void releasePoiAtBlock(Location location, Logger logger) {
        releasePoiAtLocation(location, logger);
    }

    private static void releaseMemory(
            Villager villager,
            String nmsFieldName,
            MemoryKey<Location> bukkitKey,
            Logger logger) {
        Location bukkitLocation = bukkitKey != null ? villager.getMemory(bukkitKey) : null;
        Object blockPos = null;
        try {
            Object nmsVillager = villager.getClass().getMethod("getHandle").invoke(villager);
            Object brain = nmsVillager.getClass().getMethod("getBrain").invoke(nmsVillager);
            Class<?> memoryModuleTypeClass = Class.forName("net.minecraft.world.entity.ai.memory.MemoryModuleType");
            Object memoryKey = memoryModuleTypeClass.getField(nmsFieldName).get(null);

            Method getMemory = brain.getClass().getMethod("getMemory", memoryModuleTypeClass);
            Object optionalMemory = getMemory.invoke(brain, memoryKey);
            blockPos = globalPosToBlockPos(optionalMemory);

            Method eraseMemory = brain.getClass().getMethod("eraseMemory", memoryModuleTypeClass);
            eraseMemory.invoke(brain, memoryKey);

            if (blockPos != null) {
                releasePoiBlock(nmsVillager, blockPos);
            }
        } catch (ReflectiveOperationException ex) {
            if (!poiWarningLogged) {
                poiWarningLogged = true;
                logger.log(Level.WARNING, "NMS villager POI release failed for " + nmsFieldName + ".", ex);
            }
        }

        if (bukkitKey != null) {
            villager.setMemory(bukkitKey, null);
        }

        if (blockPos == null && bukkitLocation != null && bukkitLocation.getWorld() != null) {
            releasePoiAtLocation(bukkitLocation, logger);
        }
    }

    private static Object globalPosToBlockPos(Object optionalMemory) throws ReflectiveOperationException {
        if (optionalMemory == null) {
            return null;
        }
        Method isPresent = optionalMemory.getClass().getMethod("isPresent");
        if (!(boolean) isPresent.invoke(optionalMemory)) {
            return null;
        }
        Object globalPos = optionalMemory.getClass().getMethod("get").invoke(optionalMemory);
        return globalPos.getClass().getMethod("pos").invoke(globalPos);
    }

    private static void releasePoiBlock(Object nmsVillager, Object blockPos) throws ReflectiveOperationException {
        Object level = nmsVillager.getClass().getMethod("level").invoke(nmsVillager);
        Object poiManager = level.getClass().getMethod("getPoiManager").invoke(level);
        Method release = poiManager.getClass().getMethod("release", blockPos.getClass());
        release.invoke(poiManager, blockPos);
    }

    private static void releasePoiAtLocation(Location location, Logger logger) {
        try {
            World world = location.getWorld();
            Object serverLevel = world.getClass().getMethod("getHandle").invoke(world);
            Object blockPos = Class.forName("net.minecraft.core.BlockPos")
                    .getMethod("containing", double.class, double.class, double.class)
                    .invoke(null, location.getX(), location.getY(), location.getZ());
            Object poiManager = serverLevel.getClass().getMethod("getPoiManager").invoke(serverLevel);
            Method release = poiManager.getClass().getMethod("release", blockPos.getClass());
            release.invoke(poiManager, blockPos);
        } catch (ReflectiveOperationException ex) {
            if (!poiWarningLogged) {
                poiWarningLogged = true;
                logger.log(Level.WARNING, "Could not release POI at location.", ex);
            }
        }
    }
}
