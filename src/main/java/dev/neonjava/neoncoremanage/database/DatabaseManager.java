package dev.neonjava.neoncoremanage.database;

import dev.neonjava.neoncoremanage.NeonCoreManage;
import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {
    private final NeonCoreManage plugin;
    private Connection connection;

    public DatabaseManager(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "database.db").getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS bans (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "name TEXT, " +
                        "staff TEXT, " +
                        "reason TEXT, " +
                        "expires_at INTEGER)");
                s.execute("CREATE TABLE IF NOT EXISTS mutes (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "name TEXT, " +
                        "staff TEXT, " +
                        "reason TEXT, " +
                        "expires_at INTEGER)");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not initialize SQLite database: " + e.getMessage());
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

    public record BanEntry(String uuid, String name, String staff, String reason, long expiresAt) {}
    public record MuteEntry(String uuid, String name, String staff, String reason, long expiresAt) {}

    public CompletableFuture<Void> addBan(BanEntry ban) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO bans (uuid, name, staff, reason, expires_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, ban.uuid());
                ps.setString(2, ban.name());
                ps.setString(3, ban.staff());
                ps.setString(4, ban.reason());
                ps.setLong(5, ban.expiresAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<Void> removeBan(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM bans WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<BanEntry> getBan(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM bans WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long expires = rs.getLong("expires_at");
                        if (expires > 0 && expires < System.currentTimeMillis()) {
                            removeBan(uuid);
                            return null;
                        }
                        return new BanEntry(
                                rs.getString("uuid"),
                                rs.getString("name"),
                                rs.getString("staff"),
                                rs.getString("reason"),
                                expires
                        );
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public CompletableFuture<Void> addMute(MuteEntry mute) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO mutes (uuid, name, staff, reason, expires_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, mute.uuid());
                ps.setString(2, mute.name());
                ps.setString(3, mute.staff());
                ps.setString(4, mute.reason());
                ps.setLong(5, mute.expiresAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<Void> removeMute(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM mutes WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<MuteEntry> getMute(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM mutes WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long expires = rs.getLong("expires_at");
                        if (expires > 0 && expires < System.currentTimeMillis()) {
                            removeMute(uuid);
                            return null;
                        }
                        return new MuteEntry(
                                rs.getString("uuid"),
                                rs.getString("name"),
                                rs.getString("staff"),
                                rs.getString("reason"),
                                expires
                        );
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }
}
