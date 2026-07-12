package com.yourorg.kingdomcore.core.services;

import java.util.Map;
import java.util.UUID;

public interface DebugTelemetryService {
    enum FailReason {
        MICRO_COOLDOWN,
        NO_ABILITY,
        WRONG_ITEM,
        NO_CUSTOM_NAME,
        WRONG_NAME,
        ABILITY_COOLDOWN,
        INVALID_CONTEXT,
        ALREADY_OWNED,
        HEART_ITEM_MISSING,
        NOT_ALLOWED,
        BLOCKED,
        LEVEL_ZERO_LOCK,
        SUCCESS
    }

    void record(UUID playerId, FailReason reason);

    Map<FailReason, Integer> getPlayerStats(UUID playerId);

    void setDebugEnabled(boolean enabled);

    boolean isDebugEnabled();
}
