package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.abilities.CooldownOverrideAbility;
import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.core.services.DebugTelemetryService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.service.CombatTagService;
import com.yourorg.kingdomcore.gui.MenuFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InteractListener implements Listener {
    private final KingdomConfig config;
    private final AbilityService abilityService;
    private final CooldownService cooldownService;
    private final HeartService heartService;
    private final DebugTelemetryService debugTelemetryService;
    private final com.yourorg.kingdomcore.core.services.ItemIdentityService itemIdentityService;
    private final CombatTagService combatTagService;
    private final MenuFactory menuFactory;
    private final com.yourorg.kingdomcore.service.AssassinEventService assassinEventService;
    private final Map<UUID, Long> microCooldowns = new ConcurrentHashMap<>();

    public InteractListener(KingdomConfig config,
                            AbilityService abilityService,
                            CooldownService cooldownService,
                            HeartService heartService,
                            DebugTelemetryService debugTelemetryService,
                            com.yourorg.kingdomcore.core.services.ItemIdentityService itemIdentityService,
                            CombatTagService combatTagService,
                            MenuFactory menuFactory,
                            com.yourorg.kingdomcore.service.AssassinEventService assassinEventService) {
        this.config = config;
        this.abilityService = abilityService;
        this.cooldownService = cooldownService;
        this.heartService = heartService;
        this.debugTelemetryService = debugTelemetryService;
        this.itemIdentityService = itemIdentityService;
        this.combatTagService = combatTagService;
        this.menuFactory = menuFactory;
        this.assassinEventService = assassinEventService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        boolean isRightClick = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        boolean isLeftClick = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);

        if (!isRightClick && !isLeftClick) {
            return;
        }
        
        ItemStack stack = event.getItem();
        
        // PRIORITY 1: Heart consume (RMB only, no sneak required)
        if (isRightClick && stack != null && tryConsumeHeart(event)) {
            return;
        }

        // PRIORITY 1.5: Revive Beacon consume
        if (isRightClick && stack != null && itemIdentityService.isReviveBeacon(stack)) {
            menuFactory.openRevive(event.getPlayer());
            event.setCancelled(true);
            return;
        }

        // PRIORITY 2: Ability activation
        if (stack == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        Long last = microCooldowns.get(playerId);
        if (last != null && now - last < config.getMicroCooldownMs()) {
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.MICRO_COOLDOWN);
            return;
        }
        microCooldowns.put(playerId, now);

        PlayerState state = heartService.getOrCreateState(playerId, event.getPlayer().getName());
        if (state.isBlocked()) {
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.BLOCKED);
            return;
        }
        String abilityId = state.getAbilityId();
        if (abilityId == null) {
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.NO_ABILITY);
            return;
        }

        int level = state.getAbilityLevel();

        // LEVEL 0 LOCK
        if (level == 0) {
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.LEVEL_ZERO_LOCK);
            return;
        }

        AbilityDefinition ability = abilityService.getAbility(abilityId);
        if (ability == null) {
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.INVALID_CONTEXT);
            return;
        }

        if (!itemIdentityService.matchesAbilityItem(stack, ability.id(), ability.name())) {
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.WRONG_NAME);
            return;
        }

        // Handle sneak + RMB (mode toggle for Atlantis L3)
        if (isRightClick && event.getPlayer().isSneaking()) {
            if (abilityService.handleSneakRightClick(event.getPlayer(), abilityId, level)) {
                return; // Mode toggle doesn't consume cooldown
            }
        }

        // Handle left-click (Thor strike)
        if (isLeftClick) {
            boolean activated = abilityService.activateAbilityLeftClick(event.getPlayer(), abilityId, level);
            if (activated) {
                // Thor strike doesn't start cooldown (already started on mark)
                debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.SUCCESS);
                assassinEventService.notifyAbilityUsed(playerId);
            }
            return;
        }

        // Handle right-click activation
        if (isRightClick) {
            if ("protocol".equalsIgnoreCase(abilityId)) {
                abilityService.activateAbilityRightClick(event.getPlayer(), ability, level, event);
                return;
            }
            if (!cooldownService.isReady(playerId, abilityId, now)) {
                debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.ABILITY_COOLDOWN);
                return;
            }

            boolean activated = abilityService.activateAbilityRightClick(event.getPlayer(), ability, level, event);
            if (activated) {
                // Thor/Hulk/Meteor/Berserk cooldowns are managed by their own handlers.
                if (!"thor".equalsIgnoreCase(abilityId)
                        && !"hulk".equalsIgnoreCase(abilityId)
                        && !"meteor".equalsIgnoreCase(abilityId)
                        && !"berserk".equalsIgnoreCase(abilityId)
                        && !"sanguine_fog".equalsIgnoreCase(abilityId)
                        && !"lifesteal".equalsIgnoreCase(abilityId)
                        && !"ice_nova".equalsIgnoreCase(abilityId)) {
                    long cooldownMs = ability.baseCooldownMs();
                    var handler = abilityService.getHandler(abilityId);
                    if (handler instanceof CooldownOverrideAbility overrideAbility) {
                        cooldownMs = overrideAbility.getCooldownMs(level);
                    }
                    cooldownService.markUsed(playerId, abilityId, now + cooldownMs);
                }
                debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.SUCCESS);
                assassinEventService.notifyAbilityUsed(playerId);
            }
        }
    }

    @EventHandler
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.Player player)) {
            return;
        }

        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PlayerState state = heartService.getOrCreateState(playerId, player.getName());
        if (state.isBlocked()) {
            return;
        }

        String abilityId = state.getAbilityId();
        if (abilityId == null || !"thor".equalsIgnoreCase(abilityId)) {
            return;
        }

        int level = state.getAbilityLevel();
        if (level == 0) {
            return;
        }

        AbilityDefinition ability = abilityService.getAbility(abilityId);
        if (ability == null) {
            return;
        }

        if (!itemIdentityService.matchesAbilityItem(stack, abilityId, ability.name())) {
            return;
        }

        // Thor strike should also trigger when LMB actually hits an entity.
        boolean activated = abilityService.activateAbilityLeftClick(player, abilityId, level);
        if (activated) {
            // Prevent vanilla melee from stacking with Thor strike damage.
            event.setCancelled(true);
            event.setDamage(0.0);
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.SUCCESS);
            assassinEventService.notifyAbilityUsed(playerId);
        }
    }

    private boolean tryConsumeHeart(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        if (stack == null) {
            return false;
        }
        // RMB only, no sneak required
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return false;
        }
        return heartService.consumeHeartItem(event.getPlayer(), stack, 1);
    }
}
