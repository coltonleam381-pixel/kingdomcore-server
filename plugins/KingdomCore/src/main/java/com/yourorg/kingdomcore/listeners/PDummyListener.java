package com.yourorg.kingdomcore.listeners;

import com.yourorg.kingdomcore.KingdomCorePlugin;
import com.yourorg.kingdomcore.commands.PDummyCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * Keeps P Dummy heart display synced with its real health.
 */
public class PDummyListener implements Listener {
    private final KingdomCorePlugin plugin;

    public PDummyListener(KingdomCorePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            for (var world : this.plugin.getServer().getWorlds()) {
                for (Zombie dummy : world.getEntitiesByClass(Zombie.class)) {
                    if (dummy.getScoreboardTags().contains(PDummyCommand.DUMMY_TAG)) {
                        PDummyCommand.updateDummyName(dummy);
                    }
                }
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onDummyDamaged(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie dummy)) {
            return;
        }
        if (!dummy.getScoreboardTags().contains(PDummyCommand.DUMMY_TAG)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!dummy.isDead() && dummy.isValid()) {
                PDummyCommand.updateDummyName(dummy);
            }
        });
    }

    @EventHandler
    public void onDummyHealed(EntityRegainHealthEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie dummy)) {
            return;
        }
        if (!dummy.getScoreboardTags().contains(PDummyCommand.DUMMY_TAG)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!dummy.isDead() && dummy.isValid()) {
                PDummyCommand.updateDummyName(dummy);
            }
        });
    }

    @EventHandler
    public void onDummyDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie dummy)) {
            return;
        }
        if (!dummy.getScoreboardTags().contains(PDummyCommand.DUMMY_TAG)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler
    public void onDummyPotionEffect(EntityPotionEffectEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie dummy)) {
            return;
        }
        if (!dummy.getScoreboardTags().contains(PDummyCommand.DUMMY_TAG)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!dummy.isDead() && dummy.isValid()) {
                PDummyCommand.updateDummyName(dummy);
            }
        });
    }
}
