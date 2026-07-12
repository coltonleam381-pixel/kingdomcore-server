package com.yourorg.kingdomcore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Safety guard: let specific users use `/tp`, but block `/gamemode`-related commands.
 *
 * Why: If we grant them OP permission to use `/tp`, vanilla also unlocks `/gamemode`.
 * This cancels only gamemode for the targeted user.
 */
public class OpGamemodeBlockListener implements Listener {
    private static final String TARGET_NAME = "yosaifmrj_";

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!player.getName().equalsIgnoreCase(TARGET_NAME)) {
            return;
        }

        // event.getMessage() is the full command line, e.g. "/gamemode 1 Steve"
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) {
            return;
        }
        String withoutSlash = msg.startsWith("/") ? msg.substring(1) : msg;
        String label = withoutSlash.split("\\s+", 2)[0].toLowerCase();

        if (label.equals("gamemode") || label.equals("gm")) {
            event.setCancelled(true);
            player.sendMessage("§cYou can use `/tp`, but `/gamemode` is disabled for your access.");
        }
    }
}

