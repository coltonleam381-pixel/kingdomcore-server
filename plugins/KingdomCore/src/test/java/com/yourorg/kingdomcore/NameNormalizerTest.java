package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.util.NameNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NameNormalizerTest {
    @Test
    void normalizesNames() {
        assertEquals("blaze", NameNormalizer.normalize(" Blaze "));
        assertEquals("stone", NameNormalizer.normalize("§6Stone"));
        assertEquals("", NameNormalizer.normalize(null));
    }
}
