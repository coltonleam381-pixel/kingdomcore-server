package com.yourorg.kingdomcore.listener;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class DimensionListener implements Listener {
    private final JavaPlugin plugin;

    public DimensionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            boolean netherOpen = plugin.getConfig().getBoolean("dimensions.nether.open", false);
            if (!netherOpen) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cThe Nether is currently closed!");
            }
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            boolean endOpen = plugin.getConfig().getBoolean("dimensions.end.open", false);
            if (!endOpen) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cThe End is currently closed!");
            }
        }
    }
}
