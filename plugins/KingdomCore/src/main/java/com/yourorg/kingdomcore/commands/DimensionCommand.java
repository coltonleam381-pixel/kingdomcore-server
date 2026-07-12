package com.yourorg.kingdomcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class DimensionCommand implements CommandExecutor {
    private final JavaPlugin plugin;

    public DimensionCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou must be a server operator to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dimension <open|close> <nether|end>");
            return true;
        }

        String action = args[0].toLowerCase();
        String dim = args[1].toLowerCase();

        if (!action.equals("open") && !action.equals("close") && !action.equals("cancel")) {
            sender.sendMessage("§cInvalid action. Use 'open', 'close', or 'cancel'.");
            return true;
        }

        if (!dim.equals("nether") && !dim.equals("end")) {
            sender.sendMessage("§cInvalid dimension. Use 'nether' or 'end'.");
            return true;
        }

        String dimName = dim.substring(0, 1).toUpperCase() + dim.substring(1);

        if (action.equals("cancel")) {
            plugin.getConfig().set("dimensions." + dim + ".opens_at_ms", null);
            plugin.getConfig().set("dimensions." + dim + ".closes_at_ms", null);
            plugin.saveConfig();
            sender.sendMessage("§eCancelled any pending timers for the " + dimName + ".");
            return true;
        }

        if (action.equals("open")) {
            long targetTime = System.currentTimeMillis() + 1_800_000L; // 30 minutes
            plugin.getConfig().set("dimensions." + dim + ".opens_at_ms", targetTime);
            plugin.getConfig().set("dimensions." + dim + ".closes_at_ms", null);
            plugin.saveConfig();
            sender.sendMessage("§eThe " + dimName + " will open in 30 minutes.");
            
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§b" + dimName, "§7Opens in 30 minutes", 10, 70, 20);
            }
        } else {
            long targetTime = System.currentTimeMillis() + 600000L; // 10 minutes
            plugin.getConfig().set("dimensions." + dim + ".closes_at_ms", targetTime);
            plugin.getConfig().set("dimensions." + dim + ".opens_at_ms", null);
            plugin.saveConfig();
            sender.sendMessage("§eThe " + dimName + " will close in 10 minutes.");
        }

        return true;
    }
}
