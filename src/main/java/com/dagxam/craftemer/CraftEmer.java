package com.dagxam.craftemer;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class CraftEmer extends JavaPlugin {
    private NamespacedKey itemKey;

    @Override
    public void onEnable() {
        itemKey = new NamespacedKey(this, "emerald_item");
        registerItems();
        getLogger().info("CraftEmer enabled: emerald equipment is between gold and diamond.");
    }

    private void registerItems() {
        registerTool("emerald_pickaxe", "Изумрудная кирка", 1000, 9.0f, Tag.MINEABLE_PICKAXE,
                5.0, -2.8, new String[]{"EEE", " S ", " S "});
        registerTool("emerald_axe", "Изумрудный топор", 1000, 9.0f, Tag.MINEABLE_AXE,
                7.0, -3.0, new String[]{"EE ", "ES ", " S "});
        registerTool("emerald_shovel", "Изумрудная лопата", 1000, 9.0f, Tag.MINEABLE_SHOVEL,
                3.0, -3.0, new String[]{" E ", " S ", " S "});
        registerTool("emerald_hoe", "Изумрудная мотыга", 1000, 9.0f, Tag.MINEABLE_HOE,
                0.0, -2.5, new String[]{"EE ", " S ", " S "});
        registerWeapon("emerald_sword", "Изумрудный меч", 1000, 5.0, -2.4,
                new String[]{" E ", " E ", " S "});
    }

    private void registerTool(String id, String name, int durability, float miningSpeed,
                              Tag<Material> mineableTag, double damage, double attackSpeed, String[] shape) {
        ItemStack item = createItem(id, name, durability, damage, attackSpeed, miningSpeed, mineableTag);
        addRecipe(id, item, shape);
    }

    private void registerWeapon(String id, String name, int durability,
                                double damage, double attackSpeed, String[] shape) {
        ItemStack item = createItem(id, name, durability, damage, attackSpeed, null, null);
        addRecipe(id, item, shape);
    }

    private void addRecipe(String id, ItemStack item, String[] shape) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, id), item);
        recipe.shape(shape);
        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('S', Material.STICK);
        getServer().addRecipe(recipe);
    }

    private ItemStack createItem(String id, String name, int durability,
                                 double damage, double attackSpeed, Float miningSpeed,
                                 Tag<Material> mineableTag) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a" + name);
        meta.setLore(List.of(
                "§7Изумрудный материал",
                "§8Сильнее золота • слабее алмаза"
        ));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setMaxDamage(durability);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        AttributeModifier damageModifier = new AttributeModifier(
                UUID.randomUUID(), "craftemer_damage", damage,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

        AttributeModifier speedModifier = new AttributeModifier(
                UUID.randomUUID(), "craftemer_attack_speed", attackSpeed,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

        if (miningSpeed != null && mineableTag != null) {
            ToolComponent tool = meta.getTool();
            tool.setDefaultMiningSpeed(miningSpeed);
            tool.setDamagePerBlock(1);
            tool.addRule(mineableTag, miningSpeed, true);
            meta.setTool(tool);
        }

        item.setItemMeta(meta);
        return item;
    }
}
