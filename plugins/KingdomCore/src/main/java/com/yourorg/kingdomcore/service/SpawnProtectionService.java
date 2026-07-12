package com.yourorg.kingdomcore.service;

public interface SpawnProtectionService {

    boolean isEnabled();

    boolean setEnabled(boolean enabled);

    boolean toggle();

    void ensureRegion();

    boolean applyCurrentState();
}
