package com.yourorg.kingdomcore.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class AbilityRenameTokens {

    private AbilityRenameTokens() {
    }

    public static List<String> tokensFor(String abilityId, String abilityName) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (abilityName != null && !abilityName.isBlank()) {
            tokens.add(abilityName);
        }
        if (abilityId != null && !abilityId.isBlank()) {
            tokens.add(abilityId);
            tokens.add(abilityId.replace('_', ' '));
        }
        if (abilityId == null) {
            return new ArrayList<>(tokens);
        }
        switch (abilityId.toLowerCase(Locale.ROOT)) {
            case "atlantis" -> tokens.add("Atlant");
            case "heart_shield" -> tokens.add("HeartShield");
            case "ice_nova" -> tokens.add("IceNova");
            case "phase_step" -> tokens.add("PhaseStep");
            case "phase_walk" -> tokens.add("PhaseWalk");
            case "sanguine_fog" -> tokens.add("SanguineFog");
            case "skybreaker" -> {
                tokens.add("SkyBreaker");
                tokens.add("Sky Breaker");
            }
            case "lifesteal" -> tokens.add("Life Steal");
            default -> {
            }
        }
        return new ArrayList<>(tokens);
    }

    public static boolean matchesRenamedItem(String itemName, String abilityId, String abilityName) {
        if (itemName == null || itemName.isBlank()) {
            return false;
        }
        for (String token : tokensFor(abilityId, abilityName)) {
            if (namesMatch(itemName, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesMatch(String itemName, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (itemName.equalsIgnoreCase(token)) {
            return true;
        }
        return compact(itemName).equals(compact(token));
    }

    private static String compact(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
