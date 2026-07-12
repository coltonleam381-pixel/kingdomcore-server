package com.yourorg.kingdomcore.integrations;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class DeluxeMenusBridge {

    private DeluxeMenusBridge() {
    }

    public static boolean openMenu(Plugin plugin, Player player, String menuName) {
        if (player == null || menuName == null || menuName.isBlank()) {
            return false;
        }
        if (openViaApi(plugin, player, menuName)) {
            return true;
        }
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm open " + menuName + " " + player.getName());
    }

    private static boolean openViaApi(Plugin plugin, Player player, String menuName) {
        if (Bukkit.getPluginManager().getPlugin("DeluxeMenus") == null) {
            plugin.getLogger().warning("DeluxeMenus is not loaded; cannot open menu " + menuName);
            return false;
        }
        try {
            Class<?> menuManagerClass = Class.forName("com.extendedclip.deluxemenus.menu.MenuManager");
            Method getMenuByName = menuManagerClass.getMethod("getMenuByName", String.class);
            Object menu = getMenuByName.invoke(null, menuName);
            if (menu == null) {
                plugin.getLogger().warning("DeluxeMenus menu not found: " + menuName);
                return false;
            }
            Method openMenu = menu.getClass().getMethod("openMenu", Player.class);
            openMenu.invoke(menu, player);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "DeluxeMenus API open failed for " + menuName, ex);
            return false;
        }
    }
}
