package ca.xef5000.playerprofiles.util;

import ca.xef5000.playerprofiles.api.data.Profile;

import java.util.Locale;
import java.util.UUID;

public final class ProfileUsernameGenerator {

    /**
     * Private constructor to prevent instantiation.
     */
    private ProfileUsernameGenerator() {}

    /**
     * Generates a globally unique, 16-character internal identifier for a profile
     * to be used as its "username" in LuckPerms. This ID is derived from the profile's UUID.
     *
     * @param profileId The UUID of the profile.
     * @return A 16-character string containing only hexadecimal characters (a-f, 0-9).
     */
    public static String generate(UUID profileId) {
        // A full UUID string is 36 chars with hyphens. Removing them makes it 32 chars.
        // We truncate to 16, which is more than unique enough for this purpose.
        // The characters (a-f, 0-9) are all valid according to the LuckPerms regex.
        return profileId.toString().replace("-", "").substring(0, 16);
    }
}
