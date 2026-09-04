package com.dagxam.craftemer;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class CraftEmer extends JavaPlugin {
    private static final int DURABILITY = 1000;
    private static final float MINING_SPEED = 9.0f;
    private static final int ENCHANTABILITY = 16;

    private NamespacedKey itemKey;

    @Override
    public void onEnable() {
        itemKey = new NamespacedKey(this, "emerald_item");
        registerItems();
        getLogger().info("CraftEmer enabled: emerald equipment is between gold and diamond.");
    }

    private void registerItems() {
        registerTool("emerald_pickaxe", "Изумрудная кирка", 5.0, -2.8,
                Tag.MINEABLE_PICKAXE, new String[]{"EEE", " S ", " S "});
        registerTool("emerald_axe", "Изумрудный топор", 7.0, -3.0,
                Tag.MINEABLE_AXE, new String[]{"EE ", "ES ", " S "});
        registerTool("emerald_shovel", "Изумрудная лопата", 3.0, -3.0,
                Tag.MINEABLE_SHOVEL, new String[]{" E ", " S ", " S "});
        registerTool("emerald_hoe", "Изумрудная мотыга", 0.0, -2.5,
                Tag.MINEABLE_HOE, new String[]{"EE ", " S ", " S "});
        registerWeapon("emerald_sword", "Изумрудный меч", 5.0, -2.4,
                new String[]{" E ", " E ", " S "});
    }

    private void registerTool(String id, String name, double damage, double attackSpeed,
                              Tag<Material> mineableTag, String[] shape) {
        ItemStack item = createItem(id, name, damage, attackSpeed, mineableTag);
        addRecipe(id, item, shape);
    }

    private void registerWeapon(String id, String name, double damage, double attackSpeed,
                                String[] shape) {
        ItemStack item = createItem(id, name, damage, attackSpeed, null);
        addRecipe(id, item, shape);
    }

    private void addRecipe(String id, ItemStack item, String[] shape) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, id), item);
        recipe.shape(shape);
        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('S', Material.STICK);
        getServer().addRecipe(recipe);
    }

    private ItemStack createItem(String id, String name, double damage, double attackSpeed,
                                 Tag<Material> mineableTag) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a" + name);
        meta.setLore(List.of(
                "§7Изумрудный материал",
                "§8Сильнее золота • слабее алмаза"
        ));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);

        // Emerald equipment is a single, damageable item rather than a stack of emeralds.
        meta.setMaxStackSize(1);
        meta.setMaxDamage(DURABILITY);
        meta.setEnchantable(ENCHANTABILITY);

        AttributeModifier damageModifier = new AttributeModifier(
                UUID.randomUUID(), "craftemer_damage", damage,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

        AttributeModifier speedModifier = new AttributeModifier(
                UUID.randomUUID(), "craftemer_attack_speed", attackSpeed,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

        if (mineableTag != null) {
            ToolComponent tool = meta.getTool();
            tool.setDefaultMiningSpeed(MINING_SPEED);
            tool.setDamagePerBlock(1);
            tool.addRule(mineableTag, MINING_SPEED, true);
            meta.setTool(tool);
        }

        item.setItemMeta(meta);
        return item;
    }
}
