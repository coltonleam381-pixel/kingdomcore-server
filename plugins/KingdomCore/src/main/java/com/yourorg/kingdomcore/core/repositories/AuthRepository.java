package com.yourorg.kingdomcore.core.repositories;

import java.util.UUID;

public interface AuthRepository {
    boolean hasAccount(UUID playerId);
    void createAccount(UUID playerId, String pinHash, String ip);
    String getPinHash(UUID playerId);
    String getLastIp(UUID playerId);
    void updateLastIp(UUID playerId, String ip);
    void updatePin(UUID playerId, String pinHash);
    int getFailedAttempts(UUID playerId);
    void setFailedAttempts(UUID playerId, int attempts);
    void incrementFailedAttempts(UUID playerId);
    void resetFailedAttempts(UUID playerId);
    void deleteAccount(UUID playerId);
}
