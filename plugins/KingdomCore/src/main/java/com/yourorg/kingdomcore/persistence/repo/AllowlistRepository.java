package com.yourorg.kingdomcore.persistence.repo;

import java.util.Set;

public interface AllowlistRepository {
    Set<String> loadAll();

    void add(String name);

    void remove(String name);
}
