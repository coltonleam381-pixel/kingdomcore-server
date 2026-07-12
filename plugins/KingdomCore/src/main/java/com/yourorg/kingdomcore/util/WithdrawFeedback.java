package com.yourorg.kingdomcore.util;

import com.yourorg.kingdomcore.abilities.AbilityScoreboard;
import com.yourorg.kingdomcore.core.services.WithdrawResult;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class WithdrawFeedback {
    private static final long MESSAGE_MS = 2000L;

    private WithdrawFeedback() {
    }

    public static void apply(Player player, AbilityScoreboard scoreboard, WithdrawResult result, int amount) {
        if (player == null || scoreboard == null || result == null) {
            return;
        }
        switch (result) {
            case SUCCESS -> {
                String heartsLabel = amount == 1 ? "heart" : "hearts";
                scoreboard.showTemporaryActionBar(
                        player,
                        "§a§lWithdraw successful! §7(-" + amount + " " + heartsLabel + ")",
                        MESSAGE_MS
                );
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            }
            case INVENTORY_FULL -> {
                scoreboard.showTemporaryActionBar(player, "§c§lInventory full! §7Make space first.", MESSAGE_MS);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            case NOT_ENOUGH_HEARTS -> {
                scoreboard.showTemporaryActionBar(
                        player,
                        "§c§lNot enough hearts! §7You must keep at least 1.",
                        MESSAGE_MS
                );
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
            }
            case INVALID_AMOUNT -> {
            }
        }
    }
}
