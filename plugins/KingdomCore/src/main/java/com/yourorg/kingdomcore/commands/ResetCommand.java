package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.UniqueItemService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ResetCommand implements CommandExecutor {

    private final UniqueItemService uniqueItemService;

    public ResetCommand(UniqueItemService uniqueItemService) {
        this.uniqueItemService = uniqueItemService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kingdomcore.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /kreset <item>");
            return true;
        }

        String item = args[0].toLowerCase();
        try {
            if (item.equals("all")) {
                uniqueItemService.resetAllItems();
                sender.sendMessage("§aSuccessfully reset the cooldown and presence for ALL custom items!");
            } else {
                uniqueItemService.resetItem(item);
                sender.sendMessage("§aSuccessfully reset the cooldown and presence for " + item + "!");
            }
        } catch (Exception e) {
            sender.sendMessage("§cFailed to reset item or unknown item.");
        }

        return true;
    }
}
