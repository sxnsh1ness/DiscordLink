package io.github.sxnsh1ness.discordlink.database;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import io.github.sxnsh1ness.discordlink.data.CodeData;
import io.github.sxnsh1ness.discordlink.util.Logger;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class Database {

    private Connection connection;

    public boolean initialize() {
        try {
            File dbFile = new File(DiscordLink.getInstance().getDataFolder(), "data.db");
            DiscordLink.getInstance().getDataFolder().mkdirs();

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            createTables();
            Logger.info("Database initialized successfully.");
            return true;
        } catch (Exception e) {
            Logger.severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS linked_accounts (
                    uuid         TEXT PRIMARY KEY,
                    discord_id   TEXT NOT NULL UNIQUE,
                    discord_tag  TEXT NOT NULL,
                    linked_at    LONG NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS link_codes (
                    code         TEXT PRIMARY KEY,
                    uuid         TEXT NOT NULL,
                    username     TEXT NOT NULL,
                    expires_at   LONG NOT NULL
                )
            """);
        }
    }

    public void saveLink(UUID uuid, String discordId, String discordTag) throws SQLException {
        String sql = "INSERT OR REPLACE INTO linked_accounts (uuid, discord_id, discord_tag, linked_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, discordId);
            ps.setString(3, discordTag);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public void removeLink(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void removeLinkByDiscordId(String discordId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM linked_accounts WHERE discord_id = ?")) {
            ps.setString(1, discordId);
            ps.executeUpdate();
        }
    }

    public String getDiscordId(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT discord_id FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("discord_id") : null;
        }
    }

    public String getDiscordTag(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT discord_tag FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("discord_tag") : null;
        }
    }

    public UUID getMinecraftUUID(String discordId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid FROM linked_accounts WHERE discord_id = ?")) {
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? UUID.fromString(rs.getString("uuid")) : null;
        }
    }

    public boolean isLinked(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return ps.executeQuery().next();
        }
    }

    public boolean isDiscordLinked(String discordId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM linked_accounts WHERE discord_id = ?")) {
            ps.setString(1, discordId);
            return ps.executeQuery().next();
        }
    }

    public void saveCode(String code, UUID uuid, String username, long expiresAt) throws SQLException {
        deleteCodeByUUID(uuid);
        String sql = "INSERT INTO link_codes (code, uuid, username, expires_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, uuid.toString());
            ps.setString(3, username);
            ps.setLong(4, expiresAt);
            ps.executeUpdate();
        }
    }

    public CodeData getCode(String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, username, expires_at FROM link_codes WHERE code = ?")) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            long expiresAt = rs.getLong("expires_at");
            if (System.currentTimeMillis() > expiresAt) {
                deleteCode(code);
                return null;
            }
            return new CodeData(UUID.fromString(rs.getString("uuid")), rs.getString("username"));
        }
    }

    public void deleteCode(String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM link_codes WHERE code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        }
    }

    public void deleteCodeByUUID(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM link_codes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void updateDiscordTag(String discordId, String newTag) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE linked_accounts SET discord_tag = ? WHERE discord_id = ?")) {
            ps.setString(1, newTag);
            ps.setString(2, discordId);
            ps.executeUpdate();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
