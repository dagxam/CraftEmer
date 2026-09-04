package com.dagxam.craftemer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CraftEmer extends JavaPlugin implements Listener {
    private static final int DURABILITY = 1000;
    private static final float MINING_SPEED = 9.0f;
    private static final int ENMERCHANTABILITY = 16;
    private static final int ARMOR_DURABILITY = 400;
    private static final Color EMERALD_COLOR = Color.fromRGB(0x35C96B);
    private static final String[] RECIPE_IDS = {
            "emerald_pickaxe", "emerald_axe", "emerald_shovel", "emerald_hoe", "emerald_sword",
            "emerald_helmet", "emerald_chestplate", "emerald_leggings", "emerald_boots"
    };
    private static final String[] RESOURCE_FILES = {
            "pack.mcmeta",
            "assets/minecraft/atlases/items.json",
            "assets/craftemer/items/emerald_sword.json",
            "assets/craftemer/items/emerald_pickaxe.json",
            "assets/craftemer/items/emerald_axe.json",
            "assets/craftemer/items/emerald_shovel.json",
            "assets/craftemer/items/emerald_hoe.json",
            "assets/craftemer/items/emerald_helmet.json",
            "assets/craftemer/items/emerald_chestplate.json",
            "assets/craftemer/items/emerald_leggings.json",
            "assets/craftemer/items/emerald_boots.json",
            "assets/craftemer/models/item/emerald_sword.json",
            "assets/craftemer/models/item/emerald_pickaxe.json",
            "assets/craftemer/models/item/emerald_axe.json",
            "assets/craftemer/models/item/emerald_shovel.json",
            "assets/craftemer/models/item/emerald_hoe.json",
            "assets/craftemer/equipment/leather.json",
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
    private String resourcePackUrl;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "emerald_item");
        registerItems();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(
                new EmeraldRepairListener(itemKey, getConfig().getInt("repair.amount-per-emerald", 250)), this);
        prepareResourcePack();
        startResourcePackServer();
        resourcePackUrl = resolveResourcePackUrl();
        if (resourcePackUrl != null) {
            getLogger().info("Resource pack automatic URL: " + resourcePackUrl);
        }
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
        Player player = event.getPlayer();
        for (String id : RECIPE_IDS) {
            player.discoverRecipe(new NamespacedKey(this, id));
        }
        sendResourcePack(player);
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                sendResourcePack(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onArmorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (!isEmeraldArmor(held)) {
            return;
        }
        EquipmentSlot armorSlot = getArmorSlot(held);
        if (armorSlot == null || event.getHand() == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack currentlyEquipped = player.getInventory().getItem(armorSlot);
        if (currentlyEquipped == null || currentlyEquipped.getType().isAir()) {
            player.getInventory().setItem(armorSlot, held.clone());
            setHandItem(player, event.getHand(), new ItemStack(Material.AIR));
        } else {
            player.getInventory().setItem(armorSlot, held.clone());
            setHandItem(player, event.getHand(), currentlyEquipped.clone());
        }

        event.setUseItemInHand(Result.DENY);
        event.setUseInteractedBlock(Result.DENY);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (!isCraftEmerItem(result)) {
            return;
        }
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (!hasExpectedIngredients(result, matrix)) {
            event.getInventory().setResult(null);
        }
    }

    private boolean hasExpectedIngredients(ItemStack result, ItemStack[] matrix) {
        String id = getId(result);
        if (id == null) {
            return false;
        }
        int emeralds = 0;
        int sticks = 0;
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (stack.getType() == Material.EMERALD) {
                emeralds += stack.getAmount();
            } else if (stack.getType() == Material.STICK) {
                sticks += stack.getAmount();
            }
        }
        return switch (id) {
            case "emerald_helmet", "emerald_chestplate", "emerald_leggings", "emerald_boots" -> emeralds >= 4;
            case "emerald_sword", "emerald_pickaxe", "emerald_axe", "emerald_shovel", "emerald_hoe" -> emeralds >= 1 && sticks >= 1;
            default -> false;
        };
    }

    private boolean isCraftEmerItem(ItemStack item) {
        return getId(item) != null;
    }

    private boolean isEmeraldArmor(ItemStack item) {
        String id = getId(item);
        return id != null && getArmorSlot(item) != null;
    }

    private String getId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private EquipmentSlot getArmorSlot(ItemStack item) {
        String id = getId(item);
        if ("emerald_helmet".equals(id)) return EquipmentSlot.HEAD;
        if ("emerald_chestplate".equals(id)) return EquipmentSlot.CHEST;
        if ("emerald_leggings".equals(id)) return EquipmentSlot.LEGS;
        if ("emerald_boots".equals(id)) return EquipmentSlot.FEET;
        return null;
    }

    private void setHandItem(Player player, EquipmentSlot hand, ItemStack item) {
        player.getInventory().setItem(hand, item);
    }

    private void sendResourcePack(Player player) {
        if (!getConfig().getBoolean("resource-pack.enabled", true)
                || resourcePack == null || resourcePackHash == null) {
            return;
        }
        String configuredUrl = getConfig().getString("resource-pack.url", "").trim();
        String url = configuredUrl.isEmpty() || configuredUrl.equalsIgnoreCase("auto")
                ? resourcePackUrl : configuredUrl;
        if (url == null || url.isEmpty()) {
            getLogger().warning("Could not determine a reachable resource-pack URL automatically. Set resource-pack.url manually.");
            return;
        }
        boolean force = getConfig().getBoolean("resource-pack.required", true);
        player.setResourcePack(url, resourcePackHash, force);
    }

    private String resolveResourcePackUrl() {
        int port = getConfig().getInt("resource-pack.port", 8123);
        String host = getConfig().getString("resource-pack.host", "").trim();
        if (host.isEmpty()) {
            host = getServer().getIp().trim();
        }
        if (host.isEmpty() || host.equals("0.0.0.0") || host.equals("::")) {
            host = "127.0.0.1";
            getLogger().warning("server-ip is empty or wildcard. Using 127.0.0.1 for automatic resource-pack URL. Remote players need resource-pack.host or resource-pack.url configured to a publicly reachable hostname/IP.");
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        try {
            URI uri = new URI("http", null, host, port, "/craftemer.zip", null, null);
            return uri.toString();
        } catch (Exception ex) {
            getLogger().warning("Invalid resource-pack host '" + host + "': " + ex.getMessage());
            return null;
        }
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
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
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
        registerTool("emerald_pickaxe", "Изумрудная кирка", 5.0, -2.8, Tag.MINEABLE_PICKAXE, new String[]{"EEE", " S ", " S "});
        registerTool("emerald_axe", "Изумрудный топор", 7.0, -3.0, Tag.MINEABLE_AXE, new String[]{"EE ", "ES ", " S "});
        registerTool("emerald_shovel", "Изумрудная лопата", 3.0, -3.0, Tag.MINEABLE_SHOVEL, new String[]{" E ", " S ", " S "});
        registerTool("emerald_hoe", "Изумрудная мотыга", 2.0, -2.5, Tag.MINEABLE_HOE, new String[]{"EE ", " S ", " S "});
        registerWeapon("emerald_sword", "Изумрудный меч", 5.0, -2.4, new String[]{" E ", " E ", " S "});
        registerArmor("emerald_helmet", "Изумрудный шлем", EquipmentSlot.HEAD, 3.0, 1.0, new String[]{"EEE", "E E"}, "leather_helmet");
        registerArmor("emerald_chestplate", "Изумрудный нагрудник", EquipmentSlot.CHEST, 7.0, 1.0, new String[]{"E E", "EEE", "EEE"}, "leather_chestplate");
        registerArmor("emerald_leggings", "Изумрудные поножи", EquipmentSlot.LEGS, 5.0, 1.0, new String[]{"EEE", "E E", "E E"}, "leather_leggings");
        registerArmor("emerald_boots", "Изумрудные ботинки", EquipmentSlot.FEET, 2.0, 1.0, new String[]{"E E", "E E"}, "leather_boots");
    }

    private void registerTool(String id, String name, double damage, double attackSpeed, Tag<Material> mineableTag, String[] shape) {
        addRecipe(id, createItem(id, name, damage, attackSpeed, mineableTag), shape);
    }

    private void registerWeapon(String id, String name, double damage, double attackSpeed, String[] shape) {
        addRecipe(id, createItem(id, name, damage, attackSpeed, null), shape);
    }

    private void registerArmor(String id, String name, EquipmentSlot slot, double armor, double toughness, String[] shape, String vanillaItemModel) {
        addRecipe(id, createArmor(id, name, slot, armor, toughness, vanillaItemModel), shape);
    }

    private void addRecipe(String id, ItemStack item, String[] shape) {
        getServer().removeRecipe(new NamespacedKey(this, id));
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, id), item);
        recipe.shape(shape);
        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('S', Material.STICK);
        getServer().addRecipe(recipe);
    }

    private ItemStack createItem(String id, String name, double damage, double attackSpeed, Tag<Material> mineableTag) {
        ItemStack item = new ItemStack(Material.EMERALD);
        item.setData(DataComponentTypes.MAX_DAMAGE, DURABILITY);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a" + name);
        meta.setLore(List.of("§7Изумрудный материал", "§8Сильнее золота • слабее алмаза"));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey(this, id));
        meta.setMaxStackSize(1);
        meta.setEnchantable(ENMERCHANTABILITY);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "craftemer_damage", damage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(UUID.randomUUID(), "craftemer_attack_speed", attackSpeed, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
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

    private ItemStack createArmor(String id, String name, EquipmentSlot slot, double armor, double toughness, String vanillaItemModel) {
        ItemStack item = new ItemStack(Material.EMERALD);
        item.setData(DataComponentTypes.MAX_DAMAGE, ARMOR_DURABILITY);
        item.setData(DataComponentTypes.DYED_COLOR, io.papermc.paper.datacomponent.item.DyedItemColor.dyedItemColor(EMERALD_COLOR));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a" + name);
        meta.setLore(List.of("§7Изумрудная броня", "§8Сильнее золота • слабее алмаза"));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("minecraft", vanillaItemModel));
        meta.setMaxStackSize(1);
        meta.setEnchantable(ENMERCHANTABILITY);
        EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(slot);
        equippable.setModel(new NamespacedKey("craftemer", "leather"));
        equippable.setDamageOnHurt(true);
        equippable.setDispensable(true);
        equippable.setSwappable(true);
        equippable.setEquipOnInteract(false);
        meta.setEquippable(equippable);
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(UUID.randomUUID(), "craftemer_armor", armor, AttributeModifier.Operation.ADD_NUMBER, slot.getGroup()));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(UUID.randomUUID(), "craftemer_armor_toughness", toughness, AttributeModifier.Operation.ADD_NUMBER, slot.getGroup()));
        item.setItemMeta(meta);
        return item;
    }
}
