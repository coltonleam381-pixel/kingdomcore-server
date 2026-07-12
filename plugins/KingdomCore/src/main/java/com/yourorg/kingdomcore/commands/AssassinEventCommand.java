package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.AssassinEventService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class AssassinEventCommand implements CommandExecutor {

    private final AssassinEventService assassinEventService;

    public AssassinEventCommand(AssassinEventService assassinEventService) {
        this.assassinEventService = assassinEventService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage("§cThis command can only be run from the server console.");
            return true;
        }
        AssassinEventService.StartResult result = assassinEventService.startEvent();
        switch (result) {
            case STARTED -> sender.sendMessage("Assassin event started.");
            case NOT_ENOUGH_PLAYERS -> sender.sendMessage(
                    "Assassin event not started: need at least 2 players online.");
            default -> sender.sendMessage("Assassin event not started.");
        }
        return true;
    }
}
