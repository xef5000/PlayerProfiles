package ca.xef5000.playerprofiles.managers;

import ca.xef5000.playerprofiles.PlayerProfiles;
import ca.xef5000.playerprofiles.api.data.Profile;
import ca.xef5000.playerprofiles.data.ProfileImpl;
import ca.xef5000.playerprofiles.util.Base64ItemSerializer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DatabaseManager {

    private final PlayerProfiles plugin;
    private Connection connection;
    private final Gson gson = new Gson();
    private final Type potionEffectCollectionType = new TypeToken<Collection<PotionEffect>>() {}.getType();

    // SQL Queries
    private static final String CREATE_PROFILE = "INSERT INTO pc_profiles (id, owner_uuid, profile_name, last_used_date) VALUES (?, ?, ?, ?);";
    private static final String CREATE_PROFILE_DATA = "INSERT INTO pc_profile_data (profile_id) VALUES (?);";

    private static final String UPDATE_PROFILE = "UPDATE pc_profiles SET profile_name = ?, last_used_date = ? WHERE id = ?;";
    private static final String UPDATE_PROFILE_DATA = "UPDATE pc_profile_data SET inventory = ?, armor = ?, location = ?, health = ?, " +
            "food_level = ?, experience = ?, gamemode = ?, potion_effects = ? WHERE profile_id = ?;";

    private static final String GET_PROFILE_BY_ID = "SELECT * FROM pc_profiles WHERE id = ?;";
    private static final String GET_PROFILE_DATA_BY_ID = "SELECT * FROM pc_profile_data WHERE profile_id = ?;";
    private static final String GET_PROFILES_BY_OWNER = "SELECT * FROM pc_profiles WHERE owner_uuid = ? ORDER BY last_used_date DESC;";

    private static final String DELETE_PROFILE = "DELETE FROM pc_profiles WHERE id = ?;";

    private static final String DELETE_CUSTOM_DATA = "DELETE FROM pc_custom_data WHERE profile_id = ?;";
    private static final String INSERT_CUSTOM_DATA = "INSERT INTO pc_custom_data (profile_id, namespace, data_key, data_value) VALUES (?, ?, ?, ?);";
    private static final String GET_CUSTOM_DATA = "SELECT namespace, data_key, data_value FROM pc_custom_data WHERE profile_id = ?;";

    public DatabaseManager(PlayerProfiles plugin) {
        this.plugin = plugin;
    }

    /**
     * Connects to the database and initializes tables if they don't exist.
     */
    public void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return; // Already connected
        }

        // 1. Read the database type from the config
        String dbType = ConfigManager.getDatabaseType();
        plugin.getLogger().info("Connecting to " + dbType + " database...");

        // 2. Handle MySQL connection if specified
        if (dbType.equalsIgnoreCase("MYSQL")) {
            String host = ConfigManager.getMySqlHost();
            int port = ConfigManager.getMySqlPort();
            String dbName = ConfigManager.getMySqlDatabase();
            String user = ConfigManager.getMySqlUsername();
            String pass = ConfigManager.getMySqlPassword();
            boolean useSSL = ConfigManager.isMySqlSslEnabled();

            String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=" + useSSL;
            connection = DriverManager.getConnection(jdbcUrl, user, pass);

            // 3. Handle SQLite as the default
        } else {
            File databaseFile = new File(plugin.getDataFolder(), "profiles.db");
            String jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(jdbcUrl);
        }

        plugin.getLogger().info("Database connection established successfully.");

        // This part is the same, since your initializeTables() method is already compatible
        initializeTables();
    }

    /**
     * Disconnects from the database.
     */
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not close database connection: " + e.getMessage());
            }
        }
    }

    /**
     * Creates the database tables if they do not already exist.
     */
    private void initializeTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // pc_profiles table
            statement.execute("CREATE TABLE IF NOT EXISTS pc_profiles (" +
                    "id VARCHAR(36) PRIMARY KEY," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    "profile_name VARCHAR(32) NOT NULL," +
                    "creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_used_date TIMESTAMP" +
                    ");");

            // pc_profile_data table
            statement.execute("CREATE TABLE IF NOT EXISTS pc_profile_data (" +
                    "profile_id VARCHAR(36) PRIMARY KEY," +
                    "inventory TEXT," +
                    "armor TEXT," +
                    "location TEXT," +
                    "health DOUBLE," +
                    "food_level INTEGER," +
                    "experience TEXT," +
                    "gamemode VARCHAR(16)," +
                    "potion_effects TEXT," +
                    "FOREIGN KEY(profile_id) REFERENCES pc_profiles(id) ON DELETE CASCADE" +
                    ");");

            // pc_custom_data table
            statement.execute("CREATE TABLE IF NOT EXISTS pc_custom_data (" +
                    "profile_id VARCHAR(36) NOT NULL," +
                    "namespace VARCHAR(64) NOT NULL," +
                    "data_key VARCHAR(64) NOT NULL," +
                    "data_value TEXT," +
                    "PRIMARY KEY (profile_id, namespace, data_key)," +
                    "FOREIGN KEY(profile_id) REFERENCES pc_profiles(id) ON DELETE CASCADE" +
                    ");");

            statement.execute("CREATE TABLE IF NOT EXISTS pc_players (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "active_profile_id VARCHAR(36)" +
                    ");");

            plugin.getLogger().info("Database tables initialized successfully.");
        }
    }

    /**
     * Asynchronously creates a new, empty character profile for a player.
     * @param owner The player creating the profile.
     * @param profileName The name for the new profile.
     * @return A CompletableFuture that will complete with the new CharacterProfile, or null if it failed.
     */
    public CompletableFuture<Profile> createProfile(Player owner, String profileName) {
        return CompletableFuture.supplyAsync(() -> {
            UUID profileId = UUID.randomUUID();
            Timestamp now = new Timestamp(System.currentTimeMillis());
            UUID ownerUuid = plugin.getIdentityManager().getOriginalIdentity(owner).uuid();

            // The original blocking code is now safely inside the async task
            try (PreparedStatement ps = connection.prepareStatement(CREATE_PROFILE)) {
                ps.setString(1, profileId.toString());
                ps.setString(2, ownerUuid.toString());
                ps.setString(3, profileName);
                ps.setTimestamp(4, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create new profile in pc_profiles", e);
                return null;
            }

            try (PreparedStatement ps = connection.prepareStatement(CREATE_PROFILE_DATA)) {
                ps.setString(1, profileId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create new profile data in pc_profile_data", e);
                return null;
            }

            ProfileImpl profile = new ProfileImpl(profileId, owner.getUniqueId(), profileName);
            profile.setLastUsedDate(now);
            // We should also save the player's current state to this new profile by default
            return profile;
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    /**
     * Asynchronously saves all data for a given CharacterProfile.
     * @param profile The profile to save.
     * @return A CompletableFuture that completes when the save is done.
     */
    public CompletableFuture<Void> saveProfile(Profile profile) {
        return CompletableFuture.runAsync(() -> {
            if (!(profile instanceof ProfileImpl p)) return;

            try {
                // Save to pc_profiles
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_PROFILE)) {
                    ps.setString(1, p.getProfileName());
                    ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                    ps.setString(3, p.getProfileId().toString());
                    ps.executeUpdate();
                }

                // Save to pc_profile_data
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_PROFILE_DATA)) {
                    ItemStack[] inventoryContents = p.getInventoryContents();
                    String inventoryData = Base64ItemSerializer.serialize(inventoryContents);
                    ps.setString(1, inventoryData);

                    String armorData = Base64ItemSerializer.serialize(p.getArmorContents());
                    ps.setString(2, armorData);

                    Location location = p.getLocation();

                    String locationJson = null;
                    try {
                        if (location != null) {
                            // Create a simple map with just the essential location data
                            Map<String, Object> locationMap = new HashMap<>();
                            locationMap.put("world", location.getWorld() != null ? location.getWorld().getName() : null);
                            locationMap.put("x", location.getX());
                            locationMap.put("y", location.getY());
                            locationMap.put("z", location.getZ());
                            locationMap.put("yaw", location.getYaw());
                            locationMap.put("pitch", location.getPitch());
                            locationJson = gson.toJson(locationMap);
                        } else {
                            locationJson = "null";
                        }
                    } catch (Exception e) {
                        locationJson = "null";
                    }
                    ps.setString(3, locationJson);

                    ps.setDouble(4, p.getHealth());

                    ps.setInt(5, p.getFoodLevel());

                    ps.setInt(6, p.getTotalExperience());

                    String gamemode = p.getGameMode() != null ? p.getGameMode().name() : GameMode.SURVIVAL.name();
                    ps.setString(7, gamemode);

                    String potionsJson = p.getPotionEffects() != null ? gson.toJson(p.getPotionEffects(), potionEffectCollectionType) : "[]";
                    ps.setString(8, potionsJson);

                    ps.setString(9, p.getProfileId().toString());
                    int rowsAffected = ps.executeUpdate();
                }

                // Here you would also save the custom data from the API to pc_custom_data
                // (This involves iterating the custom data map and running INSERT/UPDATE)

                try (PreparedStatement ps = connection.prepareStatement(DELETE_CUSTOM_DATA)) {
                    ps.setString(1, p.getProfileId().toString());
                    ps.executeUpdate();
                }

                // Next, insert all the current custom data.
                try (PreparedStatement ps = connection.prepareStatement(INSERT_CUSTOM_DATA)) {
                    for (Map.Entry<String, Map<String, Object>> pluginEntry : p.getCustomDataMap().entrySet()) {
                        String namespace = pluginEntry.getKey();
                        for (Map.Entry<String, Object> dataEntry : pluginEntry.getValue().entrySet()) {
                            ps.setString(1, p.getProfileId().toString());
                            ps.setString(2, namespace);
                            ps.setString(3, dataEntry.getKey());
                            ps.setString(4, gson.toJson(dataEntry.getValue())); // Serialize the value to JSON
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch(); // Execute all inserts at once for efficiency
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save profile " + p.getProfileId(), e);
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }


    /**
     * Asynchronously loads a single, complete character profile from the database.
     * @param profileId The UUID of the profile to load.
     * @return A CompletableFuture that will complete with an Optional containing the profile.
     */
    public CompletableFuture<Optional<Profile>> loadProfile(UUID profileId) {
        return CompletableFuture.supplyAsync(() -> {
            ProfileImpl profile = null;

            try (PreparedStatement ps = connection.prepareStatement(GET_PROFILE_BY_ID)) {
                ps.setString(1, profileId.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    UUID ownerId = UUID.fromString(rs.getString("owner_uuid"));
                    String name = rs.getString("profile_name");
                    profile = new ProfileImpl(profileId, ownerId, name);
                    profile.setCreationDate(rs.getTimestamp("creation_date"));
                    profile.setLastUsedDate(rs.getTimestamp("last_used_date"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load profile " + profileId, e);
                return Optional.empty();
            }

            if (profile == null) {
                return Optional.empty();
            }

            try (PreparedStatement ps = connection.prepareStatement(GET_PROFILE_DATA_BY_ID)) {
                ps.setString(1, profileId.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    profile.setInventoryContents(Base64ItemSerializer.deserialize(rs.getString("inventory")));
                    profile.setArmorContents(Base64ItemSerializer.deserialize(rs.getString("armor")));
                    String locationJson = rs.getString("location");
                    if (locationJson != null && !locationJson.equals("null")) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> locationMap = gson.fromJson(locationJson, Map.class);
                            String worldName = (String) locationMap.get("world");
                            if (worldName != null) {
                                World world = Bukkit.getWorld(worldName);
                                if (world != null) {
                                    double x = ((Number) locationMap.get("x")).doubleValue();
                                    double y = ((Number) locationMap.get("y")).doubleValue();
                                    double z = ((Number) locationMap.get("z")).doubleValue();
                                    float yaw = ((Number) locationMap.get("yaw")).floatValue();
                                    float pitch = ((Number) locationMap.get("pitch")).floatValue();
                                    Location location = new Location(world, x, y, z, yaw, pitch);
                                    profile.setLocation(location);
                                } else {
                                    plugin.getLogger().warning("World '" + worldName + "' not found when loading profile location");
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to deserialize location for profile " + profileId, e);
                        }
                    }
                    profile.setHealth(rs.getDouble("health"));
                    profile.setFoodLevel(rs.getInt("food_level"));
                    profile.setTotalExperience(rs.getInt("experience"));
                    String gamemodeName = rs.getString("gamemode");
                    if (gamemodeName != null) {
                        profile.setGameMode(GameMode.valueOf(gamemodeName));
                    }

                    String potionsJson = rs.getString("potion_effects");
                    if (potionsJson != null) {
                        profile.setPotionEffects(gson.fromJson(potionsJson, potionEffectCollectionType));
                    }
                }
            } catch (SQLException | NullPointerException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load profile data for " + profileId, e);
                // If data is corrupt or missing, we can't safely load the profile.
                return Optional.empty();
            }

            try (PreparedStatement ps = connection.prepareStatement(GET_CUSTOM_DATA)) {
                ps.setString(1, profileId.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String namespace = rs.getString("namespace");
                    String dataKey = rs.getString("data_key");
                    String dataValueJson = rs.getString("data_value");

                    // Deserialize the JSON back into a generic Object
                    Object dataValue = gson.fromJson(dataValueJson, Object.class);

                    // Add the loaded data directly to the profile's map
                    profile.getCustomDataMap()
                            .computeIfAbsent(namespace, k -> new ConcurrentHashMap<>())
                            .put(dataKey, dataValue);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load custom data for profile " + profileId, e);
                return Optional.empty();
            }

            return Optional.of(profile);
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));


    }

    /**
     * Asynchronously retrieves all character profiles owned by a specific player.
     * Note: This method only loads the core info, not the full player data, for efficiency.
     * @param ownerUuid The UUID of the player.
     * @return A CompletableFuture that will complete with a collection of character profiles.
     */
    public CompletableFuture<Collection<Profile>> getProfilesForPlayer(UUID ownerUuid) {
        // supplyAsync runs the code inside it on a separate thread pool.
        return CompletableFuture.supplyAsync(() -> {
            Collection<Profile> profiles = new ArrayList<>();
            // The original blocking code is now safely inside the async task
            try (PreparedStatement ps = connection.prepareStatement(GET_PROFILES_BY_OWNER)) {
                ps.setString(1, ownerUuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID profileId = UUID.fromString(rs.getString("id"));
                    String name = rs.getString("profile_name");
                    ProfileImpl profile = new ProfileImpl(profileId, ownerUuid, name);
                    profile.setLastUsedDate(rs.getTimestamp("last_used_date"));
                    profiles.add(profile);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load profiles for player " + ownerUuid, e);
            }
            return profiles;
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable)); // Use Bukkit's async scheduler
    }

    public CompletableFuture<Collection<Profile>> getProfilesForPlayer(Player player) {
        return getProfilesForPlayer(plugin.getIdentityManager().getOriginalIdentity(player).uuid());
    }


    /**
     * Deletes a character profile and all associated data from the database.
     * @param profileId The UUID of the profile to delete.
     */
    public void deleteProfile(UUID profileId) {
        try (PreparedStatement ps = connection.prepareStatement(DELETE_PROFILE)) {
            ps.setString(1, profileId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete profile " + profileId, e);
        }
    }

    /**
     * Asynchronously gets the UUID of the last active profile for a given player.
     * @param playerUuid The player's UUID.
     * @return A CompletableFuture that will complete with an Optional containing the profile UUID.
     */
    public CompletableFuture<Optional<UUID>> getPlayerActiveProfileId(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT active_profile_id FROM pc_players WHERE player_uuid = ?;")) {
                ps.setString(1, playerUuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String profileId = rs.getString("active_profile_id");
                    return profileId != null ? Optional.of(UUID.fromString(profileId)) : Optional.empty();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not get active profile for player " + playerUuid, e);
            }
            return Optional.empty();
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<Optional<UUID>> getPlayerActiveProfileId(Player player) {
        return getPlayerActiveProfileId(plugin.getIdentityManager().getOriginalIdentity(player).uuid());
    }

    /**
     * Sets the active profile for a player. Use null to clear it.
     * @param playerUuid The player's UUID.
     * @param profileId The profile's UUID, or null.
     */
    public void setPlayerActiveProfile(UUID playerUuid, UUID profileId) {
        // This is an "UPSERT" command (UPDATE or INSERT)
        String sql = "INSERT INTO pc_players (player_uuid, active_profile_id) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE active_profile_id = VALUES(active_profile_id);";
        // SQLite has a different syntax for UPSERT
        if (ConfigManager.getDatabaseType().equals("SQLITE")) {
            sql = "INSERT INTO pc_players (player_uuid, active_profile_id) VALUES (?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET active_profile_id = excluded.active_profile_id;";
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, profileId != null ? profileId.toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not set active profile for player " + playerUuid, e);
        }
    }
}
