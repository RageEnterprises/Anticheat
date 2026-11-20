package dev.rage.anticheat.logic;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Matches what a client tells the server about itself (its brand, such as "fabric" or
 * "vanilla", and the plugin channels its mods register, such as "baritone:main") against
 * blocked names. Names are compared as whole words, so "impact" matches "impact:sync" but not
 * "impactful".
 */
public final class ModBlocklist {

    /** Channel namespaces that vanilla clients, proxies and protocol translators use. */
    private static final List<String> VANILLA_NAMESPACES = List.of("minecraft:", "bungeecord:", "velocity:");

    private final Set<String> blocked = new HashSet<>();

    public ModBlocklist(Collection<String> names) {
        for (String name : names) {
            String word = name.trim().toLowerCase(Locale.ROOT);
            if (!word.isEmpty()) {
                blocked.add(word);
            }
        }
    }

    /** The first blocked name found in {@code text} (a brand or channel), or null. */
    public String match(String text) {
        if (text == null || blocked.isEmpty()) {
            return null;
        }
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (blocked.contains(word)) {
                return word;
            }
        }
        return null;
    }

    /** The first blocked name in the brand or any channel, or null. */
    public String match(String brand, Collection<String> channels) {
        String found = match(brand);
        if (found != null) {
            return found;
        }
        for (String channel : channels) {
            found = match(channel);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * The first channel that only a mod would register, or null. The vanilla client registers
     * none, so a client calling itself "vanilla" with such a channel is hiding its mods.
     */
    public static String moddedChannel(Collection<String> channels) {
        for (String channel : channels) {
            String lower = channel.toLowerCase(Locale.ROOT);
            if (VANILLA_NAMESPACES.stream().noneMatch(lower::startsWith)) {
                return channel;
            }
        }
        return null;
    }
}
