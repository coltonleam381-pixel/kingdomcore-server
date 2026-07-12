package com.yourorg.kingdomcore.listener;

import com.yourorg.kingdomcore.util.DogRodItem;
import com.yourorg.kingdomcore.util.WolfArmorUtil;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DogRodListener implements Listener {
    private static final int DOG_COUNT = 50;
    private static final int ARMORED_COUNT = 20;
    private static final int BUFF_TICKS = 8 * 60 * 20;

    private final JavaPlugin plugin;

    public DogRodListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCast(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        Player owner = event.getPlayer();
        if (DogRodItem.findInHands(owner, plugin) == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getHook() != null) {
            event.getHook().remove();
        }

        DogRodItem.consume(owner, plugin);
        spawnDogs(owner);
        owner.sendMessage("§6§l50 dogs §ahave joined you.");
    }

    private void spawnDogs(Player owner) {
        Location feet = owner.getLocation();
        for (int i = 0; i < DOG_COUNT; i++) {
            Wolf wolf = (Wolf) owner.getWorld().spawnEntity(feet, EntityType.WOLF);
            wolf.setOwner(owner);
            wolf.setTamed(true);
            wolf.setAdult();
            wolf.setAngry(false);
            wolf.setCollarColor(DyeColor.RED);
            wolf.addScoreboardTag(DogRodItem.WOLF_TAG);
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, BUFF_TICKS, 1, false, true, true));
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BUFF_TICKS, 0, false, true, true));
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, BUFF_TICKS, 0, false, true, true));
            if (i < ARMORED_COUNT) {
                Wolf armoredWolf = wolf;
                plugin.getServer().getScheduler().runTask(plugin, () -> WolfArmorUtil.equipBodyArmor(armoredWolf));
            }
        }
    }
}
