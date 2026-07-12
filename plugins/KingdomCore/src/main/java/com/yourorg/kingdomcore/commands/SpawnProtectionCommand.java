package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.SpawnProtectionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpawnProtectionCommand implements CommandExecutor {

    private final SpawnProtectionService spawnProtectionService;

    public SpawnProtectionCommand(SpawnProtectionService spawnProtectionService) {
        this.spawnProtectionService = spawnProtectionService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage("§cOnly operators can use this command.");
            return true;
        }

        boolean targetEnabled;
        if (args.length == 0) {
            targetEnabled = !spawnProtectionService.isEnabled();
        } else {
            String action = args[0].toLowerCase();
            if (action.equals("on") || action.equals("true") || action.equals("enable")) {
                targetEnabled = true;
            } else if (action.equals("off") || action.equals("false") || action.equals("disable")) {
                targetEnabled = false;
            } else if (action.equals("status")) {
                sender.sendMessage("§7Spawn protection is currently "
                        + (spawnProtectionService.isEnabled() ? "§aON" : "§cOFF") + "§7.");
                return true;
            } else {
                sender.sendMessage("§cUsage: /spawnprotect [on|off|status]");
                return true;
            }
        }

        if (!spawnProtectionService.setEnabled(targetEnabled)) {
            sender.sendMessage("§cCould not update spawn protection. Is WorldGuard loaded?");
            return true;
        }

        String status = targetEnabled ? "§aenabled" : "§cdisabled";
        sender.sendMessage("§eSpawn protection has been " + status + "§e.");
        if (!targetEnabled) {
            sender.sendMessage("§7Spawn PvP, building, and abilities are allowed in spawn regions.");
            sender.sendMessage("§7Also run §f/pvp on now §7if world PVP is still off.");
        } else {
            sender.sendMessage("§7Abilities are blocked inside spawn regions again.");
        }
        return true;
    }
}
