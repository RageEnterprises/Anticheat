package dev.rage.anticheat.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Posts embeds to a Discord webhook from a background thread. Messages beyond {@code maxPerMinute}
 * are dropped so a flood of alerts cannot get the webhook rate-limited or block the server.
 */
public final class DiscordWebhook implements AutoCloseable {

    private final URI url;
    private final String username;
    private final int maxPerMinute;
    private final Logger logger;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RageAnticheat-Webhook");
        t.setDaemon(true);
        return t;
    });
    private final Deque<Long> sent = new ArrayDeque<>();
    private boolean warnedDrop;

    private DiscordWebhook(URI url, String username, int maxPerMinute, Logger logger) {
        this.url = url;
        this.username = username;
        this.maxPerMinute = maxPerMinute;
        this.logger = logger;
    }

    /** Returns a webhook, or null (with a warning) if the URL is not a Discord webhook URL. */
    public static DiscordWebhook create(String url, String username, int maxPerMinute, Logger logger) {
        if (url == null || !(url.startsWith("https://discord.com/api/webhooks/")
                || url.startsWith("https://discordapp.com/api/webhooks/")
                || url.startsWith("https://ptb.discord.com/api/webhooks/")
                || url.startsWith("https://canary.discord.com/api/webhooks/"))) {
            logger.warning("discord.webhook.url is not a Discord webhook URL; the webhook is disabled.");
            return null;
        }
        return new DiscordWebhook(URI.create(url), username, Math.max(1, maxPerMinute), logger);
    }

    /** Queues one embed. Safe to call from any thread. */
    public void send(String title, String description, int color) {
        if (!allow()) {
            return;
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", truncate(title, 256));
        embed.addProperty("description", truncate(description, 4000));
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.add("embeds", embeds);
        // Never let player-controlled text ping @everyone or roles.
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);

        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        executor.execute(() -> {
            try {
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 300) {
                    logger.warning("Discord webhook returned HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                logger.warning("Discord webhook failed: " + e.getMessage());
            }
        });
    }

    private synchronized boolean allow() {
        long now = System.currentTimeMillis();
        while (!sent.isEmpty() && now - sent.peekFirst() > 60_000) {
            sent.pollFirst();
        }
        if (sent.size() >= maxPerMinute) {
            if (!warnedDrop) {
                logger.warning("Discord webhook limit of " + maxPerMinute + "/minute reached; dropping messages.");
                warnedDrop = true;
            }
            return false;
        }
        warnedDrop = false;
        sent.addLast(now);
        return true;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
