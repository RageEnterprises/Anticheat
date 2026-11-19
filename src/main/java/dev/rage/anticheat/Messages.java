package dev.rage.anticheat;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

    Component get(String key) {
        return get(key, null, null, -1);
    }

    /** Renders {@code key}; null placeholders (or count < 0) are left untouched. */
    Component get(String key, String player, String check, int count) {
        String message = yaml.getString(key, key);
        message = replace(message, "{player}", player);
        message = replace(message, "{check}", check);
        message = replace(message, "{count}", count < 0 ? null : Integer.toString(count));
        String format = yaml.getString("format", "{message}");
        return MINI.deserialize(format.replace("{message}", message));
    }

    private static String replace(String template, String placeholder, String value) {
        return value == null ? template : template.replace(placeholder, MINI.escapeTags(value));
    }
}
