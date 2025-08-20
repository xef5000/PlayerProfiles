package ca.xef5000.playerprofiles.permissions;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.api.services.NMSService;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Level;

public class LuckPermsInjector {
    private final PlayerProfiles plugin;
    private final Object luckpermsInternalPlugin;
    private final Constructor<?> permissibleConstructor;
    private final Method apiUserCastMethod; // The key to the final puzzle

    public LuckPermsInjector(PlayerProfiles plugin) {
        this.plugin = plugin;
        try {
            plugin.getLogger().info("--- Starting LuckPerms Reflection Initialization ---");

            // Steps 1-4 are proven correct.
            Plugin luckPermsLoader = Bukkit.getPluginManager().getPlugin("LuckPerms");
            Field bootstrapField = findField(luckPermsLoader.getClass(), "plugin");
            Object bootstrapInstance = bootstrapField.get(luckPermsLoader);
            ClassLoader lpClassLoader = bootstrapInstance.getClass().getClassLoader();
            Field internalPluginField = findField(bootstrapInstance.getClass(), "plugin");
            this.luckpermsInternalPlugin = internalPluginField.get(bootstrapInstance);

            // Step 5: Load all necessary internal classes.
            Class<?> permissibleClass = lpClassLoader.loadClass("me.lucko.luckperms.bukkit.inject.permissible.LuckPermsPermissible");
            Class<?> lpBukkitPluginClass = lpClassLoader.loadClass("me.lucko.luckperms.bukkit.LPBukkitPlugin");
            Class<?> internalUserClass = lpClassLoader.loadClass("me.lucko.luckperms.common.model.User");


            Class<?> apiUserClass = lpClassLoader.loadClass("me.lucko.luckperms.common.api.implementation.ApiUser");
            // The method is: public static User cast(net.luckperms.api.model.user.User u)
            this.apiUserCastMethod = apiUserClass.getMethod("cast", User.class);

            plugin.getLogger().info("Step 5 PASSED: Successfully loaded internal classes and API user cast method.");

            // Step 6: Get the constructor for the permissible using the INTERNAL User class.
            this.permissibleConstructor = permissibleClass.getConstructor(Player.class, internalUserClass, lpBukkitPluginClass);
            plugin.getLogger().info("Step 6 PASSED: Found Permissible constructor.");

            plugin.getLogger().info("--- LuckPerms Reflection Initialization FINISHED Successfully ---");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize LuckPerms injector!", e);
            throw new RuntimeException("Failed to initialize LuckPermsInjector", e);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            plugin.getLogger().severe("REFLECTION FAILED: Could not find field '" + fieldName + "' in class '" + clazz.getName() + "'");
            plugin.getLogger().severe("Available fields: " + Arrays.toString(clazz.getDeclaredFields()));
            throw e;
        }
    }

    public void inject(Player player, User newUser) { // newUser is an instance of the API User
        try {
            // 1. Unwrap the ApiUser to get the internal User object using the official cast method.
            Object internalUser = this.apiUserCastMethod.invoke(null, newUser); // null because it's a static method

            // 2. Create a new instance of LuckPermsPermissible, passing the INTERNAL user.
            Object newPermissible = this.permissibleConstructor.newInstance(player, internalUser, this.luckpermsInternalPlugin);

            // 3. Use our NMS service to inject the new permissible into the player.
            NMSService nmsHandler = this.plugin.getNmsHandler();
            nmsHandler.injectPermissible(player, newPermissible);

            this.plugin.getLogger().info("Successfully injected new permissible for user " + newUser.getUsername());

        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "CRITICAL FAILURE during permissible injection!", e);
        }
    }
}
