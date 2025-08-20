package ca.xef5000.playerprofiles.gui;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.api.data.Profile;
import ca.xef5000.playerprofiles.managers.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * GUI for selecting or creating player profiles.
 * This GUI is shown when a player joins and doesn't have an active profile.
 */
public class ProfileSelectionGui extends Gui {
    
    private Collection<Profile> profiles;
    private boolean isLoading;
    private boolean canCreateProfile;
    private int profileLimit;
    
    public ProfileSelectionGui(PlayerProfiles plugin, Player player) {
        super(plugin, player, LangManager.getMessage("gui.profile_selection.title"), 54); // 6 rows
        this.isLoading = true;
        this.canCreateProfile = false;
        this.profileLimit = plugin.getProfileManager().getProfileLimit(player);
    }
    
    @Override
    protected void build() {
        if (isLoading) {
            buildLoadingScreen();
            loadProfiles();
        } else {
            buildProfileSelection();
        }
    }
    
    private void buildLoadingScreen() {
        // Fill with glass panes
        ItemStack glassPane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        fillEmpty(glassPane);
        
        // Loading indicator in the center
        ItemStack loadingItem = createItem(Material.CLOCK, 
                LangManager.getMessage("general.loading"), 
                List.of(LangManager.getMessage("general.please_wait")));
        setButton(22, new Button(loadingItem)); // Center slot
    }
    
    private void buildProfileSelection() {
        // Clear the inventory first
        inventory.clear();
        buttons.clear();
        
        if (profiles.isEmpty()) {
            buildNoProfilesScreen();
        } else {
            buildProfileList();
        }
        
        // Add create profile button if possible
        if (canCreateProfile) {
            addCreateProfileButton();
        } else {
            addProfileLimitReachedButton();
        }
        
        // Fill empty slots with glass panes
        ItemStack glassPane = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        fillEmpty(glassPane);
    }
    
    private void buildNoProfilesScreen() {
        // No profiles message
        ItemStack noProfilesItem = createItem(Material.BARRIER,
                LangManager.getMessage("gui.profile_selection.no_profiles_title"),
                LangManager.getMessageList("gui.profile_selection.no_profiles_lore"));
        setButton(22, new Button(noProfilesItem)); // Center slot
    }
    
    private void buildProfileList() {
        int slot = 10; // Start from second row, second column
        int profileCount = 0;
        
        for (Profile profile : profiles) {
            if (profileCount >= 28) break; // Limit to prevent overflow
            
            // Skip slots that would be on the edges
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }
            
            ItemStack profileItem = createProfileItem(profile);
            Button profileButton = new Button(profileItem, (clickPlayer, clickType) -> {
                selectProfile(profile);
            });
            
            setButton(slot, profileButton);
            slot++;
            profileCount++;
            
            // Move to next row if we've filled this one
            if (slot % 9 == 8) {
                slot += 2; // Skip to start of next row
            }
        }
    }
    
    private void addCreateProfileButton() {
        ItemStack createItem = createItem(Material.EMERALD,
                LangManager.getMessage("gui.profile_selection.create_profile_name"),
                LangManager.getMessageList("gui.profile_selection.create_profile_lore"));
        
        Button createButton = new Button(createItem, (clickPlayer, clickType) -> {
            createNewProfile();
        });
        
        setButton(49, createButton); // Bottom right area
    }
    
    private void addProfileLimitReachedButton() {
        Map<String, String> placeholders = LangManager.placeholder("limit", String.valueOf(profileLimit));
        
        ItemStack limitItem = createItem(Material.BARRIER,
                LangManager.getMessage("gui.profile_selection.profile_limit_reached_name"),
                LangManager.getMessageList("gui.profile_selection.profile_limit_reached_lore", placeholders));
        
        setButton(49, new Button(limitItem)); // Bottom right area, not clickable
    }
    
    private ItemStack createProfileItem(Profile profile) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        // Set the skull to the player's head
        meta.setOwningPlayer(player);
        
        Map<String, String> placeholders = LangManager.placeholders(
                "profile_name", profile.getProfileName(),
                "creation_date", profile.getCreationDate() != null ? 
                        LangManager.formatDate(profile.getCreationDate()) : "Unknown",
                "last_used_date", profile.getLastUsedDate() != null ? 
                        LangManager.formatDate(profile.getLastUsedDate()) : "Never"
        );
        
        meta.setDisplayName(LangManager.getMessage("gui.profile_selection.profile_item_name", placeholders));
        meta.setLore(LangManager.getMessageList("gui.profile_selection.profile_item_lore", placeholders));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            if (name != null && !name.trim().isEmpty()) {
                meta.setDisplayName(name);
            }
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private void loadProfiles() {
        plugin.getDatabaseManager().getProfilesForPlayer(player)
                .thenAccept(loadedProfiles -> {
                    this.profiles = loadedProfiles;
                    this.canCreateProfile = loadedProfiles.size() < profileLimit;
                    this.isLoading = false;

                    // Update GUI on main thread
                    Bukkit.getScheduler().runTask(plugin, this::refresh);
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to load profiles for " + player.getName() + ": " + throwable.getMessage());
                    player.sendMessage(LangManager.getMessage("errors.database_error"));
                    close();
                    return null;
                });
    }
    
    private void selectProfile(Profile profile) {
        // Show loading state
        showLoadingState();
        
        plugin.getProfileManager().switchProfile(player, profile.getProfileId())
                .thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            player.sendMessage(LangManager.getMessage("messages.profile_selected", 
                                    LangManager.placeholder("profile_name", profile.getProfileName())));
                            close();
                        } else {
                            player.sendMessage(LangManager.getMessage("messages.profile_selection_failed"));
                            refresh(); // Reload the GUI
                        }
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to select profile for " + player.getName() + ": " + throwable.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(LangManager.getMessage("errors.unknown_error"));
                        refresh();
                    });
                    return null;
                });
    }
    
    private void createNewProfile() {
        if (!canCreateProfile) {
            player.sendMessage(LangManager.getMessage("messages.profile_limit_reached", 
                    LangManager.placeholder("limit", String.valueOf(profileLimit))));
            return;
        }
        
        // Show loading state
        showLoadingState();
        
        plugin.getProfileManager().createProfileWithGeneratedName(player)
                .thenAccept(newProfile -> {
                    if (newProfile != null) {
                        // Save current player state to the new profile
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getProfileManager().savePlayerStateToProfile(player, newProfile);
                            
                            // Save to database and switch to the new profile
                            plugin.getDatabaseManager().saveProfile(newProfile)
                                    .thenCompose(v -> plugin.getProfileManager().switchProfile(player, newProfile.getProfileId()))
                                    .thenAccept(success -> {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (success) {
                                                player.sendMessage(LangManager.getMessage("messages.profile_created", 
                                                        LangManager.placeholder("profile_name", newProfile.getProfileName())));
                                                close();
                                            } else {
                                                player.sendMessage(LangManager.getMessage("messages.profile_creation_failed"));
                                                refresh();
                                            }
                                        });
                                    });
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(LangManager.getMessage("messages.profile_creation_failed"));
                            refresh();
                        });
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to create profile for " + player.getName() + ": " + throwable.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(LangManager.getMessage("errors.unknown_error"));
                        refresh();
                    });
                    return null;
                });
    }
    
    private void showLoadingState() {
        inventory.clear();
        buttons.clear();
        
        ItemStack loadingItem = createItem(Material.CLOCK,
                LangManager.getMessage("gui.profile_selection.creating_profile_name"),
                LangManager.getMessageList("gui.profile_selection.creating_profile_lore"));
        
        setButton(22, new Button(loadingItem));
        
        ItemStack glassPane = createItem(Material.YELLOW_STAINED_GLASS_PANE, " ", null);
        fillEmpty(glassPane);
    }
    
    @Override
    public void onClose() {
        // If player doesn't have an active profile, they must select one
        if (plugin.getProfileManager().getActiveProfile(player) == null) {
            // Reopen the GUI after a short delay to prevent spam
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(LangManager.getMessage("messages.must_select_profile"));
                    new ProfileSelectionGui(plugin, player).open();
                }
            }, 10L); // 0.5 second delay
        }
    }
}
