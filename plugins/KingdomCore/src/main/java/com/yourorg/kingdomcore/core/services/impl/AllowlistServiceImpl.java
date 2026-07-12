package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.core.services.AllowlistService;
import com.yourorg.kingdomcore.persistence.repo.AllowlistRepository;
import com.yourorg.kingdomcore.util.NameNormalizer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AllowlistServiceImpl implements AllowlistService {
    private final AllowlistRepository repository;
    private volatile Set<String> cached = Set.of();

    public AllowlistServiceImpl(AllowlistRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isAllowed(String name) {
        String normalized = NameNormalizer.normalize(name);
        return cached.contains(normalized);
    }

    @Override
    public void add(String name) {
        String normalized = NameNormalizer.normalize(name);
        repository.add(normalized);
        reload();
    }

    @Override
    public void remove(String name) {
        String normalized = NameNormalizer.normalize(name);
        repository.remove(normalized);
        reload();
    }

    @Override
    public void reload() {
        Set<String> names = new HashSet<>(repository.loadAll());
        cached = Collections.unmodifiableSet(names);
    }
}
