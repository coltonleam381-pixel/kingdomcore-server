package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.impl.AbilityOwnershipServiceImpl;
import com.yourorg.kingdomcore.persistence.repo.AbilityOwnershipRepository;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ForceAssignAbilityTest {
    @Test
    void forceAssignClearsOldOwners() {
        InMemoryOwnershipRepo ownershipRepo = new InMemoryOwnershipRepo();
        InMemoryPlayerStateRepo playerStateRepo = new InMemoryPlayerStateRepo();
        AbilityOwnershipServiceImpl service = new AbilityOwnershipServiceImpl(ownershipRepo, playerStateRepo);

        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        playerStateRepo.upsert(new PlayerState(playerA));
        playerStateRepo.upsert(new PlayerState(playerB));

        assertTrue(service.forceAssignAbility(playerA, "blaze"));
        assertTrue(service.forceAssignAbility(playerB, "blaze"));

        assertEquals(playerB, ownershipRepo.findOwner("blaze").orElse(null));
        assertEquals("blaze", playerStateRepo.findById(playerB).orElseThrow().getAbilityId());
        assertEquals(null, playerStateRepo.findById(playerA).orElseThrow().getAbilityId());
    }

    private static class InMemoryOwnershipRepo implements AbilityOwnershipRepository {
        private final Map<String, UUID> owners = new HashMap<>();
        private final Map<UUID, String> abilityByOwner = new HashMap<>();

        @Override
        public synchronized boolean claim(UUID playerId, String abilityId) {
            if (owners.containsKey(abilityId)) {
                return false;
            }
            if (abilityByOwner.containsKey(playerId)) {
                return false;
            }
            owners.put(abilityId, playerId);
            abilityByOwner.put(playerId, abilityId);
            return true;
        }

        @Override
        public synchronized Optional<UUID> findOwner(String abilityId) {
            return Optional.ofNullable(owners.get(abilityId));
        }

        @Override
        public synchronized Optional<String> findAbilityByOwner(UUID playerId) {
            return Optional.ofNullable(abilityByOwner.get(playerId));
        }

        @Override
        public synchronized void clearAbility(String abilityId) {
            UUID owner = owners.remove(abilityId);
            if (owner != null) {
                abilityByOwner.remove(owner);
            }
        }

        @Override
        public synchronized void clearPlayer(UUID playerId) {
            String ability = abilityByOwner.remove(playerId);
            if (ability != null) {
                owners.remove(ability);
            }
        }
    }

    private static class InMemoryPlayerStateRepo implements PlayerStateRepository {
        private final Map<UUID, PlayerState> states = new HashMap<>();

        @Override
        public Optional<PlayerState> findById(UUID playerId) {
            return Optional.ofNullable(states.get(playerId));
        }

        @Override
        public Optional<PlayerState> findByLastName(String lastName) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerState> findByLastNameIgnoreCase(String lastName) {
            return Optional.empty();
        }

        @Override
        public List<String> findAllLastNames() {
            return List.of();
        }

        @Override
        public Optional<UUID> findUuidByAbilityId(String abilityId) {
            return Optional.empty();
        }

        @Override
        public void upsert(PlayerState state) {
            states.put(state.getUuid(), state);
        }

        @Override
        public void updateAbility(UUID playerId, String abilityId, int level) {
            PlayerState state = states.get(playerId);
            if (state != null) {
                state.setAbilityId(abilityId);
                state.setAbilityLevel(level);
            }
        }

        @Override
        public void updateProgression(UUID playerId, int progression, boolean blocked) {
        }

        @Override
        public void updateLastName(UUID playerId, String lastName) {
        }

        @Override
        public void updateAssassinWinBonus(UUID playerId, int bonus) {
        }

        @Override
        public long getCombatLogPendingAt(UUID playerId) {
            return 0L;
        }

        @Override
        public void setCombatLogPending(UUID playerId, long pendingAtMs) {
        }

        @Override
        public void clearCombatLogPending(UUID playerId) {
        }
    }
}
