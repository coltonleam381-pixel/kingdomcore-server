package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.MonumentService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Removes old in-world TextDisplay recipe holograms baked into the map build.
 * FancyHolograms YAML holograms use updated text and are not matched as legacy.
 */
public class MonumentLegacyHologramCleaner implements Listener {

    private static final double HORIZONTAL_RADIUS = 22.0;
    private static final double VERTICAL_RADIUS = 8.0;

    private static final Set<String> DUPLICATE_HOLOGRAM_MARKERS = Set.of(
            "dragon egg",
            "sculk catalyst",
            "4x netherite block",
            "4x heavy core",
            "4x echo shard",
            "netherite chestplate recipe",
            "1x netherite chestplate",
            "prismarine brick",
            "netherite trident",
            "custom mace",
            "custom scythe",
            "custom trident",
            "custom heart",
            "warden chestplate",
            "&7recipe:",
            "recipe:"
    );

    private static final double MONUMENT_PLATFORM_X = -55.0;
    private static final double MONUMENT_PLATFORM_Y = 80.0;
    private static final double MONUMENT_PLATFORM_Z = -136.0;
    private static final double PLATFORM_RADIUS = 30.0;

    private final Plugin plugin;
    private final MonumentService monumentService;

    public MonumentLegacyHologramCleaner(Plugin plugin, MonumentService monumentService) {
        this.plugin = plugin;
        this.monumentService = monumentService;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        // FancyHolograms spawns TEXT holograms after other plugins; run several passes.
        for (long delay : new long[] {100L, 300L, 600L}) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::removeLegacyDisplays, delay);
        }
    }

    public int removeLegacyDisplays() {
        int removed = 0;
        removed += removeNearMonuments(monumentService.getMonumentLocations());
        removed += removeNearPlatform();
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " duplicate/legacy monument hologram(s).");
        }
        return removed;
    }

    private int removeNearMonuments(List<Location> locations) {
        int removed = 0;
        for (Location monument : locations) {
            if (monument.getWorld() == null) {
                continue;
            }
            removed += removeDisplaysNear(monument);
        }
        return removed;
    }

    private int removeNearPlatform() {
        org.bukkit.World world = plugin.getServer().getWorld("world");
        if (world == null) {
            return 0;
        }
        Location center = new Location(world, MONUMENT_PLATFORM_X, MONUMENT_PLATFORM_Y, MONUMENT_PLATFORM_Z);
        return removeDisplaysNear(center, PLATFORM_RADIUS, VERTICAL_RADIUS);
    }

    private int removeDisplaysNear(Location center) {
        return removeDisplaysNear(center, HORIZONTAL_RADIUS, VERTICAL_RADIUS);
    }

    private int removeDisplaysNear(Location center, double horizontalRadius, double verticalRadius) {
        int removed = 0;
        for (Entity entity : center.getWorld().getNearbyEntities(
                center, horizontalRadius, verticalRadius, horizontalRadius)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            String text = PlainTextComponentSerializer.plainText().serialize(display.text()).toLowerCase(Locale.ROOT);
            if (!isDuplicateHologramText(text)) {
                continue;
            }
            display.remove();
            removed++;
        }
        return removed;
    }

    private boolean isDuplicateHologramText(String plainText) {
        if (plainText.isBlank()) {
            return false;
        }
        // Keep the real recipe/status holograms (emoji headers, status lines).
        if (plainText.contains("status")
                || plainText.contains("craftable")
                || plainText.contains("currently active")
                || plainText.contains("the mace recipe")
                || plainText.contains("the scythe recipe")
                || plainText.contains("the crown recipe")
                || plainText.contains("poseidon trident recipe")
                || plainText.contains("warden chestplate recipe")) {
            return false;
        }
        for (String marker : DUPLICATE_HOLOGRAM_MARKERS) {
            if (plainText.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
