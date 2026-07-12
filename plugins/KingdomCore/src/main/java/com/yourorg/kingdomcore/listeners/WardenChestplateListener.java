package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import com.yourorg.kingdomcore.service.UniqueItemService;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WardenChestplateListener implements Listener {
    private static final long COOLDOWN_MS = 300_000L;
    static final int MAX_USES = 13;

    private final Plugin plugin;
    private final ItemIdentityService itemIdentityService;
    private final UniqueItemService uniqueItemService;
    private final WorldGuardHook worldGuardHook;
    private final SpawnRegionPolicy spawnRegionPolicy;
    final NamespacedKey usesLeftKey;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> freezeTasks = new ConcurrentHashMap<>();

    public WardenChestplateListener(Plugin plugin,
                                    ItemIdentityService itemIdentityService,
                                    UniqueItemService uniqueItemService,
                                    WorldGuardHook worldGuardHook,
                                    SpawnRegionPolicy spawnRegionPolicy) {
        this.plugin = plugin;
        this.itemIdentityService = itemIdentityService;
        this.uniqueItemService = uniqueItemService;
        this.worldGuardHook = worldGuardHook;
        this.spawnRegionPolicy = spawnRegionPolicy;
        this.usesLeftKey = new NamespacedKey(plugin, "warden-cp-uses-left");
    }

    // ─── HUD methods called by AbilityScoreboard ──────────────────────────────

    /** Non-null = player is wearing the Warden CP (presence check). */
    public String getWardenSidebarLine(UUID playerId, ItemStack chestplate, long nowMs) {
        if (!itemIdentityService.isWardenCpItem(chestplate)) return null;
        return "present";
    }

    /** The actual CD + uses text line. */
    public String getWardenStatusLine(UUID playerId, ItemStack chestplate, long nowMs) {
        if (!itemIdentityService.isWardenCpItem(chestplate)) return null;
        long cdEnd = cooldownUntil.getOrDefault(playerId, 0L);
        int uses = MAX_USES;
        if (chestplate != null && chestplate.getItemMeta() != null) {
            uses = chestplate.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(usesLeftKey, PersistentDataType.INTEGER, MAX_USES);
        }
        String cdText = cdEnd > nowMs ? "§c" + ((cdEnd - nowMs + 999) / 1000) + "s" : "§aReady";
        return cdText + " §8| §f" + uses + "/" + MAX_USES;
    }

    // ─── Ability cooldown reset ────────────────────────────────────────────────

    @EventHandler
    public void onAbilityCooldownReset(com.yourorg.kingdomcore.events.AbilityCooldownResetEvent event) {
        if (event.getItem().equals("all") || event.getItem().equals("warden_cp")) {
            cooldownUntil.remove(event.getPlayer().getUniqueId());
        }
    }

    // ─── Right Click activation (air OR block, no sneak required) ─────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        // Main hand only
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Right-click only (air and block both work)
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        // Must be sneaking (Shift + RMB)
        if (!player.isSneaking()) return;

        // Must be wearing warden chestplate
        ItemStack chest = player.getInventory().getChestplate();
        if (!itemIdentityService.isWardenCpItem(chest)) return;

        // Cancel the interact so no block is opened
        event.setCancelled(true);

        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            player.sendMessage("§cYou cannot use the Warden's blast in spawn!");
            return;
        }

        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        if (cooldownUntil.getOrDefault(playerId, 0L) > now) {
            return; // CD shown silently in sidebar / actionbar
        }

        cooldownUntil.put(playerId, now + COOLDOWN_MS);
        lockAndLaunch(player);

        // Direction captured RIGHT before shot fires (1 second after click)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            Vector direction = player.getEyeLocation().getDirection().clone().normalize();
            fireBlast(player, direction);
        }, 20L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> unlockPlayer(player), 40L);

        reduceDurabilityUse(player, chest);
    }

    // ─── Launch ───────────────────────────────────────────────────────────────

    private void lockAndLaunch(Player player) {
        player.setVelocity(new Vector(0, 0.3, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 25, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 20, false, false));

        BukkitTask freezeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            double y = player.getVelocity().getY();
            player.setVelocity(new Vector(0, Math.min(Math.max(y, 0.0), 0.05), 0));
        }, 0L, 1L);
        freezeTasks.put(player.getUniqueId(), freezeTask);
    }

    private void unlockPlayer(Player player) {
        BukkitTask task = freezeTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        player.removePotionEffect(PotionEffectType.LEVITATION);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    // ─── Sonic Boom blast ─────────────────────────────────────────────────────

    private void fireBlast(Player caster, Vector direction) {
        if (!caster.isOnline() || caster.isDead()) return;

        Location origin = caster.getEyeLocation().clone();
        Vector dir = direction.clone().normalize();
        Set<LivingEntity> hit = new HashSet<>();

        for (double d = 0.0; d <= 16.0; d += 0.5) {
            Location point = origin.clone().add(dir.clone().multiply(d));
            caster.getWorld().spawnParticle(
                    Particle.DUST, point, 10, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(25, 200, 230), 1.5f));
            caster.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);

            for (LivingEntity entity : caster.getWorld().getNearbyLivingEntities(point, 3.0, 3.0, 3.0)) {
                if (entity.equals(caster)) continue;
                if (hit.add(entity)) {
                    if (plugin instanceof com.yourorg.kingdomcore.KingdomCorePlugin kcPlugin) {
                        kcPlugin.getDamageService().applyTrueDamage(entity, caster, 12.0, point, 1.1);
                    }
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.0f);
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.75f);
                    if (entity instanceof Player victim) {
                        victim.playHurtAnimation(0.0f);
                    }
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
                }
            }
        }
    }

    // ─── 20 uses, each blast = 1 use ──────────────────────────────────────────

    private void reduceDurabilityUse(Player player, ItemStack chest) {
        if (chest == null) return;
        ItemMeta meta = chest.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int usesLeft = pdc.getOrDefault(usesLeftKey, PersistentDataType.INTEGER, MAX_USES);
        usesLeft -= 1;
        pdc.set(usesLeftKey, PersistentDataType.INTEGER, Math.max(0, usesLeft));

        if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
            int max = damageable.getMaxDamage();
            int used = MAX_USES - Math.max(0, usesLeft);
            int mapped = (int) Math.round((used / (double) MAX_USES) * max);
            damageable.setDamage(Math.min(max, mapped));
        }

        chest.setItemMeta(meta);

        if (usesLeft <= 0) {
            player.getInventory().setChestplate(null);
            uniqueItemService.markDestroyed("warden_cp");
            player.sendMessage("§cYour Warden Chestplate has shattered!");
        }
    }

    // ─── Block vanilla durability loss ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (itemIdentityService.isWardenCpItem(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
