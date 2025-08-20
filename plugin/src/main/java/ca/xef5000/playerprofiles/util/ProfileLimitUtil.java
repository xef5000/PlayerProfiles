package ca.xef5000.playerprofiles.util;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Utility class for handling profile limits based on permissions.
 * Supports permission nodes like "playerprofiles.profile_limit.10" for 10 max profiles.
 */
public class ProfileLimitUtil {
    
    private static final String PERMISSION_PREFIX = "playerprofiles.profile_limit.";
    private static final int DEFAULT_LIMIT = 3; // Default limit if no permission is found
    
    /**
     * Gets the maximum number of profiles a player can have based on their permissions.
     * Looks for permissions in the format "playerprofiles.profile_limit.X" where X is the limit.
     * If multiple permissions are found, returns the highest limit.
     * 
     * @param player The player to check
     * @return The maximum number of profiles the player can have
     */
    public static int getProfileLimit(Player player) {
        int maxLimit = 0;
        boolean hasLimitPermission = false;
        
        // Check all permissions for profile limit permissions
        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String permission = permInfo.getPermission();
            
            if (permission.startsWith(PERMISSION_PREFIX) && permInfo.getValue()) {
                hasLimitPermission = true;
                
                try {
                    // Extract the number from the permission
                    String limitStr = permission.substring(PERMISSION_PREFIX.length());
                    int limit = Integer.parseInt(limitStr);
                    
                    // Keep track of the highest limit
                    if (limit > maxLimit) {
                        maxLimit = limit;
                    }
                } catch (NumberFormatException e) {
                    // Invalid permission format, skip it
                    continue;
                }
            }
        }
        
        // If no valid limit permission was found, use the default
        return hasLimitPermission ? maxLimit : DEFAULT_LIMIT;
    }
    
    /**
     * Checks if a player can create another profile based on their current profile count and limit.
     * 
     * @param player The player to check
     * @param currentProfileCount The number of profiles the player currently has
     * @return true if the player can create another profile, false otherwise
     */
    public static boolean canCreateProfile(Player player, int currentProfileCount) {
        int limit = getProfileLimit(player);
        return currentProfileCount < limit;
    }
    
    /**
     * Gets the default profile limit used when no permission is found.
     * 
     * @return The default profile limit
     */
    public static int getDefaultLimit() {
        return DEFAULT_LIMIT;
    }
    
    /**
     * Checks if a player has any profile limit permissions.
     * 
     * @param player The player to check
     * @return true if the player has at least one profile limit permission, false otherwise
     */
    public static boolean hasLimitPermission(Player player) {
        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String permission = permInfo.getPermission();
            
            if (permission.startsWith(PERMISSION_PREFIX) && permInfo.getValue()) {
                try {
                    // Verify it's a valid number
                    String limitStr = permission.substring(PERMISSION_PREFIX.length());
                    Integer.parseInt(limitStr);
                    return true;
                } catch (NumberFormatException e) {
                    // Invalid format, continue checking
                    continue;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Gets a formatted string describing the player's profile limit.
     * 
     * @param player The player to check
     * @return A string like "3 (default)" or "10 (permission)"
     */
    public static String getFormattedLimit(Player player) {
        int limit = getProfileLimit(player);
        boolean hasPermission = hasLimitPermission(player);
        
        if (hasPermission) {
            return limit + " (permission)";
        } else {
            return limit + " (default)";
        }
    }
}
