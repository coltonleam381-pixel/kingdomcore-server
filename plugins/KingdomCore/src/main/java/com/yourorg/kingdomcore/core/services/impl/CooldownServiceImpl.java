package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.persistence.repo.CooldownRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownServiceImpl implements CooldownService {
    private final CooldownRepository repository;
    private final boolean persist;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public CooldownServiceImpl(CooldownRepository repository, boolean persist) {
        this.repository = repository;
        this.persist = persist;
    }

    @Override
    public boolean isReady(UUID playerId, String abilityId, long nowMs) {
        Map<String, Long> map = cooldowns.get(playerId);
        if (map == null) {
            return true;
        }
        Long readyAt = map.get(abilityId);
        return readyAt == null || nowMs >= readyAt;
    }

    @Override
    public void markUsed(UUID playerId, String abilityId, long readyAtMs) {
        cooldowns.computeIfAbsent(playerId, key -> new ConcurrentHashMap<>())
                .put(abilityId, readyAtMs);
        if (persist && repository != null) {
            repository.saveCooldown(playerId, abilityId, readyAtMs);
        }
    }

    @Override
    public long getRemainingMs(UUID playerId, String abilityId, long nowMs) {
        Map<String, Long> map = cooldowns.get(playerId);
        if (map == null) {
            return 0;
        }
        Long readyAt = map.get(abilityId);
        if (readyAt == null || nowMs >= readyAt) {
            return 0;
        }
        return readyAt - nowMs;
    }

    @Override
    public void clear(UUID playerId) {
        cooldowns.remove(playerId);
        if (persist && repository != null) {
            repository.clear(playerId);
        }
    }

    @Override
    public void load(UUID playerId) {
        if (!persist || repository == null) {
            return;
        }
        Map<String, Long> loaded = repository.loadCooldowns(playerId);
        if (!loaded.isEmpty()) {
            cooldowns.put(playerId, new ConcurrentHashMap<>(loaded));
        }
    }

    @Override
    public void flush(UUID playerId) {
        if (!persist || repository == null) {
            return;
        }
        Map<String, Long> map = cooldowns.get(playerId);
        if (map == null) {
            return;
        }
        map.forEach((abilityId, readyAt) -> repository.saveCooldown(playerId, abilityId, readyAt));
    }
}
