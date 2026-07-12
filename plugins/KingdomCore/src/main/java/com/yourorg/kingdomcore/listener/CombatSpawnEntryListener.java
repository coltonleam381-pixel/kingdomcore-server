package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.util.CombatRules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

/**
 * Blocks players from entering spawn regions while combat-tagged
 * or for 30 seconds after their combat tag expires.
 */
public class CombatSpawnEntryListener implements Listener {

    private final CombatTagService combatTagService;
    private final WorldGuardHook worldGuardHook;
    private final List<String> spawnRegions;
    private final CombatRules combatRules;

    public CombatSpawnEntryListener(CombatTagService combatTagService,
                                    WorldGuardHook worldGuardHook,
                                    List<String> spawnRegions,
                                    CombatRules combatRules) {
        this.combatTagService = combatTagService;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegions = spawnRegions;
        this.combatRules = combatRules;
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
        blockSpawnEntry(event.getPlayer(), event.getFrom(), event.getTo(), () -> event.setTo(event.getFrom()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        blockSpawnEntry(event.getPlayer(), event.getFrom(), event.getTo(), () -> event.setCancelled(true));
    }

    private void blockSpawnEntry(Player player, org.bukkit.Location from, org.bukkit.Location to, Runnable cancel) {
        if (!combatRules.shouldBlockCombatSpawnEntry(player)) {
            return;
        }
        if (!worldGuardHook.isAvailable()) {
            return;
        }
        if (!combatTagService.isSpawnEntryBlocked(player.getUniqueId())) {
            return;
        }
        boolean wasInSpawn = worldGuardHook.isInAnyRegion(from, spawnRegions);
        boolean enteringSpawn = worldGuardHook.isInAnyRegion(to, spawnRegions);
        if (!wasInSpawn && enteringSpawn) {
            cancel.run();
            player.sendMessage("§cYou cannot enter spawn while in combat or for 30s after combat ends.");
        }
    }
}
