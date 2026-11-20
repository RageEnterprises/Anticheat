package dev.rage.anticheat.moderation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks proxycheck.io whether an IP belongs to a VPN or proxy. Results are cached so each IP is
 * looked up at most once per {@code cacheMillis}. Only call {@link #isVpn} from the async login thread.
 */
public final class VpnCheck {

    /** Thrown when the answer is unknown (network error, quota exceeded, bad response). */
    public static final class LookupException extends Exception {
        LookupException(String message) {
            super(message);
        }
    }

    private record Cached(boolean vpn, long until) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final String apiKey;
    private final long cacheMillis;

    public VpnCheck(String apiKey, long cacheMillis) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.cacheMillis = cacheMillis;
    }

    /** True if the address is a known VPN or proxy. Local and LAN addresses are never checked. */
    public boolean isVpn(InetAddress address) throws LookupException {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
            return false;
        }
        String ip = address.getHostAddress();
        long now = System.currentTimeMillis();
        Cached cached = cache.get(ip);
        if (cached != null && cached.until() > now) {
            return cached.vpn();
        }
        String url = "https://proxycheck.io/v2/" + URLEncoder.encode(ip, StandardCharsets.UTF_8) + "?vpn=1"
                + (apiKey.isEmpty() ? "" : "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build();
        String body;
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            body = response.body();
        } catch (Exception e) {
            throw new LookupException("proxycheck.io request failed: " + e.getMessage());
        }
        boolean vpn = parse(body, ip);
        cache.put(ip, new Cached(vpn, now + cacheMillis));
        return vpn;
    }

    /** Reads a proxycheck.io v2 response: {@code {"status":"ok","1.2.3.4":{"proxy":"yes",...}}}. */
    static boolean parse(String json, String ip) throws LookupException {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new LookupException("unreadable proxycheck.io response");
        }
        String status = root.has("status") ? root.get("status").getAsString() : "";
        JsonElement entry = root.get(ip);
        // "warning" still carries a result (e.g. close to the daily limit); "denied"/"error" do not.
        if (!(status.equals("ok") || status.equals("warning")) || entry == null || !entry.isJsonObject()) {
            String message = root.has("message") ? root.get("message").getAsString() : status;
            throw new LookupException("proxycheck.io: " + message);
        }
        JsonElement proxy = entry.getAsJsonObject().get("proxy");
        return proxy != null && proxy.getAsString().equalsIgnoreCase("yes");
    }
}
