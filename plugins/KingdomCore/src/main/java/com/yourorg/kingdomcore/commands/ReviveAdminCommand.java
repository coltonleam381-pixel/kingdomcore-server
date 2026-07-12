package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.services.ReviveService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReviveAdminCommand implements CommandExecutor {
    private final ReviveService reviveService;

    public ReviveAdminCommand(ReviveService reviveService) {
        this.reviveService = reviveService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !"admin".equalsIgnoreCase(args[0]) || !"unblock".equalsIgnoreCase(args[1])) {
            return false;
        }
        reviveService.unblockPlayer(args[2]);
        return true;
    }
}
