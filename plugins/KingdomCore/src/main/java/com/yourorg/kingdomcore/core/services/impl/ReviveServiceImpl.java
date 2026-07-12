package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.core.services.HealthService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.ReviveService;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import com.yourorg.kingdomcore.persistence.repo.ReviveAuditRepository;
import com.yourorg.kingdomcore.util.NameNormalizer;
import com.yourorg.kingdomcore.util.HeartRules;
import com.yourorg.kingdomcore.util.SpawnSelector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ReviveServiceImpl implements ReviveService {
    private final HeartService heartService;
    private final PlayerStateRepository playerStateRepository;
    private final ReviveAuditRepository auditRepository;
    private final ItemIdentityService itemIdentityService;
    private final HealthService healthService;
    private final int baseCap;
    private final Map<UUID, Boolean> pendingRevives = new ConcurrentHashMap<>();

    public ReviveServiceImpl(HeartService heartService,
                             PlayerStateRepository playerStateRepository,
                             ReviveAuditRepository auditRepository,
                             ItemIdentityService itemIdentityService,
                             HealthService healthService,
                             int baseCap) {
        this.heartService = heartService;
        this.playerStateRepository = playerStateRepository;
        this.auditRepository = auditRepository;
        this.itemIdentityService = itemIdentityService;
        this.healthService = healthService;
        this.baseCap = baseCap;
    }

    @Override
    public synchronized boolean revive(Player reviver, String targetName) {
        if (reviver == null || targetName == null) {
            return false;
        }
        ItemStack stack = reviver.getInventory().getItemInMainHand();
        if (!itemIdentityService.isReviveBeacon(stack)) {
            return false;
        }
        String normalized = NameNormalizer.normalize(targetName);
        Optional<PlayerState> targetState = playerStateRepository.findByLastName(normalized);
        if (targetState.isEmpty()) {
            auditRepository.record(reviver.getUniqueId(), null, System.currentTimeMillis(), false);
            return false;
        }
        PlayerState state = targetState.get();
        if (!state.isBlocked()) {
            auditRepository.record(reviver.getUniqueId(), state.getUuid(), System.currentTimeMillis(), false);
            return false;
        }
        int revivedHearts = HeartRules.clampProgression(3, baseCap);
        
        consumeBeacon(reviver, stack);
        
        state.setBlocked(false);
        state.setProgressionHearts(revivedHearts);
        
        Player online = Bukkit.getPlayer(state.getUuid());
        if (online != null && online.isOnline()) {
            heartService.addHearts(online, revivedHearts - state.getProgressionHearts());
        } else {
            // Offline modification routed purely through heartService
            heartService.setProgressionAndBlocked(state.getUuid(), revivedHearts, false);
        }
        if (online != null && online.isOnline()) {
            applyReviveToOnline(online, state);
        } else {
            pendingRevives.put(state.getUuid(), Boolean.TRUE);
        }
        
        auditRepository.record(reviver.getUniqueId(), state.getUuid(), System.currentTimeMillis(), true);
        return true;
    }

    @Override
    public void unblockPlayer(String targetName) {
        if (targetName == null) {
            return;
        }
        String normalized = NameNormalizer.normalize(targetName);
        Optional<PlayerState> targetState = playerStateRepository.findByLastName(normalized);
        if (targetState.isEmpty()) {
            return;
        }
        PlayerState state = targetState.get();
        state.setBlocked(false);
        if (state.getProgressionHearts() <= 0) {
            state.setProgressionHearts(HeartRules.clampProgression(3, baseCap));
        }
        heartService.setProgressionAndBlocked(state.getUuid(), state.getProgressionHearts(), false);
    }

    @Override
    public void handleJoin(Player player) {
        if (player == null) {
            return;
        }
        if (!pendingRevives.containsKey(player.getUniqueId())) {
            return;
        }
        pendingRevives.remove(player.getUniqueId());
        Optional<PlayerState> targetState = playerStateRepository.findById(player.getUniqueId());
        if (targetState.isEmpty()) {
            return;
        }
        PlayerState state = targetState.get();
        int revivedHearts = HeartRules.clampProgression(3, baseCap);
        state.setBlocked(false);
        state.setProgressionHearts(revivedHearts);
        heartService.setProgressionAndBlocked(state.getUuid(), revivedHearts, false);
        applyReviveToOnline(player, state);
    }

    private void applyReviveToOnline(Player player, PlayerState state) {
        healthService.applyHealth(player, state);
        Location spawn = SpawnSelector.selectSpawn(player);
        if (spawn != null) {
            player.teleport(spawn);
        }
            var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attribute != null) {
            player.setHealth(Math.min(player.getHealth(), attribute.getBaseValue()));
        }
    }

    private void consumeBeacon(Player reviver, ItemStack stack) {
        int remaining = stack.getAmount() - 1;
        if (remaining <= 0) {
            reviver.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(remaining);
        }
    }
}
