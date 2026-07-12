package com.yourorg.kingdomcore.service.impl;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.yourorg.kingdomcore.service.SpawnProtectionService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class SpawnProtectionServiceImpl implements SpawnProtectionService {

    private static final String CONFIG_PATH = "spawn-protection.enabled";
    private static final String DEFAULT_WORLD = "world";

    private final JavaPlugin plugin;
    private final String regionName;
    private boolean enabled;

    public SpawnProtectionServiceImpl(JavaPlugin plugin, String regionName, boolean enabled) {
        this.plugin = plugin;
        this.regionName = regionName;
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean setEnabled(boolean enabled) {
        this.enabled = enabled;
        saveEnabled();
        return applyCurrentState();
    }

    @Override
    public boolean toggle() {
        return setEnabled(!enabled);
    }

    @Override
    public void ensureRegion() {
        try {
            World world = Bukkit.getWorld(DEFAULT_WORLD);
            if (world == null) {
                plugin.getLogger().warning("Spawn protection world '" + DEFAULT_WORLD + "' not loaded.");
                return;
            }

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));
            if (regions == null) {
                plugin.getLogger().warning("WorldGuard region manager unavailable for " + DEFAULT_WORLD);
                return;
            }

            if (!regions.hasRegion(regionName)) {
                BlockVector3 min = BlockVector3.at(-104, -64, -201);
                BlockVector3 max = BlockVector3.at(3, 320, -69);
                ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionName, min, max);
                region.setPriority(100);
                regions.addRegion(region);
                plugin.getLogger().info("Created WorldGuard region '" + regionName + "'.");
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to ensure spawn region", ex);
        }
    }

    @Override
    public boolean applyCurrentState() {
        try {
            World world = Bukkit.getWorld(DEFAULT_WORLD);
            if (world == null) {
                return false;
            }

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));
            if (regions == null) {
                return false;
            }

            List<String> regionNames = resolveRegionNames();
            boolean anyApplied = false;

            for (String name : regionNames) {
                ProtectedRegion region = regions.getRegion(name);
                if (region == null && name.equals(regionName)) {
                    ensureRegion();
                    region = regions.getRegion(name);
                }
                if (region == null) {
                    plugin.getLogger().warning("Spawn protection region not found: " + name);
                    continue;
                }

                if (enabled) {
                    region.setFlag(Flags.PVP, StateFlag.State.DENY);
                    region.setFlag(Flags.BUILD, StateFlag.State.DENY);
                    region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
                    region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
                    region.setFlag(Flags.INVINCIBILITY, StateFlag.State.ALLOW);
                } else {
                    region.setFlag(Flags.PVP, StateFlag.State.ALLOW);
                    region.setFlag(Flags.BUILD, StateFlag.State.ALLOW);
                    region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW);
                    region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
                    region.setFlag(Flags.INVINCIBILITY, StateFlag.State.DENY);
                    region.setFlag(Flags.DAMAGE_ANIMALS, StateFlag.State.ALLOW);
                    region.setFlag(Flags.MOB_DAMAGE, StateFlag.State.ALLOW);
                }
                anyApplied = true;
            }

            if (anyApplied) {
                regions.save();
                plugin.getLogger().info("Spawn protection " + (enabled ? "enabled" : "disabled")
                        + " on regions: " + String.join(", ", regionNames));
            }
            return anyApplied;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply spawn protection state", ex);
            return false;
        }
    }

    private List<String> resolveRegionNames() {
        List<String> configured = plugin.getConfig().getStringList("spawn-protection.worldguard-regions");
        if (!configured.isEmpty()) {
            return configured;
        }
        List<String> defaults = new ArrayList<>();
        defaults.add(regionName);
        if (!regionName.equals("spawn")) {
            defaults.add("spawn");
        }
        return defaults;
    }

    private void saveEnabled() {
        FileConfiguration config = plugin.getConfig();
        config.set(CONFIG_PATH, enabled);
        plugin.saveConfig();
    }
}
