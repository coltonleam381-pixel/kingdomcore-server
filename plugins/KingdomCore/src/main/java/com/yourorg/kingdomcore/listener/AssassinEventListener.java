package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.AssassinEventService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class AssassinEventListener implements Listener {

    private final AssassinEventService assassinEventService;
    private final com.yourorg.kingdomcore.integrations.WorldGuardHook worldGuardHook;
    private final String spawnRegion;

    public AssassinEventListener(AssassinEventService assassinEventService,
                                 com.yourorg.kingdomcore.integrations.WorldGuardHook worldGuardHook,
                                 String spawnRegion) {
        this.assassinEventService = assassinEventService;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegion = spawnRegion;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (assassinEventService.isActive()) {
            assassinEventService.tryJoin(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        assassinEventService.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        AssassinEventService.DeathHandling handling = assassinEventService.handleDeath(victim, killer);
        if (handling == AssassinEventService.DeathHandling.NOT_IN_EVENT) {
            return;
        }
        if (handling == AssassinEventService.DeathHandling.WRONG_DEATH) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.deathMessage(null);
            return;
        }
        if (handling == AssassinEventService.DeathHandling.ENVIRONMENTAL
                || handling == AssassinEventService.DeathHandling.CORRECT_KILL
                || handling == AssassinEventService.DeathHandling.DIED_TO_YOUR_TARGET) {
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        blockSpawnEntry(event.getPlayer(), event.getFrom(), event.getTo(), () -> event.setTo(event.getFrom()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        blockSpawnEntry(event.getPlayer(), event.getFrom(), event.getTo(), () -> event.setCancelled(true));
    }

    private void blockSpawnEntry(Player player, org.bukkit.Location from, org.bukkit.Location to, Runnable cancel) {
        if (!assassinEventService.isParticipant(player.getUniqueId())) {
            return;
        }
        if (!worldGuardHook.isAvailable()) {
            return;
        }
        boolean wasInSpawn = worldGuardHook.isInRegion(from, spawnRegion);
        boolean enteringSpawn = worldGuardHook.isInRegion(to, spawnRegion);
        if (!wasInSpawn && enteringSpawn) {
            cancel.run();
            player.sendActionBar("§cSpawn is locked during the Assassin Event");
        }
    }
}
