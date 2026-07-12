package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.HealthService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * Prevents natural regen / food healing from exceeding the player's progression max health.
 */
public class HeartHealthCapListener implements Listener {
    private final HeartService heartService;
    private final HealthService healthService;

    public HeartHealthCapListener(HeartService heartService, HealthService healthService) {
        this.heartService = heartService;
        this.healthService = healthService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerState state = heartService.getOrCreateState(player.getUniqueId(), player.getName());
        double maxHealth = healthService.resolveMaxHealth(player, state);
        double current = player.getHealth();
        if (current >= maxHealth) {
            event.setCancelled(true);
            return;
        }
        double allowed = maxHealth - current;
        if (event.getAmount() > allowed) {
            event.setAmount(allowed);
        }
    }
}
