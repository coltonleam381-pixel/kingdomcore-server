package com.yourorg.kingdomcore.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Applies custom tab-list prefixes for configured players.
 */
public class TabPrefixListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, PrefixStyle> prefixesByName = new HashMap<>();

    public TabPrefixListener(JavaPlugin plugin,
                             ConfigurationSection tabPlayersSection,
                             ConfigurationSection chatPlayersSection) {
        this.plugin = plugin;
        loadPlayers(tabPlayersSection);
        loadChatStylePlayers(chatPlayersSection);
    }

    private void loadPlayers(ConfigurationSection playersSection) {
        if (playersSection == null) {
            return;
        }
        for (String playerName : playersSection.getKeys(false)) {
            ConfigurationSection entry = playersSection.getConfigurationSection(playerName);
            if (entry == null) {
                continue;
            }
            String label = entry.getString("label", "Owner");
            NamedTextColor color = parseColor(entry.getString("color", "GOLD"));
            boolean bold = entry.getBoolean("bold", true);
            prefixesByName.put(playerName.toLowerCase(Locale.ROOT), new PrefixStyle(label, color, bold));
        }
    }

    private void loadChatStylePlayers(ConfigurationSection playersSection) {
        if (playersSection == null) {
            return;
        }
        for (String playerName : playersSection.getKeys(false)) {
            String key = playerName.toLowerCase(Locale.ROOT);
            if (prefixesByName.containsKey(key)) {
                continue;
            }
            ConfigurationSection entry = playersSection.getConfigurationSection(playerName);
            if (entry == null) {
                continue;
            }
            String label = entry.getString("label");
            if (label == null || label.isBlank()) {
                continue;
            }
            NamedTextColor color = parseColor(entry.getString("color", "GOLD"));
            boolean bold = entry.getBoolean("bold", true);
            prefixesByName.put(key, new PrefixStyle(label, color, bold));
        }
    }

    public void applyToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }

    private void apply(Player player) {
        PrefixStyle style = prefixesByName.get(player.getName().toLowerCase(Locale.ROOT));
        if (style == null) {
            return;
        }
        boolean teamNumber = style.label.matches("\\d+");
        String formattedPrefix = teamNumber
                ? "[" + style.label + "] "
                : style.label + " ";
        Component prefix = teamNumber && style.bold
                ? Component.text(formattedPrefix, style.color, TextDecoration.BOLD)
                : Component.text(formattedPrefix, style.color);
        Component name = Component.text(player.getName(), NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false);
        player.playerListName(prefix.append(name));
    }

    private static NamedTextColor parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return NamedTextColor.GOLD;
        }
        NamedTextColor color = NamedTextColor.NAMES.value(raw.trim().toLowerCase(Locale.ROOT));
        return color != null ? color : NamedTextColor.GOLD;
    }

    private record PrefixStyle(String label, NamedTextColor color, boolean bold) {
    }
}
