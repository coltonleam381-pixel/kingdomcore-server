package com.yourorg.kingdomcore.abilities;

import com.yourorg.kingdomcore.integrations.WorldGuardHook;
import com.yourorg.kingdomcore.service.CombatTagService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class PhaseStepAbilityTest {
    @Test
    void levelZeroCannotActivate() {
        WorldGuardHook worldGuardHook = mock(WorldGuardHook.class);
        CombatTagService combatTagService = mock(CombatTagService.class);
        PhaseStepAbility ability = new PhaseStepAbility(worldGuardHook, combatTagService, "spawn");

        World world = mock(World.class);
        Player player = mock(Player.class);
        LivingEntity target = mock(LivingEntity.class);
        when(player.getWorld()).thenReturn(world);
        when(target.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(target.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        when(worldGuardHook.isInRegion(any(), anyString())).thenReturn(false);

        boolean activated = ability.onEntityRightClick(player, target, 0, mock(PlayerInteractEntityEvent.class));
        assertFalse(activated);
    }

    @Test
    void destinationUsesLineOfCenters() {
        World world = mock(World.class);
        Location caster = new Location(world, 0, 64, 0);
        Location target = new Location(world, 3, 64, 4);

        Location dest = PhaseStepAbility.computeOppositeDestination(caster, target);
        assertNotNull(dest);
        assertEquals(6.0, dest.getX(), 1.0E-6);
        assertEquals(8.0, dest.getZ(), 1.0E-6);
    }

    @Test
    void preservesDistanceAcrossTarget() {
        World world = mock(World.class);
        Location caster = new Location(world, -2, 64, 1);
        Location target = new Location(world, 1, 64, 5);

        Location dest = PhaseStepAbility.computeOppositeDestination(caster, target);
        assertNotNull(dest);

        double original = caster.distance(target);
        double across = dest.distance(target);
        assertEquals(original, across, 1.0E-6);
    }

    @Test
    void casterFacesTargetAfterMove() {
        World world = mock(World.class);
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 5, 65, 0);

        Location oriented = PhaseStepAbility.orientToward(from, to);
        Vector actual = oriented.getDirection().normalize();
        Vector expected = to.toVector().subtract(from.toVector()).normalize();

        assertTrue(actual.dot(expected) > 0.99);
    }

    @Test
    void safeLocationFallbackFindsNearbySpot() {
        World world = mock(World.class);
        Location desired = new Location(world, 10, 64, 10);
        Predicate<Location> isSafe = location ->
                location.getX() == 11.0 && location.getY() == 64.0 && location.getZ() == 10.0;

        Location found = PhaseStepAbility.findSafeDestination(desired, isSafe);
        assertNotNull(found);
        assertEquals(11.0, found.getX(), 1.0E-6);
        assertEquals(64.0, found.getY(), 1.0E-6);
        assertEquals(10.0, found.getZ(), 1.0E-6);
    }

    @Test
    void cooldownMatchesLevel() {
        WorldGuardHook worldGuardHook = mock(WorldGuardHook.class);
        CombatTagService combatTagService = mock(CombatTagService.class);
        PhaseStepAbility ability = new PhaseStepAbility(worldGuardHook, combatTagService, "spawn");

        assertEquals(45000L, ability.getCooldownMs(1));
        assertEquals(40000L, ability.getCooldownMs(2));
        assertEquals(35000L, ability.getCooldownMs(3));
    }

    @Test
    void silentFailInvalidTargetRangeSpawnBlocked() {
        WorldGuardHook worldGuardHook = mock(WorldGuardHook.class);
        CombatTagService combatTagService = mock(CombatTagService.class);
        PhaseStepAbility ability = new PhaseStepAbility(worldGuardHook, combatTagService, "spawn");

        World world = mock(World.class);
        Player player = mock(Player.class);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));

        when(worldGuardHook.isInRegion(any(), anyString())).thenReturn(true);
        LivingEntity target = mock(LivingEntity.class);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        boolean spawnBlocked = ability.onEntityRightClick(player, target, 1, event);
        assertFalse(spawnBlocked);

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 10, 64, 0);
        assertFalse(PhaseStepAbility.isWithinRange(from, to, 3.0));

        boolean sameTarget = ability.onEntityRightClick(player, player, 1, event);
        assertFalse(sameTarget);
    }

    @Test
    void returnsFalseWhenCasterInSpawn() {
        WorldGuardHook worldGuardHook = mock(WorldGuardHook.class);
        CombatTagService combatTagService = mock(CombatTagService.class);
        PhaseStepAbility ability = new PhaseStepAbility(worldGuardHook, combatTagService, "spawn");

        World world = mock(World.class);
        Player player = mock(Player.class);
        LivingEntity target = mock(LivingEntity.class);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);

        Location caster = new Location(world, 0, 64, 0);
        Location targetLoc = new Location(world, 2, 64, 0);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(caster);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(targetLoc);
        when(worldGuardHook.isInRegion(eq(caster), eq("spawn"))).thenReturn(true);

        boolean activated = ability.onEntityRightClick(player, target, 1, event);
        assertFalse(activated);
    }

    @Test
    void returnsFalseWhenTargetInSpawn() {
        WorldGuardHook worldGuardHook = mock(WorldGuardHook.class);
        CombatTagService combatTagService = mock(CombatTagService.class);
        PhaseStepAbility ability = new PhaseStepAbility(worldGuardHook, combatTagService, "spawn");

        World world = mock(World.class);
        Player player = mock(Player.class);
        LivingEntity target = mock(LivingEntity.class);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);

        Location caster = new Location(world, 0, 64, 0);
        Location targetLoc = new Location(world, 2, 64, 0);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(caster);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(targetLoc);
        when(worldGuardHook.isInRegion(eq(caster), eq("spawn"))).thenReturn(false);
        when(worldGuardHook.isInRegion(eq(targetLoc), eq("spawn"))).thenReturn(true);

        boolean activated = ability.onEntityRightClick(player, target, 1, event);
        assertFalse(activated);
    }

    @Test
    void returnsFalseWhenDestinationInSpawn() {
        WorldGuardHook worldGuardHook = mock(WorldGuardHook.class);
        CombatTagService combatTagService = mock(CombatTagService.class);
        PhaseStepAbility ability = new PhaseStepAbility(worldGuardHook, combatTagService, "spawn");

        World world = mock(World.class);
        Player player = mock(Player.class);
        LivingEntity target = mock(LivingEntity.class);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);

        Location caster = new Location(world, 0, 64, 0);
        Location targetLoc = new Location(world, 2, 64, 0);
        Location desired = PhaseStepAbility.computeOppositeDestination(caster, targetLoc);

        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(caster);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(targetLoc);
        when(worldGuardHook.isInRegion(eq(caster), eq("spawn"))).thenReturn(false);
        when(worldGuardHook.isInRegion(eq(targetLoc), eq("spawn"))).thenReturn(false);
        when(worldGuardHook.isInRegion(eq(desired), eq("spawn"))).thenReturn(true);

        boolean activated = ability.onEntityRightClick(player, target, 1, event);
        assertFalse(activated);
    }
}
