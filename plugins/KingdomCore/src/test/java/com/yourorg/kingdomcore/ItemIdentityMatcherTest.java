package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.util.ItemIdentityMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ItemIdentityMatcherTest {
    @Test
    void matchesItemsAdderIdFirst() {
        assertTrue(ItemIdentityMatcher.matches("kingdomcore:heart", "other", "kingdomcore:heart"));
        assertFalse(ItemIdentityMatcher.matches("kingdomcore:heart", "kingdomcore:heart", "kingdomcore:crown"));
    }

    @Test
    void matchesPdcWhenNoItemsAdderId() {
        assertTrue(ItemIdentityMatcher.matches(null, "kingdomcore:heart", "kingdomcore:heart"));
        assertFalse(ItemIdentityMatcher.matches(null, "kingdomcore:heart", "kingdomcore:crown"));
    }
}
