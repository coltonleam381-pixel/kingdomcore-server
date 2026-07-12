package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.abilities.AbilityHandler;
import com.yourorg.kingdomcore.api.AbilityDefinition;
import com.yourorg.kingdomcore.core.services.AbilityService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AbilityServiceImpl implements AbilityService {
    private final Map<String, AbilityDefinition> abilities;
    private final Map<String, AbilityHandler> handlers = new ConcurrentHashMap<>();

    public AbilityServiceImpl(Map<String, AbilityDefinition> abilities) {
        this.abilities = abilities;
    }

    @Override
    public AbilityDefinition getAbility(String id) {
        return abilities.get(id);
    }

    @Override
    public Collection<AbilityDefinition> getAllAbilities() {
        return abilities.values();
    }

    @Override
    public void registerHandler(AbilityHandler handler) {
        handlers.put(handler.getAbilityId(), handler);
    }

    @Override
    public AbilityHandler getHandler(String abilityId) {
        return handlers.get(abilityId);
    }

    @Override
    public boolean activateAbilityRightClick(Player player, AbilityDefinition ability, int level, PlayerInteractEvent event) {
        AbilityHandler handler = handlers.get(ability.id());
        if (handler != null) {
            return handler.onRightClick(player, level, event);
        }
        return false;
    }

    @Override
    public boolean activateAbilityLeftClick(Player player, String abilityId, int level) {
        AbilityHandler handler = handlers.get(abilityId);
        if (handler != null) {
            return handler.onLeftClick(player, level);
        }
        return false;
    }

    @Override
    public boolean handleSneakRightClick(Player player, String abilityId, int level) {
        AbilityHandler handler = handlers.get(abilityId);
        if (handler != null) {
            return handler.onSneakRightClick(player, level);
        }
        return false;
    }

    @Override
    public boolean handleSpace(Player player, String abilityId) {
        AbilityHandler handler = handlers.get(abilityId);
        if (handler != null) {
            return handler.onSpace(player);
        }
        return false;
    }

    @Override
    public void cleanup(Player player) {
        for (AbilityHandler handler : handlers.values()) {
            handler.cleanup(player);
        }
    }
}
