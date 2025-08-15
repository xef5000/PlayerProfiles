package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.api.data.Profile;
import ca.xef5000.playerprofiles.data.ProfileImpl;
import org.bukkit.Bukkit;
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
        return activeProfiles.get(player.getUniqueId());
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
                                    applyProfileToPlayer(profile, player);
                                    plugin.getLogger().info("Auto-loaded profile '" + profile.getProfileName() + "' for " + player.getName());
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
        Profile activeProfile = getActiveProfile(player);

        if (activeProfile == null) {
            plugin.getLogger().info("Player " + player.getName() + " logged out with no active profile.");
            return;
        }

        // Save data on the main thread, then save to DB async
        savePlayerStateToProfile(player, activeProfile);
        plugin.getDatabaseManager().saveProfile(activeProfile).thenRun(() -> {
            plugin.getLogger().info("Saved profile '" + activeProfile.getProfileName() + "' for " + player.getName());
        });

        activeProfiles.remove(player.getUniqueId());
    }

    /**
     * The core logic for switching a player to a new profile.
     * @param player The player to switch.
     * @param newProfileId The UUID of the profile to switch to.
     * @return A CompletableFuture that completes when the switch is done.
     */
    public CompletableFuture<Boolean> switchProfile(Player player, UUID newProfileId) {
        return plugin.getDatabaseManager().loadProfile(newProfileId).thenApply(profileOpt -> {
            if (profileOpt.isEmpty()) {
                return false; // Profile doesn't exist
            }
            Profile newProfile = profileOpt.get();

            // Switch to MAIN thread to perform the actual switch
            Bukkit.getScheduler().runTask(plugin, () -> {
                Profile oldProfile = getActiveProfile(player);
                if (oldProfile != null) {
                    savePlayerStateToProfile(player, oldProfile);
                    plugin.getDatabaseManager().saveProfile(oldProfile); // Save old profile async
                }

                applyProfileToPlayer(newProfile, player);
                activeProfiles.put(player.getUniqueId(), newProfile); // THIS IS THE CRITICAL FIX

                plugin.getDatabaseManager().setPlayerActiveProfile(player.getUniqueId(), newProfileId);
            });

            return true;
        });
    }

    /**
     * Takes a profile's data and applies it to a live player.
     * MUST be run on the main server thread.
     */
    private void applyProfileToPlayer(Profile profile, Player player) {
        if (!(profile instanceof ProfileImpl p)) return;

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
    }

    /**
     * Takes a live player's state and saves it into a profile object.
     * This only updates the Java object; it does not write to the database.
     */
    public void savePlayerStateToProfile(Player player, Profile profile) {
        if (!(profile instanceof ProfileImpl p)) return;

        p.setInventoryContents(player.getInventory().getContents());
        p.setArmorContents(player.getInventory().getArmorContents());
        p.setLocation(player.getLocation());
        p.setHealth(player.getHealth());
        p.setFoodLevel(player.getFoodLevel());
        p.setTotalExperience(player.getTotalExperience());
        p.setGameMode(player.getGameMode());
        p.setPotionEffects(player.getActivePotionEffects());
    }
}
