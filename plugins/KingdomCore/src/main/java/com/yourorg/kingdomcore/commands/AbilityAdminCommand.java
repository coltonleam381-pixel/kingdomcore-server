package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.core.services.AbilityOwnershipService;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class AbilityAdminCommand implements CommandExecutor {
    private final AbilityService abilityService;
    private final AbilityOwnershipService abilityOwnershipService;
    private final HeartService heartService;

    public AbilityAdminCommand(AbilityService abilityService,
                               AbilityOwnershipService abilityOwnershipService,
                               HeartService heartService) {
        this.abilityService = abilityService;
        this.abilityOwnershipService = abilityOwnershipService;
        this.heartService = heartService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !"admin".equalsIgnoreCase(args[0])) {
            return false;
        }
        String sub = args[1].toLowerCase();
        String targetName = args[2];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID uuid = target.getUniqueId();
        heartService.getOrCreateState(uuid, targetName);

        switch (sub) {
            case "reset" -> {
                abilityOwnershipService.resetPlayer(uuid);
                sender.sendMessage("§aReset ability for §f" + targetName + "§a.");
                return true;
            }
            case "release" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /ability admin release <ability_id>");
                    return true;
                }
                String abilityId = args[3].toLowerCase();
                AbilityDefinition ability = abilityService.getAbility(abilityId);
                if (ability == null) {
                    sender.sendMessage("§cUnknown ability: §f" + abilityId);
                    return true;
                }
                abilityOwnershipService.resetOwner(abilityId);
                sender.sendMessage("§aReleased §f" + ability.name() + "§a — no one owns it now.");
                return true;
            }
            case "set" -> {
                if (args.length < 4) {
                    return false;
                }
                String abilityId = args[3];
                AbilityDefinition ability = abilityService.getAbility(abilityId);
                if (ability == null) {
                    return true;
                }
                abilityOwnershipService.forceAssignAbility(uuid, abilityId);
                return true;
            }
            case "setlevel" -> {
                if (args.length < 4) {
                    return false;
                }
                int level;
                try {
                    level = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    return true;
                }
                if (level < 0 || level > 3) {
                    return true;
                }
                heartService.updateAbilityLevel(uuid, level);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
