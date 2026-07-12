package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.core.repositories.BountyRepository;
import com.yourorg.kingdomcore.core.services.BountyService;

import java.util.Map;
import java.util.UUID;

public class BountyServiceImpl implements BountyService {
    private final BountyRepository bountyRepository;

    public BountyServiceImpl(BountyRepository bountyRepository) {
        this.bountyRepository = bountyRepository;
    }

    @Override
    public void addBounty(UUID playerId, int amount) {
        bountyRepository.addBounty(playerId, amount);
    }

    @Override
    public void resetBounty(UUID playerId) {
        bountyRepository.resetBounty(playerId);
    }

    @Override
    public int getBounty(UUID playerId) {
        return bountyRepository.getBounty(playerId);
    }

    @Override
    public Map<UUID, Integer> getAllBounties() {
        return bountyRepository.getAllBounties();
    }
}
