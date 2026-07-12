package com.yourorg.kingdomcore;

import com.yourorg.kingdomcore.util.SpawnSelector;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class SpawnSelectorTest {
    @Test
    void prefersBedSpawnWhenAvailable() {
        Player player = mock(Player.class);
        Location bed = mock(Location.class);
        when(player.getBedSpawnLocation()).thenReturn(bed);

        assertEquals(bed, SpawnSelector.selectSpawn(player));
    }

    @Test
    void fallsBackToWorldSpawn() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location spawn = mock(Location.class);
        when(player.getBedSpawnLocation()).thenReturn(null);
        when(player.getWorld()).thenReturn(world);
        when(world.getSpawnLocation()).thenReturn(spawn);

        assertEquals(spawn, SpawnSelector.selectSpawn(player));
    }
}
