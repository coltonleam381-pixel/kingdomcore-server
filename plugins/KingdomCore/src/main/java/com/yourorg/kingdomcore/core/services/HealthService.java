package com.yourorg.kingdomcore.core.services;

import com.yourorg.kingdomcore.api.PlayerState;
import org.bukkit.entity.Player;

public interface HealthService {
    void applyHealth(Player player, PlayerState state);

    void applyCrownBonus(Player player, PlayerState state);

    double resolveMaxHealth(Player player, PlayerState state);

    void clampToMax(Player player, PlayerState state);
}
