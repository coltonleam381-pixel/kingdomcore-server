package com.yourorg.kingdomcore.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClientPolicyMatcher {

    private static final List<String> DEFAULT_FORBIDDEN = List.of(
            "wurst",
            "xaero",
            "xray",
            "x_ray",
            "x-ray",
            "minimap",
            "mini_map",
            "mini-map",
            "journeymap",
            "journey_map",
            "journey-map",
            "voxelmap",
            "voxel_map",
            "mapwriter",
            "worldmap",
            "world_map",
            "antiqueatlas",
            "antique_atlas",
            "oreesp",
            "ore_esp",
            "orefinder",
            "ore_finder",
            "wallhack",
            "meteorclient",
            "meteor-client",
            "aristois",
            "liquidbounce",
            "liquid_bounce",
            "impactclient",
            "inertiaclient",
            "chestesp",
            "playeresp",
            "replaymod",
            "replaymodrestrict",
            "replayrestrict"
    );

    private final List<String> forbiddenPatterns;

    public ClientPolicyMatcher(List<String> forbiddenPatterns) {
        if (forbiddenPatterns == null || forbiddenPatterns.isEmpty()) {
            this.forbiddenPatterns = DEFAULT_FORBIDDEN;
        } else {
            this.forbiddenPatterns = forbiddenPatterns;
        }
    }

    public MatchResult match(String raw) {
        if (raw == null || raw.isBlank()) {
            return MatchResult.none();
        }
        String normalized = normalize(raw);
        for (String pattern : forbiddenPatterns) {
            String needle = normalize(pattern);
            if (needle.isEmpty()) {
                continue;
            }
            if (normalized.contains(needle)) {
                return new MatchResult(true, pattern, raw);
            }
        }
        return MatchResult.none();
    }

    private static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public record MatchResult(boolean blocked, String pattern, String source) {
        public static MatchResult none() {
            return new MatchResult(false, null, null);
        }
    }
}
