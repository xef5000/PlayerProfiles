package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.api.data.IdentityData;
import ca.xef5000.playerprofiles.api.data.PlayerIdentity;
import ca.xef5000.playerprofiles.api.services.NMSService;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IdentityManager {

    // This is the NMS implementation we loaded via reflection (e.g., IdentityService_v1_20_R1)
    private final NMSService nmsHandler;
    private final PlayerProfiles plugin;

    private final Map<UUID, PlayerIdentity> identityCache = new ConcurrentHashMap<>();

    // Map from CURRENT UUID to ORIGINAL UUID (for reverse lookups)
    private final Map<UUID, UUID> currentToOriginalUUID = new ConcurrentHashMap<>();

    // Track if we've already cleaned up a player to prevent double cleanup
    private final Set<UUID> cleanedUpPlayers = ConcurrentHashMap.newKeySet();

    public IdentityManager(NMSService nmsHandler, PlayerProfiles plugin) {
        this.nmsHandler = nmsHandler;
        this.plugin = plugin;
    }

    /**
     * Called when a player logs in to cache their original identity.
     */
    public void handlePlayerLogin(Player player) {
        UUID originalUUID = player.getUniqueId(); // This is their real UUID when they first join

        // Clear any previous cleanup tracking for this player
        cleanedUpPlayers.remove(originalUUID);

        IdentityData originalIdentity = nmsHandler.getOriginalIdentity(player);

        // Store in cache using their ORIGINAL UUID
        identityCache.put(originalUUID, new PlayerIdentity(originalIdentity));

        // Initially, current UUID = original UUID
        currentToOriginalUUID.put(originalUUID, originalUUID);

        plugin.getLogger().info("Cached original identity for " + player.getName() + " with UUID: " + originalUUID);
    }

    /**
     * Called when a player logs out to clean up the cache.
     */
    public void handlePlayerLogout(Player player) {
        UUID currentUUID = player.getUniqueId();

        // Check if we've already cleaned up this player to prevent double cleanup
        if (cleanedUpPlayers.contains(currentUUID)) {
            plugin.getLogger().info("Player " + player.getName() + " already cleaned up, skipping");
            return;
        }

        try {
            // Mark as being cleaned up
            cleanedUpPlayers.add(currentUUID);

            // Find the original UUID for this player
            UUID originalUUID = currentToOriginalUUID.get(currentUUID);

            if (originalUUID == null) {
                // This might happen if the UUID was changed - search through the reverse mapping
                originalUUID = findOriginalUUID(currentUUID);
            }

            if (originalUUID != null) {
                PlayerIdentity identity = identityCache.get(originalUUID);
                if (identity != null && identity.getAppliedIdentity() != null) {
                    // Player has a changed identity - revert them first
                    plugin.getLogger().info("Reverting " + player.getName() + " back to original UUID before logout");
                    plugin.getLogger().info("Current UUID: " + currentUUID + ", Original UUID: " + originalUUID);

                    IdentityData originalIdentity = identity.getOriginalIdentity();
                    nmsHandler.applyIdentity(player, originalIdentity);

                    // Update the mapping - currentUUID should now be the original UUID
                    currentToOriginalUUID.remove(currentUUID);
                    // Don't re-add the mapping as the player is logging out
                }

                // Clean up cache
                identityCache.remove(originalUUID);
                // Clean up all mappings that point to this original UUID
                UUID finalOriginalUUID = originalUUID;
                currentToOriginalUUID.entrySet().removeIf(entry -> entry.getValue().equals(finalOriginalUUID));

                // Cleanup entity references
                nmsHandler.cleanupPlayerOnLogout(player);

                plugin.getLogger().info("Successfully cleaned up player " + player.getName());
            } else {
                plugin.getLogger().warning("Could not find original UUID for player " + player.getName() + " (current: " + currentUUID + ")");
                // Fallback cleanup - treat current UUID as original
                identityCache.remove(currentUUID);
                currentToOriginalUUID.remove(currentUUID);
                nmsHandler.cleanupPlayerOnLogout(player);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error during player logout cleanup for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();

            // Try basic cleanup as fallback
            try {
                nmsHandler.cleanupPlayerOnLogout(player);
            } catch (Exception cleanupError) {
                plugin.getLogger().severe("Fallback cleanup also failed: " + cleanupError.getMessage());
            }
        }
    }

    public void revertToOriginalIdentity(Player player) {
        try {
            UUID currentUUID = player.getUniqueId();
            UUID originalUUID = findOriginalUUID(currentUUID); // Using your existing private helper

            if (originalUUID != null) {
                PlayerIdentity identity = identityCache.get(originalUUID);
                if (identity != null && !currentUUID.equals(originalUUID)) {
                    // Get the original GameProfile data
                    IdentityData originalIdentityData = identity.getOriginalIdentity();

                    // Apply it via NMS
                    nmsHandler.applyIdentity(player, originalIdentityData);

                    // Update the live mapping
                    currentToOriginalUUID.remove(currentUUID);
                    currentToOriginalUUID.put(originalUUID, originalUUID);

                    plugin.getLogger().info("Reverted " + player.getName() + " to original identity before logout.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Could not revert player " + player.getName() + " to original identity!");
            e.printStackTrace();
        }
    }

    /**
     * Find the original UUID by searching through the identity cache
     */
    private UUID findOriginalUUID(UUID currentUUID) {
        // First try the direct mapping
        UUID original = currentToOriginalUUID.get(currentUUID);
        if (original != null) {
            return original;
        }

        // If that fails, search through the identity cache
        for (Map.Entry<UUID, PlayerIdentity> entry : identityCache.entrySet()) {
            PlayerIdentity identity = entry.getValue();
            if (identity.getAppliedIdentity() != null &&
                    identity.getAppliedIdentity().uuid().equals(currentUUID)) {
                return entry.getKey(); // This is the original UUID
            }
        }

        // If still not found, the currentUUID might be the original
        if (identityCache.containsKey(currentUUID)) {
            return currentUUID;
        }

        return null;
    }

    // --- Implementation of the IdentityService API ---

    public IdentityData getOriginalIdentity(Player player) {
        UUID currentUUID = player.getUniqueId();
        UUID originalUUID = currentToOriginalUUID.get(currentUUID);

        if (originalUUID == null) {
            // Try to find it by searching
            originalUUID = findOriginalUUID(currentUUID);
            if (originalUUID == null) {
                // Fallback: assume current UUID is original (new player or no changes made)
                originalUUID = currentUUID;
                // Only call handlePlayerLogin if we don't have any cached data
                if (!identityCache.containsKey(originalUUID)) {
                    handlePlayerLogin(player); // This will cache it properly
                }
            }
        }

        PlayerIdentity identity = identityCache.get(originalUUID);
        if (identity == null) {
            // Last resort: re-cache the player data
            handlePlayerLogin(player);
            identity = identityCache.get(originalUUID);
        }

        return identity != null ? identity.getOriginalIdentity() : null;
    }

    public Optional<IdentityData> getAppliedIdentity(Player player) {
        UUID currentUUID = player.getUniqueId();
        UUID originalUUID = currentToOriginalUUID.get(currentUUID);

        if (originalUUID == null) {
            originalUUID = findOriginalUUID(currentUUID);
        }

        if (originalUUID != null) {
            PlayerIdentity identity = identityCache.get(originalUUID);
            if (identity != null) {
                return Optional.ofNullable(identity.getAppliedIdentity());
            }
        }

        return Optional.empty();
    }

    public void applyIdentity(Player player, IdentityData newIdentity) {
        try {
            UUID currentUUID = player.getUniqueId();
            UUID originalUUID = currentToOriginalUUID.get(currentUUID);

            if (originalUUID == null) {
                originalUUID = findOriginalUUID(currentUUID);
                if (originalUUID == null) {
                    // First time applying identity - current UUID is original
                    originalUUID = currentUUID;
                }
            }

            PlayerIdentity identity = identityCache.get(originalUUID);
            if (identity == null) {
                handlePlayerLogin(player);
                identity = identityCache.get(originalUUID);
            }

            UUID oldCurrentUUID = currentUUID;
            UUID newCurrentUUID = newIdentity.uuid();

            // Update cache
            identity.setAppliedIdentity(newIdentity);

            // Update UUID mappings
            currentToOriginalUUID.remove(oldCurrentUUID);
            currentToOriginalUUID.put(newCurrentUUID, originalUUID);

            plugin.getLogger().info("UUID mapping: " + newCurrentUUID + " -> " + originalUUID);

            // Delegate to NMS handler
            nmsHandler.applyIdentity(player, newIdentity);

        } catch (Exception e) {
            plugin.getLogger().severe("Error applying identity for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to apply identity", e);
        }
    }

    public void resetIdentity(Player player) {
        try {
            UUID currentUUID = player.getUniqueId();
            UUID originalUUID = currentToOriginalUUID.get(currentUUID);

            if (originalUUID == null) {
                originalUUID = findOriginalUUID(currentUUID);
            }

            if (originalUUID != null) {
                PlayerIdentity identity = identityCache.get(originalUUID);
                if (identity != null) {
                    applyIdentity(player, identity.getOriginalIdentity());
                } else {
                    plugin.getLogger().warning("Could not find cached identity for " + player.getName());
                }
            } else {
                plugin.getLogger().warning("Could not find original UUID for " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error resetting identity for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
