package com.yourorg.kingdomcore.service.impl;

import com.yourorg.kingdomcore.KingdomCorePlugin;
import com.yourorg.kingdomcore.service.MonumentService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MonumentServiceImpl implements MonumentService {

    private final KingdomCorePlugin plugin;
    private final File file;
    private FileConfiguration config;

    public MonumentServiceImpl(KingdomCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "monuments.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void setGlassLocation(String itemId, Location location) {
        List<String> list = config.getStringList(itemId + ".blocks");
        String locStr = location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
        if (!list.contains(locStr)) {
            list.add(locStr);
        }
        config.set(itemId + ".blocks", list);
        save();
    }

    @Override
    public void removeGlassLocation(String itemId) {
        config.set(itemId, null);
        save();
    }

    @Override
    public void updateGlassState(String itemId, boolean isCrafted) {
        List<String> list = config.getStringList(itemId + ".blocks");
        if (list.isEmpty()) {
            // Check for legacy single block format and convert
            if (config.contains(itemId + ".world")) {
                String locStr = config.getString(itemId + ".world") + ";" + 
                                config.getInt(itemId + ".x") + ";" + 
                                config.getInt(itemId + ".y") + ";" + 
                                config.getInt(itemId + ".z");
                list.add(locStr);
                config.set(itemId + ".blocks", list);
                config.set(itemId + ".world", null);
                config.set(itemId + ".x", null);
                config.set(itemId + ".y", null);
                config.set(itemId + ".z", null);
                save();
            } else {
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String locStr : list) {
                String[] parts = locStr.split(";");
                if (parts.length != 4) continue;
                org.bukkit.World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                try {
                    int centerX = Integer.parseInt(parts[1]);
                    int centerY = Integer.parseInt(parts[2]);
                    int centerZ = Integer.parseInt(parts[3]);
                    
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int dy = 1; dy <= 3; dy++) {
                                // Leave the center column hollow for the item
                                if (dx == 0 && dz == 0 && dy < 3) continue;
                                
                                org.bukkit.block.Block b = world.getBlockAt(centerX + dx, centerY + dy, centerZ + dz);
                                if (isCrafted) {
                                    if (b.getType() == Material.AIR || b.getType() == Material.GRAY_STAINED_GLASS) {
                                        b.setType(Material.GRAY_STAINED_GLASS);
                                    }
                                } else {
                                    if (b.getType() == Material.GRAY_STAINED_GLASS) {
                                        b.setType(Material.AIR);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public List<Location> getMonumentLocations() {
        List<Location> locations = new ArrayList<>();
        for (String itemId : config.getKeys(false)) {
            for (String locStr : config.getStringList(itemId + ".blocks")) {
                Location parsed = parseLocation(locStr);
                if (parsed != null) {
                    locations.add(parsed);
                }
            }
        }
        return locations;
    }

    private Location parseLocation(String locStr) {
        String[] parts = locStr.split(";");
        if (parts.length != 4) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(
                    world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
