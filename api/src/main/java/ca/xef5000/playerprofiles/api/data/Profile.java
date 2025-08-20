package ca.xef5000.playerprofiles.api.data;

import org.bukkit.plugin.Plugin;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

public interface Profile {
    UUID getProfileId();
    UUID getOwnerId();
    String getProfileName();

    Timestamp getCreationDate();
    Timestamp getLastUsedDate();

    void setCustomData(Plugin plugin, String key, Object data);
    <T> Optional<T> getCustomData(Plugin plugin, String key, Class<T> type);
}
