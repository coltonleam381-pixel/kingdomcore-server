package com.yourorg.kingdomcore.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public final class CombatLogoutInventory {
    private CombatLogoutInventory() {
    }

    public static void dropAndClear(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        Location dropAt = location.clone();
        World world = dropAt.getWorld();
        PlayerInventory inventory = player.getInventory();

        List<ItemStack> toDrop = new ArrayList<>();
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                toDrop.add(stack.clone());
            }
        }

        inventory.clear();

        for (ItemStack stack : toDrop) {
            world.dropItemNaturally(dropAt, stack);
        }
    }
}
