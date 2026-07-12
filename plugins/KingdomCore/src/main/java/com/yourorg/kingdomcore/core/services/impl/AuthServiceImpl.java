package com.yourorg.kingdomcore.core.services.impl;

import com.yourorg.kingdomcore.core.repositories.AuthRepository;
import com.yourorg.kingdomcore.core.services.AuthService;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AuthServiceImpl implements AuthService {
    private final AuthRepository authRepository;
    private final Set<UUID> authenticatedPlayers = new HashSet<>();

    public AuthServiceImpl(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean isRegistered(UUID playerId) {
        return authRepository.hasAccount(playerId);
    }

    @Override
    public void registerPin(Player player, String pin) {
        String hash = hashPin(pin);
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        authRepository.createAccount(player.getUniqueId(), hash, ip);
        setAuthenticated(player.getUniqueId(), true);
    }

    @Override
    public boolean verifyPin(Player player, String pin) {
        String storedHash = authRepository.getPinHash(player.getUniqueId());
        if (storedHash == null) {
            return false;
        }

        if (!hashPin(pin).equals(storedHash)) {
            return false;
        }

        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        authRepository.updateLastIp(player.getUniqueId(), ip);
        setAuthenticated(player.getUniqueId(), true);
        return true;
    }

    @Override
    public boolean isAuthenticated(UUID playerId) {
        return authenticatedPlayers.contains(playerId);
    }

    @Override
    public void setAuthenticated(UUID playerId, boolean authenticated) {
        if (authenticated) {
            authenticatedPlayers.add(playerId);
        } else {
            authenticatedPlayers.remove(playerId);
        }
    }

    @Override
    public boolean requiresPinLogin(UUID playerId, String ip) {
        if (!isRegistered(playerId)) {
            return true;
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return true;
        }
        String lastIp = authRepository.getLastIp(playerId);
        if (lastIp == null || lastIp.isBlank() || "unknown".equalsIgnoreCase(lastIp)) {
            return true;
        }
        return !lastIp.equals(ip);
    }

    @Override
    public void handlePlayerJoin(Player player, String ip) {
        UUID playerId = player.getUniqueId();
        if (requiresPinLogin(playerId, ip)) {
            setAuthenticated(playerId, false);
            return;
        }
        setAuthenticated(playerId, true);
        authRepository.updateLastIp(playerId, ip);
    }

    @Override
    public void handlePlayerQuit(UUID playerId) {
        authenticatedPlayers.remove(playerId);
    }

    @Override
    public int getFailedAttempts(UUID playerId) {
        return authRepository.getFailedAttempts(playerId);
    }

    @Override
    public void setFailedAttempts(UUID playerId, int attempts) {
        authRepository.setFailedAttempts(playerId, attempts);
    }

    @Override
    public void incrementFailedAttempts(UUID playerId) {
        authRepository.incrementFailedAttempts(playerId);
    }

    @Override
    public void resetFailedAttempts(UUID playerId) {
        authRepository.resetFailedAttempts(playerId);
    }

    @Override
    public void forceAuthenticate(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        setAuthenticated(playerId, true);
        resetFailedAttempts(playerId);
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        if (isRegistered(playerId)) {
            authRepository.updateLastIp(playerId, ip);
        }
    }

    @Override
    public void resetAccountForNewPin(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        authRepository.deleteAccount(playerId);
        setAuthenticated(playerId, false);
        resetFailedAttempts(playerId);
    }
}
