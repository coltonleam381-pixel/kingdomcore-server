package com.yourorg.kingdomcore.util;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

public final class NpcOnlyCommands {

    public static final Set<String> COMMAND_LABELS = Set.of(
            "revivemenu",
            "abilitymenu",
            "abilitymen",
            "abilityupgrade",
            "bounty391381",
            "kcraft",
            "dm",
            "dmenu",
            "deluxemenus",
            "deluxemenu"
    );

    private static final String UNKNOWN_COMMAND_MESSAGE = "§cUnknown command. Type \"/help\" for help.";

    private NpcOnlyCommands() {
    }

    public static boolean isNpcOnlyCommand(String label) {
        return label != null && COMMAND_LABELS.contains(label.toLowerCase(Locale.ROOT));
    }

    public static boolean canRunNpcOnlyCommand(Player player) {
        return player.isOp() || player.hasPermission("kingdomcore.admin");
    }

    public static boolean denyUnlessAllowed(Player player) {
        if (canRunNpcOnlyCommand(player)) {
            return false;
        }
        player.sendMessage(UNKNOWN_COMMAND_MESSAGE);
        return true;
    }

    public static String extractCommandLabel(String message) {
        if (message == null) {
            return "";
        }
        String raw = message.trim();
        if (!raw.startsWith("/")) {
            return "";
        }
        return raw.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
    }
}
