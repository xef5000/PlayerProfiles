package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.api.data.IdentityData;
import ca.xef5000.playerprofiles.api.services.NMSService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles compatibility with other plugins when player UUIDs are changed.
 * This system fires fake events and manipulates plugin caches to ensure
 * other plugins work correctly with profile switching.
 */
public class PluginCompatibilityManager {

    private final PlayerProfiles plugin;
    private final NMSService nmsHandler;

    public PluginCompatibilityManager(PlayerProfiles plugin, NMSService nmsHandler) {
        this.plugin = plugin;
        this.nmsHandler = nmsHandler;
    }

    /**
     * Called when a player switches to a new profile.
     * Handles all the necessary plugin compatibility tasks.
     */
    public void handleProfileSwitch(Player player, IdentityData oldIdentity, IdentityData newIdentity) {
        try {
            // Step 3: Fire compatibility events
            //fireCompatibilityEvents(player, oldIdentity, newIdentity);

        } catch (Exception e) {
            plugin.getLogger().severe("Error handling plugin compatibility: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Fires fake PlayerQuitEvent and PlayerJoinEvent to notify other plugins.
     */
    private void fireCompatibilityEvents(Player player, IdentityData oldIdentity, IdentityData newIdentity) {
        // Fire events with delays to ensure proper ordering

        // Step 1: Fire quit event for old identity (after 1 tick)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // Temporarily change player info back to old identity for the event
                nmsHandler.setPlayerInfoTemporarily(player, oldIdentity, () -> {
                    PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, "§7" + oldIdentity.name() + " left the game", null);
                    Bukkit.getPluginManager().callEvent(quitEvent);
                    plugin.getLogger().info("Fired compatibility quit event for: " + oldIdentity.name());
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fire quit event: " + e.getMessage());
            }
        }, 1L);

        // Step 2: Fire join event for new identity (after 3 ticks)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, "§7" + newIdentity.name() + " joined the game");
                Bukkit.getPluginManager().callEvent(joinEvent);
                plugin.getLogger().info("Fired compatibility join event for: " + newIdentity.name());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fire join event: " + e.getMessage());
            }
        }, 3L);
    }
}
