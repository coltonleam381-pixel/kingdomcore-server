package com.yourorg.kingdomcore.core.services;

import org.bukkit.entity.Player;
import java.util.UUID;

public interface AuthService {
    boolean isRegistered(UUID playerId);
    void registerPin(Player player, String pin);
    boolean verifyPin(Player player, String pin);
    boolean isAuthenticated(UUID playerId);
    void setAuthenticated(UUID playerId, boolean authenticated);
    /** Returns true when the player must use the PIN GUI (new account or new IP). */
    boolean requiresPinLogin(UUID playerId, String ip);
    void handlePlayerJoin(Player player, String ip);
    void handlePlayerQuit(UUID playerId);
    
    int getFailedAttempts(UUID playerId);
    void setFailedAttempts(UUID playerId, int attempts);
    void incrementFailedAttempts(UUID playerId);
    void resetFailedAttempts(UUID playerId);

    /** Admin bypass: mark logged in, refresh IP, clear PIN lock state. */
    void forceAuthenticate(Player player);

    /** Wipe saved PIN so the player must register a new one (first-join flow). */
    void resetAccountForNewPin(Player player);
}
