package ca.xef5000.playerprofiles.nms.v1_20_R1;

import ca.xef5000.playerprofiles.api.data.IdentityData;
import ca.xef5000.playerprofiles.api.services.NMSService;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class NMSService_v1_20_R1 implements NMSService {

    private final Plugin plugin;
    /**
     * Constructor for the v1.20.R1 Identity Service implementation.
     * @param plugin The main plugin instance, required for Bukkit API calls like hide/show player.
     */
    public NMSService_v1_20_R1(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void applyIdentity(Player player, IdentityData newIdentity) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
            UUID oldUUID = serverPlayer.getUUID();

            // Create new GameProfile
            GameProfile newProfile = createGameProfileFromData(newIdentity, player);

            plugin.getLogger().info("Changing UUID from " + oldUUID + " to " + newProfile.getId());

            // Use the safer in-place update approach
            updatePlayerUUIDInPlace(serverPlayer, oldUUID, newProfile);

            plugin.getLogger().info("UUID change complete. New UUID: " + serverPlayer.getUUID());

        } catch (Exception e) {
            throw new RuntimeException("Failed to apply identity for " + player.getName(), e);
        }
    }

    @Override
    public IdentityData getOriginalIdentity(Player player) {
        try {
            GameProfile profile = ((CraftPlayer) player).getHandle().getGameProfile();

            Property skinProperty = profile.getProperties().get("textures").stream().findFirst().orElse(null);
            IdentityData.SkinData skinData = IdentityData.SkinData.EMPTY;
            if (skinProperty != null) {
                skinData = new IdentityData.SkinData(skinProperty.getValue(), skinProperty.getSignature());
            }

            return new IdentityData(profile.getId(), profile.getName(), skinData);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get original identity for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void updatePlayerUUIDInPlace(ServerPlayer serverPlayer, UUID oldUUID, GameProfile newProfile) {
        try {
            // Step 1: Remove from PlayerList maps with old UUID
            removeFromPlayerList(serverPlayer, oldUUID);

            // Step 2: Update the GameProfile using multiple approaches
            updateGameProfile(serverPlayer, newProfile);

            // Step 3: Re-add to PlayerList maps with new UUID
            addToPlayerList(serverPlayer);

            // Step 4: Update client-side representation
            updateClientSidePlayer(serverPlayer, oldUUID);

            // Step 5: Update any CraftBukkit references
            updateCraftBukkitReferences(serverPlayer);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update player UUID in place: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("UUID update failed", e);
        }
    }

    private void removeFromPlayerList(ServerPlayer player, UUID oldUUID) {
        try {
            PlayerList playerList = player.getServer().getPlayerList();

            // Remove from PlayerList maps
            Field[] fields = PlayerList.class.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(playerList);

                    if (value instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) value;
                        if (!map.isEmpty()) {
                            Object firstKey = map.keySet().iterator().next();

                            // Check if this is the UUID map
                            if (firstKey instanceof UUID) {
                                @SuppressWarnings("unchecked")
                                Map<UUID, ServerPlayer> uuidMap = (Map<UUID, ServerPlayer>) map;
                                ServerPlayer removed = uuidMap.remove(oldUUID);
                                if (removed != null) {
                                    plugin.getLogger().info("Removed player from UUID map (field: " + field.getName() + ")");
                                }
                            }
                            // Check if this is the name map
                            else if (firstKey instanceof String) {
                                @SuppressWarnings("unchecked")
                                Map<String, ServerPlayer> nameMap = (Map<String, ServerPlayer>) map;
                                ServerPlayer removed = nameMap.remove(player.getGameProfile().getName());
                                if (removed != null) {
                                    plugin.getLogger().info("Removed player from name map (field: " + field.getName() + ")");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue with other fields if one fails
                    plugin.getLogger().fine("Could not access field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not remove player from PlayerList maps: " + e.getMessage());
        }
    }

    private void updateGameProfile(ServerPlayer player, GameProfile newProfile) throws Exception {
        boolean gameProfileUpdated = false;

        // Try updating GameProfile in Entity class (parent of ServerPlayer)
        Class<?> currentClass = player.getClass();
        while (currentClass != null) {
            Field[] fields = currentClass.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);

                    // Update GameProfile field
                    if (field.getType().equals(GameProfile.class)) {
                        field.set(player, newProfile);
                        plugin.getLogger().info("Updated GameProfile in field: " + field.getName() + " (class: " + currentClass.getSimpleName() + ")");
                        gameProfileUpdated = true;
                    }

                    // Also look for UUID fields and update them
                    if (field.getType().equals(UUID.class)) {
                        UUID currentValue = (UUID) field.get(player);
                        if (currentValue != null && !currentValue.equals(newProfile.getId())) {
                            field.set(player, newProfile.getId());
                            plugin.getLogger().info("Updated UUID in field: " + field.getName() + " (class: " + currentClass.getSimpleName() + ")");
                            plugin.getLogger().info("Changed from: " + currentValue + " to: " + newProfile.getId());
                        }
                    }
                } catch (Exception e) {
                    // Continue with other fields if one fails
                    plugin.getLogger().fine("Could not update field " + field.getName() + ": " + e.getMessage());
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        if (!gameProfileUpdated) {
            throw new RuntimeException("Could not find GameProfile field to update");
        }

        // Verify the change took effect
        UUID currentUUID = player.getUUID();
        plugin.getLogger().info("After update - player.getUUID() returns: " + currentUUID);
        plugin.getLogger().info("Expected UUID: " + newProfile.getId());
        plugin.getLogger().info("GameProfile.getId() returns: " + player.getGameProfile().getId());

        if (!currentUUID.equals(newProfile.getId())) {
            plugin.getLogger().warning("UUID update failed! getUUID() still returns old value.");
            // Try to find where getUUID() is getting its value from
            debugUUIDSources(player, newProfile.getId());
        }
    }

    private void debugUUIDSources(ServerPlayer player, UUID expectedUUID) {
        plugin.getLogger().info("=== UUID DEBUG INFO ===");

        Class<?> currentClass = player.getClass();
        while (currentClass != null) {
            Field[] fields = currentClass.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(player);

                    if (value instanceof UUID) {
                        plugin.getLogger().info("UUID field '" + field.getName() + "' in " + currentClass.getSimpleName() + ": " + value);
                    } else if (value instanceof GameProfile) {
                        GameProfile profile = (GameProfile) value;
                        plugin.getLogger().info("GameProfile field '" + field.getName() + "' in " + currentClass.getSimpleName() + " has UUID: " + profile.getId());
                    }
                } catch (Exception e) {
                    // Skip inaccessible fields
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        plugin.getLogger().info("=== END UUID DEBUG ===");
    }

    private void addToPlayerList(ServerPlayer player) {
        try {
            PlayerList playerList = player.getServer().getPlayerList();
            UUID newUUID = player.getUUID();
            String playerName = player.getGameProfile().getName();

            // Find and update the maps
            Field[] fields = PlayerList.class.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(playerList);

                    if (value instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) value;
                        if (!map.isEmpty()) {
                            Object firstKey = map.keySet().iterator().next();

                            // Add to UUID map
                            if (firstKey instanceof UUID) {
                                @SuppressWarnings("unchecked")
                                Map<UUID, ServerPlayer> uuidMap = (Map<UUID, ServerPlayer>) map;
                                uuidMap.put(newUUID, player);
                                plugin.getLogger().info("Added player to UUID map (field: " + field.getName() + ")");
                            }
                            // Add to name map
                            else if (firstKey instanceof String) {
                                @SuppressWarnings("unchecked")
                                Map<String, ServerPlayer> nameMap = (Map<String, ServerPlayer>) map;
                                nameMap.put(playerName, player);
                                plugin.getLogger().info("Added player to name map (field: " + field.getName() + ")");
                            }
                        } else {
                            // Empty map - try to determine type by field name or type parameters
                            if (field.getName().toLowerCase().contains("uuid") ||
                                    field.getGenericType().toString().contains("UUID")) {
                                @SuppressWarnings("unchecked")
                                Map<UUID, ServerPlayer> uuidMap = (Map<UUID, ServerPlayer>) map;
                                uuidMap.put(newUUID, player);
                                plugin.getLogger().info("Added player to empty UUID map (field: " + field.getName() + ")");
                            } else if (field.getName().toLowerCase().contains("name") ||
                                    field.getGenericType().toString().contains("String")) {
                                @SuppressWarnings("unchecked")
                                Map<String, ServerPlayer> nameMap = (Map<String, ServerPlayer>) map;
                                nameMap.put(playerName, player);
                                plugin.getLogger().info("Added player to empty name map (field: " + field.getName() + ")");
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue with other fields if one fails
                    plugin.getLogger().fine("Could not access field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not add player to PlayerList maps: " + e.getMessage());
        }
    }

    private void updateClientSidePlayer(ServerPlayer player, UUID oldUUID) {
        try {
            // Send remove packet for old UUID
            ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(
                    Collections.singletonList(oldUUID)
            );

            // Send add packet for new player
            ClientboundPlayerInfoUpdatePacket addPacket = new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(
                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
                    ),
                    Collections.singletonList(player)
            );

            // Send to all players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                ServerPlayer serverOnlinePlayer = ((CraftPlayer) onlinePlayer).getHandle();
                serverOnlinePlayer.connection.send(removePacket);
                serverOnlinePlayer.connection.send(addPacket);
            }

            // Force client to refresh player visuals
            Player bukkitPlayer = player.getBukkitEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Create a fresh packet specifically for the player's own tab list entry
                    ClientboundPlayerInfoUpdatePacket selfUpdatePacket = new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(
                                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
                            ),
                            Collections.singletonList(player)
                    );

                    player.connection.send(selfUpdatePacket);

                    // Also refresh visual representation
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (!onlinePlayer.equals(bukkitPlayer)) {
                            onlinePlayer.hidePlayer(plugin, bukkitPlayer);
                            onlinePlayer.showPlayer(plugin, bukkitPlayer);
                        }
                    }

                    plugin.getLogger().info("Sent additional tab list update to " + bukkitPlayer.getName());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to send additional tab list update: " + e.getMessage());
                }
            }, 3L);
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating client-side player: " + e.getMessage());
        }
    }

    private void updateCraftBukkitReferences(ServerPlayer player) {
        try {
            Player bukkitPlayer = player.getBukkitEntity();

            if (bukkitPlayer instanceof CraftPlayer) {
                CraftPlayer craftPlayer = (CraftPlayer) bukkitPlayer;

                // Try to update the entity reference in CraftPlayer
                Field[] fields = CraftPlayer.class.getDeclaredFields();
                for (Field field : fields) {
                    if (field.getType().equals(ServerPlayer.class) ||
                            field.getType().isAssignableFrom(ServerPlayer.class)) {
                        field.setAccessible(true);
                        field.set(craftPlayer, player);
                        plugin.getLogger().info("Updated CraftPlayer entity reference (field: " + field.getName() + ")");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not update CraftBukkit references: " + e.getMessage());
        }
    }

    @Override
    public void cleanupPlayerOnLogout(Player player) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
            UUID currentUUID = serverPlayer.getUUID();

            plugin.getLogger().info("Cleaning up player " + player.getName() + " with UUID: " + currentUUID);

            // Remove from all entity tracking systems (with better error handling)
            cleanupEntityFromWorld(serverPlayer);

            // Remove from PlayerList maps
            removeFromPlayerList(serverPlayer, currentUUID);

            plugin.getLogger().info("Player cleanup complete for " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to cleanup player on logout: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanupEntityFromWorld(ServerPlayer player) {
        try {
            ServerLevel level = player.serverLevel();
            if (level == null) {
                plugin.getLogger().warning("Player level is null during cleanup");
                return;
            }

            // Remove from chunk tracking
            try {
                level.getChunkSource().removeEntity(player);
                plugin.getLogger().fine("Removed player from chunk tracking");
            } catch (Exception e) {
                plugin.getLogger().warning("Could not remove from chunk tracking: " + e.getMessage());
            }

            // Remove from entity lookup systems (with null checks)
            removeFromEntityLookup(level, player);

            // Remove from level's entity tracking
            removeFromLevelTracking(level, player);

        } catch (Exception e) {
            plugin.getLogger().warning("Could not cleanup entity from world: " + e.getMessage());
        }
    }

    private void removeFromEntityLookup(ServerLevel level, ServerPlayer player) {
        try {
            // Find the entity lookup/registry and remove the player
            Field[] fields = ServerLevel.class.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(level);

                    // Add null check here to prevent the null pointer error
                    if (value == null) {
                        continue; // Skip null fields
                    }

                    // Look for entity tracking fields
                    String className = value.getClass().getName();
                    if (className.contains("EntityLookup") || className.contains("EntityManager") ||
                            className.contains("EntitySectionStorage") || className.contains("EntityTickList")) {

                        // Try to find and call remove methods
                        removeEntityFromLookup(value, player);
                    }
                } catch (Exception e) {
                    // Continue with other fields if one fails
                    plugin.getLogger().fine("Could not access field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not access entity lookup: " + e.getMessage());
        }
    }

    private void removeEntityFromLookup(Object entityLookup, ServerPlayer player) {
        if (entityLookup == null) {
            return; // Safety check
        }

        try {
            // Try common remove method names
            String[] removeMethodNames = {"remove", "removeEntity", "untrack", "unregister", "removeImmediately"};

            for (String methodName : removeMethodNames) {
                try {
                    Method removeMethod = entityLookup.getClass().getDeclaredMethod(methodName, Entity.class);
                    removeMethod.setAccessible(true);
                    removeMethod.invoke(entityLookup, player);
                    plugin.getLogger().info("Removed entity using method: " + methodName);
                    return;
                } catch (NoSuchMethodException e) {
                    // Try next method
                } catch (Exception e) {
                    plugin.getLogger().fine("Method " + methodName + " failed: " + e.getMessage());
                }
            }

            // Try with UUID parameter
            for (String methodName : removeMethodNames) {
                try {
                    Method removeMethod = entityLookup.getClass().getDeclaredMethod(methodName, UUID.class);
                    removeMethod.setAccessible(true);
                    removeMethod.invoke(entityLookup, player.getUUID());
                    plugin.getLogger().info("Removed entity by UUID using method: " + methodName);
                    return;
                } catch (NoSuchMethodException e) {
                    // Try next method
                } catch (Exception e) {
                    plugin.getLogger().fine("UUID method " + methodName + " failed: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Could not remove entity from lookup: " + e.getMessage());
        }
    }

    private void removeFromLevelTracking(ServerLevel level, ServerPlayer player) {
        try {
            // Remove from the level's entity collections
            Field[] fields = ServerLevel.class.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(level);

                    // Add null check
                    if (value == null) {
                        continue;
                    }

                    if (value instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) value;

                        // Remove from any maps that might contain this entity
                        if (!map.isEmpty()) {
                            Object firstKey = map.keySet().iterator().next();
                            if (firstKey instanceof UUID) {
                                @SuppressWarnings("unchecked")
                                Map<UUID, ?> uuidMap = (Map<UUID, ?>) value;
                                Object removed = uuidMap.remove(player.getUUID());
                                if (removed != null) {
                                    plugin.getLogger().info("Removed from level UUID map: " + field.getName());
                                }
                            }
                        }
                    } else if (value instanceof Collection) {
                        @SuppressWarnings("unchecked")
                        Collection<Object> collection = (Collection<Object>) value;
                        boolean removed = collection.remove(player);
                        if (removed) {
                            plugin.getLogger().info("Removed from level collection: " + field.getName());
                        }
                    }
                } catch (Exception e) {
                    // Continue with other fields if one fails
                    plugin.getLogger().fine("Could not access field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not remove from level tracking: " + e.getMessage());
        }
    }

    @Override
    public void setPlayerInfoTemporarily(Player player, IdentityData tempIdentity, Runnable callback) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

            // Store current values
            GameProfile currentProfile = serverPlayer.getGameProfile();

            // Create temporary profile
            GameProfile tempProfile = new GameProfile(tempIdentity.uuid(), tempIdentity.name());
            if (tempIdentity.skin() != null && tempIdentity.skin().value() != null) {
                tempProfile.getProperties().put("textures",
                        new com.mojang.authlib.properties.Property("textures",
                                tempIdentity.skin().value(), tempIdentity.skin().signature()));
            }

            // Temporarily update
            updateGameProfileSilent(serverPlayer, tempProfile);

            try {
                callback.run();
            } finally {
                // Restore original profile
                updateGameProfileSilent(serverPlayer, currentProfile);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to temporarily set player info: " + e.getMessage());
            callback.run(); // Execute anyway
        }
    }

    /**
     * Silently updates GameProfile without logging.
     */
    private void updateGameProfileSilent(net.minecraft.server.level.ServerPlayer player, GameProfile newProfile) {
        try {
            Class<?> currentClass = player.getClass();
            while (currentClass != null) {
                Field[] fields = currentClass.getDeclaredFields();

                for (Field field : fields) {
                    if (field.getType().equals(GameProfile.class)) {
                        field.setAccessible(true);
                        field.set(player, newProfile);
                        return;
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        } catch (Exception e) {
            // Ignore errors in silent update
        }
    }

    /**
     * A private helper method to perform the translation from API DTO to NMS object.
     */
    private GameProfile createGameProfileFromData(IdentityData data, Player player) {
        GameProfile profile = new GameProfile(data.uuid(), player.getName());
        IdentityData.SkinData skin = data.skin();
        if (skin != null && skin.value() != null && !skin.value().isEmpty() && skin.signature() != null && !skin.signature().isEmpty()) {
            profile.getProperties().put("textures", new Property("textures", skin.value(), skin.signature()));
        }
        return profile;
    }
}
