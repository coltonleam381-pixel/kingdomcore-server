package com.yourorg.kingdomcore.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

public final class AdminToolAccess {
    private AdminToolAccess() {
    }

    public static boolean canUse(Player player, Plugin plugin) {
        if (player == null || plugin == null) {
            return false;
        }
        List<String> names = plugin.getConfig().getStringList("inv-check.allowed-players");
        if (names == null || names.isEmpty()) {
            names = List.of("GameAxion");
        }
        String viewer = player.getName().toLowerCase(Locale.ROOT);
        for (String allowed : names) {
            if (allowed != null && allowed.toLowerCase(Locale.ROOT).equals(viewer)) {
                return true;
            }
        }
        return false;
    }
}
