package com.yourorg.kingdomcore.commands;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.gui.MenuFactory;
import com.yourorg.kingdomcore.integrations.DeluxeMenusBridge;
import com.yourorg.kingdomcore.util.NpcOnlyCommands;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MenuCommand implements CommandExecutor {
    public enum MenuKind {
        ABILITY,
        REVIVE
    }

    private final MenuFactory menuFactory;
    private final HeartService heartService;
    private final MenuKind kind;

    public MenuCommand(MenuFactory menuFactory, HeartService heartService, MenuKind kind) {
        this.menuFactory = menuFactory;
        this.heartService = heartService;
        this.kind = kind;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (NpcOnlyCommands.denyUnlessAllowed(player)) {
            return true;
        }
        if (kind == MenuKind.REVIVE) {
            menuFactory.openRevive(player);
            return true;
        }
        var plugin = Bukkit.getPluginManager().getPlugin("KingdomCore");
        if (plugin != null && DeluxeMenusBridge.openMenu(plugin, player, "abilitymen")) {
            return true;
        }
        menuFactory.openAbilityList(player);
        return true;
    }
}
