package com.yourorg.kingdomcore.persistence.repo.impl;

import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.persistence.repo.ReviveAuditRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class SQLiteReviveAuditRepository implements ReviveAuditRepository {
    private final Database database;

    public SQLiteReviveAuditRepository(Database database) {
        this.database = database;
    }

    @Override
    public void record(UUID reviver, UUID target, long timestampMs, boolean success) {
        String sql = "INSERT INTO revive_audit(reviver_uuid,target_uuid,ts,success) VALUES (?,?,?,?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, reviver != null ? reviver.toString() : null);
            statement.setString(2, target != null ? target.toString() : null);
            statement.setLong(3, timestampMs);
            statement.setInt(4, success ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }
}
