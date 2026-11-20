package dev.rage.anticheat.moderation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL for punishments. Every method runs on the database thread. */
public final class PunishmentStore {

    private PunishmentStore() {
    }

    /** Stores a new punishment. A new ban or mute replaces the player's current one of that type. */
    public static Punishment insert(Connection c, UUID uuid, String name, Punishment.Type type, String reason,
                                    String staff, long now, long expires) throws SQLException {
        boolean active = type.lasting();
        if (active) {
            deactivate(c, uuid, type, staff, now);
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO punishments (uuid, name, type, reason, staff, created, expires, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, type.name());
            ps.setString(4, reason);
            ps.setString(5, staff);
            ps.setLong(6, now);
            ps.setLong(7, expires);
            ps.setInt(8, active ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1;
                return new Punishment(id, uuid, name, type, reason, staff, now, expires, active, null, 0);
            }
        }
    }

    /** The ban or mute currently in force, or null. */
    public static Punishment active(Connection c, UUID uuid, Punishment.Type type, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT * FROM punishments WHERE uuid = ? AND type = ? AND active = 1
                AND (expires = 0 OR expires > ?) ORDER BY created DESC LIMIT 1""")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            ps.setLong(3, now);
            List<Punishment> found = list(ps);
            return found.isEmpty() ? null : found.get(0);
        }
    }

    /** Lifts the player's active bans or mutes. Returns how many were lifted. */
    public static int deactivate(Connection c, UUID uuid, Punishment.Type type, String staff, long now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE punishments SET active = 0, removed_by = ?, removed_at = ?
                WHERE uuid = ? AND type = ? AND active = 1 AND (expires = 0 OR expires > ?)""")) {
            ps.setString(1, staff);
            ps.setLong(2, now);
            ps.setString(3, uuid.toString());
            ps.setString(4, type.name());
            ps.setLong(5, now);
            return ps.executeUpdate();
        }
    }

    /** Most recent punishments first. */
    public static List<Punishment> history(Connection c, UUID uuid, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM punishments WHERE uuid = ? ORDER BY created DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            return list(ps);
        }
    }

    public static Map<Punishment.Type, Integer> counts(Connection c, UUID uuid) throws SQLException {
        Map<Punishment.Type, Integer> counts = new EnumMap<>(Punishment.Type.class);
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT type, COUNT(*) FROM punishments WHERE uuid = ? GROUP BY type")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(Punishment.Type.valueOf(rs.getString(1)), rs.getInt(2));
                }
            }
        }
        return counts;
    }

    private static List<Punishment> list(PreparedStatement ps) throws SQLException {
        List<Punishment> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Punishment(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"), Punishment.Type.valueOf(rs.getString("type")),
                        rs.getString("reason"), rs.getString("staff"), rs.getLong("created"),
                        rs.getLong("expires"), rs.getInt("active") == 1, rs.getString("removed_by"),
                        rs.getLong("removed_at")));
            }
        }
        return result;
    }
}
