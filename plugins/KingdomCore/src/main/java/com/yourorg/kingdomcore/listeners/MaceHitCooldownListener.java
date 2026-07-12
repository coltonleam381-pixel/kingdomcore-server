package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.events.AbilityCooldownResetEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaceHitCooldownListener implements Listener {
    private final ItemIdentityService itemIdentityService;
    private final long cooldownMs;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotifyAt = new ConcurrentHashMap<>();

    public MaceHitCooldownListener(ItemIdentityService itemIdentityService, long cooldownMs) {
        this.itemIdentityService = itemIdentityService;
        this.cooldownMs = Math.max(0L, cooldownMs);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent event) {
        if (cooldownMs <= 0L) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (!isMace(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID attackerId = attacker.getUniqueId();
        if (cooldownUntil.getOrDefault(attackerId, 0L) > now) {
            event.setCancelled(true);
            notifyCooldown(attacker, now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMaceHitApplied(EntityDamageByEntityEvent event) {
        if (cooldownMs <= 0L) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!isMace(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getFinalDamage() <= 0.0D) {
            return;
        }

        cooldownUntil.put(attacker.getUniqueId(), System.currentTimeMillis() + cooldownMs);
    }

    @EventHandler
    public void onAbilityCooldownReset(AbilityCooldownResetEvent event) {
        if (event.getItem().equals("all") || event.getItem().equals("mace")) {
            UUID playerId = event.getPlayer().getUniqueId();
            cooldownUntil.remove(playerId);
            lastNotifyAt.remove(playerId);
        }
    }

    private boolean isMace(ItemStack stack) {
        return stack != null
                && stack.getType() == org.bukkit.Material.MACE
                && itemIdentityService.isMaceItem(stack);
    }

    private void notifyCooldown(Player player, long now) {
        UUID playerId = player.getUniqueId();
        if (now - lastNotifyAt.getOrDefault(playerId, 0L) < 1000L) {
            return;
        }
        lastNotifyAt.put(playerId, now);
        long remainingMs = cooldownUntil.getOrDefault(playerId, 0L) - now;
        int remainingSeconds = Math.max(1, (int) Math.ceil(remainingMs / 1000.0));
        player.sendActionBar(Component.text(
                "Mace on cooldown (" + remainingSeconds + "s)",
                NamedTextColor.RED));
    }
}
