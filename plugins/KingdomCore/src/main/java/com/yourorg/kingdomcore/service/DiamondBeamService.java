package com.yourorg.kingdomcore.service;

import org.bukkit.Location;

import java.util.List;

public interface DiamondBeamService {

    boolean isEnabled();

    boolean setEnabled(boolean enabled);

    double getThickness();

    boolean setThickness(double thickness);

    void reload();

    void rescan();

    List<Location> getBeamSources();
}
