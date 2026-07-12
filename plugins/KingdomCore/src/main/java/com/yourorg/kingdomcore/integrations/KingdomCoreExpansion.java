package com.yourorg.kingdomcore.integrations;

import com.yourorg.kingdomcore.core.services.AbilityOwnershipService;
import com.yourorg.kingdomcore.service.UniqueItemService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.UUID;

public class KingdomCoreExpansion extends PlaceholderExpansion {

    private final UniqueItemService uniqueItemService;
    private final AbilityOwnershipService abilityOwnershipService;

    public KingdomCoreExpansion(UniqueItemService uniqueItemService,
                                AbilityOwnershipService abilityOwnershipService) {
        this.uniqueItemService = uniqueItemService;
        this.abilityOwnershipService = abilityOwnershipService;
    }

    @Override
    public String getIdentifier() {
        return "kingdomcore";
    }

    @Override
    public String getAuthor() {
        return "KingdomCore";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.startsWith("ability_available_")) {
            String abilityId = normalizeAbilityId(params.substring("ability_available_".length()));
            UUID self = player != null ? player.getUniqueId() : null;
            return abilityOwnershipService.isAbilityTakenByOther(self, abilityId) ? "no" : "yes";
        }

        if (params.startsWith("ability_taken_")) {
            String abilityId = normalizeAbilityId(params.substring("ability_taken_".length()));
            UUID self = player != null ? player.getUniqueId() : null;
            return abilityOwnershipService.isAbilityTakenByOther(self, abilityId) ? "yes" : "no";
        }

        // %kingdomcore_status_mace%
        if (params.startsWith("status_")) {
            String itemId = params.substring(7);
            if (uniqueItemService.canCraft(itemId)) {
                return "&aCraftable";
            } else if (uniqueItemService.getRemainingMs(itemId) <= 0) {
                return "&cCurrently Active in World";
            } else {
                return formatTime(uniqueItemService.getRemainingMs(itemId));
            }
        }

        // %kingdomcore_cooldown_mace%
        if (params.startsWith("cooldown_")) {
            String itemId = params.substring(9);
            long ms = uniqueItemService.getRemainingMs(itemId);
            if (ms <= 0) {
                return "0s";
            }
            return formatTime(ms);
        }

        return null;
    }

    private String normalizeAbilityId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.toLowerCase(Locale.ROOT).trim();
        return switch (s) {
            case "atlant" -> "atlantis";
            case "heartshield" -> "heart_shield";
            case "ice_nova", "icenova" -> "ice_nova";
            case "phase_step", "phasestep" -> "phase_step";
            case "phase_walk", "phasewalk" -> "phase_walk";
            case "sanguine_fog", "sanguinefog" -> "sanguine_fog";
            case "sky_breaker" -> "skybreaker";
            default -> s;
        };
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        if (h > 0) {
            return String.format("%dh %dm", h, m);
        } else if (m > 0) {
            return String.format("%dm %ds", m, s);
        } else {
            return String.format("%ds", s);
        }
    }
}
