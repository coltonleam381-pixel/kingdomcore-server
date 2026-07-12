package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.service.PhaseWalkService;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhaseWalkDamageListenerTest {
    private PhaseWalkDamageListener listener;
    
    @Mock
    private PhaseWalkService phaseWalkService;
    
    @Mock
    private Player player;
    
    @Mock
    private EntityDamageEvent event;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new PhaseWalkDamageListener(phaseWalkService);
        when(event.getEntity()).thenReturn(player);
    }

    @Test
    void testDamageCancelledWhenPlayerIsInPhase() {
        when(phaseWalkService.isInPhase(player)).thenReturn(true);
        
        listener.onEntityDamage(event);
        
        verify(event).setCancelled(true);
    }

    @Test
    void testDamageNotCancelledWhenPlayerNotInPhase() {
        when(phaseWalkService.isInPhase(player)).thenReturn(false);
        
        listener.onEntityDamage(event);
        
        verify(event, never()).setCancelled(true);
    }

    @Test
    void testIgnoresDamageToNonPlayers() {
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        when(event.getEntity()).thenReturn(entity);
        
        listener.onEntityDamage(event);
        
        verify(phaseWalkService, never()).isInPhase(any());
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void testImplementsListener() {
        assertTrue(listener instanceof Listener);
    }
}

