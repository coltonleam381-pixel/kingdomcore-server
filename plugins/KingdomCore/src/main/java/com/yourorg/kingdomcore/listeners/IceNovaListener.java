package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.abilities.IceNovaAbility;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Listener for Ice Nova ability effects.
 * Applies debuffs and prevents jumping while player is on ice blocks.
 */
public class IceNovaListener implements Listener {
    private final IceNovaAbility iceNovaAbility;
    // Track last debuff application per player to avoid spam
    private final Map<UUID, Long> lastDebuffTime = new ConcurrentHashMap<>();
    private static final long DEBUFF_INTERVAL_MS = 1000;

    public IceNovaListener(IceNovaAbility iceNovaAbility) {
        this.iceNovaAbility = iceNovaAbility;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Block blockBelow = player.getLocation().subtract(0, 1, 0).getBlock();

        if (iceNovaAbility.isIceNovaBlock(blockBelow)) {
            handlePlayerOnIce(player, blockBelow);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Cleanup player cache on quit
        lastDebuffTime.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onIceBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!iceNovaAbility.isIceNovaBlock(block)) {
            return;
        }
        int level = iceNovaAbility.getIceLevelAt(block);
        if (level >= 2) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle player standing on ice: apply debuffs and block jumping.
     */
    private void handlePlayerOnIce(Player player, Block blockBelow) {
        // Caster immunity on own ice.
        java.util.UUID ownerId = iceNovaAbility.getTopOwnerAt(blockBelow);
        if (ownerId != null && ownerId.equals(player.getUniqueId())) {
            return;
        }

        // Get ice level from the block
        int iceLevel = iceNovaAbility.getIceLevelAt(blockBelow);
        if (iceLevel <= 0) {
            return;
        }

        // Apply debuffs periodically
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastDebuffTime.get(playerId);

        if (lastTime == null || now - lastTime >= DEBUFF_INTERVAL_MS) {
            applyDebuff(player, iceLevel);
            lastDebuffTime.put(playerId, now);
        }

        // Block jumping - cancel upward velocity
        Vector velocity = player.getVelocity();
        if (velocity.getY() > 0) {
            // Hard suppress upward jump impulse.
            velocity.setY(-0.15);
            player.setVelocity(velocity);
        }
    }

    /**
     * Apply debuffs based on ice level.
     * L1: Slowness I + Blindness 2s
     * L2: Slowness II + Blindness 2s
     * L3: Slowness III + Weakness I + Blindness 2s
     */
    private void applyDebuff(Player player, int level) {
        int slownessAmplifier = Math.max(0, level - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45, slownessAmplifier, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 45, 0, false, false));
        }
    }
}
