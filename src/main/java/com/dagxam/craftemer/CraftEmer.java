package com.dagxam.craftemer;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CraftEmer extends JavaPlugin {
    private NamespacedKey itemKey;

    @Override
    public void onEnable() {
        itemKey = new NamespacedKey(this, "emerald_item");
        registerItems();
        getLogger().info("CraftEmer enabled: emerald tools and weapons registered.");
    }

    private void registerItems() {
        register("emerald_sword", "Emerald Sword", Material.EMERALD, 6.0, 1000, new String[]{" E ", " E ", " S "});
        register("emerald_pickaxe", "Emerald Pickaxe", Material.EMERALD, 4.0, 1000, new String[]{"EEE", " S ", " S "});
        register("emerald_axe", "Emerald Axe", Material.EMERALD, 8.0, 1000, new String[]{"EE ", "ES ", " S "});
        register("emerald_shovel", "Emerald Shovel", Material.EMERALD, 4.5, 1000, new String[]{" E ", " S ", " S "});
        register("emerald_hoe", "Emerald Hoe", Material.EMERALD, 1.0, 1000, new String[]{"EE ", " S ", " S "});
    }

    private void register(String id, String name, Material material, double damage, int durability, String[] shape) {
        ItemStack item = createItem(id, name, material, damage, durability);
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, id), item);
        recipe.shape(shape);
        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('S', Material.STICK);
        getServer().addRecipe(recipe);
    }

    private ItemStack createItem(String id, String name, Material material, double damage, int durability) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a" + name);
        meta.setLore(List.of("§7Изумрудный материал", "§8Между золотом и алмазом"));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setUnbreakable(false);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setMaxDamage(durability);

        AttributeModifier damageModifier = new AttributeModifier(
                UUID.randomUUID(), "craftemer_damage", damage, AttributeModifier.Operation.ADD_NUMBER,
                org.bukkit.inventory.EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);
        item.setItemMeta(meta);
        return item;
    }
}
