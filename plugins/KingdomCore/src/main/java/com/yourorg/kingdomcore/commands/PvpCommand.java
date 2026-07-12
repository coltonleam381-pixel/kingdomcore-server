package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.PvpTimerTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class PvpCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public PvpCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage("§cOnly operators can use this command.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("cancel")) {
            if (!PvpTimerTask.hasScheduledEnable(plugin)) {
                sender.sendMessage("§eNo scheduled PVP enable to cancel.");
                return true;
            }
            PvpTimerTask.cancelScheduledEnable(plugin);
            sender.sendMessage("§eCancelled the scheduled PVP enable.");
            Bukkit.broadcastMessage("§c§lPVP §7scheduled enable was cancelled.");
            return true;
        }

        World firstWorld = Bukkit.getWorlds().get(0);
        boolean currentState = firstWorld.getPVP();
        boolean enable;

        if (args.length == 0) {
            enable = !currentState;
        } else {
            String action = args[0].toLowerCase();
            if (action.equals("on") || action.equals("true") || action.equals("enable")) {
                enable = true;
            } else if (action.equals("off") || action.equals("false") || action.equals("disable")) {
                enable = false;
            } else {
                sender.sendMessage("§cUsage: /pvp <on|off|cancel>");
                sender.sendMessage("§7Use §f/pvp on now §7to enable PVP immediately (no 30m timer).");
                return true;
            }
        }

        if (enable) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("on") && args[1].equalsIgnoreCase("now")) {
                enablePvpNow(sender);
                return true;
            }
            if (currentState) {
                sender.sendMessage("§cPVP is already enabled.");
                return true;
            }
            if (PvpTimerTask.hasScheduledEnable(plugin)) {
                sender.sendMessage("§ePVP enable is already scheduled. Use §f/pvp cancel §eto cancel it.");
                return true;
            }
            PvpTimerTask.scheduleEnable(plugin);
            sender.sendMessage("§ePVP will be enabled in §f30 minutes§e. Announcements at 30m, 10m, and 1m.");
            return true;
        }

        PvpTimerTask.cancelScheduledEnable(plugin);
        for (World world : Bukkit.getWorlds()) {
            world.setPVP(false);
        }
        sender.sendMessage("§eGlobal PVP has been §cdisabled§e.");
        Bukkit.broadcastMessage("§c§lPVP §7has been §cdisabled §7server-wide!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§c§lPVP", "§cDisabled", 10, 50, 15);
        }
        return true;
    }

    private void enablePvpNow(CommandSender sender) {
        PvpTimerTask.cancelScheduledEnable(plugin);
        for (World world : Bukkit.getWorlds()) {
            world.setPVP(true);
        }
        sender.sendMessage("§aGlobal PVP enabled immediately.");
        Bukkit.broadcastMessage("§c§lPVP §7is now §aenabled §7server-wide!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§c§lPVP", "§aEnabled", 10, 50, 15);
        }
    }
}
