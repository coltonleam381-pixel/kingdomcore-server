package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.VillagerEggGrantService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Silently delivers queued villager spawn eggs after login (no chat, titles, or commands). */
public final class VillagerEggGrantListener implements Listener {
    private final JavaPlugin plugin;
    private final VillagerEggGrantService grantService;

    public VillagerEggGrantListener(JavaPlugin plugin, VillagerEggGrantService grantService) {
        this.plugin = plugin;
        this.grantService = grantService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        // Delay until after PIN auth clears inventory lock.
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> grantService.deliverIfPending(event.getPlayer()),
                80L);
    }
}
