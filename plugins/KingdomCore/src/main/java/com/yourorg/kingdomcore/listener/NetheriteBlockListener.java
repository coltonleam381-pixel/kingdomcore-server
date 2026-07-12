package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

public class NetheriteBlockListener implements Listener {
    private final ItemIdentityService itemIdentityService;

    public NetheriteBlockListener(ItemIdentityService itemIdentityService) {
        this.itemIdentityService = itemIdentityService;
    }

    private boolean isVanillaNetheriteGear(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material type = item.getType();
        
        boolean isNetheriteGear = 
            type == Material.NETHERITE_HELMET || type == Material.NETHERITE_CHESTPLATE ||
            type == Material.NETHERITE_LEGGINGS || type == Material.NETHERITE_BOOTS ||
            type == Material.NETHERITE_SWORD || type == Material.NETHERITE_PICKAXE ||
            type == Material.NETHERITE_AXE || type == Material.NETHERITE_SHOVEL ||
            type == Material.NETHERITE_HOE;

        if (!isNetheriteGear) {
            return false;
        }

        // Check if it's a unique item. Unique items are allowed!
        if (itemIdentityService.isCrownItem(item) ||
            itemIdentityService.isMaceItem(item) ||
            itemIdentityService.isScytheItem(item) ||
            itemIdentityService.isWardenCpItem(item) ||
            itemIdentityService.isTridentItem(item)) {
            return false;
        }
        
        // Check if it has custom model data (ItemsAdder items usually have this)
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            return false;
        }

        return true;
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();

        if (isVanillaNetheriteGear(result)) {
            event.setResult(null); // Block crafting Netherite Gear
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isVanillaNetheriteGear(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cVanilla Netherite gear is disabled on this server!");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (isVanillaNetheriteGear(weapon)) {
                event.setCancelled(true);
                player.sendMessage("§cVanilla Netherite weapons are disabled on this server!");
            }
        }
    }
}
