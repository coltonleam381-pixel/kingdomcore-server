package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.util.HeartRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeartRulesTest {
    @Test
    void clampsProgressionToBaseCap() {
        assertEquals(0, HeartRules.clampProgression(-1, 20));
        assertEquals(20, HeartRules.clampProgression(25, 20));
        assertEquals(10, HeartRules.clampProgression(10, 20));
    }

    @Test
    void canConsumeOnlyBelowCap() {
        assertTrue(HeartRules.canConsume(5, 20));
        assertFalse(HeartRules.canConsume(20, 20));
    }

    @Test
    void blocksWhenPenaltyReachesZero() {
        assertTrue(HeartRules.shouldBlockAfterPenalty(1));
        assertFalse(HeartRules.shouldBlockAfterPenalty(2));
        assertFalse(HeartRules.shouldBlockAfterPenalty(0));
    }
}
