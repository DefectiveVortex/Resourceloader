package org.vortex.resourceloader.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.vortex.resourceloader.Resourceloader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    private final Resourceloader plugin;
    private FileConfiguration messages;
    private String prefix;
    private String language;
    private final Map<String, String> messageCache;
    private static final String DEFAULT_PREFIX = "&7[&6ResourceLoader&7] &r";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");
    private static final String DEFAULT_MESSAGES_FILE = "messages.yml";
    private static final String DEFAULT_LANGUAGE = "en";

    // Bundled translations, stored under lang/messages_<code>.yml
    public static final List<String> SUPPORTED_LANGUAGES = List.of("en", "ru", "tr", "ko", "zh");

    public MessageManager(Resourceloader plugin) {
        this.plugin = plugin;
        this.messageCache = new ConcurrentHashMap<>();
        loadMessages();
    }

    public void loadMessages() {
        language = resolveLanguage(plugin.getConfig().getString("language", DEFAULT_LANGUAGE));
        String resourceName = getMessagesResource(language);

        try {
            File messagesFile = new File(plugin.getDataFolder(), resourceName);

            messages = ConfigFileUpdater.update(plugin, resourceName, messagesFile);
            messageCache.clear();

            // Keep bundled defaults in memory as a fallback for unsaved or unreadable values:
            // selected language first, then English
            YamlConfiguration englishDefaults = loadBundled(DEFAULT_MESSAGES_FILE);
            YamlConfiguration languageDefaults = resourceName.equals(DEFAULT_MESSAGES_FILE)
                ? englishDefaults : loadBundled(resourceName);
            if (languageDefaults != null) {
                if (englishDefaults != null && languageDefaults != englishDefaults) {
                    languageDefaults.setDefaults(englishDefaults);
                }
                messages.setDefaults(languageDefaults);
            } else if (englishDefaults != null) {
                messages.setDefaults(englishDefaults);
            }

            // Load prefix with fallback
            prefix = formatMessage(messages.getString("prefix", DEFAULT_PREFIX));

            if (!language.equals(DEFAULT_LANGUAGE)) {
                plugin.getLogger().info("Using messages from " + resourceName);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load messages: " + e.getMessage());
            plugin.getLogger().severe("Using default messages only");
            messages = new YamlConfiguration();
            YamlConfiguration englishDefaults = loadBundled(DEFAULT_MESSAGES_FILE);
            if (englishDefaults != null) {
                messages.setDefaults(englishDefaults);
            }
            prefix = formatMessage(DEFAULT_PREFIX);
        }
    }

    private String resolveLanguage(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        // Accept locale-style values such as "ru_RU", "zh-CN" or "ko_kr"
        String code = configured.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int separator = code.indexOf('_');
        if (separator > 0) {
            code = code.substring(0, separator);
        }

        if (!SUPPORTED_LANGUAGES.contains(code)) {
            plugin.getLogger().warning("Unsupported language '" + configured + "' in config.yml. Supported: "
                + String.join(", ", SUPPORTED_LANGUAGES) + ". Falling back to English.");
            return DEFAULT_LANGUAGE;
        }
        return code;
    }

    private static String getMessagesResource(String language) {
        if (DEFAULT_LANGUAGE.equals(language)) {
            return DEFAULT_MESSAGES_FILE;
        }
        return "lang/messages_" + language + ".yml";
    }

    private YamlConfiguration loadBundled(String resourceName) {
        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled " + resourceName + ": " + e.getMessage());
            return null;
        }
    }

    public String getMessage(String path) {
        return getMessage(path, true);
    }

    public String getMessage(String path, boolean addPrefix) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        String cacheKey = addPrefix ? path + "_prefixed" : path;
        return messageCache.computeIfAbsent(cacheKey, k -> {
            String formattedMessage = formatMessage(getRawMessage(path));
            return addPrefix ? prefix + formattedMessage : formattedMessage;
        });
    }

    public String getMessageNoPrefix(String path) {
        return getMessage(path, false);
    }

    private String getRawMessage(String path) {
        // getString(path) consults the bundled defaults; getString(path, def) would not
        String message = messages.getString(path);
        return message != null ? message : "";
    }

    public String formatMessage(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String formatMessage(String message, Map<String, String> placeholders) {
        if (message == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return formatMessage(message);
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = placeholders.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(result);
        return formatMessage(result.toString());
    }

    public String formatMessage(String path, Object... args) {
        return prefix + formatMessageNoPrefix(path, args);
    }

    public String formatMessageNoPrefix(String path, Object... args) {
        if (args == null || args.length % 2 != 0) {
            return getMessageNoPrefix(path);
        }

        Map<String, String> placeholders = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (args[i] != null && args[i + 1] != null) {
                placeholders.put(args[i].toString(), args[i + 1].toString());
            }
        }

        return formatMessage(getRawMessage(path), placeholders);
    }

    public void reloadMessages() {
        loadMessages();
    }

    public Set<String> getMessageKeys() {
        return messages.getKeys(true);
    }

    public String getLanguage() {
        return language;
    }

    public void setPrefix(String newPrefix) {
        if (newPrefix != null) {
            this.prefix = formatMessage(newPrefix);
            messageCache.clear(); // Clear cache since prefix affects prefixed messages
        }
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean hasMessage(String path) {
        return messages.contains(path);
    }

    public void setMessage(String path, String message) throws IOException {
        if (path != null && message != null) {
            messages.set(path, message);
            messageCache.remove(path);
            messageCache.remove(path + "_prefixed");
            messages.save(new File(plugin.getDataFolder(), getMessagesResource(language)));
        }
    }
}
