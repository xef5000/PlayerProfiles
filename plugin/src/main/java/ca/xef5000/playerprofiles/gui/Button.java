package ca.xef5000.playerprofiles.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a clickable button in a GUI.
 * Encapsulates both the visual representation (ItemStack) and the click behavior.
 */
public class Button {
    
    private final ItemStack itemStack;
    private final ButtonClickHandler clickHandler;
    
    /**
     * Creates a new button with the specified item and click handler.
     * @param itemStack The ItemStack to display in the inventory
     * @param clickHandler The handler to execute when the button is clicked
     */
    public Button(ItemStack itemStack, ButtonClickHandler clickHandler) {
        this.itemStack = itemStack;
        this.clickHandler = clickHandler;
    }
    
    /**
     * Creates a new button with the specified item and no click handler.
     * This is useful for decorative items that shouldn't be clickable.
     * @param itemStack The ItemStack to display in the inventory
     */
    public Button(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.clickHandler = null;
    }
    
    /**
     * Gets the ItemStack that represents this button visually.
     * @return The ItemStack to display
     */
    public ItemStack getItemStack() {
        return itemStack;
    }
    
    /**
     * Executes the click handler for this button.
     * @param player The player who clicked the button
     * @param clickType The type of click that was performed
     */
    public void onClick(Player player, ClickType clickType) {
        if (clickHandler != null) {
            clickHandler.onClick(player, clickType);
        }
    }
    
    /**
     * Checks if this button has a click handler.
     * @return true if the button is clickable, false otherwise
     */
    public boolean isClickable() {
        return clickHandler != null;
    }
    
    /**
     * Functional interface for handling button clicks.
     */
    @FunctionalInterface
    public interface ButtonClickHandler {
        /**
         * Called when the button is clicked.
         * @param player The player who clicked the button
         * @param clickType The type of click that was performed
         */
        void onClick(Player player, ClickType clickType);
    }
}
