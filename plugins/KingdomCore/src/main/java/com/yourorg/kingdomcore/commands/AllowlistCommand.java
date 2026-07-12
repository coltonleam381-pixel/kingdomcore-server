package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.services.AllowlistService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AllowlistCommand implements CommandExecutor {
    private final AllowlistService allowlistService;

    public AllowlistCommand(AllowlistService allowlistService) {
        this.allowlistService = allowlistService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add" -> {
                if (args.length < 2) {
                    return false;
                }
                allowlistService.add(args[1]);
                return true;
            }
            case "remove" -> {
                if (args.length < 2) {
                    return false;
                }
                allowlistService.remove(args[1]);
                return true;
            }
            case "reload" -> {
                allowlistService.reload();
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
