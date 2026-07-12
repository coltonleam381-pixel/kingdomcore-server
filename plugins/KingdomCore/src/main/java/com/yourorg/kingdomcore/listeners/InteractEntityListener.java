package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.abilities.CooldownOverrideAbility;
import com.yourorg.kingdomcore.abilities.EntityInteractAbility;
import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.core.services.DebugTelemetryService;
import com.yourorg.kingdomcore.core.services.HeartService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.util.NameNormalizer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InteractEntityListener implements Listener {
    private final KingdomConfig config;
    private final AbilityService abilityService;
    private final CooldownService cooldownService;
    private final HeartService heartService;
    private final DebugTelemetryService debugTelemetryService;
    private final ItemIdentityService itemIdentityService;
    private final Map<UUID, Long> microCooldowns = new ConcurrentHashMap<>();

    public InteractEntityListener(KingdomConfig config,
                                  AbilityService abilityService,
                                  CooldownService cooldownService,
                                  HeartService heartService,
                                  DebugTelemetryService debugTelemetryService,
                                  ItemIdentityService itemIdentityService) {
        this.config = config;
        this.abilityService = abilityService;
        this.cooldownService = cooldownService;
        this.heartService = heartService;
        this.debugTelemetryService = debugTelemetryService;
        this.itemIdentityService = itemIdentityService;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack stack = event.getPlayer().getInventory().getItemInMainHand();
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

        if (!cooldownService.isReady(playerId, abilityId, now)) {
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.ABILITY_COOLDOWN);
            return;
        }

        if (!(event.getRightClicked() instanceof LivingEntity target)) {
            return;
        }

        var handler = abilityService.getHandler(abilityId);
        if (!(handler instanceof EntityInteractAbility entityHandler)) {
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.INVALID_CONTEXT);
            return;
        }

        boolean activated = entityHandler.onEntityRightClick(event.getPlayer(), target, level, event);
        if (activated) {
            long cooldownMs = ability.baseCooldownMs();
            if (handler instanceof CooldownOverrideAbility overrideAbility) {
                cooldownMs = overrideAbility.getCooldownMs(level);
            }
            cooldownService.markUsed(playerId, abilityId, now + cooldownMs);
            debugTelemetryService.record(playerId, DebugTelemetryService.FailReason.SUCCESS);
        }
    }
}
