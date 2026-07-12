package com.yourorg.kingdomcore.listener;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Deletes mistaken duplicate FancyHolograms (monument_*) from memory, disk, and world.
 */
public final class MonumentDuplicateHologramRemover {

    private static final Set<String> DUPLICATE_HOLOGRAM_NAMES = Set.of(
            "monument_mace",
            "monument_scythe",
            "monument_trident",
            "monument_crown",
            "monument_warden_cp"
    );

    private MonumentDuplicateHologramRemover() {
    }

    public static void register(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
            plugin.getLogger().info("FancyHolograms not loaded; duplicate hologram purge disabled.");
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Class<? extends org.bukkit.event.Event> loadedEvent = (Class<? extends org.bukkit.event.Event>) Class
                    .forName("de.oliver.fancyholograms.api.events.HologramsLoadedEvent");
            org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {
            };
            plugin.getServer().getPluginManager().registerEvent(
                    loadedEvent,
                    listener,
                    org.bukkit.event.EventPriority.MONITOR,
                    (unused, event) -> Bukkit.getScheduler().runTask(plugin, () -> purge(plugin)),
                    plugin,
                    true
            );
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("FancyHolograms loaded event not found; using delayed purge only.");
        }

        for (long delay : new long[] {40L, 120L, 300L, 600L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> purge(plugin), delay);
        }
    }

    public static int purge(Plugin plugin) {
        int removedFromYaml = stripDuplicateEntriesFromYaml(plugin);
        int removedFromPlugin = removeViaFancyHologramsApi(plugin);
        if (removedFromYaml > 0 || removedFromPlugin > 0) {
            plugin.getLogger().info("Purged duplicate monument holograms (yaml=" + removedFromYaml
                    + ", fancyholograms=" + removedFromPlugin + ").");
        }
        return removedFromYaml + removedFromPlugin;
    }

    private static int stripDuplicateEntriesFromYaml(Plugin plugin) {
        Path yaml = Path.of(plugin.getServer().getPluginsFolder().getAbsolutePath(),
                "FancyHolograms", "holograms.yml");
        if (!Files.isRegularFile(yaml)) {
            return 0;
        }
        try {
            String original = Files.readString(yaml, StandardCharsets.UTF_8);
            String cleaned = original;
            int removed = 0;
            for (String name : DUPLICATE_HOLOGRAM_NAMES) {
                String pattern = "(?s)\\n  " + name + ":.*?(?=\\n  [A-Za-z]|\\z)";
                String next = cleaned.replaceAll(pattern, "\n");
                if (!next.equals(cleaned)) {
                    removed++;
                    cleaned = next;
                }
            }
            if (removed == 0) {
                return 0;
            }
            Files.writeString(yaml, cleaned, StandardCharsets.UTF_8);
            reloadFancyHolograms(plugin);
            return removed;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to strip duplicate holograms from yaml", ex);
            return 0;
        }
    }

    private static int removeViaFancyHologramsApi(Plugin plugin) {
        try {
            Class<?> apiClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin");
            Method getPlugin = apiClass.getMethod("get");
            Object fancyPlugin = getPlugin.invoke(null);
            if (fancyPlugin == null) {
                return 0;
            }

            Object manager = apiClass.getMethod("getHologramManager").invoke(fancyPlugin);
            Method getHologram = manager.getClass().getMethod("getHologram", String.class);
            Method removeHologram = manager.getClass().getMethod("removeHologram",
                    Class.forName("de.oliver.fancyholograms.api.hologram.Hologram"));

            int removed = 0;
            for (String name : DUPLICATE_HOLOGRAM_NAMES) {
                @SuppressWarnings("unchecked")
                Optional<Object> hologram = (Optional<Object>) getHologram.invoke(manager, name);
                if (hologram.isEmpty()) {
                    continue;
                }
                Object holo = hologram.get();
                holo.getClass().getMethod("deleteHologram").invoke(holo);
                removeHologram.invoke(manager, holo);
                removed++;
            }

            if (removed > 0) {
                manager.getClass().getMethod("saveHolograms").invoke(manager);
                reloadFancyHolograms(plugin);
            }
            return removed;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove duplicate holograms via FancyHolograms API", ex);
            return 0;
        }
    }

    private static void reloadFancyHolograms(Plugin plugin) {
        try {
            Class<?> apiClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin");
            Object fancyPlugin = apiClass.getMethod("get").invoke(null);
            if (fancyPlugin == null) {
                return;
            }
            Object manager = apiClass.getMethod("getHologramManager").invoke(fancyPlugin);
            manager.getClass().getMethod("reloadHolograms").invoke(manager);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "FancyHolograms reload skipped", ex);
        }
    }
}
