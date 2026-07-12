package com.yourorg.kingdomcore.persistence.repo;

import com.yourorg.kingdomcore.api.PlayerState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerStateRepository {
    Optional<PlayerState> findById(UUID playerId);

    Optional<PlayerState> findByLastName(String lastName);

    Optional<PlayerState> findByLastNameIgnoreCase(String lastName);

    List<String> findAllLastNames();

    Optional<UUID> findUuidByAbilityId(String abilityId);

    void upsert(PlayerState state);

    void updateAbility(UUID playerId, String abilityId, int level);

    void updateProgression(UUID playerId, int progression, boolean blocked);

    void updateLastName(UUID playerId, String lastName);

    void updateAssassinWinBonus(UUID playerId, int bonus);

    long getCombatLogPendingAt(UUID playerId);

    void setCombatLogPending(UUID playerId, long pendingAtMs);

    void clearCombatLogPending(UUID playerId);
}
