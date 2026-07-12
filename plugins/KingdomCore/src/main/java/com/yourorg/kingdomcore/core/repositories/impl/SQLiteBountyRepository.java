package com.yourorg.kingdomcore.core.repositories.impl;

import com.yourorg.kingdomcore.persistence.Database;
import com.yourorg.kingdomcore.core.repositories.BountyRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SQLiteBountyRepository implements BountyRepository {
    private final Database database;

    public SQLiteBountyRepository(Database database) {
        this.database = database;
    }

    @Override
    public void addBounty(UUID playerId, int amount) {
        String selectSql = "SELECT bounty_amount FROM bounties WHERE uuid = ?";
        String updateSql = "UPDATE bounties SET bounty_amount = bounty_amount + ? WHERE uuid = ?";
        String insertSql = "INSERT INTO bounties (uuid, bounty_amount) VALUES (?, ?)";
        
        try {
            Connection conn = database.getConnection();
            boolean exists = false;
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, playerId.toString());
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        exists = true;
                    }
                }
            }
            
            if (exists) {
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setInt(1, amount);
                    updateStmt.setString(2, playerId.toString());
                    updateStmt.executeUpdate();
                }
            } else {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, playerId.toString());
                    insertStmt.setInt(2, amount);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void resetBounty(UUID playerId) {
        String sql = "DELETE FROM bounties WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getBounty(UUID playerId) {
        String sql = "SELECT bounty_amount FROM bounties WHERE uuid = ?";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("bounty_amount");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public Map<UUID, Integer> getAllBounties() {
        Map<UUID, Integer> map = new HashMap<>();
        String sql = "SELECT uuid, bounty_amount FROM bounties";
        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    map.put(UUID.fromString(rs.getString("uuid")), rs.getInt("bounty_amount"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }
}
