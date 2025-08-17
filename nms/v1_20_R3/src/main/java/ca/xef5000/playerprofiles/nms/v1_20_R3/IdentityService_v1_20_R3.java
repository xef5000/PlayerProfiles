package ca.xef5000.playerprofiles.nms.v1_20_R3;

import ca.xef5000.playerprofiles.api.data.IdentityData;
import ca.xef5000.playerprofiles.api.services.IdentityService;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R3.CraftServer;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;

public class IdentityService_v1_20_R3 implements IdentityService {

    private final Plugin plugin;
    private static Field gameProfileField;

    /**
     * Constructor for the v1.20.R1 Identity Service implementation.
     * @param plugin The main plugin instance, required for Bukkit API calls like hide/show player.
     * @throws NoSuchFieldException if the required "gameProfile" field cannot be found in the ServerPlayer class.
     */
    public IdentityService_v1_20_R3(Plugin plugin) throws NoSuchFieldException {
        this.plugin = plugin;
        // Cache the reflection lookup on startup for performance
        if (gameProfileField == null) {
            // Using the Paperweight-remapped name for stability
            gameProfileField = ServerPlayer.class.getDeclaredField("gameProfile");
            gameProfileField.setAccessible(true);
        }
    }

    @Override
    public void applyIdentity(Player player, IdentityData newIdentity) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

            // 1. Translate the pure API DTO into an NMS-compatible GameProfile
            GameProfile newProfile = createGameProfileFromData(newIdentity);

            // 2. Forcefully set the new GameProfile on the NMS player object
            gameProfileField.set(serverPlayer, newProfile);

            // 3. Perform the "re-login" sequence
            refreshPlayer(serverPlayer);

        } catch (Exception e) {
            throw new RuntimeException("Failed to apply identity for " + player.getName(), e);
        }
    }

    @Override
    public IdentityData getOriginalIdentity(Player player) {
        throw new UnsupportedOperationException("This method should be implemented in the main plugin logic, not the NMS layer.");
    }

    @Override
    public Optional<IdentityData> getAppliedIdentity(Player player) {
        throw new UnsupportedOperationException("This method should be implemented in the main plugin logic, not the NMS layer.");
    }

    @Override
    public void resetIdentity(Player player) {
        throw new UnsupportedOperationException("This method should be implemented in the main plugin logic, not the NMS layer.");
    }

    /**
     * A private helper method to perform the translation from API DTO to NMS object.
     */
    private GameProfile createGameProfileFromData(IdentityData data) {
        GameProfile profile = new GameProfile(data.uuid(), data.name());
        IdentityData.SkinData skin = data.skin();
        if (skin != null && skin.value() != null && !skin.value().isEmpty() && skin.signature() != null && !skin.signature().isEmpty()) {
            profile.getProperties().put("textures", new Property("textures", skin.value(), skin.signature()));
        }
        return profile;
    }

    /**
     * The core refresh logic, inspired by NickAPI.
     */
    private void refreshPlayer(ServerPlayer serverPlayer) {
        Player bukkitPlayer = serverPlayer.getBukkitEntity();

        // Announce departure of the old identity
        ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(Collections.singletonList(serverPlayer.getUUID()));

        // Announce arrival of the new identity
        ClientboundPlayerInfoUpdatePacket addPacket = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, serverPlayer);

        PlayerList playerList = ((CraftServer) Bukkit.getServer()).getServer().getPlayerList();

        // Send packets to all online players to update their tab lists
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            ((CraftPlayer) onlinePlayer).getHandle().connection.send(removePacket);
            ((CraftPlayer) onlinePlayer).getHandle().connection.send(addPacket);
        }

        // Respawn the player to update server-side state (like collision boxes, etc.)
        playerList.respawn(serverPlayer, serverPlayer.serverLevel(), true, bukkitPlayer.getLocation(), false, PlayerRespawnEvent.RespawnReason.PLUGIN);

        // Force clients to visually update the player model (skin and nameplate)
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(bukkitPlayer)) continue;
            onlinePlayer.hidePlayer(plugin, bukkitPlayer);
            onlinePlayer.showPlayer(plugin, bukkitPlayer);
        }

        // Update the player for themselves
        bukkitPlayer.updateInventory();
    }
}
