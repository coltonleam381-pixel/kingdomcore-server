package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.events.AbilityCooldownResetEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetAbilityCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kingdomcore.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /kresetability <player> [item|all]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        String item = args.length > 1 ? args[1].toLowerCase() : "all";

        AbilityCooldownResetEvent event = new AbilityCooldownResetEvent(target, item);
        Bukkit.getPluginManager().callEvent(event);

        sender.sendMessage("§aReset ability cooldowns for " + target.getName() + " (item: " + item + ").");
        return true;
    }
}
