package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.MonumentService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DelMonumentCommand implements CommandExecutor {

    private final MonumentService monumentService;

    public DelMonumentCommand(MonumentService monumentService) {
        this.monumentService = monumentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kingdomcore.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /kdelmonument <item_id>");
            return true;
        }

        monumentService.removeGlassLocation(args[0].toLowerCase());
        sender.sendMessage("§aRemoved monument location for " + args[0] + "!");

        return true;
    }
}
