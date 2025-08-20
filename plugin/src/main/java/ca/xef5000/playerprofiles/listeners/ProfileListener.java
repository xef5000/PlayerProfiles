package ca.xef5000.playerprofiles.listeners;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.gui.ProfileSelectionGui;
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
            handlePlayerJoin(event.getPlayer());
        }, 20L); // 1 second delay
    }

    private void handlePlayerJoin(org.bukkit.entity.Player player) {
        // First, try to load the player's last active profile
        plugin.getDatabaseManager().getPlayerActiveProfileId(player)
                .thenAcceptAsync(profileIdOpt -> {
                    if (profileIdOpt.isPresent()) {
                        // Player has an active profile, try to load it
                        plugin.getDatabaseManager().loadProfile(profileIdOpt.get()).thenAccept(profileOpt -> {
                            if (profileOpt.isPresent()) {
                                // Profile exists, switch to it
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    plugin.getProfileManager().switchProfile(player, profileIdOpt.get()).thenAccept(success -> {
                                        if (success) {
                                            plugin.getLogger().info("Auto-loaded profile '" + profileOpt.get().getProfileName() + "' for " + player.getName());
                                        } else {
                                            // Failed to switch, show profile selection GUI
                                            openProfileSelectionGui(player);
                                        }
                                    });
                                });
                            } else {
                                // Profile doesn't exist anymore, show profile selection GUI
                                Bukkit.getScheduler().runTask(plugin, () -> openProfileSelectionGui(player));
                            }
                        });
                    } else {
                        // No active profile, check if player has any profiles
                        plugin.getDatabaseManager().getProfilesForPlayer(player).thenAccept(profiles -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (profiles.isEmpty()) {
                                    // No profiles at all, show profile selection GUI
                                    openProfileSelectionGui(player);
                                } else {
                                    // Has profiles but no active one, show profile selection GUI
                                    openProfileSelectionGui(player);
                                }
                            });
                        });
                    }
                });
    }

    private void openProfileSelectionGui(org.bukkit.entity.Player player) {
        // Small delay to ensure the player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                new ProfileSelectionGui(plugin, player).open();
            }
        }, 10L); // 0.5 second delay
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getProfileManager().onPlayerQuit(event.getPlayer());
    }
}
