package ca.xef5000.playerprofiles;

import ca.xef5000.playerprofiles.api.services.IdentityService;
import ca.xef5000.playerprofiles.commands.CharacterCommand;
import ca.xef5000.playerprofiles.listeners.IdentityListener;
import ca.xef5000.playerprofiles.listeners.ProfileListener;
import ca.xef5000.playerprofiles.managers.ConfigManager;
import ca.xef5000.playerprofiles.managers.DatabaseManager;
import ca.xef5000.playerprofiles.managers.IdentityManager;
import ca.xef5000.playerprofiles.managers.ProfileManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class PlayerProfiles extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ProfileManager profileManager;
    private IdentityManager identityManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        ConfigManager.load(this);

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
        } catch (SQLException e) {
            getLogger().severe("!!! DATABASE CONNECTION FAILED! PLUGIN WILL NOT FUNCTION !!!");
            e.printStackTrace();
            // Disable the plugin if the database fails to connect
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.profileManager = new ProfileManager(this);

        IdentityService nmsHandler = setupNmsHandler();
        if (nmsHandler == null) {
            getLogger().severe("Could not load NMS handler. Advanced identity features will be disabled.");
        }

        this.identityManager = new IdentityManager(nmsHandler);

        getServer().getServicesManager().register(
                IdentityService.class, // The API Interface
                this.identityManager,   // Your Core Implementation
                this,                  // This plugin
                ServicePriority.Normal
        );

        CharacterCommand characterCommand = new CharacterCommand(this);
        getCommand("character").setExecutor(characterCommand);
        getCommand("character").setTabCompleter(characterCommand);
        registerListeners();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
    }

    private IdentityService setupNmsHandler() {
        try {
            String serverVersion = getServer().getClass().getPackage().getName().split("\\.")[3];
            getLogger().info("Detected server version: ".concat(serverVersion));

            String implementationClassName;
            switch (serverVersion) {
                case "v1_20_R1":
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_20_R1.IdentityService_v1_20_R1";
                    break;
                case "v1_20_R2": // For Minecraft 1.20.2
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_20_R2.IdentityService_v1_20_R2";
                    break;

                case "v1_20_R3": // For Minecraft 1.20.3 and 1.20.4
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_20_R3.IdentityService_v1_20_R3";
                    break;

                case "v1_20_R4": // For Minecraft 1.20.5 and 1.20.6
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_20_R4.IdentityService_v1_20_R4";
                    break;
                default:
                    getLogger().warning("Unsupported server version: " + serverVersion);
                    return null;
            }

            Class<?> clazz = Class.forName(implementationClassName);
            // Pass the plugin instance 'this' to the NMS constructor
            return (IdentityService) clazz.getConstructor(Plugin.class).newInstance(this);

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not initialize NMS handler.", e);
            return null;
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ProfileListener(this), this);
        getServer().getPluginManager().registerEvents(new IdentityListener(this.identityManager), this);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public IdentityManager getIdentityManager() {
        return identityManager;
    }
}
