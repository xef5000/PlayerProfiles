package ca.xef5000.playerprofiles.commands;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.gui.ProfileSelectionGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


public class CharacterCommand implements CommandExecutor, TabCompleter {

    private final PlayerProfiles plugin;

    public CharacterCommand(PlayerProfiles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("playerprofiles.command.base")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "switch":
                handleSwitchCommand(player, args);
                break;
            case "create":
                handleCreateCommand(player, args);
                break;
            case "gui":
                handleGuiCommand(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Showing help:");
                sendHelpMessage(player, label);
                break;
        }

        return true;
    }

    private void handleSwitchCommand(Player player, String[] args) {
        if (!player.hasPermission("playerprofiles.command.switch")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to switch profiles.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /character switch <profileName>");
            // Suggest available profiles to the user
            plugin.getDatabaseManager().getProfilesForPlayer(player).thenAccept(profiles -> {
                if (!profiles.isEmpty()) {
                    String available = profiles.stream().map(p -> p.getProfileName()).collect(Collectors.joining(", "));
                    player.sendMessage(ChatColor.YELLOW + "Available profiles: " + available);
                }
            });
            return;
        }

        String profileName = args[1];
        player.sendMessage(ChatColor.GRAY + "Searching for profile '" + profileName + "'...");

        // Asynchronously find and switch the profile to avoid lagging the server
        plugin.getDatabaseManager().getProfilesForPlayer(player).thenAccept(profiles -> {
            UUID targetProfileId = profiles.stream()
                    .filter(p -> p.getProfileName().equalsIgnoreCase(profileName))
                    .map(p -> p.getProfileId())
                    .findFirst()
                    .orElse(null);

            if (targetProfileId == null) {
                player.sendMessage(ChatColor.RED + "Profile not found: '" + profileName + "'");
                return;
            }

            player.sendMessage(ChatColor.GREEN + "Profile found! Switching now...");
            plugin.getProfileManager().switchProfile(player, targetProfileId).thenAccept(success -> {
                if (success) {
                    player.sendMessage(ChatColor.GOLD + "Successfully switched to profile: " + profileName);
                } else {
                    player.sendMessage(ChatColor.RED + "An error occurred while switching profiles.");
                }
            });
        });
    }

    private void handleCreateCommand(Player player, String[] args) {
        if (!player.hasPermission("playerprofiles.command.create")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to create profiles.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /character create <profileName>");
            return;
        }

        String profileName = args[1];

        // --- Input Validation ---
        if (profileName.length() < 3 || profileName.length() > 16) {
            player.sendMessage(ChatColor.RED + "Profile name must be between 3 and 16 characters long.");
            return;
        }
        if (!profileName.matches("[a-zA-Z0-9_]+")) {
            player.sendMessage(ChatColor.RED + "Profile name can only contain letters, numbers, and underscores.");
            return;
        }
        // You could also add a limit to the number of profiles a player can have.

        player.sendMessage(ChatColor.GRAY + "Checking if profile name is available...");

        // Asynchronously check if the name is already taken before creating
        plugin.getDatabaseManager().getProfilesForPlayer(player).thenAccept(profiles -> {
            boolean nameExists = profiles.stream()
                    .anyMatch(p -> p.getProfileName().equalsIgnoreCase(profileName));

            if (nameExists) {
                player.sendMessage(ChatColor.RED + "You already have a profile with that name.");
                return;
            }

            // Name is available, proceed to create the profile
            player.sendMessage(ChatColor.GRAY + "Creating profile '" + profileName + "'...");
            plugin.getDatabaseManager().createProfile(player, profileName).thenAccept(newProfile -> {
                if (newProfile != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // This code block is now running safely on the main server thread.
                        plugin.getProfileManager().savePlayerStateToProfile(player, newProfile);

                        plugin.getDatabaseManager().saveProfile(newProfile);

                        // 3. Inform the player that everything is complete.
                        player.sendMessage(ChatColor.GREEN + "Successfully created profile: " + ChatColor.GOLD + newProfile.getProfileName());
                        player.sendMessage(ChatColor.GRAY + "Your current state has been saved as its starting point.");
                    });
                } else {
                    player.sendMessage(ChatColor.RED + "An error occurred while creating the profile in the database.");
                }
            });
        });
    }

    private void handleGuiCommand(Player player) {
        if (!player.hasPermission("playerprofiles.command.base")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        new ProfileSelectionGui(plugin, player).open();
    }

    private void sendHelpMessage(Player player, String label) {
        player.sendMessage(ChatColor.GOLD + "--- PlayerProfiles Help ---");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " switch <name>" + ChatColor.GRAY + " - Switch to a different profile.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " create <name>" + ChatColor.GRAY + " - Creates a new, empty profile.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " gui" + ChatColor.GRAY + " - Opens the profile selection GUI.");
        // Add more help messages for future subcommands here
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (player.hasPermission("playerprofiles.command.switch")) {
                completions.add("switch");
            }
            if (player.hasPermission("playerprofiles.command.create")) {
                completions.add("create");
            }
            if (player.hasPermission("playerprofiles.command.base")) {
                completions.add("gui");
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("switch")) {
            // ... (This block remains unchanged)
        }

        // No special tab completion needed for `/character create <name>`, so we're done.
        return new ArrayList<>();
    }
}
