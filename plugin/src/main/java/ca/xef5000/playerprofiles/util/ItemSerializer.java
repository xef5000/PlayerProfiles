package ca.xef5000.playerprofiles.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Deprecated
public class ItemSerializer {

    /**
     * Serializes an array of ItemStacks into a Base64 string.
     *
     * @param items The array of items to serialize.
     * @return A Base64 string representing the items.
     * @throws IOException If an IO error occurs during serialization.
     */
    public static String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }

    }

    /**
     * Deserializes a Base64 string back into an array of ItemStacks.
     *
     * @param base64 The Base64 string to deserialize.
     * @return An array of ItemStacks.
     * @throws IOException            If an IO error occurs during deserialization.
     * @throws ClassNotFoundException If the class of a serialized object cannot be found.
     */
    public static ItemStack[] base64ToItemStackArray(String base64) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(base64);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        }
    }
}
