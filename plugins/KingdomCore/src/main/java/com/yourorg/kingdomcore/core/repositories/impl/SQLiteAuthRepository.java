package com.yourorg.kingdomcore.core.repositories.impl;

import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.core.repositories.AuthRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SQLiteAuthRepository implements AuthRepository {
    private final Database database;

    public SQLiteAuthRepository(Database database) {
        this.database = database;
    }

    @Override
    public boolean hasAccount(UUID playerId) {
        String sql = "SELECT 1 FROM player_auth WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void createAccount(UUID playerId, String pinHash, String ip) {
        String sql = "INSERT INTO player_auth (uuid, pin_hash, last_ip) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, pinHash);
            pstmt.setString(3, ip);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getPinHash(UUID playerId) {
        String sql = "SELECT pin_hash FROM player_auth WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("pin_hash");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getLastIp(UUID playerId) {
        String sql = "SELECT last_ip FROM player_auth WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("last_ip");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void updateLastIp(UUID playerId, String ip) {
        String sql = "UPDATE player_auth SET last_ip = ? WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, ip);
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updatePin(UUID playerId, String pinHash) {
        String sql = "UPDATE player_auth SET pin_hash = ? WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, pinHash);
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getFailedAttempts(UUID playerId) {
        String sql = "SELECT failed_attempts FROM player_auth WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("failed_attempts");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public void setFailedAttempts(UUID playerId, int attempts) {
        String sql = "UPDATE player_auth SET failed_attempts = ? WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, attempts);
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void incrementFailedAttempts(UUID playerId) {
        String sql = "UPDATE player_auth SET failed_attempts = failed_attempts + 1 WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void resetFailedAttempts(UUID playerId) {
        setFailedAttempts(playerId, 0);
    }

    @Override
    public void deleteAccount(UUID playerId) {
        String sql = "DELETE FROM player_auth WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
