package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.service.UniqueItemService;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.yourorg.kingdomcore.util.NpcOnlyCommands;

import java.util.HashMap;
import java.util.Map;

public class UniqueCraftCommand implements CommandExecutor {

    private final UniqueItemService uniqueItemService;
    private final ItemIdentityService itemIdentityService;
    private final HeartService heartService;
    private final com.yourorg.kingdomcore.core.KingdomConfig config;

    public UniqueCraftCommand(UniqueItemService uniqueItemService, ItemIdentityService itemIdentityService, HeartService heartService, com.yourorg.kingdomcore.core.KingdomConfig config) {
        this.uniqueItemService = uniqueItemService;
        this.itemIdentityService = itemIdentityService;
        this.heartService = heartService;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /kcraft <item> [player]");
            return true;
        }

        Player player;
        if (args.length >= 2) {
            boolean console = sender instanceof org.bukkit.command.ConsoleCommandSender;
            if (!console && !sender.hasPermission("kingdomcore.admin")) {
                sender.sendMessage("§cNo permission to craft for other players.");
                return true;
            }
            player = org.bukkit.Bukkit.getPlayerExact(args[1]);
            if (player == null) {
                for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (online.getName().equalsIgnoreCase(args[1])) {
                        player = online;
                        break;
                    }
                }
            }
            if (player == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this without specifying a target.");
                return true;
            }
            player = (Player) sender;
            if (NpcOnlyCommands.denyUnlessAllowed(player)) {
                return true;
            }
        }

        String item = args[0].toLowerCase();
        
        if (item.equals("heart")) {
            if (heartService.getOrCreateState(player.getUniqueId(), player.getName()).getProgressionHearts() >= config.getCraftMaxHearts()) {
                player.sendMessage("§cYou have reached the maximum of " + config.getCraftMaxHearts() + " hearts and can no longer craft them. You must kill players to get more.");
                return true;
            }
            Map<Material, Integer> req = Map.of(
                    Material.DIAMOND_BLOCK, 6,
                    Material.OBSIDIAN, 20,
                    Material.NETHERITE_INGOT, 2,
                    Material.TRIAL_KEY, 2
            );
            if (!hasIngredients(player, req, 0)) {
                player.sendMessage("§cNot enough ingredients! You need: 6 Diamond Blocks, 20 Obsidian, 2 Netherite Ingots, 2 Trial Keys.");
                return true;
            }
            takeIngredients(player, req, 0);
            player.getInventory().addItem(itemIdentityService.createHeartItem(1));
            player.sendMessage("§aYou crafted a Custom Heart!");
            return true;
        }

        if (item.equals("revive_beacon")) {
            Map<Material, Integer> req = Map.of(
                    Material.GOLD_BLOCK, 2,
                    Material.BEACON, 1,
                    Material.NETHERITE_INGOT, 3
            );
            if (!hasIngredients(player, req, 3)) {
                player.sendMessage("§cNot enough ingredients! You need: 2 Gold Blocks, 1 Beacon, 3 Netherite Ingots, 3 Custom Hearts.");
                return true;
            }
            takeIngredients(player, req, 3);
            player.getInventory().addItem(itemIdentityService.createReviveBeacon(1));
            player.sendMessage("§aYou crafted a Revival Beacon!");
            return true;
        }

        // Unique Items
        synchronized (uniqueItemService) {
            if (!uniqueItemService.canCraft(item)) {
                if (uniqueItemService.isPresent(item)) {
                    player.sendMessage("§cThis item is currently active in the world and cannot be crafted!");
                } else {
                    long remainingMs = uniqueItemService.getRemainingMs(item);
                    long hours = remainingMs / (1000 * 60 * 60);
                    long minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60);
                    player.sendMessage("§cThis item is on cooldown! Time remaining: " + hours + "h " + minutes + "m.");
                }
                return true;
            }

            Map<Material, Integer> req = new HashMap<>();
            int customHeartsReq = 0;
            String missingMsg = "";

            switch (item) {
                case "mace":
                    req.put(Material.PURPLE_CANDLE, 2);
                    req.put(Material.WIND_CHARGE, 2);
                    req.put(Material.CREEPER_HEAD, 1);
                    req.put(Material.CAMPFIRE, 2);
                    req.put(Material.HEAVY_CORE, 1);
                    req.put(Material.BREEZE_ROD, 1);
                    missingMsg = "2 Purple Candles, 2 Wind Charges, 1 Creeper Head, 2 Campfires, 1 Heavy Core, 1 Breeze Rod";
                    break;
                case "scythe":
                    req.put(Material.WITHER_SKELETON_SKULL, 2);
                    req.put(Material.WITHER_ROSE, 4);
                    req.put(Material.NETHERITE_INGOT, 2);
                    customHeartsReq = 2;
                    missingMsg = "2 Wither Skeleton Skulls, 4 Wither Roses, 2 Netherite Ingots, 2 Custom Hearts";
                    break;
                case "trident":
                    req.put(Material.HEART_OF_THE_SEA, 2);
                    req.put(Material.NETHERITE_INGOT, 2);
                    req.put(Material.ENCHANTED_GOLDEN_APPLE, 1);
                    req.put(Material.TRIDENT, 1);
                    customHeartsReq = 3;
                    missingMsg = "2 Hearts of the Sea, 2 Netherite Ingots, 1 Enchanted Golden Apple, 1 Trident, 3 Custom Hearts";
                    break;
                case "warden_cp":
                    req.put(Material.ECHO_SHARD, 3);
                    req.put(Material.SCULK_SHRIEKER, 2);
                    req.put(Material.NETHER_STAR, 1);
                    customHeartsReq = 2;
                    missingMsg = "3 Echo Shards, 2 Sculk Shriekers, 1 Nether Star, 2 Custom Hearts";
                    break;
                case "crown":
                    req.put(Material.REDSTONE_BLOCK, 20);
                    req.put(Material.EMERALD_BLOCK, 1);
                    req.put(Material.DIAMOND_BLOCK, 30);
                    req.put(Material.GOLD_BLOCK, 10);
                    customHeartsReq = 3;
                    missingMsg = "20 Redstone Blocks, 1 Emerald Block, 30 Diamond Blocks, 10 Gold Blocks, 3 Custom Hearts";
                    break;
                default:
                    player.sendMessage("§cUnknown unique item.");
                    return true;
            }

            if (!hasIngredients(player, req, customHeartsReq)) {
                player.sendMessage("§cNot enough ingredients! You need: " + missingMsg);
                return true;
            }

            takeIngredients(player, req, customHeartsReq);
            uniqueItemService.markCrafted(item);
        }
        
        ItemStack result = null;
        switch (item) {
            case "mace": result = itemIdentityService.createMaceItem(); break;
            case "scythe": result = itemIdentityService.createScytheItem(); break;
            case "trident": result = itemIdentityService.createTridentItem(); break;
            case "warden_cp": result = itemIdentityService.createWardenCpItem(); break;
            case "crown": result = itemIdentityService.createCrownItem(); break;
        }

        if (result != null) {
            player.getInventory().addItem(result);
            player.sendMessage("§aYou successfully crafted the " + item + "!");
        }

        return true;
    }

    private boolean hasIngredients(Player player, Map<Material, Integer> materials, int customHearts) {
        for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
            if (!player.getInventory().contains(entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        if (customHearts > 0) {
            int heartsFound = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && itemIdentityService.isHeartItem(item)) {
                    heartsFound += item.getAmount();
                }
            }
            if (heartsFound < customHearts) return false;
        }
        return true;
    }

    private void takeIngredients(Player player, Map<Material, Integer> materials, int customHearts) {
        for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
            player.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue()));
        }
        if (customHearts > 0) {
            int toRemove = customHearts;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && itemIdentityService.isHeartItem(item)) {
                    if (item.getAmount() <= toRemove) {
                        toRemove -= item.getAmount();
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(item.getAmount() - toRemove);
                        toRemove = 0;
                    }
                    if (toRemove <= 0) break;
                }
            }
        }
    }
}
