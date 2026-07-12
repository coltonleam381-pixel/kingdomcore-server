package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.core.services.WithdrawResult;
import com.yourorg.kingdomcore.abilities.AbilityScoreboard;
import com.yourorg.kingdomcore.util.WithdrawFeedback;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.Map;

public class HeartsCommand implements CommandExecutor {
    private final KingdomConfig config;
    private final HeartService heartService;
    private final ItemIdentityService itemIdentityService;
    private final AbilityScoreboard abilityScoreboard;

    public HeartsCommand(KingdomConfig config,
                         HeartService heartService,
                         ItemIdentityService itemIdentityService,
                         AbilityScoreboard abilityScoreboard) {
        this.config = config;
        this.heartService = heartService;
        this.itemIdentityService = itemIdentityService;
        this.abilityScoreboard = abilityScoreboard;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length == 3 && ("give".equalsIgnoreCase(args[0]) || "add".equalsIgnoreCase(args[0]))) {
                return giveToTarget(sender, args[1], args[2]);
            }
            if (args.length == 2) {
                return giveToTarget(sender, args[0], args[1]);
            }
            sender.sendMessage("Usage: /hearts [amount] | /hearts give|add <player> <amount> | /hearts withdraw <amount>");
            return true;
        }

        if (args.length == 3 && ("give".equalsIgnoreCase(args[0]) || "add".equalsIgnoreCase(args[0]))) {
            return giveToTarget(sender, args[1], args[2]);
        }

        if (args.length == 2 && !"withdraw".equalsIgnoreCase(args[0])) {
            return giveToTarget(sender, args[0], args[1]);
        }

        if (args.length == 0) {
            giveToPlayer(player, 1);
            player.sendMessage("§aYou received §f1§a heart.");
            return true;
        }

        if (args.length == 1) {
            int amount;
            try {
                amount = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (amount <= 0) {
                player.sendMessage("§cAmount must be greater than 0.");
                return true;
            }
            giveToPlayer(player, amount);
            player.sendMessage("§aYou received §f" + amount + "§a hearts.");
            return true;
        }

        if (args.length < 2 || !"withdraw".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Usage: /hearts [amount] | /hearts give|add <player> <amount> | /hearts withdraw <amount>");
            return true;
        }
        if (!config.isWithdrawEnabled()) {
            sender.sendMessage("§cWithdraw is disabled.");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cAmount must be a number.");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage("§cAmount must be greater than 0.");
            return true;
        }
        WithdrawResult result = heartService.withdrawHearts(player, amount);
        WithdrawFeedback.apply(player, abilityScoreboard, result, amount);
        if (result == WithdrawResult.SUCCESS) {
            sender.sendMessage("§aWithdrew §f" + amount + "§a heart" + (amount == 1 ? "" : "s") + ".");
        }
        return true;
    }

    private boolean giveToTarget(CommandSender sender, String targetName, String amountRaw) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: §f" + targetName);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountRaw);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cAmount must be a number.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("§cAmount must be greater than 0.");
            return true;
        }

        giveToPlayer(target, amount);
        sender.sendMessage("§aGave §f" + amount + "§a hearts to §f" + target.getName() + "§a.");
        if (!target.equals(sender)) {
            target.sendMessage("§aYou received §f" + amount + "§a hearts.");
        }
        return true;
    }

    private void giveToPlayer(Player target, int amount) {
        ItemStack stack = itemIdentityService.createHeartItem(amount);
        if (stack == null) {
            target.sendMessage("§cHeart item is not configured correctly.");
            return;
        }

        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        }
    }
}
