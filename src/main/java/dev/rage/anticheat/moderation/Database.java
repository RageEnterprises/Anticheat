package dev.rage.anticheat.moderation;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One SQLite connection used only by one background thread, so queries never block the server
 * thread and never run concurrently. SQLite is loaded through plugin.yml {@code libraries}.
 */
public final class Database implements AutoCloseable {

    /** A unit of work run on the database thread. */
    @FunctionalInterface
    public interface Task<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Connection connection;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RageAnticheat-Database");
        t.setDaemon(true);
        return t;
    });

    private Database(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
    }

    public static Database open(File file, Logger logger) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver not found; check that the server can download plugin libraries", e);
        }
        file.getParentFile().mkdirs();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        Database db = new Database(connection, logger);
        db.migrate();
        return db;
    }

    private void migrate() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        name_lower TEXT NOT NULL,
                        first_join INTEGER NOT NULL,
                        last_join INTEGER NOT NULL,
                        last_ip TEXT
                    )""");
            s.execute("CREATE INDEX IF NOT EXISTS players_name ON players(name_lower)");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS player_ips (
                        uuid TEXT NOT NULL,
                        ip TEXT NOT NULL,
                        last_seen INTEGER NOT NULL,
                        PRIMARY KEY (uuid, ip)
                    )""");
            s.execute("CREATE INDEX IF NOT EXISTS player_ips_ip ON player_ips(ip)");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS flags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        check_name TEXT NOT NULL,
                        details TEXT NOT NULL,
                        time INTEGER NOT NULL
                    )""");
            s.execute("CREATE INDEX IF NOT EXISTS flags_uuid ON flags(uuid)");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS punishments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        staff TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        expires INTEGER NOT NULL,
                        active INTEGER NOT NULL,
                        removed_by TEXT,
                        removed_at INTEGER
                    )""");
            s.execute("CREATE INDEX IF NOT EXISTS punishments_uuid ON punishments(uuid, type, active)");
        }
    }

    /** Runs {@code task} on the database thread. Errors are logged and complete the future exceptionally. */
    public <T> CompletableFuture<T> submit(Task<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(task.run(connection));
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Database query failed", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** Runs {@code task} and waits for it. Only call this from a thread that may block, never the server thread. */
    public <T> T call(Task<T> task) {
        return submit(task).join();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
            connection.close();
        } catch (InterruptedException | SQLException e) {
            logger.log(Level.WARNING, "Could not close the database cleanly", e);
        }
    }
}
