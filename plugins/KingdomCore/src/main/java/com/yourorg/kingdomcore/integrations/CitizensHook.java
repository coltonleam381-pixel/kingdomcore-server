package com.yourorg.kingdomcore.integrations;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

public class CitizensHook {
    private final boolean available;

    public CitizensHook() {
        this.available = Bukkit.getPluginManager().getPlugin("Citizens") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isNpc(Entity entity) {
        return entity != null && entity.hasMetadata("NPC");
    }

    public String getNpcName(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getCustomName() != null) {
            return entity.getCustomName();
        }
        return entity.getName();
    }
}
