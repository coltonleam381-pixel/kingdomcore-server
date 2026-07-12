package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.util.AdminToolAccess;
import com.yourorg.kingdomcore.util.DogRodItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class HundredDogCommand implements CommandExecutor {
    private final Plugin plugin;

    public HundredDogCommand(Plugin plugin) {
        this.plugin = plugin;
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

        ItemStack rod = DogRodItem.create(plugin);
        var leftover = player.getInventory().addItem(rod);
        leftover.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage("§aYou received a §f100dog §afishing rod. §7Anyone can cast it — breaks on first use.");
        return true;
    }
}
