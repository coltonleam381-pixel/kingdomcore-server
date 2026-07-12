package com.yourorg.kingdomcore.persistence.repo.impl;

import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.persistence.repo.AllowlistRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class SQLiteAllowlistRepository implements AllowlistRepository {
    private final Database database;

    public SQLiteAllowlistRepository(Database database) {
        this.database = database;
    }

    @Override
    public Set<String> loadAll() {
        Set<String> names = new HashSet<>();
        String sql = "SELECT name FROM allowlist";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            return names;
        }
        return names;
    }

    @Override
    public void add(String name) {
        String sql = "INSERT OR IGNORE INTO allowlist(name) VALUES (?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public void remove(String name) {
        String sql = "DELETE FROM allowlist WHERE name = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }
}
