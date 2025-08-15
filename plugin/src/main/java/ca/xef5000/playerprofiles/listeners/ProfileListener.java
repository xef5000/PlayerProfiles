package ca.xef5000.playerprofiles.listeners;

import ca.xef5000.playerprofiles.PlayerProfiles;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ProfileListener implements Listener {

    private final PlayerProfiles plugin;

    public ProfileListener(PlayerProfiles plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Run this slightly delayed to ensure other plugins have loaded the player
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getProfileManager().onPlayerJoin(event.getPlayer());
        }, 20L); // 1 second delay
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getProfileManager().onPlayerQuit(event.getPlayer());
    }
}
