package com.yourorg.kingdomcore.ability;

import com.yourorg.kingdomcore.abilities.AbilityHandler;
import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.service.PhaseWalkService;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhaseWalkAbilityTest {
    private PhaseWalkAbility ability;
    
    @Mock
    private Plugin plugin;
    
    @Mock
    private WorldGuardHook worldGuardHook;
    
    @Mock
    private PhaseWalkService phaseWalkService;
    
    @Mock
    private Player player;
    
    @Mock
    private PlayerInteractEvent event;
    
    @Mock
    private Location location;
    
    @Mock
    private Server server;
    
    @Mock
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ability = new PhaseWalkAbility(plugin, worldGuardHook, "spawn", phaseWalkService);
        
        when(player.getLocation()).thenReturn(location);
        when(event.getPlayer()).thenReturn(player);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(worldGuardHook.isInRegion(location, "spawn")).thenReturn(false);
        when(phaseWalkService.isInPhase(player)).thenReturn(false);
    }

    @Test
    void testLevelZeroCannotActivate() {
        boolean result = ability.onRightClick(player, 0, event);
        assertFalse(result);
        verify(phaseWalkService, never()).startPhase(any(), anyLong(), anyInt());
    }

    @Test
    void testSpawnRegionIsSkipped() {
        when(worldGuardHook.isInRegion(location, "spawn")).thenReturn(true);
        boolean result = ability.onRightClick(player, 1, event);
        assertFalse(result);
        verify(phaseWalkService, never()).startPhase(any(), anyLong(), anyInt());
    }

    @Test
    void testAlreadyPhasingIsSkipped() {
        when(phaseWalkService.isInPhase(player)).thenReturn(true);
        boolean result = ability.onRightClick(player, 1, event);
        assertFalse(result);
        verify(phaseWalkService, never()).startPhase(any(), anyLong(), anyInt());
    }

    @Test
    void testGetAbilityId() {
        assertEquals("phase_walk", ability.getAbilityId());
    }

    @Test
    void testImplementsAbilityHandler() {
        assertTrue(ability instanceof AbilityHandler);
    }

    @Test
    void testCleanupCallsService() {
        ability.cleanup(player);
        verify(phaseWalkService).cleanup(player);
    }

    @Test
    void testActivationSchedulesPhaseDuration() {
        boolean result = ability.onRightClick(player, 1, event);
        assertTrue(result);
        verify(phaseWalkService).startPhase(player, 60, 1); // Level 1: 60 ticks
        verify(scheduler).scheduleSyncDelayedTask(eq(plugin), any(Runnable.class), eq(60L));
    }
}


