package com.yourorg.kingdomcore.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class InventoryFit {
    private InventoryFit() {
    }

    public static boolean canFit(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        int remaining = stack.getAmount();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (ItemStack slot : storage) {
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            if (slot.isSimilar(stack)) {
                remaining -= slot.getMaxStackSize() - slot.getAmount();
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        for (ItemStack slot : storage) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= stack.getMaxStackSize();
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return remaining <= 0;
    }
}
