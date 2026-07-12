package com.yourorg.kingdomcore.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves tab/chat team numbers for friendly-fire checks.
 */
public final class TabTeamResolver {

    private final Map<String, String> teamLabelByPlayer = new HashMap<>();

    public TabTeamResolver(ConfigurationSection tabPlayersSection, ConfigurationSection chatPlayersSection) {
        loadSection(tabPlayersSection);
        loadSection(chatPlayersSection);
    }

    private void loadSection(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String playerName : section.getKeys(false)) {
            String key = playerName.toLowerCase(Locale.ROOT);
            if (teamLabelByPlayer.containsKey(key)) {
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(playerName);
            if (entry == null) {
                continue;
            }
            String label = entry.getString("label");
            if (label != null && label.matches("\\d+")) {
                teamLabelByPlayer.put(key, label);
            }
        }
    }

    public boolean isSameTeam(Player first, Player second) {
        if (first == null || second == null || first.equals(second)) {
            return first != null && first.equals(second);
        }
        String firstTeam = teamLabelByPlayer.get(first.getName().toLowerCase(Locale.ROOT));
        String secondTeam = teamLabelByPlayer.get(second.getName().toLowerCase(Locale.ROOT));
        return firstTeam != null && firstTeam.equals(secondTeam);
    }
}
