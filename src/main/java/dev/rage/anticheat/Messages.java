package dev.rage.anticheat;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads messages.yml and renders templates through the shared "RAGE → {message}" format. */
final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private YamlConfiguration yaml = new YamlConfiguration();

    void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        InputStream bundled = plugin.getResource("messages.yml");
        if (bundled != null) {
            // Keys missing from an older file fall back to the bundled defaults.
            loaded.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        yaml = loaded;
    }

    /**
     * Renders {@code key} inside the shared format.
     *
     * @param placeholders name/value pairs, e.g. {@code "player", "Steve", "check", "Speed"} fills
     *                     {player} and {check}. Values are escaped so they cannot inject formatting.
     */
    Component get(String key, String... placeholders) {
        String format = yaml.getString("format", "{message}");
        return MINI.deserialize(format.replace("{message}", fill(key, placeholders)));
    }

    /** Renders {@code key} on its own, without the "RAGE →" format. Used for kick and ban screens. */
    Component screen(String key, String... placeholders) {
        return MINI.deserialize(fill(key, placeholders));
    }

    /** {@code key} as plain text without the format, for Discord. */
    String plain(String key, String... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(MINI.deserialize(fill(key, placeholders)));
    }

    private String fill(String key, String... placeholders) {
        String message = yaml.getString(key, key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1];
            message = message.replace("{" + placeholders[i] + "}", value == null ? "" : MINI.escapeTags(value));
        }
        return message;
    }
}
