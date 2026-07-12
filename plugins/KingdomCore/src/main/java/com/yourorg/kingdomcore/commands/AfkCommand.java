package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.AfkService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AfkCommand implements CommandExecutor {

    private final AfkService afkService;

    public AfkCommand(AfkService afkService) {
        this.afkService = afkService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (afkService.isAfk(player)) {
            player.sendMessage("§cYou are already AFK.");
            return true;
        }

        if (afkService.isWindup(player)) {
            player.sendMessage("§cYou are already entering AFK mode.");
            return true;
        }

        if (afkService.canUseAfkCommand(player)) {
            afkService.startWindup(player);
        }

        return true;
    }
}
