package dev.rage.anticheat.moderation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** SQL for players, their IP history and their anticheat flags. Every method runs on the database thread. */
public final class PlayerStore {

    private PlayerStore() {
    }

    /** Inserts or updates the player and remembers the IP. Returns true if this is their first join. */
    public static boolean recordJoin(Connection c, UUID uuid, String name, String ip, long now) throws SQLException {
        boolean firstJoin = find(c, uuid) == null;
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO players (uuid, name, name_lower, first_join, last_join, last_ip) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, name_lower = excluded.name_lower,
                    last_join = excluded.last_join, last_ip = excluded.last_ip""")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, name.toLowerCase(Locale.ROOT));
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.setString(6, ip);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO player_ips (uuid, ip, last_seen) VALUES (?, ?, ?)
                ON CONFLICT(uuid, ip) DO UPDATE SET last_seen = excluded.last_seen""")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
        return firstJoin;
    }

    public static PlayerRecord find(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return single(ps);
        }
    }

    /** Most recent player with this name (names can move between accounts). */
    public static PlayerRecord findByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM players WHERE name_lower = ? ORDER BY last_join DESC LIMIT 1")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            return single(ps);
        }
    }

    public static int countIps(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM player_ips WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static void recordFlag(Connection c, UUID uuid, String check, String details, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO flags (uuid, check_name, details, time) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, check);
            ps.setString(3, details);
            ps.setLong(4, now);
            ps.executeUpdate();
        }
    }

    /** Number of flags per check, most frequent first. */
    public static Map<String, Integer> flagCounts(Connection c, UUID uuid) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT check_name, COUNT(*) AS n FROM flags WHERE uuid = ?
                GROUP BY check_name ORDER BY n DESC""")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return counts;
    }

    private static PlayerRecord single(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new PlayerRecord(UUID.fromString(rs.getString("uuid")), rs.getString("name"),
                    rs.getLong("first_join"), rs.getLong("last_join"), rs.getString("last_ip"));
        }
    }

    static List<PlayerRecord> list(PreparedStatement ps) throws SQLException {
        List<PlayerRecord> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new PlayerRecord(UUID.fromString(rs.getString("uuid")), rs.getString("name"),
                        rs.getLong("first_join"), rs.getLong("last_join"), rs.getString("last_ip")));
            }
        }
        return result;
    }
}
