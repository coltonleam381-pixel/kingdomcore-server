package com.yourorg.kingdomcore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityRenameTokensTest {

    @Test
    void matchesAllConfiguredAbilityNames() {
        assertRenameWorks("hulk", "Hulk", "Hulk", "hulk");
        assertRenameWorks("thor", "Thor", "Thor");
        assertRenameWorks("zeus", "Zeus", "Zeus");
        assertRenameWorks("atlantis", "Atlantis", "Atlant", "atlantis");
        assertRenameWorks("skybreaker", "Skybreaker", "SkyBreaker", "Sky Breaker");
        assertRenameWorks("meteor", "Meteor", "meteor");
        assertRenameWorks("dash", "Dash", "dash");
        assertRenameWorks("heart_shield", "Heart Shield", "heart_shield", "HeartShield");
        assertRenameWorks("lifesteal", "Lifesteal", "Life Steal");
        assertRenameWorks("sanguine_fog", "Sanguine Fog", "SanguineFog");
        assertRenameWorks("recall", "Recall", "recall");
        assertRenameWorks("phase_step", "Phase Step", "PhaseStep");
        assertRenameWorks("berserk", "Berserk", "berserk");
        assertRenameWorks("protocol", "Protocol", "protocol");
        assertRenameWorks("protector", "Protector", "protector");
        assertRenameWorks("ice_nova", "Ice Nova", "IceNova");
        assertRenameWorks("phase_walk", "Phase Walk", "PhaseWalk");
    }

    private void assertRenameWorks(String id, String displayName, String... renames) {
        for (String rename : renames) {
            assertTrue(
                    AbilityRenameTokens.matchesRenamedItem(rename, id, displayName),
                    () -> "Expected rename '" + rename + "' to match ability " + id
            );
        }
    }
}
