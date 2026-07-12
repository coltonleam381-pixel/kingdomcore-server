package com.yourorg.kingdomcore.persistence.repo.impl;

import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.persistence.repo.CooldownRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SQLiteCooldownRepository implements CooldownRepository {
    private final Database database;

    public SQLiteCooldownRepository(Database database) {
        this.database = database;
    }

    @Override
    public Map<String, Long> loadCooldowns(UUID playerId) {
        String sql = "SELECT ability_id, ready_at_ms FROM cooldowns WHERE uuid = ?";
        Map<String, Long> result = new HashMap<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("ability_id"), rs.getLong("ready_at_ms"));
                }
            }
        } catch (SQLException e) {
            return result;
        }
        return result;
    }

    @Override
    public void saveCooldown(UUID playerId, String abilityId, long readyAtMs) {
        String sql = "INSERT INTO cooldowns(uuid, ability_id, ready_at_ms) VALUES (?,?,?) " +
                "ON CONFLICT(uuid, ability_id) DO UPDATE SET ready_at_ms=excluded.ready_at_ms";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, abilityId);
            statement.setLong(3, readyAtMs);
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public void clear(UUID playerId) {
        String sql = "DELETE FROM cooldowns WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }
}
