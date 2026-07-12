package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.impl.DiamondBeamServiceImpl;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class DiamondBeamListener implements Listener {

    private final DiamondBeamServiceImpl diamondBeamService;

    public DiamondBeamListener(DiamondBeamServiceImpl diamondBeamService) {
        this.diamondBeamService = diamondBeamService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.DIAMOND_BLOCK) {
            return;
        }
        diamondBeamService.addSource(event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.DIAMOND_BLOCK) {
            return;
        }
        diamondBeamService.removeSource(event.getBlock().getLocation());
    }
}
