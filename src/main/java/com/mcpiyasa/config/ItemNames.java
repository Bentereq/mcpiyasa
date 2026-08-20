package com.mcpiyasa.config;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

/** YAML urun adlarini ve bilinmeyen kimlikler icin okunur yedegi sunar. */
public final class ItemNames {
    private final Map<String, String> names;

    private ItemNames(Map<String, String> names) {
        this.names = Collections.unmodifiableMap(
            new LinkedHashMap<String, String>(names)
        );
    }

    public static ItemNames load(Reader reader) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
        Map<String, String> loaded = new LinkedHashMap<String, String>();

        for (String key : yaml.getKeys(false)) {
            if (yaml.isString(key)) {
                String value = yaml.getString(key);
                if (value != null && !value.trim().isEmpty()) {
                    loaded.put(key.toUpperCase(Locale.ROOT), value.trim());
                }
            }
        }

        return new ItemNames(loaded);
    }

    /** Test/eski yapicilar icin yalniz otomatik ad ureten bos harita. */
    public static ItemNames empty() {
        return new ItemNames(Collections.<String, String>emptyMap());
    }

    public String of(String itemId) {
        String raw = itemId == null ? "" : itemId.trim();
        if (!raw.isEmpty()) {
            String mapped = names.get(raw.toUpperCase(Locale.ROOT));
            if (mapped != null && !mapped.trim().isEmpty()) {
                return mapped;
            }
            String pretty = prettify(raw);
            if (!pretty.isEmpty()) {
                return pretty;
            }
            return raw;
        }
        return "Item";
    }

    public Set<String> keys() {
        return names.keySet();
    }

    private static String prettify(String raw) {
        String[] words = raw.toLowerCase(Locale.ROOT).split("_+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }
}
