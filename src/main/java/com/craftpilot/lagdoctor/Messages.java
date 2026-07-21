package com.craftpilot.lagdoctor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Loads messages_&lt;lang&gt;.yml from the data folder with the jar-bundled file as
 * fallback; a completely missing key falls back to the key name itself.
 */
public final class Messages {

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)&[0-9a-fk-orx]");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private YamlConfiguration lang = new YamlConfiguration();
    private YamlConfiguration bundled = new YamlConfiguration();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        String code = "en".equalsIgnoreCase(language) ? "en" : "tr";
        if (!code.equalsIgnoreCase(language)) {
            plugin.getLogger().warning("Unknown language '" + language + "', falling back to 'tr'.");
        }
        saveIfMissing("messages_tr.yml");
        saveIfMissing("messages_en.yml");

        File file = new File(plugin.getDataFolder(), "messages_" + code + ".yml");
        lang = YamlConfiguration.loadConfiguration(file);

        bundled = new YamlConfiguration();
        InputStream in = plugin.getResource("messages_" + code + ".yml");
        if (in != null) {
            bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private void saveIfMissing(String name) {
        if (!new File(plugin.getDataFolder(), name).isFile() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
    }

    /** Raw message with {placeholder} substitution; kv is key,value,key,value... */
    public String raw(String key, String... kv) {
        String value = lang.getString(key);
        if (value == null) {
            value = bundled.getString(key, key);
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            value = value.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return value;
    }

    /** Message without color codes, for markdown/console file output. */
    public String plain(String key, String... kv) {
        return COLOR_CODES.matcher(raw(key, kv)).replaceAll("");
    }

    public Component msg(String key, String... kv) {
        return LEGACY.deserialize(raw(key, kv));
    }

    public Component prefixed(String key, String... kv) {
        return LEGACY.deserialize(raw("prefix") + raw(key, kv));
    }
}
