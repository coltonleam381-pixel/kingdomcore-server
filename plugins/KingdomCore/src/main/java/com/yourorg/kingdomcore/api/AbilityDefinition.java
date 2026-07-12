package com.yourorg.kingdomcore.api;

public record AbilityDefinition(
        String id,
        String name,
        long baseCooldownMs,
        String shortDescription,
        String longDescription
) {
}
