package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.HealthService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Listens for crown armor changes and recalculates max health with crown bonus.
 * Works with inventory events to detect crown equip/unequip across Paper versions.
 */
public class ArmorListener implements Listener {
    private final HeartService heartService;
    private final HealthService healthService;
    private final ItemIdentityService itemIdentityService;
    private final org.bukkit.plugin.Plugin plugin;

    public ArmorListener(org.bukkit.plugin.Plugin plugin, HeartService heartService, HealthService healthService, ItemIdentityService itemIdentityService) {
        this.plugin = plugin;
        this.heartService = heartService;
        this.healthService = healthService;
        this.itemIdentityService = itemIdentityService;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        ItemStack oldItem = event.getOldItem();
        ItemStack newItem = event.getNewItem();
        
        if ((oldItem != null && itemIdentityService.isCrownItem(oldItem)) ||
            (newItem != null && itemIdentityService.isCrownItem(newItem))) {
            
            org.bukkit.entity.Player player = event.getPlayer();
            
            // Schedule recalc for next tick after inventory updates
            org.bukkit.Bukkit.getScheduler().scheduleSyncDelayedTask(
                plugin,
                () -> {
                    PlayerState state = heartService.getOrCreateState(player.getUniqueId(), player.getName());
                    healthService.applyCrownBonus(player, state);
                },
                1L
            );
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        // Detect crown drop from armor
        if (itemIdentityService.isCrownItem(event.getItemDrop().getItemStack())) {
            PlayerState state = heartService.getOrCreateState(event.getPlayer().getUniqueId(), event.getPlayer().getName());
            healthService.applyCrownBonus(event.getPlayer(), state);
        }
    }
}
