package com.yourorg.kingdomcore.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPolicyMatcherTest {

    private final ClientPolicyMatcher matcher = new ClientPolicyMatcher(null);

    @Test
    void blocksWurstVariants() {
        assertTrue(matcher.match("wurst").blocked());
        assertTrue(matcher.match("Wurst Client").blocked());
        assertTrue(matcher.match("wurst_71.21").blocked());
    }

    @Test
    void blocksXaeroAndMinimaps() {
        assertTrue(matcher.match("xaerominimap").blocked());
        assertTrue(matcher.match("xaeros-world-map").blocked());
        assertTrue(matcher.match("journeymap").blocked());
        assertTrue(matcher.match("voxelmap").blocked());
    }

    @Test
    void blocksXrayPatterns() {
        assertTrue(matcher.match("xray").blocked());
        assertTrue(matcher.match("x-ray-mod").blocked());
        assertTrue(matcher.match("ore_esp").blocked());
    }

    @Test
    void allowsVanillaFabricLoader() {
        assertFalse(matcher.match("fabric").blocked());
        assertFalse(matcher.match("fabric-loader").blocked());
        assertFalse(matcher.match("minecraft").blocked());
        assertFalse(matcher.match("sodium").blocked());
    }
}
