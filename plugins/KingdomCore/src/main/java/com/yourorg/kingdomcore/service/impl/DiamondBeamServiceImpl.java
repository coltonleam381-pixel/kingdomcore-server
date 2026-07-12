package com.yourorg.kingdomcore.service.impl;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.service.DiamondBeamService;
import com.yourorg.kingdomcore.util.ParticleBroadcast;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public class DiamondBeamServiceImpl implements DiamondBeamService {

    private static final String CONFIG_ROOT = "diamond-beams.";

    private final JavaPlugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final List<String> spawnRegions;
    private final File cacheFile;

    private boolean enabled;
    private double thickness;
    private int height;
    private int intervalTicks;
    private int scanMinY;
    private int scanMaxY;
    private boolean scanOnStartup;
    private Color beamColor;
    private float particleSize;
    private final Set<Location> beamSources = Collections.synchronizedSet(new LinkedHashSet<>());
    private BukkitTask renderTask;

    public DiamondBeamServiceImpl(JavaPlugin plugin, WorldGuardHook worldGuardHook, List<String> spawnRegions) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegions = List.copyOf(spawnRegions);
        this.cacheFile = new File(plugin.getDataFolder(), "diamond-beams.yml");
        reload();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set(CONFIG_ROOT + "enabled", enabled);
        plugin.saveConfig();
        restartRenderTask();
        return true;
    }

    @Override
    public double getThickness() {
        return thickness;
    }

    @Override
    public boolean setThickness(double thickness) {
        this.thickness = Math.max(0.0, Math.min(5.0, thickness));
        plugin.getConfig().set(CONFIG_ROOT + "thickness", this.thickness);
        plugin.saveConfig();
        return true;
    }

    @Override
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(CONFIG_ROOT + "enabled", true);
        thickness = Math.max(0.0, Math.min(5.0, config.getDouble(CONFIG_ROOT + "thickness", 1.0)));
        height = Math.max(16, config.getInt(CONFIG_ROOT + "height", 256));
        intervalTicks = Math.max(2, config.getInt(CONFIG_ROOT + "interval-ticks", 5));
        scanMinY = config.getInt(CONFIG_ROOT + "scan-min-y", 70);
        scanMaxY = config.getInt(CONFIG_ROOT + "scan-max-y", 95);
        scanOnStartup = config.getBoolean(CONFIG_ROOT + "scan-on-startup", true);
        particleSize = (float) Math.max(0.5, config.getDouble(CONFIG_ROOT + "particle-size", 1.15));
        beamColor = Color.fromRGB(
                config.getInt(CONFIG_ROOT + "color.red", 80),
                config.getInt(CONFIG_ROOT + "color.green", 220),
                config.getInt(CONFIG_ROOT + "color.blue", 255)
        );

        loadCachedSources();
        if (beamSources.isEmpty() && scanOnStartup) {
            rescan();
        } else {
            restartRenderTask();
        }
    }

    @Override
    public void rescan() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<Location> found = scanDiamondBlocks();
            Bukkit.getScheduler().runTask(plugin, () -> {
                beamSources.clear();
                beamSources.addAll(found);
                saveCachedSources();
                plugin.getLogger().info("Diamond beam scan found " + found.size() + " diamond block(s).");
                restartRenderTask();
            });
        });
    }

    @Override
    public List<Location> getBeamSources() {
        synchronized (beamSources) {
            return List.copyOf(beamSources);
        }
    }

    public void addSource(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Location normalized = normalize(location);
        if (normalized.getBlock().getType() != Material.DIAMOND_BLOCK) {
            return;
        }
        if (beamSources.add(normalized)) {
            saveCachedSources();
        }
    }

    public void removeSource(Location location) {
        if (location == null) {
            return;
        }
        if (beamSources.remove(normalize(location))) {
            saveCachedSources();
        }
    }

    public void shutdown() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
        }
    }

    private void restartRenderTask() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
        }
        if (!enabled || beamSources.isEmpty()) {
            return;
        }
        renderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderBeams, intervalTicks, intervalTicks);
    }

    private void renderBeams() {
        if (!enabled || beamSources.isEmpty()) {
            return;
        }

        Particle.DustOptions dust = new Particle.DustOptions(beamColor, particleSize);
        for (Location source : getBeamSources()) {
            World world = source.getWorld();
            if (world == null) {
                continue;
            }
            if (source.getBlock().getType() != Material.DIAMOND_BLOCK) {
                continue;
            }
            if (!world.isChunkLoaded(source.getBlockX() >> 4, source.getBlockZ() >> 4)) {
                continue;
            }
            if (!hasNearbyViewer(world, source)) {
                continue;
            }

            double centerX = source.getBlockX() + 0.5;
            double centerZ = source.getBlockZ() + 0.5;
            int startY = source.getBlockY() + 1;
            int endY = Math.min(world.getMaxHeight() - 1, startY + height);

            for (int y = startY; y <= endY; y += 2) {
                Location center = new Location(world, centerX, y + 0.5, centerZ);
                spawnBeamPoint(center, dust, 0.0, 0.0);

                if (thickness <= 0.01) {
                    continue;
                }

                double radius = 0.12 + (thickness * 0.18);
                int points = Math.max(4, (int) Math.round(thickness * 6.0));
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2.0 * i) / points;
                    spawnBeamPoint(center, dust, Math.cos(angle) * radius, Math.sin(angle) * radius);
                }

                if (thickness >= 2.0) {
                    double innerRadius = radius * 0.55;
                    for (int i = 0; i < points; i++) {
                        double angle = (Math.PI * 2.0 * i) / points + (Math.PI / points);
                        spawnBeamPoint(center, dust, Math.cos(angle) * innerRadius, Math.sin(angle) * innerRadius);
                    }
                }
            }
        }
    }

    private void spawnBeamPoint(Location center, Particle.DustOptions dust, double offsetX, double offsetZ) {
        Location point = center.clone().add(offsetX, 0.0, offsetZ);
        ParticleBroadcast.particle(center.getWorld(), point, Particle.DUST, 1, 0.0, 0.0, 0.0, 0.0, dust);
        if (offsetX == 0.0 && offsetZ == 0.0) {
            ParticleBroadcast.particle(center.getWorld(), point, Particle.END_ROD, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private boolean hasNearbyViewer(World world, Location source) {
        double viewDistance = 64.0;
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(source) <= viewDistance * viewDistance) {
                return true;
            }
        }
        return false;
    }

    private Set<Location> scanDiamondBlocks() {
        if (!worldGuardHook.isAvailable() || spawnRegions.isEmpty()) {
            return Set.of();
        }

        Set<Location> found = new LinkedHashSet<>();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        World world = Bukkit.getWorld("world");
        if (world == null) {
            return found;
        }

        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return found;
        }

        Set<String> scannedRegions = new LinkedHashSet<>();
        for (String regionName : spawnRegions) {
            if (!scannedRegions.add(regionName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            ProtectedRegion region = manager.getRegion(regionName);
            if (region == null) {
                continue;
            }

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            int minY = Math.max(min.getY(), scanMinY);
            int maxY = Math.min(max.getY(), scanMaxY);

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        world.getChunkAt(x >> 4, z >> 4).load(true);
                    }
                    for (int y = minY; y <= maxY; y++) {
                        if (world.getBlockAt(x, y, z).getType() == Material.DIAMOND_BLOCK) {
                            found.add(new Location(world, x, y, z));
                        }
                    }
                }
            }
        }
        return found;
    }

    private Location normalize(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    private void loadCachedSources() {
        beamSources.clear();
        if (!cacheFile.exists()) {
            return;
        }
        FileConfiguration cache = YamlConfiguration.loadConfiguration(cacheFile);
        for (String entry : cache.getStringList("sources")) {
            Location parsed = parseLocation(entry);
            if (parsed != null && parsed.getBlock().getType() == Material.DIAMOND_BLOCK) {
                beamSources.add(parsed);
            }
        }
    }

    private void saveCachedSources() {
        FileConfiguration cache = new YamlConfiguration();
        List<String> serialized = new ArrayList<>();
        synchronized (beamSources) {
            for (Location location : beamSources) {
                serialized.add(serializeLocation(location));
            }
        }
        cache.set("sources", serialized);
        try {
            cache.save(cacheFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save diamond beam cache", ex);
        }
    }

    private static String serializeLocation(Location location) {
        return location.getWorld().getName() + ";"
                + location.getBlockX() + ";"
                + location.getBlockY() + ";"
                + location.getBlockZ();
    }

    private static Location parseLocation(String value) {
        String[] parts = value.split(";");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
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
}
