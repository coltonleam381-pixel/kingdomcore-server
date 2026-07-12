package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.service.SelectionService;
import com.yourorg.kingdomcore.util.AdminToolAccess;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SelectCommand implements CommandExecutor {
    private final Plugin plugin;
    private final SelectionService selectionService;

    public SelectCommand(Plugin plugin, SelectionService selectionService) {
        this.plugin = plugin;
        this.selectionService = selectionService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (!AdminToolAccess.canUse(player, plugin)) {
            player.sendMessage("§cYou cannot use this command.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§cUsage: /select <1|2|copy|paste|rotate|move>");
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "1" -> setCorner(player, true);
            case "2" -> setCorner(player, false);
            case "copy" -> copy(player);
            case "paste" -> paste(player);
            case "rotate" -> rotate(player);
            case "move" -> move(player);
            default -> {
                player.sendMessage("§cUsage: /select <1|2|copy|paste|rotate|move>");
                yield true;
            }
        };
    }

    private boolean setCorner(Player player, boolean first) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType().isAir()) {
            player.sendMessage("§cLook at a block within 6 blocks.");
            return true;
        }
        Location location = target.getLocation();
        if (first) {
            selectionService.setPos1(player, location);
            player.sendMessage("§aPosition 1 set to §f" + formatBlock(location) + "§a.");
        } else {
            selectionService.setPos2(player, location);
            player.sendMessage("§aPosition 2 set to §f" + formatBlock(location) + "§a.");
        }
        return true;
    }

    private boolean copy(Player player) {
        int count = selectionService.copy(player);
        if (count >= 0) {
            player.sendMessage("§aCopied §f" + count + "§a blocks.");
        }
        return true;
    }

    private boolean paste(Player player) {
        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            player.sendMessage("§cLook at the block where the corner should be pasted.");
            return true;
        }
        int count = selectionService.paste(player, target.getLocation());
        if (count >= 0) {
            player.sendMessage("§aPasted §f" + count + "§a blocks at §f" + formatBlock(target.getLocation()) + "§a.");
        }
        return true;
    }

    private boolean rotate(Player player) {
        boolean appliedInWorld = selectionService.rotate(player);
        if (!selectionService.hasClipboard(player)) {
            return true;
        }
        if (appliedInWorld) {
            player.sendMessage("§aPasted selection rotated 90° clockwise in place.");
        } else {
            player.sendMessage("§aClipboard rotated 90° clockwise. Paste first to rotate blocks in the world.");
        }
        return true;
    }

    private boolean move(Player player) {
        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            player.sendMessage("§cLook at where the selection should move.");
            return true;
        }
        int count = selectionService.move(player, target.getLocation());
        if (count >= 0) {
            player.sendMessage("§aMoved §f" + count + "§a blocks to §f" + formatBlock(target.getLocation()) + "§a.");
        }
        return true;
    }

    private static String formatBlock(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
