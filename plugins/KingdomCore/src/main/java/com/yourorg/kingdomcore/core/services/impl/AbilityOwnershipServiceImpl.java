package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.persistence.repo.AbilityOwnershipRepository;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import com.yourorg.kingdomcore.core.services.AbilityOwnershipService;
import com.yourorg.kingdomcore.core.services.HeartService;

import java.util.Optional;
import java.util.UUID;

public class AbilityOwnershipServiceImpl implements AbilityOwnershipService {
    private final AbilityOwnershipRepository ownershipRepository;
    private final PlayerStateRepository playerStateRepository;
    private final HeartService heartService;

    public AbilityOwnershipServiceImpl(AbilityOwnershipRepository ownershipRepository,
                                       PlayerStateRepository playerStateRepository,
                                       HeartService heartService) {
        this.ownershipRepository = ownershipRepository;
        this.playerStateRepository = playerStateRepository;
        this.heartService = heartService;
    }

    @Override
    public synchronized boolean claimAbility(UUID playerId, String abilityId) {
        if (playerId == null || abilityId == null) {
            return false;
        }
        if (isAbilityTakenByOther(playerId, abilityId)) {
            return false;
        }
        Optional<UUID> abilityOwner = ownershipRepository.findOwner(abilityId);
        if (abilityOwner.isPresent() && !abilityOwner.get().equals(playerId)) {
            return false;
        }
        Optional<String> playerAbility = ownershipRepository.findAbilityByOwner(playerId);
        if (playerAbility.isPresent() && !playerAbility.get().equals(abilityId)) {
            return false;
        }
        if (abilityOwner.isPresent()) {
            heartService.updateAbilityState(playerId, abilityId, 0);
            return true;
        }
        boolean claimed = ownershipRepository.claim(playerId, abilityId);
        if (claimed) {
            heartService.updateAbilityState(playerId, abilityId, 0);
        }
        return claimed;
    }

    @Override
    public Optional<UUID> getOwner(String abilityId) {
        Optional<UUID> owner = ownershipRepository.findOwner(abilityId);
        if (owner.isPresent()) {
            return owner;
        }
        return playerStateRepository.findUuidByAbilityId(abilityId);
    }

    @Override
    public boolean isAbilityTakenByOther(UUID playerId, String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return false;
        }
        Optional<UUID> owner = ownershipRepository.findOwner(abilityId);
        if (owner.isPresent() && (playerId == null || !owner.get().equals(playerId))) {
            return true;
        }
        Optional<UUID> stateOwner = playerStateRepository.findUuidByAbilityId(abilityId);
        return stateOwner.isPresent() && (playerId == null || !stateOwner.get().equals(playerId));
    }

    @Override
    public void resetOwner(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return;
        }
        Optional<UUID> owner = ownershipRepository.findOwner(abilityId);
        ownershipRepository.clearAbility(abilityId);
        owner.ifPresent(uuid -> heartService.updateAbilityState(uuid, null, 0));
    }

    @Override
    public void resetPlayer(UUID playerId) {
        ownershipRepository.clearPlayer(playerId);
        heartService.updateAbilityState(playerId, null, 0);
    }

    @Override
    public void syncPlayerAbilityFromOwnership(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Optional<String> ownedAbility = ownershipRepository.findAbilityByOwner(playerId);
        if (ownedAbility.isEmpty()) {
            return;
        }
        String abilityId = ownedAbility.get();
        playerStateRepository.findById(playerId).ifPresent(state -> {
            if (!abilityId.equals(state.getAbilityId())) {
                heartService.updateAbilityState(playerId, abilityId, state.getAbilityLevel());
            }
        });
    }

    @Override
    public synchronized boolean forceAssignAbility(UUID targetId, String abilityId) {
        if (targetId == null || abilityId == null) {
            return false;
        }
        ownershipRepository.findOwner(abilityId).ifPresent(oldOwner -> {
            if (!oldOwner.equals(targetId)) {
                ownershipRepository.clearAbility(abilityId);
                heartService.updateAbilityState(oldOwner, null, 0);
            }
        });

        playerStateRepository.findUuidByAbilityId(abilityId).ifPresent(stateOwner -> {
            if (!stateOwner.equals(targetId)) {
                heartService.updateAbilityState(stateOwner, null, 0);
            }
        });

        ownershipRepository.findAbilityByOwner(targetId).ifPresent(existingAbility -> {
            ownershipRepository.clearAbility(existingAbility);
        });

        boolean claimed = ownershipRepository.claim(targetId, abilityId);
        if (claimed) {
            heartService.updateAbilityState(targetId, abilityId, 0);
        }
        return claimed;
    }
}
