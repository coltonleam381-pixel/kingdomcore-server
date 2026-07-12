package com.yourorg.kingdomcore.service.impl;

import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.util.CombatLogoutInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagServiceImpl implements CombatTagService {
    
    private static final long TAG_DURATION_MS = 23000;
    
    private final Plugin plugin;
    private final PlayerStateRepository playerStateRepository;
    private final HeartService heartService;
    private final String blockedKickMessage;
    private final Map<UUID, CombatTag> activeTags = new ConcurrentHashMap<>();
    private final long pearlPostTagMs;
    private final long pearlCombatCooldownMs;
    private final Map<UUID, Long> echestCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spawnEntryCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pearlBlockCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pearlUseCooldowns = new ConcurrentHashMap<>();
    
    private static final long SPAWN_ENTRY_POST_TAG_MS = 30000L;
    
    private static class CombatTag {
        final long expiryTime;
        final UUID lastDamager;
        
        CombatTag(long expiryTime, UUID lastDamager) {
            this.expiryTime = expiryTime;
            this.lastDamager = lastDamager;
        }
    }
    
    public CombatTagServiceImpl(Plugin plugin,
                                PlayerStateRepository playerStateRepository,
                                HeartService heartService,
                                String blockedKickMessage,
                                long pearlPostTagMs,
                                long pearlCombatCooldownMs) {
        this.plugin = plugin;
        this.playerStateRepository = playerStateRepository;
        this.heartService = heartService;
        this.blockedKickMessage = blockedKickMessage;
        this.pearlPostTagMs = Math.max(0L, pearlPostTagMs);
        this.pearlCombatCooldownMs = Math.max(0L, pearlCombatCooldownMs);
        startCleanupTask();
    }
    
    @Override
    public void tagPlayer(Player player, Player lastDamager) {
        long expiryTime = System.currentTimeMillis() + TAG_DURATION_MS;
        UUID damagerUuid = lastDamager != null ? lastDamager.getUniqueId() : null;
        activeTags.put(player.getUniqueId(), new CombatTag(expiryTime, damagerUuid));
        echestCooldowns.put(player.getUniqueId(), expiryTime + 30000L);
        afkCooldowns.put(player.getUniqueId(), expiryTime + 180000L);
        spawnEntryCooldowns.put(player.getUniqueId(), expiryTime + SPAWN_ENTRY_POST_TAG_MS);
        pearlBlockCooldowns.put(player.getUniqueId(), expiryTime + pearlPostTagMs);
    }
    
    @Override
    public boolean isTagged(UUID playerId) {
        CombatTag tag = activeTags.get(playerId);
        if (tag == null) {
            return false;
        }
        if (System.currentTimeMillis() >= tag.expiryTime) {
            activeTags.remove(playerId);
            return false;
        }
        return true;
    }
    
    @Override
    public long getRemainingTagMs(UUID playerId) {
        CombatTag tag = activeTags.get(playerId);
        if (tag == null) {
            return 0;
        }
        long remaining = tag.expiryTime - System.currentTimeMillis();
        if (remaining <= 0) {
            activeTags.remove(playerId);
            return 0;
        }
        return remaining;
    }
    
    @Override
    public UUID getLastDamager(UUID playerId) {
        CombatTag tag = activeTags.get(playerId);
        if (tag == null || System.currentTimeMillis() >= tag.expiryTime) {
            return null;
        }
        return tag.lastDamager;
    }
    
    @Override
    public void clearTag(UUID playerId) {
        activeTags.remove(playerId);
    }
    
    @Override
    public void handleCombatLogout(Player player) {
        UUID killerId = getLastDamager(player.getUniqueId());
        Location logoutSpot = player.getLocation().clone();

        CombatLogoutInventory.dropAndClear(player, logoutSpot);
        Player killer = killerId != null ? Bukkit.getPlayer(killerId) : null;
        heartService.applyKillHeartTransfer(player, killer, killerId, logoutSpot, blockedKickMessage);

        playerStateRepository.setCombatLogPending(player.getUniqueId(), System.currentTimeMillis());
        clearTag(player.getUniqueId());
    }

    @Override
    public long getEchestCooldownRemainingMs(UUID playerId) {
        Long expiry = echestCooldowns.get(playerId);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            echestCooldowns.remove(playerId);
            return 0;
        }
        return remaining;
    }

    @Override
    public boolean isPearlBlocked(UUID playerId) {
        return getPearlBlockRemainingMs(playerId) > 0;
    }

    @Override
    public long getPearlBlockRemainingMs(UUID playerId) {
        long now = System.currentTimeMillis();
        long remaining = 0;

        if (isTagged(playerId)) {
            Long readyAt = pearlUseCooldowns.get(playerId);
            if (readyAt != null && readyAt > now) {
                remaining = readyAt - now;
            }
        } else {
            Long expiry = pearlBlockCooldowns.get(playerId);
            if (expiry != null && expiry > now) {
                remaining = expiry - now;
            }
        }
        return remaining;
    }

    @Override
    public void recordCombatPearlUse(UUID playerId) {
        if (!isTagged(playerId) || pearlCombatCooldownMs <= 0L) {
            return;
        }
        pearlUseCooldowns.put(playerId, System.currentTimeMillis() + pearlCombatCooldownMs);
    }

    @Override
    public long getSpawnEntryCooldownRemainingMs(UUID playerId) {
        Long expiry = spawnEntryCooldowns.get(playerId);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            spawnEntryCooldowns.remove(playerId);
            return 0;
        }
        return remaining;
    }

    @Override
    public boolean isSpawnEntryBlocked(UUID playerId) {
        return isTagged(playerId) || getSpawnEntryCooldownRemainingMs(playerId) > 0;
    }

    @Override
    public long getAfkCooldownRemainingMs(UUID playerId) {
        Long expiry = afkCooldowns.get(playerId);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            afkCooldowns.remove(playerId);
            return 0;
        }
        return remaining;
    }
    
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            activeTags.entrySet().removeIf(entry -> now >= entry.getValue().expiryTime);
            echestCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
            afkCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
            spawnEntryCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
            pearlBlockCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
            pearlUseCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
        }, 100L, 100L);
    }
}
