package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.core.services.DebugTelemetryService;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DebugTelemetryServiceImpl implements DebugTelemetryService {
    private final Map<UUID, EnumMap<FailReason, Integer>> stats = new ConcurrentHashMap<>();
    private volatile boolean enabled;

    @Override
    public void record(UUID playerId, FailReason reason) {
        if (!enabled || playerId == null || reason == null) {
            return;
        }
        stats.computeIfAbsent(playerId, key -> new EnumMap<>(FailReason.class))
                .merge(reason, 1, Integer::sum);
    }

    @Override
    public Map<FailReason, Integer> getPlayerStats(UUID playerId) {
        EnumMap<FailReason, Integer> map = stats.get(playerId);
        if (map == null) {
            return Map.of();
        }
        return new EnumMap<>(map);
    }

    @Override
    public void setDebugEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isDebugEnabled() {
        return enabled;
    }
}
