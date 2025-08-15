package ca.xef5000.playerprofiles.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class Base64ItemSerializer {
    /**
     * Serializes an array of ItemStacks into a Base64 string.
     * This method properly preserves ALL item data including components, NBT, etc.
     *
     * @param items The array of items to serialize.
     * @return A Base64 string representing the items.
     */
    public static String serialize(ItemStack[] items) {
        if (items == null) {
            return "";
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the length of the inventory
            dataOutput.writeInt(items.length);

            // Write each ItemStack
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            System.err.println("Failed to serialize items: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Deserializes a Base64 string back into an array of ItemStacks.
     * This method properly restores ALL item data including components, NBT, etc.
     *
     * @param data The Base64 string to deserialize.
     * @return An array of ItemStacks.
     */
    public static ItemStack[] deserialize(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            // Read the length of the inventory
            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];

            // Read each ItemStack
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to deserialize items: " + e.getMessage());
            e.printStackTrace();
            return new ItemStack[0];
        }
    }
}
