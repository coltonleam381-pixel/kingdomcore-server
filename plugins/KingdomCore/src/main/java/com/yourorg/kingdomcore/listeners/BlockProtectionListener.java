package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.integrations.CitizensHook;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.service.SpawnProtectionService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class BlockProtectionListener implements Listener {
    private final KingdomConfig config;
    private final WorldGuardHook worldGuardHook;
    private final CitizensHook citizensHook;
    private final SpawnProtectionService spawnProtectionService;

    public BlockProtectionListener(KingdomConfig config,
                                   WorldGuardHook worldGuardHook,
                                   CitizensHook citizensHook,
                                   SpawnProtectionService spawnProtectionService) {
        this.config = config;
        this.worldGuardHook = worldGuardHook;
        this.citizensHook = citizensHook;
        this.spawnProtectionService = spawnProtectionService;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!spawnProtectionService.isEnabled() || !worldGuardHook.isAvailable()) {
            return;
        }
        if (worldGuardHook.isInRegion(event.getBlock().getLocation(), config.getSpawnRegionName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!spawnProtectionService.isEnabled() || !worldGuardHook.isAvailable()) {
            return;
        }
        if (worldGuardHook.isInRegion(event.getBlock().getLocation(), config.getSpawnRegionName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!citizensHook.isAvailable()) {
            return;
        }
        // GUI-based NPC interactions were removed from KingdomCore.
    }
}
