package ca.xef5000.playerprofiles.api.utils;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public record PlayerState(
        Location location,
        GameMode gameMode,
        float health,
        int foodLevel,
        float saturation,
        int experience,
        float expToNext,
        ItemStack[] inventory,
        ItemStack[] armor,
        List<PotionEffect> effects
) {}
