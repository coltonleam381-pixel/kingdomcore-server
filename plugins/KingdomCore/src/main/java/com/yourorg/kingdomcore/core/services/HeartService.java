package com.yourorg.kingdomcore.core.services;

import com.yourorg.kingdomcore.api.PlayerState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface HeartService {
    PlayerState getOrCreateState(UUID playerId, String lastName);

    boolean consumeHeartItem(Player player, ItemStack stack, int amount);

    boolean tryUpgradeAbility(Player player, int cost);

    boolean applyDeathPenalty(Player player, String kickMessage);

    /**
     * Victim loses 1 heart with no item drop. Killer gains 1 heart automatically unless they are
     * already at max progression hearts, in which case a heart item drops at dropLocation.
     */
    boolean applyKillHeartTransfer(Player victim, Player killer, UUID killerId, Location dropLocation, String blockedKickMessage);

    WithdrawResult withdrawHearts(Player player, int amount);

    /**
     * Removes progression hearts from source and gives physical heart items to receiver.
     * Source must keep at least one heart. Works for offline sources via UUID.
     */
    WithdrawResult withdrawHeartsFrom(java.util.UUID sourceId, String sourceName, Player receiver, int amount);
    
    void addHearts(Player player, int amount);
    
    void removeHearts(Player player, int amount);

    void updateAbilityLevel(UUID playerId, int newLevel);

    void updateAbilityState(UUID playerId, String abilityId, int level);

    void updateLastName(UUID playerId, String name);
    
    void invalidateCache(UUID playerId);
    
    void setBlocked(UUID playerId, boolean blocked);

    void setProgressionAndBlocked(UUID playerId, int hearts, boolean blocked);

    void grantAssassinWinBonus(Player player, int bonusHearts);
}
