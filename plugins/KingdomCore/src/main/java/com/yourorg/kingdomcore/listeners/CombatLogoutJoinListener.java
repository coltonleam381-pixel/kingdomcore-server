package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Reminds players who combat-logged that their items were already dropped at logout.
 * Inventory drop and heart penalty happen on quit, not on reconnect.
 */
public class CombatLogoutJoinListener implements Listener {
    private final PlayerStateRepository playerStateRepository;

    public CombatLogoutJoinListener(PlayerStateRepository playerStateRepository) {
        this.playerStateRepository = playerStateRepository;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long pendingAt = playerStateRepository.getCombatLogPendingAt(player.getUniqueId());
        if (pendingAt <= 0L) {
            return;
        }

        playerStateRepository.clearCombatLogPending(player.getUniqueId());
        player.sendMessage("§cYou combat logged while tagged. Your inventory was dropped where you logged out and you lost §c1 heart§c.");
    }
}
