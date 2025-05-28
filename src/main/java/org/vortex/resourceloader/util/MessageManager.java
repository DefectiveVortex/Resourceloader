package org.vortex.resourceloader.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.vortex.resourceloader.Resourceloader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class MessageManager {
    private final Resourceloader plugin;
    private FileConfiguration messages;
    private String prefix;
    private final Map<String, String> messageCache;
    private static final String DEFAULT_PREFIX = "&7[&6ResourceLoader&7] &r";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");
    private static final String DEFAULT_MESSAGES_FILE = "messages.yml";
    private Path messagesFile;

    public MessageManager(Resourceloader plugin) {
        this.plugin = plugin;
        this.messageCache = new ConcurrentHashMap<>();
        loadMessages();
    }

    public void loadMessages() {
        try {
            messagesFile = plugin.getDataFolder().toPath().resolve(DEFAULT_MESSAGES_FILE);
            
            // Create messages file if it doesn't exist
            if (!Files.exists(messagesFile)) {
                Files.createDirectories(messagesFile.getParent());
                plugin.saveResource(DEFAULT_MESSAGES_FILE, false);
            }

            // Load messages from file
            messages = YamlConfiguration.loadConfiguration(messagesFile.toFile());
            messageCache.clear();

            // Load default messages for missing keys
            try (InputStream defaultStream = plugin.getResource(DEFAULT_MESSAGES_FILE)) {
                if (defaultStream != null) {
                    YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                    );
                    messages.setDefaults(defaultMessages);
                    messages.options().copyDefaults(true);
                    
                    // Save if defaults were copied
                    if (messages.getDefaults() != null && !messages.getDefaults().getKeys(true).isEmpty()) {
                        messages.save(messagesFile.toFile());
                    }
                }
            }

            // Load prefix with fallback
            prefix = formatMessage(messages.getString("prefix", DEFAULT_PREFIX));
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load messages: " + e.getMessage());
            plugin.getLogger().severe("Using default messages only");
            messages = new YamlConfiguration();
            prefix = formatMessage(DEFAULT_PREFIX);
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
            String message = messages.getString(path, "");
            String formattedMessage = formatMessage(message);
            return addPrefix ? prefix + formattedMessage : formattedMessage;
        });
    }

    public String getMessageNoPrefix(String path) {
        return getMessage(path, false);
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

        StringBuilder result = new StringBuilder(message);
        PLACEHOLDER_PATTERN.matcher(message).results().forEach(matchResult -> {
            String placeholder = matchResult.group(1);
            String value = placeholders.get(placeholder);
            if (value != null) {
                int start = matchResult.start();
                int end = matchResult.end();
                result.replace(start, end, value);
            }
        });
        return formatMessage(result.toString());
    }

    public String formatMessage(String path, Object... args) {
        if (args == null || args.length % 2 != 0) {
            return getMessage(path);
        }

        String message = getMessageNoPrefix(path);
        Map<String, String> placeholders = new HashMap<>();
        
        for (int i = 0; i < args.length; i += 2) {
            if (args[i] != null && args[i + 1] != null) {
                placeholders.put(args[i].toString(), args[i + 1].toString());
            }
        }
        
        return prefix + formatMessage(message, placeholders);
    }

    public void reloadMessages() {
        loadMessages();
    }

    public Set<String> getMessageKeys() {
        return messages.getKeys(true);
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
            messages.save(messagesFile.toFile());
        }
    }
} 