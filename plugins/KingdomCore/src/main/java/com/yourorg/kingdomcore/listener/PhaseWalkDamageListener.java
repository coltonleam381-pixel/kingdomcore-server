package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.PhaseWalkService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Listener to provide damage immunity while a player is in phase walk.
 */
public class PhaseWalkDamageListener implements Listener {
    private final PhaseWalkService phaseWalkService;

    public PhaseWalkDamageListener(PhaseWalkService phaseWalkService) {
        this.phaseWalkService = phaseWalkService;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Only handle damage to players
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        // Cancel damage if player is in phase walk
        if (phaseWalkService.isInPhase(player)) {
            event.setCancelled(true);
        }
    }
}
