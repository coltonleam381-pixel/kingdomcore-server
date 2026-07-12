package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * Bridges SPACE (toggle flight) to ability handlers for abilities that use onSpace().
 */
public class SpaceAbilityListener implements Listener {
    private final AbilityService abilityService;
    private final HeartService heartService;

    public SpaceAbilityListener(AbilityService abilityService, HeartService heartService) {
        this.abilityService = abilityService;
        this.heartService = heartService;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        PlayerState state = heartService.getOrCreateState(player.getUniqueId(), player.getName());
        if (state == null || state.getAbilityId() == null || state.getAbilityLevel() <= 0) {
            return;
        }

        boolean handled = abilityService.handleSpace(player, state.getAbilityId());
        if (handled) {
            event.setCancelled(true);
            player.setFlying(false);
        }
    }
}

