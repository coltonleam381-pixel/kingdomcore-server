package com.yourorg.kingdomcore.integrations;

import com.yourorg.kingdomcore.core.services.AbilityOwnershipService;
import com.yourorg.kingdomcore.service.UniqueItemService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

public final class PlaceholderRegistration {
    private static boolean registered;

    private PlaceholderRegistration() {
    }

    public static void registerWhenReady(Plugin plugin,
                                         UniqueItemService uniqueItemService,
                                         AbilityOwnershipService abilityOwnershipService) {
        Runnable register = () -> {
            if (registered) {
                return;
            }
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                return;
            }
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return;
            }
            new KingdomCoreExpansion(uniqueItemService, abilityOwnershipService).register();
            registered = true;
            plugin.getLogger().info("PlaceholderAPI expansion 'kingdomcore' registered.");
        };

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if ("PlaceholderAPI".equals(event.getPlugin().getName())) {
                    register.run();
                }
            }
        }, plugin);

        Bukkit.getScheduler().runTaskLater(plugin, register, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, register, 40L);
    }
}
