package com.yourorg.kingdomcore.service;

import com.yourorg.kingdomcore.listener.VillagerBedDistanceListener;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers villager bed distance handling (tick-end listener). */
public final class VillagerBedDistanceService {
    private final JavaPlugin plugin;
    private VillagerBedDistanceListener listener;

    public VillagerBedDistanceService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("villager-beds.enabled", true)) {
            return;
        }
        listener = new VillagerBedDistanceListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public void stop() {
        if (listener != null) {
            VillagerBedDistanceListener tickListener = listener;
            listener = null;
            org.bukkit.event.HandlerList.unregisterAll(tickListener);
        }
    }

    public void reload() {
        start();
    }
}
