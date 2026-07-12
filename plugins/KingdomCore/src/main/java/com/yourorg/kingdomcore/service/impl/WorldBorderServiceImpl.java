package com.yourorg.kingdomcore.service.impl;

import com.yourorg.kingdomcore.service.WorldBorderService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class WorldBorderServiceImpl implements WorldBorderService {

    private final JavaPlugin plugin;

    public WorldBorderServiceImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void apply() {
        if (!plugin.getConfig().getBoolean("world-border.enabled", true)) {
            return;
        }

        List<String> worldNames = plugin.getConfig().getStringList("world-border.worlds");
        if (worldNames.isEmpty()) {
            String baseWorld = plugin.getConfig().getString("world-border.world", "world");
            worldNames = List.of(baseWorld, baseWorld + "_nether", baseWorld + "_the_end");
        }

        double defaultCenterX = plugin.getConfig().getDouble("world-border.center-x", 0.0);
        double defaultCenterZ = plugin.getConfig().getDouble("world-border.center-z", 0.0);
        double defaultRadius = plugin.getConfig().getDouble("world-border.radius", 3000.0);
        int warningDistance = plugin.getConfig().getInt("world-border.warning-distance", 50);
        double damageAmount = plugin.getConfig().getDouble("world-border.damage-amount", 0.2);
        double damageBuffer = plugin.getConfig().getDouble("world-border.damage-buffer", 5.0);

        List<String> applied = new ArrayList<>();
        for (String worldName : worldNames) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World border skipped: world '" + worldName + "' is not loaded.");
                continue;
            }

            String perWorldPath = "world-border.per-world." + worldName + ".";
            double centerX = plugin.getConfig().getDouble(perWorldPath + "center-x", defaultCenterX);
            double centerZ = plugin.getConfig().getDouble(perWorldPath + "center-z", defaultCenterZ);
            double radius = plugin.getConfig().getDouble(perWorldPath + "radius", defaultRadius);

            int minX = (int) Math.floor(centerX - radius);
            int maxX = (int) Math.ceil(centerX + radius);
            int minZ = (int) Math.floor(centerZ - radius);
            int maxZ = (int) Math.ceil(centerZ + radius);

            try {
                WorldBorder border = world.getWorldBorder();
                border.setCenter(centerX, centerZ);
                border.setSize(radius * 2.0);
                border.setWarningDistance(warningDistance);
                border.setDamageAmount(damageAmount);
                border.setDamageBuffer(damageBuffer);
                applied.add(worldName + " (X " + minX + " to " + maxX + ", Z " + minZ + " to " + maxZ + ")");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply world border on " + worldName, ex);
            }
        }

        if (!applied.isEmpty()) {
            plugin.getLogger().info("World border set on " + String.join("; ", applied) + ".");
        }
    }
}
