package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.impl.HeartServiceImpl;
import com.yourorg.kingdomcore.core.services.HealthService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

public class HeartServiceConsumeTest {
    @Test
    void consumeReturnsFalseAtCap() {
        PlayerStateRepository repo = mock(PlayerStateRepository.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        HealthService health = mock(HealthService.class);

        UUID playerId = UUID.randomUUID();
        PlayerState state = new PlayerState(playerId);
        state.setProgressionHearts(20);
        when(repo.findById(playerId)).thenReturn(Optional.of(state));
        when(identity.isHeartItem(any())).thenReturn(true);

        HeartServiceImpl service = new HeartServiceImpl(repo, identity, health, 10, 20);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Test");
        when(player.getInventory()).thenReturn(inventory);

        ItemStack stack = mock(ItemStack.class);
        boolean consumed = service.consumeHeartItem(player, stack, 1);

        assertFalse(consumed);
    }
}
