package com.yourorg.kingdomcore.service;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase Walk state manager.
 * Keeps players in regular gameplay mode (no spectator), with temporary phase buffs.
 */
public class PhaseWalkService {
    private final Map<UUID, PhaseState> activePhases = new HashMap<>();

    public void startPhase(Player player, long durationTicks, int level) {
        UUID playerId = player.getUniqueId();

        Map<PotionEffectType, PotionEffect> originalEffects = new HashMap<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            originalEffects.put(effect.getType(), effect);
        }

        PhaseState state = new PhaseState(
                playerId,
                originalEffects,
                System.currentTimeMillis() + (durationTicks * 50L),
                level
        );
        activePhases.put(playerId, state);

        // Keep normal gameplay mode and no spectator vision/flying behavior.
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setAllowFlight(false);
        player.setFlying(false);

        applyLevelEffects(player, level);
    }

    public void endPhase(Player player) {
        UUID playerId = player.getUniqueId();
        PhaseState state = activePhases.remove(playerId);
        if (state == null) {
            return;
        }
        restoreOriginalEffects(player, state.originalEffects);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    public boolean isInPhase(Player player) {
        return activePhases.containsKey(player.getUniqueId());
    }

    public PhaseState getPhaseState(UUID playerId) {
        return activePhases.get(playerId);
    }

    public void checkAndCleanupExpiredPhases(Player player) {
        UUID playerId = player.getUniqueId();
        PhaseState state = activePhases.get(playerId);
        if (state != null && System.currentTimeMillis() >= state.expirationTimeMs) {
            endPhase(player);
        }
    }

    public void cleanup(Player player) {
        if (activePhases.containsKey(player.getUniqueId())) {
            endPhase(player);
        }
    }

    /**
     * If the player is pressing into a wall, step them through to nearest open space.
     */
    public void tryPhaseThroughWall(Player player) {
        if (!isInPhase(player)) {
            return;
        }
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().clone();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-6) {
            return;
        }
        dir.normalize();

        Location ahead = eye.clone().add(dir.clone().multiply(0.7));
        if (!isSolid(ahead.getBlock())) {
            return;
        }

        Location base = player.getLocation().clone();
        double y = base.getY();
        for (double d = 0.75; d <= 6.0; d += 0.25) {
            Location candidate = base.clone().add(dir.clone().multiply(d));
            candidate.setY(y);
            if (isPassableForPlayer(candidate)) {
                player.teleport(candidate);
                return;
            }
        }
    }

    private void applyLevelEffects(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));

        switch (level) {
            case 2 -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
            case 3 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
            }
            default -> {
            }
        }
    }

    private void restoreOriginalEffects(Player player, Map<PotionEffectType, PotionEffect> originalEffects) {
        player.clearActivePotionEffects();
        for (PotionEffect effect : originalEffects.values()) {
            player.addPotionEffect(effect);
        }
    }

    private boolean isSolid(Block block) {
        return block != null && block.getType().isSolid();
    }

    private boolean isPassableForPlayer(Location feetLoc) {
        Block feet = feetLoc.getBlock();
        Block head = feetLoc.clone().add(0, 1, 0).getBlock();
        Block below = feetLoc.clone().add(0, -1, 0).getBlock();
        return (feet.isPassable() || !feet.getType().isSolid())
                && (head.isPassable() || !head.getType().isSolid())
                && below.getType().isSolid();
    }

    public static class PhaseState {
        public final UUID playerId;
        public final Map<PotionEffectType, PotionEffect> originalEffects;
        public final long expirationTimeMs;
        public final int level;

        public PhaseState(UUID playerId,
                          Map<PotionEffectType, PotionEffect> originalEffects,
                          long expirationTimeMs,
                          int level) {
            this.playerId = playerId;
            this.originalEffects = originalEffects;
            this.expirationTimeMs = expirationTimeMs;
            this.level = level;
        }
    }
}
