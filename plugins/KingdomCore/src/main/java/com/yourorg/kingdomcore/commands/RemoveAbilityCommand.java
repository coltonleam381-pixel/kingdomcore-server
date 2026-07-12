package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.AbilityOwnershipService;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class RemoveAbilityCommand implements CommandExecutor {
    private final AbilityService abilityService;
    private final AbilityOwnershipService abilityOwnershipService;
    private final HeartService heartService;

    public RemoveAbilityCommand(AbilityService abilityService,
                                AbilityOwnershipService abilityOwnershipService,
                                HeartService heartService) {
        this.abilityService = abilityService;
        this.abilityOwnershipService = abilityOwnershipService;
        this.heartService = heartService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage("§cOnly operators can use this command.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cUsage: /removeability <player|ability> [ability]");
                return true;
            }
            removeAbility(sender, player.getUniqueId(), player.getName(), null);
            return true;
        }

        if (args.length == 1) {
            String arg = args[0].toLowerCase(Locale.ROOT);
            AbilityDefinition ability = abilityService.getAbility(arg);
            if (ability != null) {
                Optional<UUID> owner = abilityOwnershipService.getOwner(arg);
                if (owner.isEmpty()) {
                    abilityOwnershipService.resetOwner(arg);
                    sender.sendMessage("§eNobody currently has §f" + ability.name() + "§e. Ability slot freed.");
                    return true;
                }
                OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(owner.get());
                String ownerName = ownerPlayer.getName() != null ? ownerPlayer.getName() : owner.get().toString();
                removeAbility(sender, owner.get(), ownerName, arg);
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            String targetName = target.getName() != null ? target.getName() : args[0];
            removeAbility(sender, target.getUniqueId(), targetName, null);
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String abilityId = args[1].toLowerCase(Locale.ROOT);
        AbilityDefinition ability = abilityService.getAbility(abilityId);
        if (ability == null) {
            sender.sendMessage("§cUnknown ability: §f" + abilityId);
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : args[0];
        PlayerState state = heartService.getOrCreateState(target.getUniqueId(), targetName);
        String currentAbility = state.getAbilityId();
        if (currentAbility == null || currentAbility.isBlank()) {
            sender.sendMessage("§c" + targetName + " does not have an ability.");
            return true;
        }
        if (!currentAbility.equalsIgnoreCase(abilityId)) {
            sender.sendMessage("§c" + targetName + " has §f" + currentAbility + "§c, not §f" + abilityId + "§c.");
            return true;
        }

        removeAbility(sender, target.getUniqueId(), targetName, abilityId);
        return true;
    }

    private void removeAbility(CommandSender sender, UUID targetId, String targetName, String abilityId) {
        heartService.getOrCreateState(targetId, targetName);
        abilityOwnershipService.resetPlayer(targetId);

        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            abilityService.cleanup(online);
        }

        if (abilityId != null) {
            AbilityDefinition ability = abilityService.getAbility(abilityId);
            String abilityName = ability != null ? ability.name() : abilityId;
            sender.sendMessage("§aRemoved §f" + abilityName + " §afrom §f" + targetName + "§a.");
        } else {
            sender.sendMessage("§aAbility removed for §f" + targetName + "§a.");
        }

        if (online != null && !online.equals(sender)) {
            online.sendMessage("§eYour ability was removed by an admin.");
        }
    }
}
