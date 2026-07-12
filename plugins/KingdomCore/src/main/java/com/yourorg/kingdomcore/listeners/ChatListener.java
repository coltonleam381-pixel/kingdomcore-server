package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.gui.MenuFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
    private final Plugin plugin;
    private final MenuFactory menuFactory;

    public ChatListener(Plugin plugin, MenuFactory menuFactory) {
        this.plugin = plugin;
        this.menuFactory = menuFactory;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!menuFactory.isRevivePending(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null || !online.isOnline()) {
                return;
            }
            menuFactory.handleReviveChat(online, message);
        });
    }
}
