package ca.xef5000.playerprofiles.data;

import ca.xef5000.playerprofiles.api.data.Profile;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileImpl implements Profile {

    // Core Profile Info
    private final UUID characterId;
    private final UUID ownerId;
    private String characterName;
    private Timestamp creationDate;
    private Timestamp lastUsedDate;

    // Player State Data
    private ItemStack[] inventoryContents;
    private ItemStack[] armorContents;
    private ItemStack[] enderChestContents;
    private Location location;
    private double health;
    private int foodLevel;
    private float saturation;
    private int totalExperience;
    private GameMode gameMode;
    private Collection<PotionEffect> potionEffects;

    // API data
    private final Map<String, Map<String, Object>> customData = new ConcurrentHashMap<>();

    public ProfileImpl(UUID characterId, UUID ownerId, String characterName) {
        this.characterId = characterId;
        this.ownerId = ownerId;
        this.characterName = characterName;
    }

    // Core
    @Override
    public UUID getProfileId() { return characterId; }

    @Override
    public UUID getOwnerId() { return ownerId; }

    @Override
    public String getProfileName() { return characterName; }

    public void setCharacterName(String characterName) { this.characterName = characterName; }

    public Timestamp getCreationDate() { return creationDate; }

    public void setCreationDate(Timestamp creationDate) { this.creationDate = creationDate; }

    public Timestamp getLastUsedDate() { return lastUsedDate; }

    public void setLastUsedDate(Timestamp lastUsedDate) { this.lastUsedDate = lastUsedDate; }

    // Player State Data

    public ItemStack[] getInventoryContents() { return inventoryContents; }

    public void setInventoryContents(ItemStack[] inventoryContents) { this.inventoryContents = inventoryContents; }

    public ItemStack[] getArmorContents() { return armorContents; }

    public void setArmorContents(ItemStack[] armorContents) { this.armorContents = armorContents; }

    public ItemStack[] getEnderChestContents() { return enderChestContents; }

    public void setEnderChestContents(ItemStack[] enderChestContents) { this.enderChestContents = enderChestContents; }

    public Location getLocation() { return location; }

    public void setLocation(Location location) { this.location = location; }



    public double getHealth() { return health; }

    public void setHealth(double health) { this.health = health; }

    public int getFoodLevel() { return foodLevel; }

    public void setFoodLevel(int foodLevel) { this.foodLevel = foodLevel; }

    public float getSaturation() { return saturation; }

    public void setSaturation(float saturation) { this.saturation = saturation; }

    public int getTotalExperience() { return totalExperience; }

    public void setTotalExperience(int totalExperience) { this.totalExperience = totalExperience; }

    public GameMode getGameMode() { return gameMode; }

    public void setGameMode(GameMode gameMode) { this.gameMode = gameMode; }

    public Collection<PotionEffect> getPotionEffects() { return potionEffects; }

    public void setPotionEffects(Collection<PotionEffect> potionEffects) { this.potionEffects = potionEffects; }

    // api

    @Override
    public void setCustomData(Plugin plugin, String key, Object data) {
        if (data == null) {
            customData.computeIfPresent(plugin.getName(), (k, v) -> {
                v.remove(key);
                return v.isEmpty() ? null : v;
            });
        } else {
            customData.computeIfAbsent(plugin.getName(), k -> new ConcurrentHashMap<>()).put(key, data);
        }
    }

    @Override
    public <T> Optional<T> getCustomData(Plugin plugin, String key, Class<T> type) {
        Map<String, Object> pluginData = customData.get(plugin.getName());
        if (pluginData == null) {
            return Optional.empty();
        }
        Object value = pluginData.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * Allows the DatabaseManager to get the entire custom data map for serialization.
     * @return The complete custom data map.
     */
    public Map<String, Map<String, Object>> getCustomDataMap() {
        return customData;
    }
}
