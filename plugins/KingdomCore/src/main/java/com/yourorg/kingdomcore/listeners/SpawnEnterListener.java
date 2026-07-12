package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listener to handle Thor mark clearing when players enter spawn.
 */
public class SpawnEnterListener implements Listener {
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final AbilityService abilityService;

    public SpawnEnterListener(com.yourorg.kingdomcore.integrations.WorldGuardHook worldGuardHook,
                                SpawnRegionPolicy spawnRegionPolicy,
                                AbilityService abilityService) {
        this.spawnRegionPolicy = spawnRegionPolicy;
        this.abilityService = abilityService;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Optimization: only check if player crossed block boundary
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        boolean wasInSpawn = spawnRegionPolicy.isInSpawnArea(event.getFrom());
        boolean nowInSpawn = spawnRegionPolicy.isInSpawnArea(event.getTo());

        if (!wasInSpawn && nowInSpawn) {
            // Player entered spawn - cleanup Thor marks
            abilityService.cleanup(player);
        }
    }
}
