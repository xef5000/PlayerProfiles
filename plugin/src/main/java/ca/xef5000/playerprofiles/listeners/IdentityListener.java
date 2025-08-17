package ca.xef5000.playerprofiles.listeners;

import ca.xef5000.playerprofiles.managers.IdentityManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class IdentityListener implements Listener {

    private final IdentityManager identityManager;

    public IdentityListener(IdentityManager identityManager) {
        this.identityManager = identityManager;
    }

    // Use PlayerLoginEvent at LOWEST priority to catch the player as early as possible.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        identityManager.handlePlayerLogin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        identityManager.handlePlayerLogout(event.getPlayer());
    }
}