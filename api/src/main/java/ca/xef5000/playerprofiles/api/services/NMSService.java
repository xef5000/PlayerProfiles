package ca.xef5000.playerprofiles.api.services;


import ca.xef5000.playerprofiles.api.data.IdentityData;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * A high-level service for managing and applying custom identities to players.
 */
public interface NMSService {

    /**
     * Gets the original, true identity of a player as it was when they logged in.
     * @param player The player.
     * @return The original IdentityData.
     */
    IdentityData getOriginalIdentity(Player player);

    /**
     * Changes a player's identity to match the provided IdentityData.
     * @param player The player to change.
     * @param newIdentity The new identity to apply.
     */
    void applyIdentity(Player player, IdentityData newIdentity);

    void cleanupPlayerOnLogout(Player player);

    /**
     * Temporarily changes player info for the duration of a callback.
     */
    void setPlayerInfoTemporarily(Player player, IdentityData tempIdentity, Runnable callback);

    /**
     * Surgically injects a new Permissible object into the player.
     * @param player The player to modify.
     * @param permissible The new Permissible object to inject.
     * @throws Exception if reflection fails.
     */
    void injectPermissible(Player player, Object permissible) throws Exception;

}
