package com.yourorg.kingdomcore.persistence.repo;

import java.util.Map;

public interface UniqueItemRepository {
    Map<String, Long> loadCooldowns();

    void saveCooldown(String itemId, long unlockAtMs);
}
