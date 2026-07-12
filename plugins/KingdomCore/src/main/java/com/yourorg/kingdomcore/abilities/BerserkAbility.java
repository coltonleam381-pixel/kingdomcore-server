package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Berserk ability - increased attack speed and critical hits.
 */
public class BerserkAbility implements AbilityHandler, AbilityHudProvider {
    private final Plugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final CooldownService cooldownService;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, BerserkState> activeBerserks = new HashMap<>();
    
    private static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("7f3e51e0-8b1d-4c9a-9f2e-1d5a6c8b9e4f");
    private static final String ATTACK_SPEED_MODIFIER_NAME = "berserk_attack_speed";
    
    private static class BerserkState {
        final int taskId;
        final boolean hasCrits;
        final int level;
        final long endsAtMs;
        
        BerserkState(int taskId, boolean hasCrits, int level, long endsAtMs) {
            this.taskId = taskId;
            this.hasCrits = hasCrits;
            this.level = level;
            this.endsAtMs = endsAtMs;
        }
    }
    
    public BerserkAbility(Plugin plugin, WorldGuardHook worldGuardHook, CooldownService cooldownService, SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.cooldownService = cooldownService;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }
    
    @Override
    public String getAbilityId() {
        return "berserk";
    }
    
    @Override
    public boolean onRightClick(Player player, int level, PlayerInteractEvent event) {
        // Level 0 = locked
        if (level == 0) {
            return false;
        }
        
        // Block in spawn
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return false;
        }
        
        if (activeBerserks.containsKey(player.getUniqueId())) {
            return false;
        }
        
        // Duration and effects based on level
        int durationTicks;
        boolean hasCrits;
        
        switch (level) {
            case 1:
                durationTicks = 100; // 5 seconds
                hasCrits = false;
                break;
            case 2:
                durationTicks = 120; // 6 seconds
                hasCrits = true;
                break;
            case 3:
                durationTicks = 140; // 7 seconds
                hasCrits = true;
                // Speed one level higher than current speed.
                PotionEffect existingSpeed = player.getPotionEffect(PotionEffectType.SPEED);
                int amp = (existingSpeed == null) ? 0 : Math.min(existingSpeed.getAmplifier() + 1, 4);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, amp, false, false));
                break;
            default:
                return false;
        }
        
        // Vanilla player attack speed is 4.0. +25% = 5.0, so additive modifier +1.0.
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            // Remove existing modifier if any
            attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER_UUID);
            
            // Add +25% attack speed compared to vanilla.
            AttributeModifier modifier = new AttributeModifier(
                ATTACK_SPEED_MODIFIER_UUID,
                ATTACK_SPEED_MODIFIER_NAME,
                1.0,
                AttributeModifier.Operation.ADD_NUMBER
            );
            attackSpeed.addModifier(modifier);
        }
        
        // Visual feedback
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.5f);
        player.spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);
        
        // Schedule cleanup after duration
        long endsAtMs = System.currentTimeMillis() + durationTicks * 50L;
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> endBerserk(player, true), durationTicks);
        activeBerserks.put(player.getUniqueId(), new BerserkState(taskId, hasCrits, level, endsAtMs));
        return true;
    }
    
    /**
     * Check if player should have critical hits.
     * This should be called from damage listener.
     */
    public boolean shouldForceCrit(Player player) {
        BerserkState state = activeBerserks.get(player.getUniqueId());
        return state != null && state.hasCrits;
    }
    
    @Override
    public boolean onLeftClick(Player player, int level) {
        return false;
    }
    
    @Override
    public boolean onSneakRightClick(Player player, int level) {
        return false;
    }
    
    @Override
    public boolean onSpace(Player player) {
        return false;
    }
    
    @Override
    public void cleanup(Player player) {
        endBerserk(player, false);
    }

    @Override
    public String getHudLine(UUID playerId, long nowMs) {
        BerserkState state = activeBerserks.get(playerId);
        if (state == null) {
            return null;
        }
        long left = Math.max(0L, state.endsAtMs - nowMs);
        long sec = (left + 999L) / 1000L;
        return "§cBerserk §7[L" + state.level + "] §8| §e" + sec + "s";
    }

    private void endBerserk(Player player, boolean startCooldown) {
        BerserkState state = activeBerserks.remove(player.getUniqueId());
        if (state != null) {
            if (state.taskId > 0) {
                Bukkit.getScheduler().cancelTask(state.taskId);
            }
            if (startCooldown) {
                long now = System.currentTimeMillis();
                long cd = switch (state.level) {
                    case 1 -> 50000L;
                    case 2 -> 45000L;
                    case 3 -> 40000L;
                    default -> 50000L;
                };
                cooldownService.markUsed(player.getUniqueId(), getAbilityId(), now + cd);
            }
        }
        
        // Remove attack speed modifier
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER_UUID);
        }
    }
}
