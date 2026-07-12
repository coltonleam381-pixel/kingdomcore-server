package com.yourorg.kingdomcore.core.services;

import java.util.Map;
import java.util.UUID;

public interface BountyService {
    void addBounty(UUID playerId, int amount);
    void resetBounty(UUID playerId);
    int getBounty(UUID playerId);
    Map<UUID, Integer> getAllBounties();
}
