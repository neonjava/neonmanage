package dev.neonjava.neoncoremanage.database;

import dev.neonjava.neoncoremanage.NeonCoreManage;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {
    private final NeonCoreManage plugin;
    private final String dbPath;
    private Connection connection;

    public DatabaseManager(NeonCoreManage plugin) {
        this.plugin = plugin;
        this.dbPath = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "database.db").getAbsolutePath();
    }

    /** Returns a valid connection, reconnecting if it is closed or null. */
    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            plugin.getLogger().info("(Re)connecting to SQLite database...");
            connection = DriverManager.getConnection(dbPath);
        }
        return connection;
    }

    public void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            try (Statement s = getConnection().createStatement()) {
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
                s.execute("CREATE TABLE IF NOT EXISTS pending_ranks (" +
                        "player_name TEXT PRIMARY KEY, " +
                        "rank_name TEXT, " +
                        "staff_name TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS ip_bans (" +
                        "ip TEXT PRIMARY KEY, " +
                        "staff TEXT, " +
                        "reason TEXT, " +
                        "expires_at INTEGER)");
                s.execute("CREATE TABLE IF NOT EXISTS player_ips (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "name TEXT, " +
                        "ip TEXT)");
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
    public record PendingRank(String playerName, String rankName, String staffName) {}
    public record IpBanEntry(String ip, String staff, String reason, long expiresAt) {}

    public CompletableFuture<Void> addIpBan(IpBanEntry ban) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO ip_bans (ip, staff, reason, expires_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, ban.ip());
                ps.setString(2, ban.staff());
                ps.setString(3, ban.reason());
                ps.setLong(4, ban.expiresAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> removeIpBan(String ip) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM ip_bans WHERE ip = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, ip);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<IpBanEntry> getIpBan(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM ip_bans WHERE ip = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long expires = rs.getLong("expires_at");
                        if (expires > 0 && expires < System.currentTimeMillis()) {
                            removeIpBan(ip).join();
                            return null;
                        }
                        return new IpBanEntry(
                                rs.getString("ip"),
                                rs.getString("staff"),
                                rs.getString("reason"),
                                expires
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing query: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> savePlayerIp(UUID uuid, String name, String ip) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO player_ips (uuid, name, ip) VALUES (?, ?, ?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name.toLowerCase());
                ps.setString(3, ip);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<String> getLastKnownIp(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT ip FROM player_ips WHERE name = ? ORDER BY uuid DESC LIMIT 1";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, name.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("ip");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing query: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> addBan(BanEntry ban) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO bans (uuid, name, staff, reason, expires_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, ban.uuid());
                ps.setString(2, ban.name());
                ps.setString(3, ban.staff());
                ps.setString(4, ban.reason());
                ps.setLong(5, ban.expiresAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> removeBan(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM bans WHERE uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<BanEntry> getBan(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM bans WHERE uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long expires = rs.getLong("expires_at");
                        if (expires > 0 && expires < System.currentTimeMillis()) {
                            removeBan(uuid).join();
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
                plugin.getLogger().severe("[NeonCoreManage] Error executing query: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> addMute(MuteEntry mute) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO mutes (uuid, name, staff, reason, expires_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, mute.uuid());
                ps.setString(2, mute.name());
                ps.setString(3, mute.staff());
                ps.setString(4, mute.reason());
                ps.setLong(5, mute.expiresAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> removeMute(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM mutes WHERE uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<MuteEntry> getMute(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM mutes WHERE uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long expires = rs.getLong("expires_at");
                        if (expires > 0 && expires < System.currentTimeMillis()) {
                            removeMute(uuid).join();
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
                plugin.getLogger().severe("[NeonCoreManage] Error executing query: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> addPendingRank(String playerName, String rankName, String staffName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO pending_ranks (player_name, rank_name, staff_name) VALUES (?, ?, ?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, playerName.toLowerCase());
                ps.setString(2, rankName);
                ps.setString(3, staffName);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> removePendingRank(String playerName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM pending_ranks WHERE player_name = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, playerName.toLowerCase());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing update: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<PendingRank> getPendingRank(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM pending_ranks WHERE player_name = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, playerName.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new PendingRank(
                                rs.getString("player_name"),
                                rs.getString("rank_name"),
                                rs.getString("staff_name")
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[NeonCoreManage] Error executing query: " + e.getMessage());
            }
            return null;
        });
    }
}
