package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.PlayerProfiles;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private static FileConfiguration config;

    /**
     * Initializes the ConfigManager. This should be called once in onEnable.
     * @param plugin The main plugin instance.
     */
    public static void load(PlayerProfiles plugin) {
        // Ensure a default config.yml exists and load it
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public static String getDatabaseType() {
        return config.getString("database.type", "SQLITE").toUpperCase();
    }

    public static String getMySqlHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public static int getMySqlPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public static String getMySqlDatabase() {
        return config.getString("database.mysql.database", "playercharacters");
    }

    public static String getMySqlUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public static String getMySqlPassword() {
        return config.getString("database.mysql.password", "");
    }

    public static boolean isMySqlSslEnabled() {
        return config.getBoolean("database.mysql.useSSL", false);
    }
}
