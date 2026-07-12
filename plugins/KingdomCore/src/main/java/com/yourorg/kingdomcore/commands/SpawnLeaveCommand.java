package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.SpawnLeaveLockService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpawnLeaveCommand implements CommandExecutor {

    private final SpawnLeaveLockService spawnLeaveLockService;

    public SpawnLeaveCommand(SpawnLeaveLockService spawnLeaveLockService) {
        this.spawnLeaveLockService = spawnLeaveLockService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage("§cOnly operators can use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /spawnleave <on|off|status>");
            return true;
        }

        String action = args[0].toLowerCase();
        if (action.equals("status")) {
            sender.sendMessage("§7Spawn leave lock is currently "
                    + (spawnLeaveLockService.isEnabled() ? "§aON" : "§cOFF") + "§7.");
            return true;
        }

        boolean targetEnabled;
        if (action.equals("on") || action.equals("true") || action.equals("enable")) {
            targetEnabled = true;
        } else if (action.equals("off") || action.equals("false") || action.equals("disable")) {
            targetEnabled = false;
        } else {
            sender.sendMessage("§cUsage: /spawnleave <on|off|status>");
            return true;
        }

        spawnLeaveLockService.setEnabled(targetEnabled);
        if (targetEnabled) {
            sender.sendMessage("§aSpawn leave lock enabled. §7Players cannot walk or teleport out of spawn regions.");
        } else {
            sender.sendMessage("§cSpawn leave lock disabled. §7Players can leave spawn normally.");
        }
        return true;
    }
}
