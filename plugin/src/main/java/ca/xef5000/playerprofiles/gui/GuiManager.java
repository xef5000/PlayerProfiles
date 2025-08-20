package ca.xef5000.playerprofiles.gui;

import ca.xef5000.playerprofiles.PlayerProfiles;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Manages all GUI instances and handles inventory events.
 * Uses WeakHashMap to automatically clean up GUI references when players disconnect.
 */
public class GuiManager implements Listener {
    
    private final PlayerProfiles plugin;
    private final Map<UUID, Gui> openGuis;
    
    public GuiManager(PlayerProfiles plugin) {
        this.plugin = plugin;
        this.openGuis = new WeakHashMap<>();
    }
    
    /**
     * Registers a GUI for a player.
     * @param player The player who has the GUI open
     * @param gui The GUI instance
     */
    public void registerGui(Player player, Gui gui) {
        openGuis.put(player.getUniqueId(), gui);
    }
    
    /**
     * Unregisters a GUI for a player.
     * @param player The player whose GUI should be unregistered
     */
    public void unregisterGui(Player player) {
        openGuis.remove(player.getUniqueId());
    }
    
    /**
     * Gets the currently open GUI for a player.
     * @param player The player to check
     * @return The open GUI, or null if no GUI is open
     */
    public Gui getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }
    
    /**
     * Checks if a player has a GUI open.
     * @param player The player to check
     * @return true if the player has a GUI open, false otherwise
     */
    public boolean hasGuiOpen(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }
    
    /**
     * Closes the GUI for a player if they have one open.
     * @param player The player whose GUI should be closed
     */
    public void closeGui(Player player) {
        Gui gui = getOpenGui(player);
        if (gui != null) {
            gui.close();
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        Gui gui = getOpenGui(player);
        
        if (gui == null) {
            return; // Player doesn't have a GUI open
        }
        
        // Check if the clicked inventory belongs to our GUI
        if (!event.getInventory().equals(gui.getInventory())) {
            return; // Player clicked in a different inventory
        }
        
        // Cancel the event to prevent item movement
        event.setCancelled(true);
        
        int slot = event.getSlot();
        Button button = gui.getButton(slot);
        
        if (button != null && button.isClickable()) {
            try {
                button.onClick(player, event.getClick());
            } catch (Exception e) {
                plugin.getLogger().severe("Error handling button click in GUI for player " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        Gui gui = getOpenGui(player);
        
        if (gui == null) {
            return; // Player doesn't have a GUI open
        }
        
        // Check if the closed inventory belongs to our GUI
        if (!event.getInventory().equals(gui.getInventory())) {
            return; // Player closed a different inventory
        }
        
        try {
            gui.onClose();
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling GUI close for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always unregister the GUI, even if onClose() throws an exception
            unregisterGui(player);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up any GUI references when a player quits
        // The WeakHashMap should handle this automatically, but this ensures immediate cleanup
        unregisterGui(event.getPlayer());
    }
    
    /**
     * Closes all open GUIs. This should be called when the plugin is disabled.
     */
    public void closeAllGuis() {
        for (Gui gui : openGuis.values()) {
            try {
                gui.getPlayer().closeInventory();
            } catch (Exception e) {
                plugin.getLogger().warning("Error closing GUI during shutdown: " + e.getMessage());
            }
        }
        openGuis.clear();
    }
}
