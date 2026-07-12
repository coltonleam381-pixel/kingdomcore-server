package com.yourorg.kingdomcore.persistence.repo;

import java.util.Optional;
import java.util.UUID;

public interface AbilityOwnershipRepository {
    boolean claim(UUID playerId, String abilityId);

    Optional<UUID> findOwner(String abilityId);

    Optional<String> findAbilityByOwner(UUID playerId);

    void clearAbility(String abilityId);

    void clearPlayer(UUID playerId);
}
