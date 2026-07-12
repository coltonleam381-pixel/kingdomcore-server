package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.abilities.AbilityHandler;
import com.yourorg.kingdomcore.abilities.CooldownOverrideAbility;
import com.yourorg.kingdomcore.abilities.EntityInteractAbility;
import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.KingdomConfig;
import com.yourorg.kingdomcore.core.services.AbilityService;
import com.yourorg.kingdomcore.core.services.CooldownService;
import com.yourorg.kingdomcore.core.services.DebugTelemetryService;
import com.yourorg.kingdomcore.core.services.HeartService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class InteractEntityListenerTest {
    @Test
    void handlesAnyEntityInteractAbility() {
        KingdomConfig config = mock(KingdomConfig.class);
        AbilityService abilityService = mock(AbilityService.class);
        CooldownService cooldownService = mock(CooldownService.class);
        HeartService heartService = mock(HeartService.class);
        DebugTelemetryService debugTelemetryService = mock(DebugTelemetryService.class);

        InteractEntityListener listener = new InteractEntityListener(
                config, abilityService, cooldownService, heartService, debugTelemetryService);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        LivingEntity target = mock(LivingEntity.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);

        when(config.getMicroCooldownMs()).thenReturn(0L);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(target);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("Shadow Swap");

        PlayerState state = new PlayerState(playerId);
        state.setAbilityId("shadow_swap");
        state.setAbilityLevel(2);
        when(heartService.getOrCreateState(playerId, "Tester")).thenReturn(state);

        AbilityDefinition definition = new AbilityDefinition(
                "shadow_swap", "Shadow Swap", 10000L, "", "");
        when(abilityService.getAbility("shadow_swap")).thenReturn(definition);
        when(cooldownService.isReady(eq(playerId), eq("shadow_swap"), anyLong())).thenReturn(true);

        AbilityHandler handler = mock(AbilityHandler.class, withSettings().extraInterfaces(
                EntityInteractAbility.class, CooldownOverrideAbility.class));
        EntityInteractAbility entityHandler = (EntityInteractAbility) handler;
        CooldownOverrideAbility overrideAbility = (CooldownOverrideAbility) handler;
        when(abilityService.getHandler("shadow_swap")).thenReturn(handler);
        when(entityHandler.onEntityRightClick(player, target, 2, event)).thenReturn(true);
        when(overrideAbility.getCooldownMs(2)).thenReturn(1234L);

        long before = System.currentTimeMillis();
        listener.onEntityInteract(event);
        long after = System.currentTimeMillis();

        ArgumentCaptor<Long> readyAtCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cooldownService).markUsed(eq(playerId), eq("shadow_swap"), readyAtCaptor.capture());
        long readyAt = readyAtCaptor.getValue();
        assertTrue(readyAt >= before + 1234L && readyAt <= after + 1234L);
    }

    @Test
    void cooldownAppliedOnlyOnSuccessfulActivation() {
        KingdomConfig config = mock(KingdomConfig.class);
        AbilityService abilityService = mock(AbilityService.class);
        CooldownService cooldownService = mock(CooldownService.class);
        HeartService heartService = mock(HeartService.class);
        DebugTelemetryService debugTelemetryService = mock(DebugTelemetryService.class);

        InteractEntityListener listener = new InteractEntityListener(
                config, abilityService, cooldownService, heartService, debugTelemetryService);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        LivingEntity target = mock(LivingEntity.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);

        when(config.getMicroCooldownMs()).thenReturn(0L);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(target);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("Shadow Swap");

        PlayerState state = new PlayerState(playerId);
        state.setAbilityId("shadow_swap");
        state.setAbilityLevel(1);
        when(heartService.getOrCreateState(playerId, "Tester")).thenReturn(state);

        AbilityDefinition definition = new AbilityDefinition(
                "shadow_swap", "Shadow Swap", 10000L, "", "");
        when(abilityService.getAbility("shadow_swap")).thenReturn(definition);
        when(cooldownService.isReady(eq(playerId), eq("shadow_swap"), anyLong())).thenReturn(true);

        AbilityHandler handler = mock(AbilityHandler.class, withSettings().extraInterfaces(EntityInteractAbility.class));
        EntityInteractAbility entityHandler = (EntityInteractAbility) handler;
        when(abilityService.getHandler("shadow_swap")).thenReturn(handler);
        when(entityHandler.onEntityRightClick(player, target, 1, event)).thenReturn(false);

        listener.onEntityInteract(event);

        verify(cooldownService, never()).markUsed(any(), any(), anyLong());
    }
}
