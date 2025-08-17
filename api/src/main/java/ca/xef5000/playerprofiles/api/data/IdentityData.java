package ca.xef5000.playerprofiles.api.data;

import java.util.UUID;

/**
 * A pure, serializable Data Transfer Object (DTO) representing a player's identity.
 * This class has no dependencies on server code (like GameProfile) and is safe to use in any API.
 */
public record IdentityData(UUID uuid, String name, SkinData skin) {

    /**
     * Represents the skin part of an identity.
     * @param value The Base64 texture value.
     * @param signature The Base64 signature.
     */
    public record SkinData(String value, String signature) {
        /**
         * Represents an empty or nonexistent skin.
         */
        public static final SkinData EMPTY = new SkinData("", "");
    }
}