package com.yourorg.kingdomcore.persistence.repo.impl;

import com.yourorg.kingdomcore.api.PlayerState;
import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.persistence.repo.PlayerStateRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SQLitePlayerStateRepository implements PlayerStateRepository {
    private final Database database;

    public SQLitePlayerStateRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<PlayerState> findById(UUID playerId) {
        String sql = "SELECT uuid,last_name,ability_id,ability_level,progression_hearts,blocked_state,assassin_win_bonus FROM player_state WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readState(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PlayerState> findByLastName(String lastName) {
        String sql = "SELECT uuid,last_name,ability_id,ability_level,progression_hearts,blocked_state,assassin_win_bonus FROM player_state WHERE last_name = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, lastName);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readState(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private PlayerState readState(ResultSet rs) throws SQLException {
        PlayerState state = new PlayerState(UUID.fromString(rs.getString("uuid")));
        state.setLastName(rs.getString("last_name"));
        state.setAbilityId(rs.getString("ability_id"));
        state.setAbilityLevel(rs.getInt("ability_level"));
        state.setProgressionHearts(rs.getInt("progression_hearts"));
        state.setBlocked(rs.getInt("blocked_state") == 1);
        state.setAssassinWinBonus(rs.getInt("assassin_win_bonus"));
        return state;
    }

    @Override
    public Optional<PlayerState> findByLastNameIgnoreCase(String lastName) {
        String sql = "SELECT uuid,last_name,ability_id,ability_level,progression_hearts,blocked_state,assassin_win_bonus " +
                "FROM player_state WHERE lower(last_name) = lower(?) LIMIT 1";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, lastName);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readState(rs));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<String> findAllLastNames() {
        String sql = "SELECT last_name FROM player_state WHERE last_name IS NOT NULL AND last_name != '' ORDER BY last_name";
        List<String> names = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("last_name"));
            }
        } catch (SQLException ignored) {
            // ignored
        }
        return names;
    }

    @Override
    public Optional<UUID> findUuidByAbilityId(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT uuid FROM player_state WHERE ability_id = ? LIMIT 1";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, abilityId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public void upsert(PlayerState state) {
        String sql = "INSERT INTO player_state(uuid,last_name,ability_id,ability_level,progression_hearts,blocked_state,assassin_win_bonus,updated_at) " +
                "VALUES (?,?,?,?,?,?,?,strftime('%s','now')) " +
                "ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name, ability_id=excluded.ability_id, " +
                "ability_level=excluded.ability_level, progression_hearts=excluded.progression_hearts, blocked_state=excluded.blocked_state, " +
                "assassin_win_bonus=excluded.assassin_win_bonus, updated_at=strftime('%s','now')";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, state.getUuid().toString());
            statement.setString(2, state.getLastName());
            statement.setString(3, state.getAbilityId());
            statement.setInt(4, state.getAbilityLevel());
            statement.setInt(5, state.getProgressionHearts());
            statement.setInt(6, state.isBlocked() ? 1 : 0);
            statement.setInt(7, state.getAssassinWinBonus());
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public void updateAbility(UUID playerId, String abilityId, int level) {
        String updateSql = "UPDATE player_state SET ability_id = ?, ability_level = ?, updated_at=strftime('%s','now') WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(updateSql)) {
            statement.setString(1, abilityId);
            statement.setInt(2, level);
            statement.setString(3, playerId.toString());
            if (statement.executeUpdate() > 0) {
                return;
            }
        } catch (SQLException e) {
            return;
        }

        String insertSql = "INSERT INTO player_state(uuid,last_name,ability_id,ability_level,progression_hearts,blocked_state,assassin_win_bonus,updated_at) " +
                "VALUES (?,?,?,?,10,0,0,strftime('%s','now'))";
        try (PreparedStatement insert = database.getConnection().prepareStatement(insertSql)) {
            insert.setString(1, playerId.toString());
            insert.setString(2, "");
            insert.setString(3, abilityId);
            insert.setInt(4, level);
            insert.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public void updateProgression(UUID playerId, int progression, boolean blocked) {
        String sql = "UPDATE player_state SET progression_hearts = ?, blocked_state = ?, updated_at=strftime('%s','now') WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, progression);
            statement.setInt(2, blocked ? 1 : 0);
            statement.setString(3, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public void updateLastName(UUID playerId, String lastName) {
        String sql = "UPDATE player_state SET last_name = ?, updated_at=strftime('%s','now') WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, lastName);
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public void updateAssassinWinBonus(UUID playerId, int bonus) {
        String sql = "UPDATE player_state SET assassin_win_bonus = ?, updated_at=strftime('%s','now') WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, Math.max(0, bonus));
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public long getCombatLogPendingAt(UUID playerId) {
        String sql = "SELECT combat_log_pending_at FROM player_state WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return rs.getLong("combat_log_pending_at");
            }
        } catch (SQLException e) {
            return 0L;
        }
    }

    @Override
    public void setCombatLogPending(UUID playerId, long pendingAtMs) {
        String updateSql = "UPDATE player_state SET combat_log_pending_at = ?, updated_at=strftime('%s','now') WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(updateSql)) {
            statement.setLong(1, pendingAtMs);
            statement.setString(2, playerId.toString());
            if (statement.executeUpdate() > 0) {
                return;
            }
        } catch (SQLException e) {
            return;
        }

        String insertSql = "INSERT INTO player_state(uuid,last_name,ability_id,ability_level,progression_hearts,blocked_state,assassin_win_bonus,combat_log_pending_at,updated_at) " +
                "VALUES (?,?,?,?,10,0,0,?,strftime('%s','now'))";
        try (PreparedStatement insert = database.getConnection().prepareStatement(insertSql)) {
            insert.setString(1, playerId.toString());
            insert.setString(2, "");
            insert.setString(3, null);
            insert.setInt(4, 0);
            insert.setLong(5, pendingAtMs);
            insert.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }

    @Override
    public void clearCombatLogPending(UUID playerId) {
        String sql = "UPDATE player_state SET combat_log_pending_at = 0, updated_at=strftime('%s','now') WHERE uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            // ignored
        }
    }
}
