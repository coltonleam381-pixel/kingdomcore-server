package com.yourorg.kingdomcore.abilities;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public interface EntityInteractAbility {
    boolean onEntityRightClick(Player player, LivingEntity target, int level, PlayerInteractEntityEvent event);
}
