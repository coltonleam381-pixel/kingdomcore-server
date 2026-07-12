package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.api.AbilityLevelCosts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbilityLevelCostsTest {
    @Test
    void returnsCorrectCosts() {
        AbilityLevelCosts costs = new AbilityLevelCosts(3, 4, 5);
        assertEquals(3, costs.costForNextLevel(0));
        assertEquals(4, costs.costForNextLevel(1));
        assertEquals(5, costs.costForNextLevel(2));
        assertEquals(0, costs.costForNextLevel(3));
    }
}
