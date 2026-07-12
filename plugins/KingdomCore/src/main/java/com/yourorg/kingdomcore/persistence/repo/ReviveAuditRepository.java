package com.yourorg.kingdomcore.persistence.repo;

import java.util.UUID;

public interface ReviveAuditRepository {
    void record(UUID reviver, UUID target, long timestampMs, boolean success);
}
