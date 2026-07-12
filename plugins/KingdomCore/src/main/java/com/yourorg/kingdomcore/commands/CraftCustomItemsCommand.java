package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CraftCustomItemsCommand implements CommandExecutor {

    private final ItemIdentityService itemIdentityService;

    public CraftCustomItemsCommand(ItemIdentityService itemIdentityService) {
        this.itemIdentityService = itemIdentityService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("kingdomcore.admin")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        // Vanilla Ingredients
        give(player, Material.DIAMOND_BLOCK, 64);
        give(player, Material.OBSIDIAN, 64);
        give(player, Material.NETHERITE_INGOT, 16);
        give(player, Material.NETHER_STAR, 5);
        give(player, Material.WITHER_ROSE, 10);
        give(player, Material.PURPLE_CANDLE, 5);
        give(player, Material.WIND_CHARGE, 5);
        give(player, Material.CREEPER_HEAD, 5);
        give(player, Material.CAMPFIRE, 5);
        give(player, Material.HEAVY_CORE, 5);
        give(player, Material.BREEZE_ROD, 5);
        give(player, Material.ECHO_SHARD, 10);
        give(player, Material.SCULK_SHRIEKER, 5);
        give(player, Material.GOLD_BLOCK, 32);
        give(player, Material.BEACON, 5);
        give(player, Material.HEART_OF_THE_SEA, 5);
        give(player, Material.ENCHANTED_GOLDEN_APPLE, 5);
        give(player, Material.TRIDENT, 1);
        give(player, Material.REDSTONE_BLOCK, 64);
        give(player, Material.EMERALD_BLOCK, 5);
        give(player, Material.WITHER_SKELETON_SKULL, 5);

        // Custom Items
        ItemStack hearts = itemIdentityService.createHeartItem(16);
        if (hearts != null) {
            java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(hearts);
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        player.sendMessage("§aYou have been given all the ingredients to craft the custom items!");
        return true;
    }

    private void give(Player player, Material material, int amount) {
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
