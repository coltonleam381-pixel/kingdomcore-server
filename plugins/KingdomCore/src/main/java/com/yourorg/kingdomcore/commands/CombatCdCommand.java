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
 * Per-player bypass for post-combat NPC/chest cooldown restrictions.
 */
public class CombatCdCommand implements CommandExecutor {

    private final KingdomCorePlugin plugin;
    private final CombatRules combatRules;

    public CombatCdCommand(KingdomCorePlugin plugin, CombatRules combatRules) {
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
                boolean bypass = combatRules.bypassesCombatCooldowns(target.getUniqueId());
                String name = target.getName() != null ? target.getName() : args[1];
                sender.sendMessage("§7Combat CD for §f" + name + "§7: "
                        + (bypass ? "§aOFF (no cooldown)" : "§cON (normal)"));
                return true;
            }
            List<String> bypassed = combatRules.getCombatCooldownBypassNames();
            if (bypassed.isEmpty()) {
                sender.sendMessage("§7No players have combat CD bypass. Everyone uses normal cooldowns.");
            } else {
                sender.sendMessage("§7Combat CD bypass (no NPC/chest cooldown): §f" + String.join(", ", bypassed));
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
            if (action.equals("off") || action.equals("disable") || action.equals("false")) {
                bypass = true;
            } else if (action.equals("on") || action.equals("enable") || action.equals("true")) {
                bypass = false;
            } else {
                sender.sendMessage("§cUsage: /combatcd <on|off> [player]");
                return true;
            }
        } else if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cUsage: /combatcd <on|off> [player]");
                return true;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
            String action = args[0].toLowerCase();
            if (action.equals("off")) {
                bypass = true;
            } else if (action.equals("on")) {
                bypass = false;
            } else {
                sender.sendMessage("§cUsage: /combatcd <on|off> [player]");
                return true;
            }
        } else {
            sender.sendMessage("§cUsage: /combatcd <on|off> [player]");
            return true;
        }

        combatRules.setCombatCooldownBypass(targetId, bypass);
        saveBypassList();

        if (bypass) {
            sender.sendMessage("§aCombat NPC/chest cooldowns disabled for §f" + targetName + "§a.");
            Player online = Bukkit.getPlayer(targetId);
            if (online != null && !online.equals(sender)) {
                online.sendMessage("§7An admin disabled your post-combat NPC/chest cooldowns.");
            }
        } else {
            sender.sendMessage("§eCombat NPC/chest cooldowns enabled for §f" + targetName + "§e (normal rules).");
            Player online = Bukkit.getPlayer(targetId);
            if (online != null && !online.equals(sender)) {
                online.sendMessage("§7An admin restored your post-combat NPC/chest cooldowns.");
            }
        }
        return true;
    }

    private void saveBypassList() {
        plugin.getConfig().set("combat-rules.combat-cd-bypass-players", combatRules.getCombatCooldownBypassNames());
        plugin.saveConfig();
    }
}
