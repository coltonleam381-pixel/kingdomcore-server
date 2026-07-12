package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CooldownAdminCommand implements CommandExecutor {
    private final CooldownService cooldownService;
    private final HeartService heartService;
    private final AbilityService abilityService;

    public CooldownAdminCommand(CooldownService cooldownService,
                                HeartService heartService,
                                AbilityService abilityService) {
        this.cooldownService = cooldownService;
        this.heartService = heartService;
        this.abilityService = abilityService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !"skip".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Usage: /cd skip [player] [ability_id]");
            return true;
        }

        Player target = null;
        String abilityId = null;

        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: §f" + args[1]);
                return true;
            }
            if (args.length >= 3) {
                abilityId = args[2];
            }
        } else {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage("§cConsole must provide a player. Usage: /cd skip <player> [ability_id]");
                return true;
            }
            target = playerSender;
        }

        PlayerState state = heartService.getOrCreateState(target.getUniqueId(), target.getName());
        if (abilityId == null || abilityId.isBlank()) {
            abilityId = state.getAbilityId();
        }
        if (abilityId == null || abilityId.isBlank()) {
            sender.sendMessage("§cTarget has no ability selected.");
            return true;
        }
        if (abilityService.getAbility(abilityId) == null) {
            sender.sendMessage("§cUnknown ability id: §f" + abilityId);
            return true;
        }

        // Setting readyAt to now/earlier makes cooldown immediately ready.
        cooldownService.markUsed(target.getUniqueId(), abilityId, 0L);
        sender.sendMessage("§aCooldown skipped for §f" + target.getName() + "§a ability §f" + abilityId + "§a.");
        if (!target.equals(sender)) {
            target.sendMessage("§aYour cooldown was skipped for ability §f" + abilityId + "§a.");
        }
        return true;
    }
}
