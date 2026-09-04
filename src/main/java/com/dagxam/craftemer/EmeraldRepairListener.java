package com.dagxam.craftemer;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class EmeraldRepairListener implements Listener {
    private final NamespacedKey itemKey;
    private final int repairAmount;

    public EmeraldRepairListener(NamespacedKey itemKey, int repairAmount) {
        this.itemKey = itemKey;
        this.repairAmount = repairAmount;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack left = inventory.getItem(0);
        ItemStack right = inventory.getItem(1);

        if (!isEmeraldTool(left) || right == null || right.getType() != Material.EMERALD) {
            return;
        }

        ItemMeta meta = left.getItemMeta();
        if (meta == null || !meta.hasMaxDamage()) {
            return;
        }

        int maxDamage = meta.getMaxDamage();
        int currentDamage = meta.getDamage();
        if (currentDamage <= 0) {
            event.setResult(null);
            return;
        }

        int emeraldsUsed = Math.min(right.getAmount(), (int) Math.ceil((double) currentDamage / repairAmount));
        int repairedDamage = Math.max(0, currentDamage - emeraldsUsed * repairAmount);

        ItemStack result = left.clone();
        ItemMeta resultMeta = result.getItemMeta();
        resultMeta.setDamage(Math.min(repairedDamage, maxDamage));
        result.setItemMeta(resultMeta);

        event.setResult(result);
        event.getInventory().setRepairCost(Math.max(1, emeraldsUsed));
    }

    private boolean isEmeraldTool(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return id != null && id.startsWith("emerald_");
    }
}
