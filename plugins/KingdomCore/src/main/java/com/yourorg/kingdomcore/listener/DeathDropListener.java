package com.yourorg.kingdomcore.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class DeathDropListener implements Listener {

    /** Multiplier on dropItemNaturally velocity; 1.44 = 20% more scatter than the previous 1.2 setting. */
    private static final double DEATH_DROP_SCATTER_MULTIPLIER = 1.44;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }

        Player player = event.getEntity();
        Location loc = player.getLocation().add(0, 0.5, 0); // Slightly above ground so they don't clip

        // Copy the drops
        List<ItemStack> dropsToSpawn = new ArrayList<>(event.getDrops());

        // Clear the vanilla drops to prevent the massive default scatter
        event.getDrops().clear();

        // Spawn them manually with significantly reduced velocity
        for (ItemStack item : dropsToSpawn) {
            if (item != null && item.getType() != Material.AIR) {
                Item itemEntity = loc.getWorld().dropItemNaturally(loc, item);
                
                // Increase the scatter velocity since dropItemNaturally is smaller than vanilla death drops
                Vector velocity = itemEntity.getVelocity();
                itemEntity.setVelocity(velocity.multiply(DEATH_DROP_SCATTER_MULTIPLIER));
            }
        }
    }
}
