package com.yourorg.kingdomcore.persistence.repo.impl;

import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.persistence.repo.AbilityOwnershipRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class SQLiteAbilityOwnershipRepository implements AbilityOwnershipRepository {
    private final Database database;

    public SQLiteAbilityOwnershipRepository(Database database) {
        this.database = database;
    }

    @Override
    public boolean claim(UUID playerId, String abilityId) {
        String insert = "INSERT INTO ability_owners(ability_id, owner_uuid, claimed_at) VALUES (?,?,strftime('%s','now'))";
        try {
            Connection connection = database.getConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, abilityId);
                statement.setString(2, playerId.toString());
                statement.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public Optional<UUID> findOwner(String abilityId) {
        String sql = "SELECT owner_uuid FROM ability_owners WHERE ability_id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, abilityId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(UUID.fromString(rs.getString("owner_uuid")));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findAbilityByOwner(UUID playerId) {
        String sql = "SELECT ability_id FROM ability_owners WHERE owner_uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("ability_id"));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public void clearAbility(String abilityId) {
        String sql = "DELETE FROM ability_owners WHERE ability_id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, abilityId);
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public void clearPlayer(UUID playerId) {
        String sql = "DELETE FROM ability_owners WHERE owner_uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }
}
