package com.yourorg.kingdomcore.core.services;

import java.util.UUID;

public interface CooldownService {
    boolean isReady(UUID playerId, String abilityId, long nowMs);

    void markUsed(UUID playerId, String abilityId, long readyAtMs);

    /**
     * Get remaining cooldown in milliseconds (0 if ready).
     */
    long getRemainingMs(UUID playerId, String abilityId, long nowMs);

    void clear(UUID playerId);

    void load(UUID playerId);

    void flush(UUID playerId);
}
