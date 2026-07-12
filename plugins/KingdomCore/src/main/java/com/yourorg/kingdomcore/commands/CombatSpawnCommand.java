package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.KingdomCorePlugin;
import com.yourorg.kingdomcore.util.CombatRules;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Per-player bypass for combat spawn entry restrictions.
 */
public class CombatSpawnCommand implements CommandExecutor {

    private final KingdomCorePlugin plugin;
    private final CombatRules combatRules;

    public CombatSpawnCommand(KingdomCorePlugin plugin, CombatRules combatRules) {
        this.plugin = plugin;
        this.combatRules = combatRules;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage("§cOnly operators can use this command.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("status")) {
            if (args.length >= 2) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target.getUniqueId() == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                boolean bypass = combatRules.bypassesCombatSpawnEntry(target.getUniqueId());
                String name = target.getName() != null ? target.getName() : args[1];
                sender.sendMessage("§7Combat spawn for §f" + name + "§7: "
                        + (bypass ? "§aON (can enter spawn in combat)" : "§cOFF (normal)"));
                return true;
            }
            List<String> bypassed = combatRules.getCombatSpawnBypassNames();
            if (bypassed.isEmpty()) {
                sender.sendMessage("§7No players have combat spawn bypass.");
            } else {
                sender.sendMessage("§7Combat spawn bypass (enter spawn in combat): §f" + String.join(", ", bypassed));
            }
            if (combatRules.isOpSpawnEntryBypass()) {
                sender.sendMessage("§7Global OP spawn bypass: §aenabled");
            }
            return true;
        }

        boolean bypass;
        UUID targetId;
        String targetName;

        if (args.length >= 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            targetId = target.getUniqueId();
            targetName = target.getName() != null ? target.getName() : args[1];
            String action = args[0].toLowerCase();
            if (action.equals("on") || action.equals("allow") || action.equals("true")) {
                bypass = true;
            } else if (action.equals("off") || action.equals("block") || action.equals("false")) {
                bypass = false;
            } else {
                sender.sendMessage("§cUsage: /combatspawn <on|off> [player]");
                return true;
            }
        } else if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cUsage: /combatspawn <on|off> [player]");
                return true;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
            String action = args[0].toLowerCase();
            if (action.equals("on")) {
                bypass = true;
            } else if (action.equals("off")) {
                bypass = false;
            } else {
                sender.sendMessage("§cUsage: /combatspawn <on|off> [player]");
                return true;
            }
        } else {
            sender.sendMessage("§cUsage: /combatspawn <on|off> [player]");
            return true;
        }

        combatRules.setCombatSpawnBypass(targetId, bypass);
        saveBypassList();

        if (bypass) {
            sender.sendMessage("§a" + targetName + " §acan now enter spawn during combat.");
            Player online = Bukkit.getPlayer(targetId);
            if (online != null && !online.equals(sender)) {
                online.sendMessage("§7An admin allowed you to enter spawn during combat.");
            }
        } else {
            sender.sendMessage("§e" + targetName + " §eis now blocked from spawn during combat (normal rules).");
            Player online = Bukkit.getPlayer(targetId);
            if (online != null && !online.equals(sender)) {
                online.sendMessage("§7An admin restored normal combat spawn rules for you.");
            }
        }
        return true;
    }

    private void saveBypassList() {
        plugin.getConfig().set("combat-rules.combat-spawn-bypass-players", combatRules.getCombatSpawnBypassNames());
        plugin.saveConfig();
    }
}
