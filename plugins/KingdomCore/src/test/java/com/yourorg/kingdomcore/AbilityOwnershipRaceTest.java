package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.core.services.impl.AbilityOwnershipServiceImpl;
import com.yourorg.kingdomcore.persistence.repo.AbilityOwnershipRepository;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbilityOwnershipRaceTest {
    @Test
    void claimIsAtomicAcrossThreads() throws InterruptedException {
        InMemoryOwnershipRepo ownershipRepo = new InMemoryOwnershipRepo();
        InMemoryPlayerStateRepo playerStateRepo = new InMemoryPlayerStateRepo();
        AbilityOwnershipServiceImpl service = new AbilityOwnershipServiceImpl(ownershipRepo, playerStateRepo);

        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        String abilityId = "blaze";

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();

        new Thread(() -> {
            await(start);
            if (service.claimAbility(playerA, abilityId)) {
                success.incrementAndGet();
            }
            done.countDown();
        }).start();

        new Thread(() -> {
            await(start);
            if (service.claimAbility(playerB, abilityId)) {
                success.incrementAndGet();
            }
            done.countDown();
        }).start();

        start.countDown();
        done.await();

        assertEquals(1, success.get());
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static class InMemoryOwnershipRepo implements AbilityOwnershipRepository {
        private String abilityId;
        private UUID owner;

        @Override
        public synchronized boolean claim(UUID playerId, String abilityId) {
            if (owner != null) {
                return false;
            }
            this.abilityId = abilityId;
            this.owner = playerId;
            return true;
        }

        @Override
        public synchronized Optional<UUID> findOwner(String abilityId) {
            if (owner == null) {
                return Optional.empty();
            }
            return Optional.of(owner);
        }

        @Override
        public synchronized Optional<String> findAbilityByOwner(UUID playerId) {
            if (owner == null || !owner.equals(playerId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(abilityId);
        }

        @Override
        public synchronized void clearAbility(String abilityId) {
            if (this.abilityId != null && this.abilityId.equals(abilityId)) {
                this.owner = null;
                this.abilityId = null;
            }
        }

        @Override
        public synchronized void clearPlayer(UUID playerId) {
            if (owner != null && owner.equals(playerId)) {
                owner = null;
                abilityId = null;
            }
        }
    }

    private static class InMemoryPlayerStateRepo implements PlayerStateRepository {
        @Override
        public Optional<com.yourorg.kingdomcore.api.PlayerState> findById(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.yourorg.kingdomcore.api.PlayerState> findByLastName(String lastName) {
            return Optional.empty();
        }

        @Override
        public Optional<com.yourorg.kingdomcore.api.PlayerState> findByLastNameIgnoreCase(String lastName) {
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
        public void upsert(com.yourorg.kingdomcore.api.PlayerState state) {
        }

        @Override
        public void updateAbility(UUID playerId, String abilityId, int level) {
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
