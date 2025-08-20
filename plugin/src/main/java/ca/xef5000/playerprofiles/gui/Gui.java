package ca.xef5000.playerprofiles.gui;

import ca.xef5000.playerprofiles.PlayerProfiles;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for all GUI implementations.
 * Provides common functionality for managing inventory-based GUIs.
 */
public abstract class Gui {
    
    protected final PlayerProfiles plugin;
    protected final Player player;
    protected final String title;
    protected final int size;
    protected final Inventory inventory;
    protected final Map<Integer, Button> buttons;
    
    /**
     * Creates a new GUI with the specified parameters.
     * @param plugin The plugin instance
     * @param player The player who will view this GUI
     * @param title The title of the inventory
     * @param size The size of the inventory (must be a multiple of 9)
     */
    public Gui(PlayerProfiles plugin, Player player, String title, int size) {
        this.plugin = plugin;
        this.player = player;
        this.title = title;
        this.size = size;
        this.inventory = Bukkit.createInventory(null, size, title);
        this.buttons = new HashMap<>();
    }
    
    /**
     * Sets a button at the specified slot.
     * @param slot The slot to place the button in (0-based)
     * @param button The button to place
     */
    public void setButton(int slot, Button button) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("Slot " + slot + " is out of bounds for inventory size " + size);
        }
        
        buttons.put(slot, button);
        inventory.setItem(slot, button.getItemStack());
    }
    
    /**
     * Removes a button from the specified slot.
     * @param slot The slot to clear
     */
    public void removeButton(int slot) {
        buttons.remove(slot);
        inventory.setItem(slot, null);
    }
    
    /**
     * Gets the button at the specified slot.
     * @param slot The slot to check
     * @return The button at that slot, or null if no button exists
     */
    public Button getButton(int slot) {
        return buttons.get(slot);
    }
    
    /**
     * Sets an item at the specified slot without making it clickable.
     * This is useful for decorative items.
     * @param slot The slot to place the item in
     * @param item The item to place
     */
    public void setItem(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }
    
    /**
     * Fills empty slots with the specified item.
     * @param item The item to use as filler
     */
    public void fillEmpty(ItemStack item) {
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, item);
            }
        }
    }
    
    /**
     * Opens this GUI for the player.
     */
    public void open() {
        // Build the GUI content before opening
        build();
        
        // Register this GUI with the manager
        plugin.getGuiManager().registerGui(player, this);
        
        // Open the inventory
        player.openInventory(inventory);
    }
    
    /**
     * Closes this GUI for the player.
     */
    public void close() {
        player.closeInventory();
        plugin.getGuiManager().unregisterGui(player);
    }
    
    /**
     * Refreshes the GUI by rebuilding its content.
     */
    public void refresh() {
        buttons.clear();
        inventory.clear();
        build();
    }
    
    /**
     * Abstract method that subclasses must implement to build the GUI content.
     * This method is called when the GUI is opened or refreshed.
     */
    protected abstract void build();
    
    /**
     * Called when the GUI is closed by the player.
     * Subclasses can override this to perform cleanup or handle close events.
     */
    public void onClose() {
        // Default implementation does nothing
    }
    
    /**
     * Gets the player who owns this GUI.
     * @return The player
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * Gets the inventory associated with this GUI.
     * @return The inventory
     */
    public Inventory getInventory() {
        return inventory;
    }
    
    /**
     * Gets the title of this GUI.
     * @return The title
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Gets the size of this GUI.
     * @return The size
     */
    public int getSize() {
        return size;
    }
}
