package dev.rage.anticheat.moderation;

import java.util.UUID;

/**
 * One punishment in a player's history. Times are epoch milliseconds; {@code expires} is 0 for
 * permanent. Kicks and warnings are stored as inactive records so they show up in the history.
 */
public record Punishment(long id, UUID uuid, String name, Type type, String reason, String staff,
                         long created, long expires, boolean active, String removedBy, long removedAt) {

    public enum Type {
        BAN, MUTE, KICK, WARN;

        /** Bans and mutes last for a time and can be lifted; kicks and warnings are one-off. */
        public boolean lasting() {
            return this == BAN || this == MUTE;
        }
    }

    public boolean permanent() {
        return expires == 0;
    }

    public boolean expired(long now) {
        return !permanent() && now >= expires;
    }

    /** In force right now. */
    public boolean inForce(long now) {
        return active && !expired(now);
    }
}
