package dev.rage.anticheat.moderation;

import java.util.UUID;

/** A player who has joined at least once. Times are epoch milliseconds. */
public record PlayerRecord(UUID uuid, String name, long firstJoin, long lastJoin, String lastIp) {
}
