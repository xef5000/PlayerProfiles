package ca.xef5000.playerprofiles.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Deprecated
public class YamlItemSerializer {

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Optional.class, new OptionalTypeAdapter())
            .registerTypeAdapter(Class.class, new ClassTypeAdapter())
            .serializeNulls()
            .create();
    private static final Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();

    /**
     * Serializes an array of ItemStacks into a JSON string.
     *
     * @param items The array of items to serialize.
     * @return A JSON string representing the items.
     */
    public static String serialize(ItemStack[] items) {
        List<Map<String, Object>> serializedItems = new ArrayList<>();
        if (items == null) {
            return gson.toJson(serializedItems);
        }

        for (ItemStack item : items) {
            if (item != null) {
                try {
                    // Use Bukkit's serialization but handle it more safely
                    Map<String, Object> serializedItem = item.serialize();
                    // Clean the map to remove any problematic objects
                    Map<String, Object> cleanedItem = cleanSerializedMap(serializedItem);
                    serializedItems.add(cleanedItem);
                } catch (Exception e) {
                    // If serialization fails for a specific item, add null to maintain slot positions
                    System.err.println("Failed to serialize item: " + e.getMessage());
                    serializedItems.add(null);
                }
            } else {
                // Add a null placeholder so inventory slot positions are maintained.
                serializedItems.add(null);
            }
        }
        return gson.toJson(serializedItems, listType);
    }

    /**
     * Cleans a serialized map by removing or converting problematic objects
     */
    private static Map<String, Object> cleanSerializedMap(Map<String, Object> original) {
        Map<String, Object> cleaned = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Optional) {
                // Convert Optional to its contained value or null
                Optional<?> opt = (Optional<?>) value;
                cleaned.put(entry.getKey(), opt.orElse(null));
            } else if (value instanceof Class) {
                // Convert Class objects to their name string
                Class<?> clazz = (Class<?>) value;
                cleaned.put(entry.getKey(), clazz.getName());
            } else if (value instanceof Map) {
                // Recursively clean nested maps
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                cleaned.put(entry.getKey(), cleanSerializedMap(nestedMap));
            } else if (value instanceof List) {
                // Clean lists that might contain problematic objects
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                cleaned.put(entry.getKey(), cleanList(list));
            } else {
                cleaned.put(entry.getKey(), value);
            }
        }
        return cleaned;
    }

    /**
     * Cleans a list by converting problematic objects
     */
    private static List<Object> cleanList(List<Object> original) {
        List<Object> cleaned = new ArrayList<>();
        for (Object item : original) {
            if (item instanceof Optional) {
                Optional<?> opt = (Optional<?>) item;
                cleaned.add(opt.orElse(null));
            } else if (item instanceof Class) {
                Class<?> clazz = (Class<?>) item;
                cleaned.add(clazz.getName());
            } else if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                cleaned.add(cleanSerializedMap(map));
            } else if (item instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) item;
                cleaned.add(cleanList(list));
            } else {
                cleaned.add(item);
            }
        }
        return cleaned;
    }

    /**
     * Deserializes a JSON string back into an array of ItemStacks.
     *
     * @param json The JSON string to deserialize.
     * @return An array of ItemStacks.
     */
    public static ItemStack[] deserialize(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return new ItemStack[0]; // Return an empty array, not null
        }

        try {
            List<Map<String, Object>> serializedItems = gson.fromJson(json, listType);
            ItemStack[] items = new ItemStack[serializedItems.size()];

            for (int i = 0; i < serializedItems.size(); i++) {
                Map<String, Object> itemMap = serializedItems.get(i);
                if (itemMap != null) {
                    try {
                        // This is the other half of the magic: Bukkit's deserialization.
                        items[i] = ItemStack.deserialize(itemMap);
                    } catch (Exception e) {
                        System.err.println("Failed to deserialize item at index " + i + ": " + e.getMessage());
                        items[i] = null;
                    }
                } else {
                    items[i] = null;
                }
            }
            return items;
        } catch (Exception e) {
            System.err.println("Failed to deserialize inventory: " + e.getMessage());
            return new ItemStack[0];
        }
    }
}
