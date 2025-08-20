package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.PlayerProfiles;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LangManager {
    
    private static PlayerProfiles plugin;
    private static FileConfiguration langConfig;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");
    
    /**
     * Initializes the LangManager. This should be called once in onEnable.
     * @param pluginInstance The main plugin instance.
     */
    public static void load(PlayerProfiles pluginInstance) {
        plugin = pluginInstance;
        loadLangFile();
    }
    
    private static void loadLangFile() {
        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        
        // Create the file if it doesn't exist
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        // Load defaults from the resource file
        try (InputStream defConfigStream = plugin.getResource("lang.yml")) {
            if (defConfigStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
                langConfig.setDefaults(defConfig);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load default lang.yml from plugin resources");
        }
    }
    
    /**
     * Gets a message from the language file and applies color codes.
     * @param path The path to the message in the YAML file (e.g., "gui.profile_selection.title")
     * @return The formatted message with color codes applied
     */
    public static String getMessage(String path) {
        String message = langConfig.getString(path, "Missing message: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Gets a message from the language file with placeholder replacements.
     * @param path The path to the message in the YAML file
     * @param placeholders A map of placeholder names to their replacement values
     * @return The formatted message with color codes and placeholders applied
     */
    public static String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return message;
    }
    
    /**
     * Gets a list of messages from the language file and applies color codes.
     * @param path The path to the message list in the YAML file
     * @return The list of formatted messages with color codes applied
     */
    public static List<String> getMessageList(String path) {
        List<String> messages = langConfig.getStringList(path);
        return messages.stream()
                .map(msg -> ChatColor.translateAlternateColorCodes('&', msg))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets a list of messages from the language file with placeholder replacements.
     * @param path The path to the message list in the YAML file
     * @param placeholders A map of placeholder names to their replacement values
     * @return The list of formatted messages with color codes and placeholders applied
     */
    public static List<String> getMessageList(String path, Map<String, String> placeholders) {
        List<String> messages = getMessageList(path);
        
        return messages.stream()
                .map(message -> {
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        message = message.replace("{" + entry.getKey() + "}", entry.getValue());
                    }
                    return message;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Convenience method to create a placeholder map with a single entry.
     * @param key The placeholder key
     * @param value The placeholder value
     * @return A map containing the single placeholder
     */
    public static Map<String, String> placeholder(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
    
    /**
     * Convenience method to create a placeholder map with multiple entries.
     * @param placeholders Key-value pairs for placeholders (key1, value1, key2, value2, ...)
     * @return A map containing all the placeholders
     */
    public static Map<String, String> placeholders(String... placeholders) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return map;
    }
    
    /**
     * Formats a date for display in messages.
     * @param date The date to format
     * @return The formatted date string
     */
    public static String formatDate(Date date) {
        return DATE_FORMAT.format(date);
    }
    
    /**
     * Reloads the language configuration from disk.
     */
    public static void reload() {
        loadLangFile();
    }
}
