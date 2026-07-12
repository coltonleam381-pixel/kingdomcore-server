package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.core.services.HealthService;
import com.yourorg.kingdomcore.core.services.ItemIdentityService;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class HealthServiceImpl implements HealthService {
    private static final double MIN_HEALTH = 2.0;

    private final ItemIdentityService itemIdentityService;
    private final com.yourorg.kingdomcore.core.KingdomConfig config;
    private final Plugin plugin;

    public HealthServiceImpl(ItemIdentityService itemIdentityService,
                             com.yourorg.kingdomcore.core.KingdomConfig config,
                             Plugin plugin) {
        this.itemIdentityService = itemIdentityService;
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyHealth(Player player, PlayerState state) {
        if (player == null || state == null) {
            return;
        }
        double maxHealth = resolveMaxHealth(player, state);
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(maxHealth);
        }
        clampToMax(player, state);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                clampToMax(player, state);
            }
        }, 1L);
    }

    @Override
    public void applyCrownBonus(Player player, PlayerState state) {
        applyHealth(player, state);
    }

    @Override
    public double resolveMaxHealth(Player player, PlayerState state) {
        double maxHealth = Math.max(MIN_HEALTH, state.getProgressionHearts() * 2.0);
        if (isCrownWorn(player)) {
            maxHealth += config.getCrownBonusHearts() * 2.0;
        }
        return Math.min(maxHealth, absoluteCap(player, state) * 2.0);
    }

    @Override
    public void clampToMax(Player player, PlayerState state) {
        if (player == null || state == null) {
            return;
        }
        double maxHealth = resolveMaxHealth(player, state);
        if (player.getHealth() > maxHealth) {
            player.setHealth(Math.max(MIN_HEALTH, maxHealth));
        }
    }

    private boolean isCrownWorn(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return itemIdentityService.isCrownItem(helmet);
    }

    private int absoluteCap(Player player, PlayerState state) {
        int bonus = state == null ? 0 : state.getAssassinWinBonus();
        int cap = config.getProgressionMaxHearts() + bonus;
        if (isCrownWorn(player)) {
            cap += config.getCrownBonusHearts();
        }
        return cap;
    }
}
