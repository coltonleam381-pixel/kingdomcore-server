package com.yourorg.kingdomcore.persistence;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private final File file;

    private Connection connection;

    public Database(File file) {
        this.file = file;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = "jdbc:sqlite:" + file.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA journal_mode=WAL");
                pragma.execute("PRAGMA synchronous=NORMAL");
            }
        }
        return connection;
    }
    
    // Kept for backward compatibility if missed somewhere
    public Connection openConnection() throws SQLException {
        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        return DriverManager.getConnection(url);
    }

    public void checkpointAndClose() {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                try (Statement checkpoint = connection.createStatement()) {
                    checkpoint.execute("PRAGMA wal_checkpoint(FULL)");
                }
                connection.close();
            }
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }
}
