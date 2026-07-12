package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.core.services.AuthService;
import com.yourorg.kingdomcore.gui.PinGui;
import com.yourorg.kingdomcore.integrations.ItemsAdderHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class KAuthCommand implements CommandExecutor {
    private final AuthService authService;
    private final PinGui pinGui;
    private final ItemsAdderHook itemsAdderHook;
    private final JavaPlugin plugin;

    public KAuthCommand(AuthService authService, PinGui pinGui, ItemsAdderHook itemsAdderHook, JavaPlugin plugin) {
        this.authService = authService;
        this.pinGui = pinGui;
        this.itemsAdderHook = itemsAdderHook;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kingdomcore.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /kauth <force|resetpin> <player>");
            return true;
        }

        String sub = args[0].toLowerCase();
        Player target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer must be online.");
            return true;
        }

        if (sub.equals("force")) {
            authService.forceAuthenticate(target);
            pinGui.removePlayer(target.getUniqueId());
            target.removePotionEffect(PotionEffectType.BLINDNESS);
            target.closeInventory();
            itemsAdderHook.applyResourcePackDelayed(plugin, target, 20L);
            target.sendMessage("§aYou have been logged in by an admin.");
            sender.sendMessage("§aForced login for §f" + target.getName() + "§a.");
            return true;
        }

        if (sub.equals("resetpin")) {
            authService.resetAccountForNewPin(target);
            pinGui.removePlayer(target.getUniqueId());
            target.closeInventory();
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
            target.sendActionBar(Component.text("Create a new 4-digit PIN", NamedTextColor.GREEN));
            Bukkit.getScheduler().runTask(plugin, () -> pinGui.openRegisterGui(target));
            target.sendMessage("§eYour PIN was reset. Choose a new 4-digit PIN.");
            sender.sendMessage("§aReset PIN for §f" + target.getName() + "§a — register menu opened.");
            return true;
        }

        sender.sendMessage("§cUsage: /kauth <force|resetpin> <player>");
        return true;
    }

    private static Player resolvePlayer(String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target != null) {
            return target;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }
        return null;
    }
}
