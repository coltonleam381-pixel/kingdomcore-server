package com.yourorg.kingdomcore.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AbilityCooldownResetEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String item;

    public AbilityCooldownResetEvent(Player player, String item) {
        this.player = player;
        this.item = item;
    }

    public Player getPlayer() {
        return player;
    }

    public String getItem() {
        return item;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
