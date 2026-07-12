package com.yourorg.kingdomcore.core.services;

import java.util.Optional;
import java.util.UUID;

public interface AbilityOwnershipService {
    boolean claimAbility(UUID playerId, String abilityId);

    Optional<UUID> getOwner(String abilityId);

    boolean isAbilityTakenByOther(UUID playerId, String abilityId);

    void resetOwner(String abilityId);

    void resetPlayer(UUID playerId);

    void syncPlayerAbilityFromOwnership(UUID playerId);

    boolean forceAssignAbility(UUID targetId, String abilityId);
}
