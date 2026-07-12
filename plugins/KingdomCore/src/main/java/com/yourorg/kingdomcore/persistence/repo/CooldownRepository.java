package com.yourorg.kingdomcore.persistence.repo;

import java.util.Map;
import java.util.UUID;

public interface CooldownRepository {
    Map<String, Long> loadCooldowns(UUID playerId);

    void saveCooldown(UUID playerId, String abilityId, long readyAtMs);

    void clear(UUID playerId);
}
