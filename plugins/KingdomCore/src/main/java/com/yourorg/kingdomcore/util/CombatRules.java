package com.yourorg.kingdomcore.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Determines whether standard combat restrictions apply to a player.
 * Operators are NOT exempt unless explicitly listed in config.
 */
public final class CombatRules {

    private final Set<String> exemptNames;
    private final Set<UUID> combatCooldownBypass = new HashSet<>();
    private final Set<UUID> combatSpawnBypass = new HashSet<>();
    private boolean opSpawnEntryBypass;

    public CombatRules(List<String> exemptPlayers,
                       boolean opSpawnEntryBypass,
                       List<String> combatCooldownBypassNames,
                       List<String> combatSpawnBypassNames) {
        exemptNames = new HashSet<>();
        if (exemptPlayers != null) {
            for (String name : exemptPlayers) {
                if (name != null && !name.isBlank()) {
                    exemptNames.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        this.opSpawnEntryBypass = opSpawnEntryBypass;
        loadCombatCooldownBypass(combatCooldownBypassNames);
        loadCombatSpawnBypass(combatSpawnBypassNames);
    }

    public boolean shouldEnforce(Player player) {
        return player != null && !exemptNames.contains(player.getName().toLowerCase(Locale.ROOT));
    }

    public boolean bypassesCombatCooldowns(Player player) {
        return player != null && combatCooldownBypass.contains(player.getUniqueId());
    }

    public boolean bypassesCombatCooldowns(UUID playerId) {
        return playerId != null && combatCooldownBypass.contains(playerId);
    }

    public void setCombatCooldownBypass(UUID playerId, boolean bypass) {
        if (playerId == null) {
            return;
        }
        if (bypass) {
            combatCooldownBypass.add(playerId);
        } else {
            combatCooldownBypass.remove(playerId);
        }
    }

    public List<String> getCombatCooldownBypassNames() {
        return combatCooldownBypass.stream()
                .map(id -> {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
                    return offline.getName() != null ? offline.getName() : id.toString();
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void loadCombatCooldownBypass(List<String> names) {
        combatCooldownBypass.clear();
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (offline.getUniqueId() != null) {
                combatCooldownBypass.add(offline.getUniqueId());
            }
        }
    }

    public boolean bypassesCombatSpawnEntry(Player player) {
        return player != null && combatSpawnBypass.contains(player.getUniqueId());
    }

    public boolean bypassesCombatSpawnEntry(UUID playerId) {
        return playerId != null && combatSpawnBypass.contains(playerId);
    }

    public void setCombatSpawnBypass(UUID playerId, boolean bypass) {
        if (playerId == null) {
            return;
        }
        if (bypass) {
            combatSpawnBypass.add(playerId);
        } else {
            combatSpawnBypass.remove(playerId);
        }
    }

    public List<String> getCombatSpawnBypassNames() {
        return combatSpawnBypass.stream()
                .map(id -> {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
                    return offline.getName() != null ? offline.getName() : id.toString();
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void loadCombatSpawnBypass(List<String> names) {
        combatSpawnBypass.clear();
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (offline.getUniqueId() != null) {
                combatSpawnBypass.add(offline.getUniqueId());
            }
        }
    }

    public boolean isOpSpawnEntryBypass() {
        return opSpawnEntryBypass;
    }

    public void setOpSpawnEntryBypass(boolean opSpawnEntryBypass) {
        this.opSpawnEntryBypass = opSpawnEntryBypass;
    }

    public boolean shouldBlockCombatSpawnEntry(Player player) {
        if (!shouldEnforce(player)) {
            return false;
        }
        if (bypassesCombatSpawnEntry(player)) {
            return false;
        }
        if (player.isOp() && opSpawnEntryBypass) {
            return false;
        }
        return true;
    }

    public boolean shouldBlockCombatCooldowns(Player player) {
        if (!shouldEnforce(player)) {
            return false;
        }
        return !bypassesCombatCooldowns(player);
    }
}
