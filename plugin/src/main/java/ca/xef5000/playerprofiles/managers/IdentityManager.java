package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.api.data.IdentityData;
import ca.xef5000.playerprofiles.api.data.PlayerIdentity;
import ca.xef5000.playerprofiles.api.services.IdentityService;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IdentityManager implements IdentityService {

    // This is the NMS implementation we loaded via reflection (e.g., IdentityService_v1_20_R1)
    private final IdentityService nmsHandler;
    private final Map<UUID, PlayerIdentity> identityCache = new ConcurrentHashMap<>();

    public IdentityManager(IdentityService nmsHandler) {
        this.nmsHandler = nmsHandler;
    }

    /**
     * Called when a player logs in to cache their original identity.
     */
    public void handlePlayerLogin(Player player) {
        // Get the real GameProfile at login and convert it to our pure DTO
        IdentityData originalIdentity = nmsHandler.getOriginalIdentity(player);
        identityCache.put(player.getUniqueId(), new PlayerIdentity(originalIdentity));
    }

    /**
     * Called when a player logs out to clean up the cache.
     */
    public void handlePlayerLogout(Player player) {
        identityCache.remove(player.getUniqueId());
    }


    // --- Implementation of the IdentityService API ---

    @Override
    public IdentityData getOriginalIdentity(Player player) {
        PlayerIdentity identity = identityCache.get(player.getUniqueId());
        if (identity == null) {
            // This can happen if the player was online before a reload.
            // We can fetch it on-demand.
            handlePlayerLogin(player);
            identity = identityCache.get(player.getUniqueId());
        }
        return identity.getOriginalIdentity();
    }

    @Override
    public Optional<IdentityData> getAppliedIdentity(Player player) {
        return Optional.ofNullable(identityCache.get(player.getUniqueId()))
                .map(PlayerIdentity::getAppliedIdentity);
    }

    @Override
    public void applyIdentity(Player player, IdentityData newIdentity) {
        // First, update our internal state cache
        PlayerIdentity identity = identityCache.get(player.getUniqueId());
        if (identity == null) {
            // Player might not be fully loaded, handle gracefully
            handlePlayerLogin(player);
            identity = identityCache.get(player.getUniqueId());
        }
        identity.setAppliedIdentity(newIdentity);

        // Then, delegate the dangerous NMS work to the loaded implementation
        nmsHandler.applyIdentity(player, newIdentity);
    }

    @Override
    public void resetIdentity(Player player) {
        PlayerIdentity identity = identityCache.get(player.getUniqueId());
        if (identity != null) {
            // Simply apply the original identity
            applyIdentity(player, identity.getOriginalIdentity());
        }
    }
}
