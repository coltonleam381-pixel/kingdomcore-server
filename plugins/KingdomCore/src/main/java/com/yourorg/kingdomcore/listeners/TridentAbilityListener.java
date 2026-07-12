package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.core.services.KingdomDamageService;
import com.yourorg.kingdomcore.util.SpawnRegionPolicy;
import com.yourorg.kingdomcore.util.TabTeamResolver;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TridentAbilityListener implements Listener {
    static final String LIGHTNING_OWNER_META = "kc_trident_lightning_owner";
    static final String TRIDENT_PROJECTILE_META = "kc_trident_projectile";
    static final String LOYALTY_RETURNING_META = "kc_trident_loyalty_returning";
    static final String LOYALTY_COMPLETED_META = "kc_trident_loyalty_completed";
    private static final double LIGHTNING_DAMAGE = 4.0D;
    private static final long FLIGHT_TICKS = 100L;
    private static final long COMBO_WINDOW_MS = 3_000L;
    private static final int MIN_THROW_TICKS = 10;
    private static final double THROW_VELOCITY = 2.5D;
    private static final int LOYALTY_PICKUP_DELAY_TICKS = 3;
    private static final int LOYALTY_MAX_AIR_TICKS = 50;
    private static final int LOYALTY_HIT_RETURN_DELAY_TICKS = 4;

    private final Plugin plugin;
    private final ItemIdentityService itemIdentityService;
    private final KingdomDamageService damageService;
    private final SpawnRegionPolicy spawnRegionPolicy;
    private final TabTeamResolver tabTeamResolver;
    private final Map<UUID, BukkitTask> flightTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> flightStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> noFallDamageUntil = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> comboHits = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> comboTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Long> comboLastHit = new ConcurrentHashMap<>();
    private final Set<UUID> pendingLightningTargets = ConcurrentHashMap.newKeySet();
    private final Set<String> processingLightning = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dryThrowCharging = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> dryThrowTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> dryThrowWasRaised = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> dryThrowMonitorTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCustomThrowAt = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> loyaltyFlightTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> loyaltyReturnTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> loyaltyStuckTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> loyaltyAirTicks = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> loyaltyReturnItems = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> loyaltyLevels = new ConcurrentHashMap<>();

    public TridentAbilityListener(Plugin plugin,
                                  ItemIdentityService itemIdentityService,
                                  KingdomDamageService damageService,
                                  SpawnRegionPolicy spawnRegionPolicy,
                                  TabTeamResolver tabTeamResolver) {
        this.plugin = plugin;
        this.itemIdentityService = itemIdentityService;
        this.damageService = damageService;
        this.spawnRegionPolicy = spawnRegionPolicy;
        this.tabTeamResolver = tabTeamResolver;
    }

    @EventHandler
    public void onAbilityCooldownReset(com.yourorg.kingdomcore.events.AbilityCooldownResetEvent event) {
        if (event.getItem().equals("all") || event.getItem().equals("trident")) {
            comboHits.remove(event.getPlayer().getUniqueId());
            comboTarget.remove(event.getPlayer().getUniqueId());
            comboLastHit.remove(event.getPlayer().getUniqueId());
        }
    }

    /** Shift + RMB = surf flight. Plain RMB = vanilla throw / vanilla riptide (player's enchants). */
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
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!itemIdentityService.isTridentItem(hand)) {
            return;
        }
        if (player.isSneaking()) {
            if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
                return;
            }
            if (flightTasks.containsKey(player.getUniqueId())) {
                return;
            }
            cancelDryThrowCharge(player.getUniqueId());
            event.setCancelled(true);
            startFlight(player);
            return;
        }
        if (needsDryLandCustomThrow(player, hand)) {
            if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
                return;
            }
            if (action == Action.RIGHT_CLICK_BLOCK
                    && event.useInteractedBlock() == org.bukkit.event.Event.Result.ALLOW) {
                return;
            }
            beginDryThrowCharge(player);
        }
    }

    /**
     * Riptide blocks vanilla throws on dry land. When both Riptide and Loyalty are on the Poseidon trident,
     * we launch the projectile ourselves so throw + riptide-in-rain both work.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!needsDryLandCustomThrow(player, item)) {
            return;
        }
        if (player.isSneaking()) {
            return;
        }
        if (event.getTicksHeldFor() < MIN_THROW_TICKS) {
            cancelDryThrowCharge(player.getUniqueId());
            return;
        }
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            cancelDryThrowCharge(player.getUniqueId());
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!itemIdentityService.isTridentItem(hand)) {
            cancelDryThrowCharge(player.getUniqueId());
            return;
        }
        cancelDryThrowCharge(player.getUniqueId());
        tryFinishDryThrow(player, hand, event.getTicksHeldFor());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) {
            return;
        }
        if (!(trident.getShooter() instanceof Player player)) {
            return;
        }
        if (!isCustomTridentProjectile(trident, player)) {
            return;
        }
        if (spawnRegionPolicy.blocksAbilities(player.getLocation())) {
            event.setCancelled(true);
            return;
        }
        trident.setMetadata(TRIDENT_PROJECTILE_META, new FixedMetadataValue(plugin, true));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!loyaltyReturnItems.containsKey(trident.getUniqueId())) {
                registerLoyaltyReturn(trident, trident.getItem());
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHitLoyalty(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) {
            return;
        }
        if (!(trident.getShooter() instanceof Player thrower)) {
            return;
        }
        if (!isCustomTridentProjectile(trident, thrower)) {
            return;
        }
        if (!loyaltyReturnItems.containsKey(trident.getUniqueId())) {
            registerLoyaltyReturn(trident, trident.getItem());
        }
        int loyalty = loyaltyLevels.getOrDefault(trident.getUniqueId(), resolveLoyaltyLevel(trident.getItem()));
        if (loyalty <= 0) {
            return;
        }
        trident.setHasDealtDamage(true);
        loyaltyStuckTicks.put(trident.getUniqueId(), LOYALTY_PICKUP_DELAY_TICKS);
        UUID tridentId = trident.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!trident.isValid() || trident.isDead() || trident.hasMetadata(LOYALTY_COMPLETED_META)) {
                restoreLoyaltyItemToOwner(tridentId, trident);
                cleanupLoyalty(tridentId);
                return;
            }
            if (trident.getShooter() instanceof Player owner) {
                startLoyaltyReturnFlight(trident, owner, loyalty);
            }
        }, LOYALTY_HIT_RETURN_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) {
            return;
        }
        if (!(trident.getShooter() instanceof Player thrower)) {
            return;
        }
        if (!isCustomTridentProjectile(trident, thrower)) {
            return;
        }
        if (spawnRegionPolicy.blocksAbilities(thrower.getLocation())) {
            return;
        }
        if (!(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        procLightning(thrower, target);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.CUSTOM) {
            return;
        }
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!itemIdentityService.isTridentItem(damager.getInventory().getItemInMainHand())) {
            return;
        }
        if (spawnRegionPolicy.blocksAbilities(damager.getLocation())) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID damagerId = damager.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (now - comboLastHit.getOrDefault(damagerId, 0L) > COMBO_WINDOW_MS || !targetId.equals(comboTarget.get(damagerId))) {
            comboHits.put(damagerId, 1);
            comboTarget.put(damagerId, targetId);
        } else {
            int hits = comboHits.getOrDefault(damagerId, 0) + 1;
            if (hits >= 3) {
                comboHits.remove(damagerId);
                comboTarget.remove(damagerId);
                procLightning(damager, target);
            } else {
                comboHits.put(damagerId, hits);
            }
        }
        comboLastHit.put(damagerId, now);

        if (event.getEntity() instanceof Player victim) {
            comboHits.remove(victim.getUniqueId());
            comboTarget.remove(victim.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLightningDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike lightning)) {
            return;
        }
        if (lightning.hasMetadata(LIGHTNING_OWNER_META)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof LivingEntity victim
                && pendingLightningTargets.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Long expire = noFallDamageUntil.get(player.getUniqueId());
        if (expire != null && expire > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopFlight(event.getPlayer());
        clearCombo(event.getPlayer().getUniqueId());
        cancelDryThrowCharge(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stopFlight(event.getEntity());
        clearCombo(event.getEntity().getUniqueId());
        cancelDryThrowCharge(event.getEntity().getUniqueId());
    }

    private void procLightning(Player owner, LivingEntity target) {
        if (shouldSkipLightningTarget(owner, target)) {
            return;
        }
        String procKey = owner.getUniqueId() + ":" + target.getUniqueId();
        if (!processingLightning.add(procKey)) {
            return;
        }
        UUID targetId = target.getUniqueId();
        pendingLightningTargets.add(targetId);
        try {
            Location strikeAt = target.getLocation();
            LightningStrike lightning = strikeAt.getWorld().strikeLightning(strikeAt);
            lightning.setMetadata(LIGHTNING_OWNER_META, new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
            applyLightningDamage(owner, target);
        } finally {
            processingLightning.remove(procKey);
            plugin.getServer().getScheduler().runTask(plugin, () -> pendingLightningTargets.remove(targetId));
        }
    }

    private void applyLightningDamage(Player owner, LivingEntity target) {
        if (target == null || target.isDead() || shouldSkipLightningTarget(owner, target)) {
            return;
        }
        if (damageService != null) {
            damageService.applyTrueDamage(target, owner, LIGHTNING_DAMAGE, target.getLocation());
            return;
        }
        target.setNoDamageTicks(0);
        target.damage(LIGHTNING_DAMAGE, owner);
    }

    private boolean shouldSkipLightningTarget(Player owner, LivingEntity target) {
        if (target.equals(owner)) {
            return true;
        }
        return target instanceof Player victim && tabTeamResolver.isSameTeam(owner, victim);
    }

    private boolean needsDryLandCustomThrow(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT || !itemIdentityService.isTridentItem(item)) {
            return false;
        }
        if (item.getEnchantmentLevel(Enchantment.RIPTIDE) <= 0) {
            return false;
        }
        return !player.isInWaterOrRain();
    }

    private void beginDryThrowCharge(Player player) {
        UUID playerId = player.getUniqueId();
        if (dryThrowCharging.contains(playerId)) {
            return;
        }
        dryThrowCharging.add(playerId);
        dryThrowTicks.put(playerId, 0);
        dryThrowWasRaised.put(playerId, false);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !dryThrowCharging.contains(playerId)) {
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!needsDryLandCustomThrow(player, hand)) {
                cancelDryThrowCharge(playerId);
                return;
            }
            if (!player.isHandRaised()) {
                player.startUsingItem(EquipmentSlot.HAND);
            }
        });
        dryThrowMonitorTasks.computeIfAbsent(playerId, id -> plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelDryThrowCharge(id);
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!dryThrowCharging.contains(id)) {
                return;
            }
            if (!needsDryLandCustomThrow(player, hand)) {
                cancelDryThrowCharge(id);
                return;
            }
            if (player.isHandRaised()) {
                dryThrowWasRaised.put(id, true);
                dryThrowTicks.merge(id, 1, Integer::sum);
                return;
            }
            if (!Boolean.TRUE.equals(dryThrowWasRaised.get(id))) {
                return;
            }
            int ticks = dryThrowTicks.getOrDefault(id, 0);
            cancelDryThrowCharge(id);
            if (ticks >= MIN_THROW_TICKS && !spawnRegionPolicy.blocksAbilities(player.getLocation())) {
                tryFinishDryThrow(player, hand, ticks);
            }
        }, 1L, 1L));
    }

    private void cancelDryThrowCharge(UUID playerId) {
        dryThrowCharging.remove(playerId);
        dryThrowTicks.remove(playerId);
        dryThrowWasRaised.remove(playerId);
        BukkitTask task = dryThrowMonitorTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void tryFinishDryThrow(Player player, ItemStack hand, int ticksHeld) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        if (now - lastCustomThrowAt.getOrDefault(playerId, 0L) < 250L) {
            return;
        }
        lastCustomThrowAt.put(playerId, now);
        performCustomThrow(player, hand, ticksHeld);
    }

    private void performCustomThrow(Player player, ItemStack handStack, int ticksHeld) {
        float power = Math.min(1.0f, ticksHeld / (float) MIN_THROW_TICKS);
        if (power <= 0.0f) {
            return;
        }

        ItemStack thrownStack = handStack.asQuantity(1);
        GameMode mode = player.getGameMode();
        if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
            thrownStack = thrownStack.damage(1, player);
            if (thrownStack == null || thrownStack.getType().isAir()) {
                return;
            }
        }

        Vector velocity = player.getEyeLocation().getDirection().normalize().multiply(THROW_VELOCITY * power);
        Trident projectile = player.launchProjectile(Trident.class, velocity);
        projectile.setItem(thrownStack);
        projectile.setMetadata(TRIDENT_PROJECTILE_META, new FixedMetadataValue(plugin, true));
        ItemStack returnItem = thrownStack.clone();
        registerLoyaltyReturn(projectile, returnItem, resolveLoyaltyLevel(returnItem));

        int amount = handStack.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            handStack.setAmount(amount - 1);
        }
    }

    private int resolveLoyaltyLevel(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        int level = item.getEnchantmentLevel(Enchantment.LOYALTY);
        if (level > 0) {
            return level;
        }
        // Poseidon trident should always return even if enchant NBT is missing on the projectile copy.
        if (itemIdentityService.isTridentItem(item)) {
            return 3;
        }
        return 0;
    }

    private int enchantPreservationScore(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        int score = item.getEnchantments().values().stream().mapToInt(Integer::intValue).sum();
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            score += 2;
        }
        if (itemIdentityService.isTridentItem(item)) {
            score += 5;
        }
        return score;
    }

    private void registerLoyaltyReturn(Trident trident, ItemStack returnItem) {
        registerLoyaltyReturn(trident, returnItem, resolveLoyaltyLevel(returnItem));
    }

    private void registerLoyaltyReturn(Trident trident, ItemStack returnItem, int loyalty) {
        if (!trident.isValid() || trident.isDead()) {
            return;
        }
        UUID tridentId = trident.getUniqueId();
        if (returnItem == null || returnItem.getType().isAir()) {
            returnItem = trident.getItem();
        }
        if (returnItem == null || returnItem.getType().isAir()) {
            return;
        }
        if (loyalty <= 0) {
            loyalty = resolveLoyaltyLevel(returnItem);
        }
        if (loyalty <= 0) {
            return;
        }
        if (loyaltyReturnItems.containsKey(tridentId)) {
            ItemStack stored = loyaltyReturnItems.get(tridentId);
            if (stored != null && enchantPreservationScore(returnItem) > enchantPreservationScore(stored)) {
                loyaltyReturnItems.put(tridentId, returnItem.clone());
            }
            return;
        }
        // Vanilla loyalty conflicts with Riptide on the same item and can delete the entity without returning it.
        trident.setLoyaltyLevel(0);

        loyaltyReturnItems.put(tridentId, returnItem.clone());
        loyaltyLevels.put(tridentId, loyalty);
        loyaltyStuckTicks.put(tridentId, 0);
        loyaltyAirTicks.put(tridentId, 0);

        BukkitTask existingFlight = loyaltyFlightTasks.remove(tridentId);
        if (existingFlight != null) {
            existingFlight.cancel();
        }
        final int returnLoyalty = loyalty;
        loyaltyFlightTasks.put(tridentId, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!trident.isValid() || trident.isDead()) {
                restoreLoyaltyItemToOwner(tridentId, trident);
                cleanupLoyalty(tridentId);
                return;
            }
            if (trident.hasMetadata(LOYALTY_RETURNING_META) || trident.hasMetadata(LOYALTY_COMPLETED_META)) {
                return;
            }
            if (!(trident.getShooter() instanceof Player owner)) {
                dropStoredTridentItem(tridentId, trident.getLocation());
                trident.remove();
                cleanupLoyalty(tridentId);
                return;
            }

            boolean stuck = trident.isInBlock()
                    || trident.isOnGround()
                    || trident.hasDealtDamage()
                    || trident.getVelocity().lengthSquared() < 0.04D;
            if (stuck) {
                int stuckTicks = loyaltyStuckTicks.merge(tridentId, 1, Integer::sum);
                if (stuckTicks >= LOYALTY_PICKUP_DELAY_TICKS) {
                    startLoyaltyReturnFlight(trident, owner, returnLoyalty);
                }
                return;
            }

            loyaltyStuckTicks.put(tridentId, 0);
            int airTicks = loyaltyAirTicks.merge(tridentId, 1, Integer::sum);
            if (airTicks >= LOYALTY_MAX_AIR_TICKS) {
                startLoyaltyReturnFlight(trident, owner, returnLoyalty);
            }
        }, 1L, 1L));
    }

    private void startLoyaltyReturnFlight(Trident trident, Player owner, int loyalty) {
        UUID tridentId = trident.getUniqueId();
        if (trident.hasMetadata(LOYALTY_RETURNING_META)) {
            return;
        }
        trident.setMetadata(LOYALTY_RETURNING_META, new FixedMetadataValue(plugin, true));
        trident.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.CREATIVE_ONLY);
        trident.setNoPhysics(true);

        BukkitTask flightTask = loyaltyFlightTasks.remove(tridentId);
        if (flightTask != null) {
            flightTask.cancel();
        }

        double returnSpeed = 0.35D + (loyalty * 0.25D);
        loyaltyReturnTasks.put(tridentId, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!trident.isValid() || trident.isDead()) {
                restoreLoyaltyItemToOwner(tridentId, trident);
                cleanupLoyalty(tridentId);
                return;
            }
            if (!owner.isOnline() || owner.isDead() || !owner.getWorld().equals(trident.getWorld())) {
                dropStoredTridentItem(tridentId, trident.getLocation());
                trident.remove();
                cleanupLoyalty(tridentId);
                return;
            }

            Location tridentLoc = trident.getLocation();
            Location target = owner.getEyeLocation();
            if (tridentLoc.distanceSquared(target) <= 2.25D) {
                completeLoyaltyReturn(trident, owner);
                return;
            }

            Vector step = target.toVector().subtract(tridentLoc.toVector());
            if (step.lengthSquared() > 0.0001D) {
                step.normalize().multiply(returnSpeed);
            }
            trident.teleport(tridentLoc.add(step));
            trident.setVelocity(new Vector(0, 0, 0));
        }, 1L, 1L));
    }

    private void completeLoyaltyReturn(Trident trident, Player owner) {
        UUID tridentId = trident.getUniqueId();
        if (trident.hasMetadata(LOYALTY_COMPLETED_META)) {
            return;
        }
        trident.setMetadata(LOYALTY_COMPLETED_META, new FixedMetadataValue(plugin, true));

        BukkitTask returnTask = loyaltyReturnTasks.remove(tridentId);
        if (returnTask != null) {
            returnTask.cancel();
        }

        ItemStack item = loyaltyReturnItems.remove(tridentId);
        if (item == null) {
            ItemStack fromEntity = trident.getItem();
            item = fromEntity != null ? fromEntity.clone() : null;
        }
        if (item != null && !item.getType().isAir()) {
            itemIdentityService.ensureTridentItem(item);
            giveTridentToPlayer(owner, item);
        }
        if (trident.isValid()) {
            trident.remove();
        }
        cleanupLoyalty(tridentId);
    }

    private void giveTridentToPlayer(Player owner, ItemStack item) {
        ItemStack main = owner.getInventory().getItemInMainHand();
        if (main == null || main.getType().isAir()) {
            owner.getInventory().setItemInMainHand(item);
            return;
        }
        Map<Integer, ItemStack> leftover = owner.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            owner.getWorld().dropItemNaturally(owner.getLocation(), extra);
        }
    }

    private void restoreLoyaltyItemToOwner(UUID tridentId, Trident trident) {
        ItemStack item = loyaltyReturnItems.remove(tridentId);
        if (item == null) {
            return;
        }
        if (trident.getShooter() instanceof Player owner && owner.isOnline() && !owner.isDead()) {
            itemIdentityService.ensureTridentItem(item);
            giveTridentToPlayer(owner, item);
            return;
        }
        Location dropAt = trident.isValid() ? trident.getLocation() : null;
        if (dropAt != null) {
            dropStoredTridentItem(tridentId, dropAt, item);
        }
    }

    private void dropStoredTridentItem(UUID tridentId, Location location) {
        ItemStack item = loyaltyReturnItems.remove(tridentId);
        if (item != null) {
            dropStoredTridentItem(tridentId, location, item);
        }
    }

    private void dropStoredTridentItem(UUID tridentId, Location location, ItemStack item) {
        if (item == null || item.getType().isAir() || location == null || location.getWorld() == null) {
            return;
        }
        itemIdentityService.ensureTridentItem(item);
        location.getWorld().dropItemNaturally(location, item);
    }

    private void cleanupLoyalty(UUID tridentId) {
        loyaltyStuckTicks.remove(tridentId);
        loyaltyAirTicks.remove(tridentId);
        loyaltyReturnItems.remove(tridentId);
        loyaltyLevels.remove(tridentId);
        BukkitTask flightTask = loyaltyFlightTasks.remove(tridentId);
        if (flightTask != null) {
            flightTask.cancel();
        }
        BukkitTask returnTask = loyaltyReturnTasks.remove(tridentId);
        if (returnTask != null) {
            returnTask.cancel();
        }
    }

    private boolean isCustomTridentProjectile(Trident trident, Player thrower) {
        if (trident.hasMetadata(TRIDENT_PROJECTILE_META)) {
            return true;
        }
        if (itemIdentityService.isTridentItem(trident.getItem())) {
            return true;
        }
        return itemIdentityService.isTridentItem(thrower.getInventory().getItemInMainHand())
                || itemIdentityService.isTridentItem(thrower.getInventory().getItemInOffHand());
    }

    private void startFlight(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            long ticks = 0L;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !player.isSneaking()) {
                    stopFlight(player);
                    return;
                }
                if (ticks >= FLIGHT_TICKS) {
                    stopFlight(player);
                    return;
                }

                Vector direction = player.getEyeLocation().getDirection().normalize().multiply(0.6);
                player.setVelocity(direction);
                player.setFallDistance(0f);
                player.setAllowFlight(true);

                Location below = player.getLocation().clone().add(-0.5, -1.0, -0.5);
                org.bukkit.entity.BlockDisplay wave = player.getWorld().spawn(below, org.bukkit.entity.BlockDisplay.class, display -> {
                    display.setBlock(org.bukkit.Bukkit.createBlockData(Material.LIGHT_BLUE_STAINED_GLASS));
                });
                plugin.getServer().getScheduler().runTaskLater(plugin, wave::remove, 15L);

                ticks++;
            }
        }, 0L, 1L);
        flightTasks.put(playerId, task);
        flightStartTime.put(playerId, System.currentTimeMillis());
    }

    private void stopFlight(Player player) {
        BukkitTask task = flightTasks.remove(player.getUniqueId());
        flightStartTime.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            noFallDamageUntil.put(player.getUniqueId(), System.currentTimeMillis() + 15_000L);
            if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    private void clearCombo(UUID playerId) {
        comboHits.remove(playerId);
        comboTarget.remove(playerId);
        comboLastHit.remove(playerId);
    }

    public String getTridentHudLine(Player player, long now) {
        if (!itemIdentityService.isTridentItem(player.getInventory().getItemInMainHand())) {
            return null;
        }
        UUID id = player.getUniqueId();
        if (flightStartTime.containsKey(id)) {
            long elapsedMs = now - flightStartTime.get(id);
            double remainingSeconds = Math.max(0.0, 5.0 - (elapsedMs / 1000.0));
            return String.format("§b🌊 Surfing... §f%.1fs", remainingSeconds);
        }
        int combo = comboHits.getOrDefault(id, 0);
        return "§3🔱 Trident §8| §eCombo: " + combo + "/3 §8| §7Throw=⚡";
    }
}
