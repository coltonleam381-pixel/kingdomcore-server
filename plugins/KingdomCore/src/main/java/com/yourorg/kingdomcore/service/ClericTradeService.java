package com.yourorg.kingdomcore.service;

import com.yourorg.kingdomcore.listener.ClericTradeListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClericTradeService {
    private final JavaPlugin plugin;
    private ClericTradeListener listener;

    public ClericTradeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("villager-trades.cleric.blaze-rod-for-emerald.enabled", true)) {
            return;
        }
        listener = new ClericTradeListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, listener::patchAllLoadedClerics, 100L);
        Bukkit.getScheduler().runTaskLater(plugin, listener::patchAllLoadedClerics, 600L);
        Bukkit.getScheduler().runTaskLater(plugin, listener::patchAllLoadedClerics, 1200L);
        plugin.getLogger().info("Cleric blaze rod trade enabled ("
                + plugin.getConfig().getInt("villager-trades.cleric.blaze-rod-for-emerald.emeralds", 32)
                + " emeralds -> "
                + plugin.getConfig().getInt("villager-trades.cleric.blaze-rod-for-emerald.blaze-rods", 1)
                + " blaze rod).");
    }

    public void stop() {
        if (listener != null) {
            ClericTradeListener active = listener;
            listener = null;
            org.bukkit.event.HandlerList.unregisterAll(active);
        }
    }
}
