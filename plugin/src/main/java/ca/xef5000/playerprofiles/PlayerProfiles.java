package ca.xef5000.playerprofiles;

import ca.xef5000.playerprofiles.api.services.NMSService;
import ca.xef5000.playerprofiles.commands.CharacterCommand;
import ca.xef5000.playerprofiles.gui.GuiManager;
import ca.xef5000.playerprofiles.listeners.IdentityListener;
import ca.xef5000.playerprofiles.listeners.ProfileListener;
import ca.xef5000.playerprofiles.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class PlayerProfiles extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ProfileManager profileManager;
    private IdentityManager identityManager;
    private PluginCompatibilityManager pluginCompatibilityManager;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        ConfigManager.load(this);
        LangManager.load(this);

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
        this.guiManager = new GuiManager(this);

        NMSService nmsHandler = setupNmsHandler();
        if (nmsHandler == null) {
            getLogger().severe("Could not load NMS handler. Advanced identity features will be disabled.");
        }

        this.identityManager = new IdentityManager(nmsHandler, this);
        this.pluginCompatibilityManager = new PluginCompatibilityManager(this, nmsHandler);

        CharacterCommand characterCommand = new CharacterCommand(this);
        getCommand("character").setExecutor(characterCommand);
        getCommand("character").setTabCompleter(characterCommand);
        registerListeners();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (guiManager != null) {
            guiManager.closeAllGuis();
        }

        if (databaseManager != null) {
            databaseManager.disconnect();
        }
    }

    private NMSService setupNmsHandler() {
        try {
            // The new, reliable method: Get the Bukkit version string.
            String bukkitVersion = Bukkit.getBukkitVersion();
            getLogger().info("Detected Bukkit version: " + bukkitVersion);

            String nmsRevision;

            if (bukkitVersion.startsWith("1.21.6") || bukkitVersion.startsWith("1.21.7") || bukkitVersion.startsWith("1.21.8")) {
                nmsRevision = "v1_21_R2";
            } else if (bukkitVersion.startsWith("1.21")) { // 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5
                nmsRevision = "v1_21_R1";
            } else if (bukkitVersion.startsWith("1.20.5") || bukkitVersion.startsWith("1.20.6")) {
                nmsRevision = "v1_20_R4";
            } else if (bukkitVersion.startsWith("1.20.3") || bukkitVersion.startsWith("1.20.4")) {
                nmsRevision = "v1_20_R3";
            } else if (bukkitVersion.startsWith("1.20.2")) {
                nmsRevision = "v1_20_R2";
            } else if (bukkitVersion.startsWith("1.20") || bukkitVersion.startsWith("1.20.1")) {
                nmsRevision = "v1_20_R1";
            } else {
                // If none of the above match, we don't have a supported version.
                getLogger().severe("====================================================");
                getLogger().severe("Unsupported server version: " + bukkitVersion);
                getLogger().severe("PlayerProfiles' advanced identity features will be disabled.");
                getLogger().severe("====================================================");
                return null;
            }

            getLogger().info("Determined NMS revision: " + nmsRevision);

            String implementationClassName;
            // The switch statement is still perfect for this part.
            switch (nmsRevision) {
                case "v1_21_R1":
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_21_R1.NMSService_v1_21_R1";
                    break;
                case "v1_20_R4":
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_20_R4.NMSService_v1_20_R4";
                    break;
                case "v1_20_R3":
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_20_R3.NMSService_v1_20_R3";
                    break;
                case "v1_20_R2":
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_20_R2.NMSService_v1_20_R2";
                    break;
                case "v1_20_R1":
                    implementationClassName = "ca.xef5000.playerprofiles.nms.v1_20_R1.NMSService_v1_20_R1";
                    break;
                default:
                    // This case should theoretically never be reached due to the check above, but it's good practice.
                    return null;
            }

            Class<?> clazz = Class.forName(implementationClassName);
            NMSService handler = (NMSService) clazz.getConstructor(Plugin.class).newInstance(this);
            getLogger().info("Successfully loaded NMS implementation for " + nmsRevision);
            return handler;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not initialize NMS handler. This can happen with a plugin conflict or an unexpected server version.", e);
            return null;
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ProfileListener(this), this);
        getServer().getPluginManager().registerEvents(new IdentityListener(this.identityManager), this);
        getServer().getPluginManager().registerEvents(guiManager, this);
    }

    // Getter methods for managers
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public IdentityManager getIdentityManager() {
        return identityManager;
    }

    public PluginCompatibilityManager getPluginCompatibilityManager() {
        return pluginCompatibilityManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }
}
