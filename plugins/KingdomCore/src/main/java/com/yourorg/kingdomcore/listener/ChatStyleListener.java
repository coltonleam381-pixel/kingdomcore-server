package com.yourorg.kingdomcore.listener;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Applies custom chat colors only when {@code chat-color} is set in config.
 * Team {@code color} entries are used for tab prefixes, not chat.
 */
public class ChatStyleListener implements Listener {

    private final Map<String, String> formatPrefixByName = new HashMap<>();

    public ChatStyleListener(ConfigurationSection playersSection) {
        if (playersSection == null) {
            return;
        }
        for (String playerName : playersSection.getKeys(false)) {
            ConfigurationSection entry = playersSection.getConfigurationSection(playerName);
            if (entry == null) {
                continue;
            }
            String chatColorRaw = entry.getString("chat-color");
            if (chatColorRaw == null || chatColorRaw.isBlank()) {
                continue;
            }
            ChatColor color = parseColor(chatColorRaw);
            boolean bold = entry.getBoolean("bold", false);
            String prefix = (bold ? ChatColor.BOLD.toString() : "") + color.toString();
            formatPrefixByName.put(playerName.toLowerCase(Locale.ROOT), prefix);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String prefix = formatPrefixByName.get(event.getPlayer().getName().toLowerCase(Locale.ROOT));
        if (prefix == null) {
            return;
        }
        event.setFormat(prefix + "<%1$s" + prefix + "> " + prefix + "%2$s");
    }

    private static ChatColor parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChatColor.GOLD;
        }
        try {
            return ChatColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ChatColor.GOLD;
        }
    }
}
