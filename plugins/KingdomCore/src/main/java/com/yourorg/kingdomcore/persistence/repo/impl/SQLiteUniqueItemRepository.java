package com.yourorg.kingdomcore.persistence.repo.impl;

import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.persistence.repo.UniqueItemRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SQLiteUniqueItemRepository implements UniqueItemRepository {
    private final Database database;

    public SQLiteUniqueItemRepository(Database database) {
        this.database = database;
    }

    @Override
    public Map<String, Long> loadCooldowns() {
        Map<String, Long> result = new HashMap<>();
        String sql = "SELECT item_id, unlock_at_ms FROM unique_item_cooldowns";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("item_id"), rs.getLong("unlock_at_ms"));
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    @Override
    public void saveCooldown(String itemId, long unlockAtMs) {
        String sql = "INSERT INTO unique_item_cooldowns(item_id, unlock_at_ms) VALUES (?, ?) " +
                "ON CONFLICT(item_id) DO UPDATE SET unlock_at_ms=excluded.unlock_at_ms";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, itemId);
            statement.setLong(2, unlockAtMs);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}
