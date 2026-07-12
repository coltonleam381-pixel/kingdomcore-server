package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class RemoveLevelCommand implements CommandExecutor {
    private final HeartService heartService;

    public RemoveLevelCommand(HeartService heartService) {
        this.heartService = heartService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        UUID targetId;
        String targetName;

        if (args.length >= 1) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            targetId = target.getUniqueId();
            targetName = target.getName() != null ? target.getName() : args[0];
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cUsage: /removelvl <player>");
                return true;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
        }

        heartService.getOrCreateState(targetId, targetName);
        heartService.updateAbilityLevel(targetId, 0);

        sender.sendMessage("§aAbility level reset to 0 for §f" + targetName + "§a.");
        if (Bukkit.getPlayer(targetId) != null) {
            Bukkit.getPlayer(targetId).sendMessage("§eYour ability level was reset to §f0§e.");
        }
        return true;
    }
}
