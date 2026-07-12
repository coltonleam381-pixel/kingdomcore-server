package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.DiamondBeamService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DiamondBeamCommand implements CommandExecutor {

    private final DiamondBeamService diamondBeamService;

    public DiamondBeamCommand(DiamondBeamService diamondBeamService) {
        this.diamondBeamService = diamondBeamService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage("§cOnly operators can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendStatus(sender);
            sender.sendMessage("§7Usage: /diamondbeam <on|off|status|reload|rescan|thickness <0-5>>");
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "status" -> sendStatus(sender);
            case "on", "true", "enable" -> {
                diamondBeamService.setEnabled(true);
                sender.sendMessage("§aDiamond beams enabled.");
            }
            case "off", "false", "disable" -> {
                diamondBeamService.setEnabled(false);
                sender.sendMessage("§cDiamond beams disabled. Only diamond blocks remain visible.");
            }
            case "reload" -> {
                diamondBeamService.reload();
                sender.sendMessage("§aDiamond beam settings reloaded.");
                sendStatus(sender);
            }
            case "rescan" -> {
                sender.sendMessage("§7Scanning spawn for diamond blocks...");
                diamondBeamService.rescan();
                sender.sendMessage("§aRescan started. Check console for results.");
            }
            case "thickness" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /diamondbeam thickness <0-5>");
                    return true;
                }
                try {
                    double value = Double.parseDouble(args[1]);
                    diamondBeamService.setThickness(value);
                    sender.sendMessage("§aDiamond beam thickness set to §f" + diamondBeamService.getThickness() + "§a.");
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cThickness must be a number between 0 and 5.");
                }
            }
            default -> {
                sender.sendMessage("§cUsage: /diamondbeam <on|off|status|reload|rescan|thickness <0-5>>");
            }
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("§7Diamond beams: "
                + (diamondBeamService.isEnabled() ? "§aON" : "§cOFF")
                + "§7, thickness: §f" + diamondBeamService.getThickness()
                + "§7, sources: §f" + diamondBeamService.getBeamSources().size());
    }
}
