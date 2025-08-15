package ca.xef5000.playerprofiles;

import ca.xef5000.playerprofiles.commands.CharacterCommand;
import ca.xef5000.playerprofiles.listeners.ProfileListener;
import ca.xef5000.playerprofiles.managers.ConfigManager;
import ca.xef5000.playerprofiles.managers.DatabaseManager;
import ca.xef5000.playerprofiles.managers.ProfileManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class PlayerProfiles extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ProfileManager profileManager;

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

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ProfileListener(this), this);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }
}
