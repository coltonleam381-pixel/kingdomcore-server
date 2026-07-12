package com.yourorg.kingdomcore.util;

import org.bukkit.ChatColor;

public final class NameNormalizer {
    private NameNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(input);
        if (stripped == null) {
            stripped = input;
        }
        return stripped.trim().toLowerCase();
    }
}
