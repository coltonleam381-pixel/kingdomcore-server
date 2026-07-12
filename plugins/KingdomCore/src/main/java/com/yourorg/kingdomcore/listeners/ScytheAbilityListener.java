package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScytheAbilityListener implements Listener {
    private static final long ARM_WINDOW_MS = 10_000L;
    private static final long COOLDOWN_MS = 30_000L;

    private final Plugin plugin;
    private final ItemIdentityService itemIdentityService;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> armedUntil = new ConcurrentHashMap<>();

    public ScytheAbilityListener(Plugin plugin,
                                 ItemIdentityService itemIdentityService,
                                 WorldGuardHook worldGuardHook,
                                 SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.itemIdentityService = itemIdentityService;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            return;
        }
        if (!itemIdentityService.isScytheItem(player.getInventory().getItemInMainHand())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (cooldownUntil.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long armedTo = now + ARM_WINDOW_MS;
        armedUntil.put(playerId, armedTo);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Long expires = armedUntil.get(playerId);
            if (expires != null && expires <= System.currentTimeMillis()) {
                armedUntil.remove(playerId);
                cooldownUntil.put(playerId, System.currentTimeMillis() + COOLDOWN_MS);
            }
        }, ARM_WINDOW_MS / 50L);
    }

    @EventHandler
    public void onAbilityCooldownReset(com.yourorg.kingdomcore.events.AbilityCooldownResetEvent event) {
        if (event.getItem().equals("all") || event.getItem().equals("scythe")) {
            cooldownUntil.remove(event.getPlayer().getUniqueId());
            armedUntil.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!itemIdentityService.isScytheItem(player.getInventory().getItemInMainHand())) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Long armedTo = armedUntil.get(playerId);
        if (armedTo == null || armedTo < System.currentTimeMillis()) {
            return;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 30, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));

        armedUntil.remove(playerId);
        cooldownUntil.put(playerId, System.currentTimeMillis() + COOLDOWN_MS);
    }

    public String getScytheHudLine(Player player, long now) {
        if (!itemIdentityService.isScytheItem(player.getInventory().getItemInMainHand())) {
            return null;
        }
        UUID id = player.getUniqueId();
        Long armedTo = armedUntil.get(id);
        if (armedTo != null && armedTo > now) {
            double remainingSeconds = (armedTo - now) / 1000.0;
            return String.format("§5☠ Armed... §f%.1fs", Math.max(0.0, remainingSeconds));
        } else {
            long cdRemaining = Math.max(0, cooldownUntil.getOrDefault(id, 0L) - now);
            String cdStr = cdRemaining > 0 ? "§c" + (cdRemaining / 1000) + "s" : "§aReady";
            return "§5☠ Scythe §8| §eSoul Reaping: " + cdStr;
        }
    }
}
