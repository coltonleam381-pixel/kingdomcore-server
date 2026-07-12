package com.yourorg.kingdomcore.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class MigrationRunner {
    private final Database database;
    private final List<Migration> migrations;

    public MigrationRunner(Database database, List<Migration> migrations) {
        this.database = database;
        this.migrations = migrations;
    }

    public void migrate() throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            ensureSchemaTable(connection);
            int current = getCurrentVersion(connection);
            for (Migration migration : migrations) {
                if (migration.version() > current) {
                    applyMigration(connection, migration);
                    setVersion(connection, migration.version());
                }
            }
            connection.commit();
        }
    }

    private void ensureSchemaTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS count FROM schema_version")) {
            if (rs.next() && rs.getInt("count") == 0) {
                statement.executeUpdate("INSERT INTO schema_version(version) VALUES (0)");
            }
        }
    }

    private int getCurrentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_version")) {
            if (rs.next()) {
                return rs.getInt("version");
            }
            return 0;
        }
    }

    private void applyMigration(Connection connection, Migration migration) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(migration.sql());
        }
    }

    private void setVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE schema_version SET version = " + version);
        }
    }
}
