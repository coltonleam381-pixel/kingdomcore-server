package com.yourorg.kingdomcore.service;

import com.yourorg.kingdomcore.listener.CartographerTradeListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CartographerTradeService {
    private final JavaPlugin plugin;
    private CartographerTradeListener listener;

    public CartographerTradeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("villager-trades.cartographer.glass-pane-for-emerald.enabled", true)) {
            return;
        }
        listener = new CartographerTradeListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, listener::patchAllLoadedCartographers, 100L);
        plugin.getLogger().info("Cartographer glass pane trade override enabled ("
                + plugin.getConfig().getInt("villager-trades.cartographer.glass-pane-for-emerald.glass-panes", 5)
                + " panes -> 1 emerald).");
    }

    public void stop() {
        if (listener != null) {
            CartographerTradeListener active = listener;
            listener = null;
            org.bukkit.event.HandlerList.unregisterAll(active);
        }
    }
}
