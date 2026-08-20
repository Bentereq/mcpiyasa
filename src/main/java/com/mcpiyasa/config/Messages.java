package com.mcpiyasa.config;

import com.mcpiyasa.compat.Text;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

/** YAML mesajlarini renk ve yer tutucu destegiyle sunar. */
public final class Messages {
    private final Map<String, String> messages;

    private Messages(Map<String, String> messages) {
        this.messages = Collections.unmodifiableMap(
            new LinkedHashMap<String, String>(messages)
        );
    }

    public static Messages load(Reader reader) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
        Map<String, String> loaded = new LinkedHashMap<String, String>();

        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                String value = yaml.getString(key);
                loaded.put(key, Text.color(value));
            }
        }

        return new Messages(loaded);
    }

    public String get(String key) {
        return get(key, Collections.<String, String>emptyMap());
    }

    public String get(String key, Map<String, String> vars) {
        String message = messages.get(key);
        if (message == null) {
            return "!eksik-mesaj:" + key + "!";
        }

        if (vars != null) {
            for (Map.Entry<String, String> variable : vars.entrySet()) {
                if (variable.getKey() != null && variable.getValue() != null) {
                    message = message.replace(
                        "{" + variable.getKey() + "}",
                        variable.getValue()
                    );
                }
            }
        }
        return message;
    }

    /** Oyuncu/komut sohbetine giden bir mesaji tek tanimli prefix ile sunar. */
    public String chat(String key) {
        return chat(key, Collections.<String, String>emptyMap());
    }

    /** GUI baslik/lore metinlerinden bagimsiz, prefix'li sohbet mesaji. */
    public String chat(String key, Map<String, String> vars) {
        return get("prefix") + get(key, vars);
    }

    public Set<String> keys() {
        return messages.keySet();
    }
}
