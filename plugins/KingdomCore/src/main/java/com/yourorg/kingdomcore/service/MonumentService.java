package com.yourorg.kingdomcore.service;

import org.bukkit.Location;

import java.util.List;

public interface MonumentService {
    void setGlassLocation(String itemId, Location location);

    void removeGlassLocation(String itemId);

    void updateGlassState(String itemId, boolean isCrafted);

    List<Location> getMonumentLocations();
}
