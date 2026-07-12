package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.service.SpawnLeaveLockService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

/**
 * When spawn leave lock is on, players inside WorldGuard spawn regions cannot exit them.
 */
public class SpawnLeaveListener implements Listener {

    private final SpawnLeaveLockService spawnLeaveLockService;
    private final WorldGuardHook worldGuardHook;
    private final List<String> spawnRegions;

    public SpawnLeaveListener(SpawnLeaveLockService spawnLeaveLockService,
                              WorldGuardHook worldGuardHook,
                              List<String> spawnRegions) {
        this.spawnLeaveLockService = spawnLeaveLockService;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegions = spawnRegions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        blockSpawnLeave(event.getPlayer(), event.getFrom(), event.getTo(), () -> event.setTo(event.getFrom()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        blockSpawnLeave(event.getPlayer(), event.getFrom(), event.getTo(), () -> event.setCancelled(true));
    }

    private void blockSpawnLeave(Player player, Location from, Location to, Runnable cancel) {
        if (!spawnLeaveLockService.isEnabled()) {
            return;
        }
        if (player.isOp()) {
            return;
        }
        if (!worldGuardHook.isAvailable()) {
            return;
        }
        if (to == null) {
            return;
        }

        boolean wasInSpawn = worldGuardHook.isInAnyRegion(from, spawnRegions);
        boolean leavingSpawn = wasInSpawn && !worldGuardHook.isInAnyRegion(to, spawnRegions);
        if (leavingSpawn) {
            cancel.run();
            player.sendActionBar("§cYou cannot leave spawn right now.");
        }
    }
}
