package com.dagxam.craftemer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CraftEmer extends JavaPlugin implements Listener {
    private static final int DURABILITY = 1000;
    private static final float MINING_SPEED = 9.0f;
    private static final int ENCHANTABILITY = 16;
    private static final String[] RESOURCE_FILES = {
            "pack.mcmeta",
            "assets/craftemer/items/emerald_sword.json",
            "assets/craftemer/items/emerald_pickaxe.json",
            "assets/craftemer/items/emerald_axe.json",
            "assets/craftemer/items/emerald_shovel.json",
            "assets/craftemer/items/emerald_hoe.json",
            "assets/craftemer/models/item/emerald_sword.json",
            "assets/craftemer/models/item/emerald_pickaxe.json",
            "assets/craftemer/models/item/emerald_axe.json",
            "assets/craftemer/models/item/emerald_shovel.json",
            "assets/craftemer/models/item/emerald_hoe.json",
            "assets/craftemer/textures/item/emerald_sword.png",
            "assets/craftemer/textures/item/emerald_pickaxe.png",
            "assets/craftemer/textures/item/emerald_axe.png",
            "assets/craftemer/textures/item/emerald_shovel.png",
            "assets/craftemer/textures/item/emerald_hoe.png"
    };

    private NamespacedKey itemKey;
    private byte[] resourcePack;
    private String resourcePackHash;
    private HttpServer resourcePackServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "emerald_item");
        registerItems();
        getServer().getPluginManager().registerEvents(this, this);
        prepareResourcePack();
        startResourcePackServer();
        getServer().getOnlinePlayers().forEach(this::sendResourcePack);
        getLogger().info("CraftEmer enabled: emerald equipment is between gold and diamond.");
    }

    @Override
    public void onDisable() {
        if (resourcePackServer != null) {
            resourcePackServer.stop(0);
            resourcePackServer = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sendResourcePack(event.getPlayer());
    }

    private void sendResourcePack(Player player) {
        if (!getConfig().getBoolean("resource-pack.enabled", true)
                || resourcePack == null
                || resourcePackHash == null) {
            return;
        }

        String url = getConfig().getString("resource-pack.url", "").trim();
        if (url.isEmpty()) {
            return;
        }

        boolean force = getConfig().getBoolean("resource-pack.required", false);
        player.setResourcePack(url, resourcePackHash, force);
    }

    private void prepareResourcePack() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String path : RESOURCE_FILES) {
                try (InputStream input = getResource(path)) {
                    if (input == null) {
                        throw new IOException("Missing embedded resource: " + path);
                    }
                    zip.putNextEntry(new ZipEntry(path));
                    input.transferTo(zip);
                    zip.closeEntry();
                }
            }
            zip.finish();
            resourcePack = output.toByteArray();
            resourcePackHash = sha1(resourcePack);
            getLogger().info("Embedded resource pack prepared: " + resourcePack.length + " bytes, SHA-1 " + resourcePackHash);
        } catch (Exception ex) {
            resourcePack = null;
            resourcePackHash = null;
            getLogger().severe("Could not prepare embedded resource pack: " + ex.getMessage());
        }
    }

    private void startResourcePackServer() {
        if (!getConfig().getBoolean("resource-pack.enabled", true) || resourcePack == null) {
            return;
        }

        int port = getConfig().getInt("resource-pack.port", 8123);
        try {
            resourcePackServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            resourcePackServer.createContext("/craftemer.zip", this::handleResourcePackRequest);
            resourcePackServer.setExecutor(null);
            resourcePackServer.start();
            getLogger().info("Embedded resource pack HTTP server started on port " + port + ".");
        } catch (IOException ex) {
            getLogger().severe("Could not start resource pack HTTP server on port " + port + ": " + ex.getMessage());
        }
    }

    private void handleResourcePackRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(resourcePack.length));
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        exchange.sendResponseHeaders(200, resourcePack.length);
        try {
            exchange.getResponseBody().write(resourcePack);
        } finally {
            exchange.close();
        }
    }

    private String sha1(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(data);
        StringBuilder result = new StringBuilder(40);
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
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
        meta.setItemModel(new NamespacedKey(this, id));
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
