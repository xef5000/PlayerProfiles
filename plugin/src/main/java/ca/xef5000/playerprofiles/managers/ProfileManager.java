package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.api.data.IdentityData;
import ca.xef5000.playerprofiles.api.data.Profile;
import ca.xef5000.playerprofiles.api.services.NMSService;
import ca.xef5000.playerprofiles.data.ProfileImpl;
import ca.xef5000.playerprofiles.util.ProfileLimitUtil;
import ca.xef5000.playerprofiles.util.ProfileUsernameGenerator;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataType;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileManager {

    private final PlayerProfiles plugin;
    private final Map<UUID, Profile> activeProfiles = new ConcurrentHashMap<>();

    public ProfileManager(PlayerProfiles plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the currently active profile for a player from the cache.
     * @param player The player.
     * @return The active CharacterProfile, or null if none is active.
     */
    public Profile getActiveProfile(Player player) {
        UUID originalUUID = plugin.getIdentityManager().getOriginalIdentity(player).uuid();
        return activeProfiles.get(originalUUID);
    }

    /**
     * Loads a player's last used profile into the cache when they join.
     * @param player The player joining.
     */
    public void onPlayerJoin(Player player) {
        plugin.getDatabaseManager().getPlayerActiveProfileId(player)
                .thenAcceptAsync(profileIdOpt -> {
                    profileIdOpt.ifPresent(profileId -> {
                        plugin.getDatabaseManager().loadProfile(profileId).thenAccept(profileOpt -> {
                            profileOpt.ifPresent(profile -> {
                                // Switch to main thread to apply the profile
                                Bukkit.getScheduler().runTask(plugin, () -> {

                                    activeProfiles.put(player.getUniqueId(), profile);
                                    switchProfile(player, profileId).thenAccept(success -> {
                                        if (success) {
                                            plugin.getLogger().info("Auto-loaded profile '" + profile.getProfileName() + "' for " + player.getName());
                                        } else {
                                            player.sendMessage(ChatColor.RED + "An error occurred while auto-loading profiles.");
                                        }
                                    });
                                });
                            });
                        });
                    });
                });
    }

    /**
     * Saves a player's active profile when they quit.
     * This naturally handles the "last person to use it saves their state" logic.
     * @param player The player quitting.
     */
    public void onPlayerQuit(Player player) {
        try {
            // Get the original UUID - this is the key used in activeProfiles
            IdentityData originalIdentity = plugin.getIdentityManager().getOriginalIdentity(player);
            if (originalIdentity == null) {
                plugin.getLogger().warning("Could not get original identity for " + player.getName() + " during logout");
                return;
            }

            UUID originalUUID = originalIdentity.uuid();
            Profile activeProfile = activeProfiles.get(originalUUID);

            if (activeProfile != null) {
                // Save data on the main thread, then save to DB async
                savePlayerStateToProfile(player, activeProfile);

                // Save to database asynchronously with error handling
                plugin.getDatabaseManager().saveProfile(activeProfile)
                        .thenRun(() -> {
                            plugin.getLogger().info("Saved profile '" + activeProfile.getProfileName() + "' for " + player.getName());
                        })
                        .exceptionally(throwable -> {
                            plugin.getLogger().severe("Failed to save profile for " + player.getName() + ": " + throwable.getMessage());
                            return null;
                        });

                activeProfiles.remove(originalUUID);
                plugin.getLogger().info("Removed active profile for " + player.getName());
            } else {
                plugin.getLogger().info("Player " + player.getName() + " logged out with no active profile.");
            }

            // Let IdentityManager handle the UUID reset and cleanup
            // NOTE: This should be the ONLY place we call handlePlayerLogout
            plugin.getIdentityManager().handlePlayerLogout(player);

        } catch (Exception e) {
            plugin.getLogger().severe("Error during player quit handling for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();

            // Emergency cleanup - try to let IdentityManager clean up even if we had errors
            try {
                plugin.getIdentityManager().handlePlayerLogout(player);
            } catch (Exception cleanupError) {
                plugin.getLogger().severe("Emergency cleanup also failed: " + cleanupError.getMessage());
            }
        }
    }

    /**
     * The core logic for switching a player to a new profile.
     * @param player The player to switch.
     * @param newProfileId The UUID of the profile to switch to.
     * @return A CompletableFuture that completes when the switch is done.
     */
    public CompletableFuture<Boolean> switchProfile(Player player, UUID newProfileId) {
        IdentityData originalIdentity = plugin.getIdentityManager().getOriginalIdentity(player);
        if (originalIdentity == null) {
            player.sendMessage(ChatColor.RED + "Could not retrieve your original identity. Aborting switch.");
            return CompletableFuture.completedFuture(false);
        }
        // First, load the profile data from your database
        return plugin.getDatabaseManager().loadProfile(newProfileId)
                .thenCompose(profileOpt -> {
                    if (profileOpt.isEmpty()) {
                        // Profile doesn't exist, fail early.
                        return CompletableFuture.completedFuture(false);
                    }
                    Profile newProfile = profileOpt.get();

                    LuckPerms luckPerms = plugin.getLuckPermsApi();
                    UserManager userManager = luckPerms.getUserManager();

                    String newUsername = newProfile.getProfileName().replace(" ", "_");

                    // Step 1: Create/load the user object. This prepares their data.
                    return userManager.loadUser(newProfile.getProfileId(), newUsername)
                            .thenCompose(newUser -> {
                                // STEP 2: THIS IS THE MISSING LINK.
                                // Explicitly tell LuckPerms to save and cache the username mapping.
                                return userManager.savePlayerData(newProfile.getProfileId(), newUsername)
                                        .thenApply(result -> newUser); // Pass the user object along the chain
                            })
                            .thenApply(newUser -> {
                                // Step 3: Now that all async work is done, switch on the main thread.
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    performSwitch(player, originalIdentity, newProfile, newUser, newUsername);
                                });
                                return true;
                            });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed during profile switch process for " + newProfileId + ": " + throwable.getMessage());
                    throwable.printStackTrace();
                    return false;
                });
    }

    /**
     * This helper method contains the logic that MUST run on the main server thread.
     */
    private void performSwitch(Player player, IdentityData originalIdentity, Profile newProfile, User newLuckPermsUser, String newUsername) {
        try {
            // Cleanup, save, and apply identity are all correct.
            User oldUser = plugin.getLuckPermsApi().getUserManager().getUser(player.getUniqueId());
            if (oldUser != null) plugin.getLuckPermsApi().getUserManager().cleanupUser(oldUser);

            Profile oldProfile = getActiveProfile(player);
            if (oldProfile != null) {
                savePlayerStateToProfile(player, oldProfile);
                plugin.getDatabaseManager().saveProfile(oldProfile);
            }

            IdentityData newIdentity = new IdentityData(
                    newProfile.getProfileId(),
                    newUsername,
                    originalIdentity.skin()
            );
            plugin.getIdentityManager().applyIdentity(player, newIdentity);

            applyProfileToPlayer(newProfile, player);

            UUID originalUUID = originalIdentity.uuid();
            activeProfiles.put(originalUUID, newProfile);
            plugin.getDatabaseManager().setPlayerActiveProfile(originalUUID, newProfile.getProfileId());

            // Schedule the definitive injection for the next tick.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && plugin.getLuckPermsInjector() != null) {
                    plugin.getLuckPermsInjector().inject(player, newLuckPermsUser);
                }
            }, 1L);

            plugin.getLogger().info("Successfully switched identity for " + originalIdentity.name() + ". Permissible injection scheduled.");

        } catch (Exception e) {
            plugin.getLogger().severe("Error during performSwitch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the reflection and NMS calls to inject a new LuckPerms permissible.
     */
    private void injectAndRefreshPermissible(Player player, User newUser) {
        try {
            plugin.getLogger().info("Starting permissible injection for " + player.getName());

            // 1. Get the internal LPBukkitPlugin instance via casting.
            // This requires the LuckPerms Bukkit JAR on the compile classpath.
            Object lpPlugin = plugin.getLuckPermsApi();

            // 2. Find the internal LuckPermsPermissible class via reflection.
            Class<?> permissibleClass = Class.forName("me.lucko.luckperms.bukkit.inject.permissible.LuckPermsPermissible");

            // 3. Find its constructor: LuckPermsPermissible(Player, User, LPBukkitPlugin)
            Constructor<?> constructor = permissibleClass.getConstructor(Player.class, User.class, lpPlugin.getClass().getInterfaces()[0]);

            // 4. Create a new instance of the permissible, linked to our new user.
            Object newPermissible = constructor.newInstance(player, newUser, lpPlugin);

            // 5. Use our NMS service to inject the new permissible into the player.
            NMSService nmsHandler = plugin.getNmsHandler();
            nmsHandler.injectPermissible(player, newPermissible);

            // 6. Find and call the 'setupAttachments' method on the new permissible to activate it.
            Method setupMethod = permissibleClass.getDeclaredMethod("setupAttachments");
            setupMethod.setAccessible(true);
            setupMethod.invoke(newPermissible);

            plugin.getLogger().info("Successfully injected and initialized new permissible for user " + newUser.getUsername());

        } catch (Exception e) {
            plugin.getLogger().severe("CRITICAL FAILURE during permissible injection!");
            e.printStackTrace();
        }
    }

    /**
     * Takes a profile's data and applies it to a live player.
     * MUST be run on the main server thread.
     */
    private void applyProfileToPlayer(Profile profile, Player player) {
        if (!(profile instanceof ProfileImpl p)) return;

        try {
            // Clear player's current state to prevent item duplication
            player.getInventory().clear();
            player.getEnderChest().clear();
            player.setTotalExperience(0);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }

            // Apply new state
            if (p.getInventoryContents() != null) player.getInventory().setContents(p.getInventoryContents());
            if (p.getArmorContents() != null) player.getInventory().setArmorContents(p.getArmorContents());
            if (p.getLocation() != null) player.teleport(p.getLocation());
            player.setHealth(p.getHealth());
            player.setFoodLevel(p.getFoodLevel());
            player.setTotalExperience(p.getTotalExperience());
            if (p.getGameMode() != null) player.setGameMode(p.getGameMode());
            if (p.getPotionEffects() != null) player.addPotionEffects(p.getPotionEffects());

            // Here you would also apply the skin and use NickAPI
        } catch (Exception e) {
            plugin.getLogger().severe("Error applying profile to player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Takes a live player's state and saves it into a profile object.
     * This only updates the Java object; it does not write to the database.
     */
    public void savePlayerStateToProfile(Player player, Profile profile) {
        if (!(profile instanceof ProfileImpl p)) return;

        try {
            p.setInventoryContents(player.getInventory().getContents());
            p.setArmorContents(player.getInventory().getArmorContents());
            p.setLocation(player.getLocation());
            p.setHealth(player.getHealth());
            p.setFoodLevel(player.getFoodLevel());
            p.setTotalExperience(player.getTotalExperience());
            p.setGameMode(player.getGameMode());
            p.setPotionEffects(player.getActivePotionEffects());
        } catch (Exception e) {
            plugin.getLogger().severe("Error saving player state to profile for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets the maximum number of profiles a player can have based on their permissions.
     * @param player The player to check
     * @return The maximum number of profiles the player can have
     */
    public int getProfileLimit(Player player) {
        return ProfileLimitUtil.getProfileLimit(player);
    }

    /**
     * Checks if a player can create another profile based on their current profile count and limit.
     * @param player The player to check
     * @return A CompletableFuture that completes with true if the player can create another profile
     */
    public CompletableFuture<Boolean> canCreateProfile(Player player) {
        return plugin.getDatabaseManager().getProfilesForPlayer(player)
                .thenApply(profiles -> ProfileLimitUtil.canCreateProfile(player, profiles.size()));
    }

    /**
     * Generates the next available profile name for a player.
     * Names follow the pattern "Profile 1", "Profile 2", etc.
     * @param player The player to generate a name for
     * @return A CompletableFuture that completes with the next available profile name
     */
    public CompletableFuture<String> generateProfileName(Player player) {
        return plugin.getDatabaseManager().getProfilesForPlayer(player)
                .thenApply(profiles -> {
                    int nextNumber = 1;
                    boolean nameExists;

                    do {
                        String candidateName = "Profile " + nextNumber;
                        nameExists = profiles.stream()
                                .anyMatch(p -> p.getProfileName().equalsIgnoreCase(candidateName));

                        if (!nameExists) {
                            return candidateName;
                        }

                        nextNumber++;
                    } while (nextNumber <= 100); // Safety limit to prevent infinite loops

                    // Fallback if we somehow can't find a name
                    return "Profile " + System.currentTimeMillis();
                });
    }

    /**
     * Creates a new profile with an automatically generated name.
     * @param player The player to create the profile for
     * @return A CompletableFuture that completes with the new profile, or null if creation failed
     */
    public CompletableFuture<Profile> createProfileWithGeneratedName(Player player) {
        return generateProfileName(player)
                .thenCompose(profileName -> plugin.getDatabaseManager().createProfile(player, profileName));
    }
}
