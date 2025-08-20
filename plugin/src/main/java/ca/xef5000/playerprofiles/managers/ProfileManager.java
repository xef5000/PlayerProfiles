package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.api.data.IdentityData;
import ca.xef5000.playerprofiles.api.data.Profile;
import ca.xef5000.playerprofiles.data.ProfileImpl;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

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
        plugin.getDatabaseManager().getPlayerActiveProfileId(player.getUniqueId())
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
        return plugin.getDatabaseManager().loadProfile(newProfileId)
                .thenApply(profileOpt -> {
                    if (profileOpt.isEmpty()) {
                        return false; // Profile doesn't exist
                    }
                    Profile newProfile = profileOpt.get();

                    // Switch to MAIN thread to perform the actual switch
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            IdentityData originalIdentity = plugin.getIdentityManager().getOriginalIdentity(player);
                            if (originalIdentity == null) {
                                player.sendMessage(ChatColor.RED + "Could not retrieve your original identity. Aborting switch.");
                                return;
                            }

                            // 3. Save the state of the CURRENT profile before we change anything.
                            Profile oldProfile = getActiveProfile(player);
                            if (oldProfile != null) {
                                savePlayerStateToProfile(player, oldProfile);

                                // Save async with error handling
                                plugin.getDatabaseManager().saveProfile(oldProfile)
                                        .exceptionally(throwable -> {
                                            plugin.getLogger().severe("Failed to save old profile during switch: " + throwable.getMessage());
                                            return null;
                                        });
                            }

                            // 4. Construct the NEW identity using the profile's UUID.
                            // We use the profile's UUID and name, but the player's ORIGINAL skin.
                            IdentityData newIdentity = new IdentityData(
                                    newProfile.getProfileId(),      // The profile's ID is the NEW UUID
                                    newProfile.getProfileName(),    // The profile's name is the NEW name
                                    originalIdentity.skin()         // Keep the player's original skin
                            );

                            // 5. Apply the new IDENTITY. This is the dangerous NMS operation.
                            plugin.getIdentityManager().applyIdentity(player, newIdentity);

                            // 6. Apply the new PROFILE DATA (inventory, location, etc.)
                            applyProfileToPlayer(newProfile, player);

                            // Use the original UUID as the key (this is important!)
                            UUID originalUUID = originalIdentity.uuid();
                            activeProfiles.put(originalUUID, newProfile);

                            // 7. Handle plugin compatibility - THIS IS THE NEW PART
                            plugin.getPluginCompatibilityManager().handleProfileSwitch(player, originalIdentity, newIdentity);

                            // 8. Update the database to remember this is the new active profile.
                            plugin.getDatabaseManager().setPlayerActiveProfile(originalUUID, newProfileId);

                        } catch (Exception e) {
                            plugin.getLogger().severe("Error during profile switch for " + player.getName() + ": " + e.getMessage());
                            e.printStackTrace();
                            player.sendMessage(ChatColor.RED + "An error occurred while switching profiles. Please try again.");
                        }
                    });

                    return true;
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to load profile " + newProfileId + ": " + throwable.getMessage());
                    return false;
                });
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
}
