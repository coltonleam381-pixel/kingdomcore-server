package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.MonumentService;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetMonumentCommand implements CommandExecutor {

    private final MonumentService monumentService;

    public SetMonumentCommand(MonumentService monumentService) {
        this.monumentService = monumentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (!player.hasPermission("kingdomcore.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /ksetmonument <item_id>");
            return true;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§cYou must be looking at a block!");
            return true;
        }

        monumentService.setGlassLocation(args[0].toLowerCase(), target.getLocation());
        player.sendMessage("§aSet monument glass location for " + args[0] + "!");

        return true;
    }
}
