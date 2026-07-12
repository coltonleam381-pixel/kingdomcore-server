package com.yourorg.kingdomcore.core.services;

public interface AllowlistService {
    boolean isAllowed(String name);

    void add(String name);

    void remove(String name);

    void reload();
}
