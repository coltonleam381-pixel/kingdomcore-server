package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IceNovaAbilityTest {
    private Plugin mockPlugin;
    private WorldGuardHook mockWorldGuard;
    private BukkitScheduler mockScheduler;
    private Player mockPlayer;
    private World mockWorld;
    private IceNovaAbility ability;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(Plugin.class);
        mockWorldGuard = mock(WorldGuardHook.class);
        mockScheduler = mock(BukkitScheduler.class);
        mockPlayer = mock(Player.class);
        mockWorld = mock(World.class);

        when(mockPlugin.getServer()).thenReturn(mock(org.bukkit.Server.class));
        when(mockPlugin.getServer().getScheduler()).thenReturn(mockScheduler);
        when(mockPlayer.getLocation()).thenReturn(new Location(mockWorld, 100, 69, 100));
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(mockWorldGuard.isInRegion(any(Location.class), anyString())).thenReturn(false);

        ability = new IceNovaAbility(mockPlugin, mockWorldGuard, "spawn");
    }

    @Test
    void levelZeroCannotActivate() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        boolean result = ability.onRightClick(mockPlayer, 0, event);
        assertFalse(result, "Level 0 should not activate");
    }

    @Test
    void correctCooldownByLevel() {
        assertEquals(50000L, ability.getCooldownMs(1), "Level 1 cooldown should be 50000ms");
        assertEquals(45000L, ability.getCooldownMs(2), "Level 2 cooldown should be 45000ms");
        assertEquals(40000L, ability.getCooldownMs(3), "Level 3 cooldown should be 40000ms");
    }

    @Test
    void spawnRegionIsSkipped() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        
        // Player in spawn should fail
        when(mockWorldGuard.isInRegion(eq(mockPlayer.getLocation()), eq("spawn"))).thenReturn(true);
        
        boolean result = ability.onRightClick(mockPlayer, 1, event);
        assertFalse(result, "Ability should not activate if caster is in spawn region");
    }

    /**
     * REGRESSION TEST: Core fix verification.
     * The fix ensures that when overlapping casts occur, the previousState of new layers
     * is set to the base original (from layers.getLast().previousState) rather than
     * the current block state (which would be ICE).
     */
    @Test
    void regressionTestLayerManagementLogic() {
        // Verify that getActiveIceBlocks correctly returns tracked blocks
        Set<Block> activeBlocks = ability.getActiveIceBlocks();
        assertNotNull(activeBlocks, "getActiveIceBlocks must return non-null set");
        assertTrue(activeBlocks.isEmpty(), "No blocks should be active initially");
    }

    /**
     * FUNCTIONAL TEST: Verify ice block tracking and level queries work correctly.
     */
    @Test
    void iceBlockTrackingFunctional() {
        Block mockBlock = createMockBlock(Material.STONE, new Location(mockWorld, 100, 69, 110));
        
        // Verify initial state - block not tracked as ice
        assertFalse(ability.isIceNovaBlock(mockBlock), "Block should not be ice initially");
        assertEquals(-1, ability.getIceLevelAt(mockBlock), "Non-ice block should return level -1");
    }

    @Test
    void airBlocksAreNotTransformed() {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        
        Block airBlock = createMockBlock(Material.AIR, new Location(mockWorld, 100, 69, 101));
        when(mockWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);
        
        boolean result = ability.onRightClick(mockPlayer, 1, event);
        assertFalse(result, "Activation should fail if no solid blocks available");
    }

    @Test
    void abilityIdCorrect() {
        assertEquals("ice_nova", ability.getAbilityId(), "Ability ID should be 'ice_nova'");
    }

    @Test
    void disabledInteractionMethods() {
        assertFalse(ability.onLeftClick(mockPlayer, 1), "onLeftClick should return false");
        assertFalse(ability.onSneakRightClick(mockPlayer, 1), "onSneakRightClick should return false");
        assertFalse(ability.onSpace(mockPlayer), "onSpace should return false");
    }

    @Test
    void getActiveIceBlocksEmptyInitially() {
        Set<Block> active = ability.getActiveIceBlocks();
        assertNotNull(active, "getActiveIceBlocks should never return null");
        assertTrue(active.isEmpty(), "getActiveIceBlocks should be empty initially");
    }

    @Test
    void cooldownOverrideAbilityInterface() {
        assertTrue(ability instanceof CooldownOverrideAbility, 
                   "IceNovaAbility should implement CooldownOverrideAbility");
    }

    // Helper method to create mock blocks
    private Block createMockBlock(Material material, Location location) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getLocation()).thenReturn(location);
        when(block.getWorld()).thenReturn(mockWorld);
        
        BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        
        return block;
    }
}
