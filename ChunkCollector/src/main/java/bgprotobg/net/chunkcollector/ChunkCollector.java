package bgprotobg.net.chunkcollector;

import com.artillexstudios.axboosters.api.AxBoostersAPI;
import com.artillexstudios.axboosters.boosters.BoosterManager;
import com.artillexstudios.axboosters.hooks.HookManager;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.api.DHAPI;
import net.brcdev.shopgui.event.ShopPreTransactionEvent;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import net.milkbowl.vault.economy.Economy;
import bgprotobg.net.chunkcollector.events.SellAllEvent;
import net.brcdev.shopgui.ShopGuiPlusApi;


import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;


public class ChunkCollector extends JavaPlugin implements Listener {

    private final NamespacedKey chunkCollectorKey = new NamespacedKey(this, "chunk_collector");
    private final NamespacedKey ownerKey = new NamespacedKey(this, "owner_uuid");
    public final HashMap<Block, Inventory> collectorInventories = new HashMap<>();
    protected final HashMap<Block, UUID> beaconOwners = new HashMap<>();
    private final HashMap<Block, Hologram> beaconHolograms = new HashMap<>();
    private List<Material> collectableItems;
    private static Economy econ = null;
    private final Set<Chunk> occupiedChunks = new HashSet<>();
    private final HashMap<Block, Double> totalSoldAmounts = new HashMap<>();
    private final HashMap<Block, List<SellerInfo>> latestSellers = new HashMap<>();
    private boolean isSuperiorSkyblockLoaded;
    private boolean isResidenceLoaded;
    private final Map<Block, Player> collectorOpeners = new HashMap<>();
    private Map<Block, List<ItemStack>> itemStorage = new HashMap<>();
    private Map<String, CustomItem> customItems = new HashMap<>();
    private boolean isShopGUIPlusLoaded;

    private int sellAllSlot;
    private int pickUpSlot;
    private String pickUpButtonName;
    private List<String> pickUpButtonLore;
    private String sellAllButtonName;
    private List<String> sellAllButtonLore;
    private SQLite sqLite;
    private SellWand sellWand;
    private String collectorGuiName;
    private String chunkCollectorItemName;
    private List<String> chunkCollectorItemLore;
    private String bookItemName;
    private List<String> bookItemLore; private boolean isAxBoostersLoaded;



    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCustomItems();

        sqLite = new SQLite(getDataFolder() + "/data.db");
        sqLite.createTables();
        this.sellWand = new SellWand(this);
        this.getCommand("sellwand").setExecutor(sellWand);
        Bukkit.getPluginManager().registerEvents(sellWand, this);

        isSuperiorSkyblockLoaded = getServer().getPluginManager().isPluginEnabled("SuperiorSkyblock2");
        isShopGUIPlusLoaded = getServer().getPluginManager().isPluginEnabled("ShopGUIPlus");
        Bukkit.getLogger().info("SuperiorSkyblock2 is loaded and enabled: " + isSuperiorSkyblockLoaded);
        Bukkit.getLogger().info("ShopGUIPlus is loaded and enabled: " + isShopGUIPlusLoaded);

        if (!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            getLogger().severe("Disabled due to no DecentHolograms dependency found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        isAxBoostersLoaded = getServer().getPluginManager().isPluginEnabled("AxBoosters");
        Bukkit.getLogger().info("AxBoosters is loaded and enabled: " + isAxBoostersLoaded);


        getServer().getPluginManager().registerEvents(this, this);

        if (!setupEconomy()) {
            getLogger().severe("Disabled due to no Vault dependency found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CollectorExpansion(this).register();
        }

        getCommand("chunkcollector").setExecutor(this);
        getCommand("chunkcollector").setTabCompleter((sender, command, alias, args) -> {
            if (args.length == 1) {
                return Collections.singletonList("give");
            }
            if (args.length == 2) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return playerNames;
            }
            return Collections.emptyList();
        });

        loadCollectableItems();
        loadButtonConfig();
        loadHologramConfig();
        loadCollectorConfig();
        loadCollectorsFromDatabase();
    }

    private void loadHologramConfig() {
        if (!getConfig().contains("hologram.lines")) {
            getConfig().set("hologram.lines", Arrays.asList(
                    "&b{owner}'s Collector",
                    "&6&nRight click to open",
                    "&aItems: &e{items}",
                    "&aTotal Sold: &e{total_sold}"
            ));
            saveConfig();
        }
    }

    @Override
    public void onDisable() {
        for (Map.Entry<Block, Inventory> entry : collectorInventories.entrySet()) {
            Block block = entry.getKey();
            Inventory inventory = entry.getValue();
            UUID ownerUUID = beaconOwners.get(block);
            Hologram hologram = beaconHolograms.get(block);
            String sold = getTotalSoldAmount(block);

            if (hologram != null) {
                Location hologramLocation = hologram.getLocation();
                List<ItemStack> storedItems = itemStorage.get(block);
                List<SellerInfo> sellers = latestSellers.getOrDefault(block, new ArrayList<>());

                sqLite.saveCollector(block, ownerUUID, hologramLocation, storedItems, sold, sellers);
            }
        }
    }


    private void loadCollectorConfig() {
        collectorGuiName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("collector.gui.name", "Chunk Collector"));
        chunkCollectorItemName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("collector.item.name", "&6Chunk Collector"));

        chunkCollectorItemLore = new ArrayList<>();
        for (String line : getConfig().getStringList("collector.item.lore")) {
            chunkCollectorItemLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        bookItemName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("collector.book.name", "&aLatest 5 Sellers"));

        bookItemLore = new ArrayList<>();
        for (String line : getConfig().getStringList("collector.book.item-lore")) {  
            bookItemLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
    }


    private void loadCollectorsFromDatabase() {
        Map<Block, SQLite.CollectorData> collectors = sqLite.loadCollectors();
        int guiSize = getConfig().getInt("collector.gui-size", 27);

        for (Map.Entry<Block, SQLite.CollectorData> entry : collectors.entrySet()) {
            Block block = entry.getKey();
            SQLite.CollectorData data = entry.getValue();

            Inventory storage = Bukkit.createInventory(null, guiSize, collectorGuiName);
            collectorInventories.put(block, storage);
            itemStorage.put(block, data.getItems());
            beaconOwners.put(block, data.getOwner());
            totalSoldAmounts.put(block, sqLite.parseSold(data.getSold()));

            String hologramName = UUID.randomUUID().toString();
            Hologram hologram = DHAPI.createHologram(hologramName, data.getHologramLocation());
            beaconHolograms.put(block, hologram);
            updateCollectorHologram(block);

            List<SellerInfo> sellers = data.getSellers();
            latestSellers.put(block, sellers);

            new CollectorTask(block).runTaskTimer(this, 0, 20);
            occupiedChunks.add(block.getChunk());
        }
    }



    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("chunkcollector")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("help") && (sender instanceof Player || sender instanceof ConsoleCommandSender)) {
                showHelp(sender);
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("reload") && (sender instanceof Player || sender instanceof ConsoleCommandSender)) {
                if (!sender.hasPermission("chunkcollector.reload")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }

                reloadConfig();
                loadCustomItems();
                loadCollectableItems();
                loadButtonConfig();
                loadCollectorConfig();
                sender.sendMessage(ChatColor.GREEN + "ChunkCollector configuration reloaded.");
                return true;
            }

            if (args.length != 3 || !args[0].equalsIgnoreCase("give") || !(sender instanceof Player || sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(ChatColor.RED + "Usage: /chunkcollector give <player> <amount>");
                return false;
            }

            if (!sender.hasPermission("chunkcollector.give")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found!");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount!");
                return true;
            }

            giveChunkCollector(target, amount);
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " chunk collector(s) to " + target.getName());
            return true;
        }
        return false;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "---------- " + ChatColor.YELLOW + "ChunkCollector Commands" + ChatColor.GRAY + " ----------");
        sender.sendMessage(ChatColor.YELLOW + "/chunkcollector give <player> <amount>" + ChatColor.WHITE + " - " + ChatColor.GRAY + "Give chunk collectors to a player");
        sender.sendMessage(ChatColor.YELLOW + "/sellwand give <player> <rarity> <amount> <multiplier> <uses>" + ChatColor.WHITE + " - " + ChatColor.GRAY + "Give sellwand to a player");
        sender.sendMessage(ChatColor.YELLOW + "/chunkcollector help" + ChatColor.WHITE + " - " + ChatColor.GRAY + "Show available commands");
        sender.sendMessage(ChatColor.YELLOW + "/chunkcollector reload" + ChatColor.WHITE + " - " + ChatColor.GRAY + "Reload the plugin configuration");
        sender.sendMessage(ChatColor.GRAY + "-----------------------------------------");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Location location = event.getEntity().getLocation();
        Chunk chunk = location.getChunk();

        for (Block block : collectorInventories.keySet()) {
            if (block.getChunk().equals(chunk)) {
                List<ItemStack> storedItems = itemStorage.get(block);
                if (storedItems == null) {
                    storedItems = new ArrayList<>();
                    itemStorage.put(block, storedItems);
                }

                List<ItemStack> drops = event.getDrops();
                for (Iterator<ItemStack> iterator = drops.iterator(); iterator.hasNext(); ) {
                    ItemStack itemStack = iterator.next();
                    if (collectableItems.contains(itemStack.getType())) {
                        boolean itemExists = false;
                        for (ItemStack storedItem : storedItems) {
                            if (storedItem.getType() == itemStack.getType() &&
                                    storedItem.getItemMeta().hasDisplayName() == itemStack.getItemMeta().hasDisplayName() &&
                                    storedItem.getItemMeta().getDisplayName().equals(itemStack.getItemMeta().getDisplayName()) &&
                                    storedItem.getItemMeta().hasLore() == itemStack.getItemMeta().hasLore() &&
                                    String.join(",", storedItem.getItemMeta().getLore()).equals(String.join(",", itemStack.getItemMeta().getLore()))) {
                                storedItem.setAmount(storedItem.getAmount() + itemStack.getAmount());
                                itemExists = true;
                                break;
                            }
                        }

                        if (!itemExists) {
                            storedItems.add(itemStack.clone());
                        }

                        iterator.remove();
                    }
                }

                updateCollectorHologram(block);
            }
        }
    }




    private void giveChunkCollector(Player player, int amount) {
        ItemStack beacon = new ItemStack(Material.BEACON, amount);
        ItemMeta meta = beacon.getItemMeta();
        meta.setDisplayName(chunkCollectorItemName);
        meta.getPersistentDataContainer().set(chunkCollectorKey, PersistentDataType.BYTE, (byte) 1);
        meta.setLore(chunkCollectorItemLore);
        beacon.setItemMeta(meta);
        player.getInventory().addItem(beacon);
    }


    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (beaconOwners.containsKey(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot break this Chunk Collector. Use the Pick Up button to retrieve it.");
            occupiedChunks.remove(block.getChunk());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() == Material.BEACON && item.getItemMeta() != null && item.getItemMeta().getPersistentDataContainer().has(chunkCollectorKey, PersistentDataType.BYTE)) {
            Block block = event.getBlockPlaced();
            Player player = event.getPlayer();
            Chunk chunk = block.getChunk();

            if (occupiedChunks.contains(chunk)) {
                player.sendMessage(ChatColor.RED + "You can only place one Chunk Collector per chunk.");
                event.setCancelled(true);
                return;
            }

            int guiSize = getConfig().getInt("collector.gui-size", 27);
            Inventory storage = Bukkit.createInventory(null, guiSize, collectorGuiName);
            ItemStack pickUpButton = new ItemStack(Material.BARRIER);
            ItemMeta pickUpMeta = pickUpButton.getItemMeta();
            pickUpMeta.setDisplayName(ChatColor.RED + "Pick Up");
            pickUpButton.setItemMeta(pickUpMeta);
            storage.setItem(8, pickUpButton);

            collectorInventories.put(block, storage);
            itemStorage.put(block, new ArrayList<>());
            beaconOwners.put(block, player.getUniqueId());
            latestSellers.put(block, new ArrayList<>());
            new CollectorTask(block).runTaskTimer(this, 0, 20);

            double yOffset = getConfig().getDouble("hologram.y-offset", 2.3);
            String hologramName = UUID.randomUUID().toString();
            Hologram hologram = DHAPI.createHologram(hologramName, block.getLocation().add(0.5, yOffset, 0.5));
            beaconHolograms.put(block, hologram);
            updateCollectorHologram(block);
            occupiedChunks.add(chunk);
        }
    }



    private void updateCollectorHologram(Block block) {
        Hologram hologram = beaconHolograms.get(block);
        if (hologram == null) {
            String hologramName = UUID.randomUUID().toString();
            hologram = DHAPI.createHologram(hologramName, block.getLocation().add(0.5, 2.3, 0.5));
            beaconHolograms.put(block, hologram);
        }

        UUID ownerUUID = beaconOwners.get(block);
        String ownerName = null;
        if (ownerUUID != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUUID);
            if (offlinePlayer != null) {
                ownerName = offlinePlayer.getName();
            }
        }
        if (ownerName == null) {
            ownerName = "Unknown";
        }

        int totalAmount = getTotalAmount(block);
        String formattedTotalAmount = formatAmount(totalAmount);
        if (formattedTotalAmount == null) {
            formattedTotalAmount = "0";
        }

        String totalSoldAmount = getTotalSoldAmount(block);
        if (totalSoldAmount == null) {
            totalSoldAmount = "0";
        }

        List<String> hologramLines = getConfig().getStringList("hologram.lines");
        List<String> formattedLines = new ArrayList<>();

        for (String line : hologramLines) {
            formattedLines.add(ChatColor.translateAlternateColorCodes('&', line)
                    .replace("{owner}", ownerName)
                    .replace("{items}", formattedTotalAmount)
                    .replace("{total_sold}", totalSoldAmount));
        }

        double yOffset = getConfig().getDouble("hologram.y-offset", 2.3);
        Location hologramBaseLocation = block.getLocation().add(0.5, yOffset + (0.3 * formattedLines.size()), 0.5);
        DHAPI.moveHologram(hologram, hologramBaseLocation);
        DHAPI.setHologramLines(hologram, formattedLines);
    }




    public Block getCollectorBlockForPlayer(Player player) {
        for (Map.Entry<Block, UUID> entry : beaconOwners.entrySet()) {
            if (entry.getValue().equals(player.getUniqueId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            Player player = event.getPlayer();

            if (block.getType() == Material.BEACON) {
                ItemStack itemInHand = player.getInventory().getItemInMainHand();

                if (sellWand.isSellWand(itemInHand)) {
                    event.setCancelled(true);

                    if (isSuperiorSkyblockLoaded) {
                        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player.getUniqueId());
                        if (superiorPlayer != null) {
                            Island island = SuperiorSkyblockAPI.getGrid().getIslandAt(block.getLocation());
                            if (island != null) {
                                if (island.isMember(superiorPlayer)) {
                                    double multiplier = sellWand.getMultiplier(itemInHand);
                                    int uses = sellWand.getUses(itemInHand);

                                    if (uses != 0) {
                                        if (sellBeaconWithSellwand(player, block, multiplier, itemInHand)) {
                                            sellWand.decrementUses(itemInHand);
                                        }
                                    } else {
                                        player.sendMessage(ChatColor.RED + "This Sellwand has no uses left.");
                                    }
                                } else {
                                    player.sendMessage(ChatColor.RED + "You do not have permission to use this Sellwand on this Chunk Collector.");
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + "This Chunk Collector is not on any island.");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "You are not part of any island.");
                        }
                    } else {
                        double multiplier = sellWand.getMultiplier(itemInHand);
                        int uses = sellWand.getUses(itemInHand);

                        if (uses != 0) {
                            if (sellBeaconWithSellwand(player, block, multiplier, itemInHand)) {
                                sellWand.decrementUses(itemInHand);
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "This Sellwand has no uses left.");
                        }
                    }

                    return;
                }

                if (collectorInventories.containsKey(block)) {
                    event.setCancelled(true);

                    if (collectorOpeners.containsKey(block)) {
                        Player opener = collectorOpeners.get(block);
                        if (!opener.equals(player) && !player.hasPermission("chunkcollector.open.others")) {
                            player.sendMessage(ChatColor.RED + "The collector is already opened by another player.");
                            return;
                        }
                    }

                    if (isSuperiorSkyblockLoaded) {
                        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player.getUniqueId());
                        if (superiorPlayer != null) {
                            Island island = SuperiorSkyblockAPI.getGrid().getIslandAt(block.getLocation());
                            if (island != null) {
                                if (island.isMember(superiorPlayer) || player.hasPermission("chunkcollector.interact.others")) {
                                    Inventory storage = collectorInventories.get(block);
                                    updateInventory(storage, block);
                                    player.openInventory(storage);
                                    collectorOpeners.put(block, player);
                                } else {
                                    player.sendMessage(ChatColor.RED + "You do not have permission to access this Chunk Collector.");
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + "This Chunk Collector is not on any island.");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "You are not part of any island.");
                        }
                    } else {
                        Inventory storage = collectorInventories.get(block);
                        updateInventory(storage, block);
                        player.openInventory(storage);
                        collectorOpeners.put(block, player);
                    }
                }
            }
        }
    }
    private void loadCustomItems() {
        ConfigurationSection section = getConfig().getConfigurationSection("custom-items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String name = ChatColor.translateAlternateColorCodes('&', section.getString(key + ".name"));
                List<String> lore = section.getStringList(key + ".lore").stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                boolean enchanted = section.getBoolean(key + ".enchanted");
                double price = section.getDouble(key + ".price");

                customItems.put(key, new CustomItem(name, lore, enchanted, price));
            }
        }
    }

    public class CustomItem {
        private final String name;
        private final List<String> lore;
        private final boolean enchanted;
        private final double price;

        public CustomItem(String name, List<String> lore, boolean enchanted, double price) {
            this.name = name;
            this.lore = lore;
            this.enchanted = enchanted;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }

        public boolean isEnchanted() {
            return enchanted;
        }

        public double getPrice() {
            return price;
        }
    }



    protected boolean sellBeaconWithSellwand(Player player, Block block, double sellwandMultiplier, ItemStack sellwand) {
        if (!collectorInventories.containsKey(block)) {
            player.sendMessage(ChatColor.RED + "This is not a valid Chunk Collector.");
            return false;
        }

        List<ItemStack> storedItems = itemStorage.get(block);
        if (storedItems == null || storedItems.isEmpty()) {
            player.sendMessage(ChatColor.RED + "There are no items to sell.");
            return false;
        }

        double totalValue = 0;
        for (ItemStack stack : storedItems) {
            double pricePerItem = getPricePerItem(stack.getType(), stack.getItemMeta());
            double itemSellPrice = pricePerItem * stack.getAmount();

            try {
                ShopPreTransactionEvent shopPreTransactionEvent = (ShopPreTransactionEvent) Class.forName("net.brcdev.shopgui.event.ShopPreTransactionEvent")
                        .getDeclaredConstructor(ShopManager.ShopAction.class, ShopItem.class, Player.class, int.class, double.class)
                        .newInstance(ShopManager.ShopAction.SELL, ShopGuiPlusApi.getItemStackShopItem(new ItemStack(stack.getType())), player, stack.getAmount(), itemSellPrice);

                Bukkit.getPluginManager().callEvent(shopPreTransactionEvent);
                itemSellPrice = shopPreTransactionEvent.getPrice();
            } catch (Exception exception) {
                exception.printStackTrace();
            }

            totalValue += itemSellPrice;
        }

        if (totalValue > 0) {
            AxBoostersMoneyExample booster = new AxBoostersMoneyExample();
            double boosterMultiplier = BoosterManager.getMultiplier(player, booster);

            double finalMultiplier = sellwandMultiplier * boosterMultiplier;
            double finalValue = totalValue * finalMultiplier;

            econ.depositPlayer(player, finalValue);
            player.sendMessage(ChatColor.GREEN + "You have earned " + econ.format(finalValue) + " from selling the Chunk Collector with a sellwand.");

            storedItems.clear();

            totalSoldAmounts.put(block, totalSoldAmounts.getOrDefault(block, 0.0) + finalValue);

            List<SellerInfo> sellers = latestSellers.getOrDefault(block, new ArrayList<>());
            if (sellers.size() >= 5) {
                sellers.remove(0);
            }
            sellers.add(new SellerInfo(player.getName(), finalValue, new Date()));
            latestSellers.put(block, sellers);

            updateCollectorHologram(block);

            double currentEarnings = sellWand.getEarnings(sellwand);
            sellWand.setEarnings(sellwand, currentEarnings + finalValue);

            return true;
        } else {
            player.sendMessage(ChatColor.RED + "There are no items to sell.");
            return false;
        }
    }




        @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();

        if (clickedInventory != null && topInventory != null && collectorInventories.containsValue(topInventory)) {
            event.setCancelled(true);

            if (event.getCurrentItem() != null) {
                ItemStack currentItem = event.getCurrentItem();
                Player player = (Player) event.getWhoClicked();
                Block block = getBlockFromInventory(topInventory);

                if (currentItem.getType() == Material.BARRIER && currentItem.getItemMeta().getDisplayName().equals(ChatColor.RED + "Pick Up")) {
                    if (block != null) {
                        UUID ownerUUID = beaconOwners.get(block);
                        if (ownerUUID.equals(player.getUniqueId()) || player.hasPermission("chunkcollector.interact.others")) {
                            collectorInventories.remove(block);
                            itemStorage.remove(block);
                            beaconOwners.remove(block);
                            totalSoldAmounts.remove(block);

                            Hologram hologram = beaconHolograms.remove(block);
                            if (hologram != null) {
                                DHAPI.removeHologram(hologram.getId());
                            }

                            block.setType(Material.AIR);
                            ItemStack beacon = new ItemStack(Material.BEACON);
                            ItemMeta meta = beacon.getItemMeta();
                            meta.setDisplayName(chunkCollectorItemName);
                            meta.getPersistentDataContainer().set(chunkCollectorKey, PersistentDataType.BYTE, (byte) 1);
                            meta.setLore(chunkCollectorItemLore);

                            beacon.setItemMeta(meta);
                            player.getInventory().addItem(beacon);
                            player.closeInventory();
                            player.sendMessage(ChatColor.GREEN + "You have picked up the Chunk Collector.");

                        } else {
                            player.sendMessage(ChatColor.RED + "You are not the owner of this Chunk Collector.");
                        }
                    }
                } else if (currentItem.getType() == Material.EMERALD && currentItem.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Sell All")) {
                    if (block != null) {
                        sellAllItems(player, block);

                        UUID ownerUUID = beaconOwners.get(block);
                        Player owner = Bukkit.getPlayer(ownerUUID);
                        if (owner != null && !owner.equals(player)) {
                            owner.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " has sold the collector for " + ChatColor.YELLOW + getTotalSoldAmount(block) + ChatColor.GREEN + " value.");
                        }

                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + "You have sold all items from the Chunk Collector.");
                    }
                } else if (currentItem.getType() == Material.PAPER && currentItem.getItemMeta().getDisplayName().equals(ChatColor.YELLOW + "Total Amount")) {
                    if (block != null) {
                        int totalAmount = getTotalAmount(block);
                        String formattedTotalAmount = formatAmount(totalAmount);
                        player.sendMessage(ChatColor.GREEN + "Total amount of items in storage: " + ChatColor.YELLOW + formattedTotalAmount);
                    }
                }
            }
        }
    }




    protected String formatPrice(double price) {
        if (price >= 1_000_000_000_000_000_000.0) {
            return String.format("%.1fQ", price / 1_000_000_000_000_000_000.0);
        } else if (price >= 1_000_000_000_000_000.0) {
            return String.format("%.1fq", price / 1_000_000_000_000_000.0);
        } else if (price >= 1_000_000_000_000.0) {
            return String.format("%.1fT", price / 1_000_000_000_000.0);
        } else if (price >= 1_000_000_000.0) {
            return String.format("%.1fB", price / 1_000_000_000.0);
        } else if (price >= 1_000_000.0) {
            return String.format("%.1fM", price / 1_000_000.0);
        } else {
            return String.format("%.2f", price);
        }
    }
    protected String formatAmount(int amount) {
        long longAmount = amount & 0xFFFFFFFFL;
        if (longAmount >= 1_000_000_000_000_000_000L) {
            return String.format("%.1fQ", longAmount / 1_000_000_000_000_000_000.0);
        } else if (longAmount >= 1_000_000_000_000_000L) {
            return String.format("%.1fq", longAmount / 1_000_000_000_000_000.0);
        } else if (longAmount >= 1_000_000_000_000L) {
            return String.format("%.1fT", longAmount / 1_000_000_000_000.0);
        } else if (longAmount >= 1_000_000_000L) {
            return String.format("%.1fB", longAmount / 1_000_000_000.0);
        } else if (longAmount >= 1_000_000L) {
            return String.format("%.1fM", longAmount / 1_000_000.0);
        } else if (longAmount >= 100_000L) {
            return String.format("%.1fK", longAmount / 1_000.0);
        } else {
            return String.valueOf(amount);
        }
    }





    public int getTotalAmount(Block block) {
        int totalAmount = 0;
        List<ItemStack> storedItems = itemStorage.get(block);
        if (storedItems != null) {
            for (ItemStack stack : storedItems) {
                totalAmount += stack.getAmount();
            }
        }
        return totalAmount;
    }



    private void sellAllItems(Player player, Block block) {
        List<ItemStack> storedItems = itemStorage.get(block);
        double totalValue = 0;

        if (storedItems == null || storedItems.isEmpty()) {
            player.sendMessage(ChatColor.RED + "There are no items to sell.");
            return;
        }

        for (ItemStack stack : storedItems) {
            double pricePerItem = getPricePerItem(stack.getType(), stack.getItemMeta());
            double itemSellPrice = pricePerItem * stack.getAmount();

            try {
                ShopPreTransactionEvent shopPreTransactionEvent = (ShopPreTransactionEvent) Class.forName("net.brcdev.shopgui.event.ShopPreTransactionEvent")
                        .getDeclaredConstructor(ShopManager.ShopAction.class, ShopItem.class, Player.class, int.class, double.class)
                        .newInstance(ShopManager.ShopAction.SELL, ShopGuiPlusApi.getItemStackShopItem(new ItemStack(stack.getType())), player, stack.getAmount(), itemSellPrice);

                Bukkit.getPluginManager().callEvent(shopPreTransactionEvent);
                itemSellPrice = shopPreTransactionEvent.getPrice();
            } catch (Exception exception) {
                exception.printStackTrace();
            }

            totalValue += itemSellPrice;
        }

        if (totalValue > 0) {
            SellAllEvent sellAllEvent = new SellAllEvent(player, totalValue);
            Bukkit.getPluginManager().callEvent(sellAllEvent);

            econ.depositPlayer(player, sellAllEvent.getSellPrice());
            player.sendMessage(ChatColor.GREEN + "You have earned " + econ.format(sellAllEvent.getSellPrice()) + " from selling items.");
            storedItems.clear();

            totalSoldAmounts.put(block, totalSoldAmounts.getOrDefault(block, 0.0) + sellAllEvent.getSellPrice());

            List<SellerInfo> sellers = latestSellers.getOrDefault(block, new ArrayList<>());
            if (sellers.size() >= 5) {
                sellers.remove(0);
            }
            sellers.add(new SellerInfo(player.getName(), sellAllEvent.getSellPrice(), new Date()));
            latestSellers.put(block, sellers);

            Inventory storage = collectorInventories.get(block);
            updateInventory(storage, block);
        } else {
            player.sendMessage(ChatColor.RED + "There are no items to sell.");
        }
    }






    public String getTotalSoldAmount(Block block) {
        double totalSoldAmount = totalSoldAmounts.getOrDefault(block, 0.0);
        return formatPrice(totalSoldAmount);
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory closedInventory = event.getInventory();
        Player player = (Player) event.getPlayer();
        Block blockToRemove = null;

        for (Map.Entry<Block, Inventory> entry : collectorInventories.entrySet()) {
            if (entry.getValue().equals(closedInventory)) {
                blockToRemove = entry.getKey();
                break;
            }
        }

        if (blockToRemove != null) {
            collectorOpeners.remove(blockToRemove, player);
        }
    }


    private Block getBlockFromInventory(Inventory inventory) {
        for (Map.Entry<Block, Inventory> entry : collectorInventories.entrySet()) {
            if (entry.getValue().equals(inventory)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private double getPricePerItem(Material material, ItemMeta meta) {
        for (CustomItem customItem : customItems.values()) {
            if (customItem.getName().equals(meta.getDisplayName()) && customItem.getLore().equals(meta.getLore())) {
                return customItem.getPrice();
            }
        }
        ItemStack itemStack = new ItemStack(material);
        return ShopGuiPlusApi.getItemStackPriceSell(itemStack);
    }

    private void updateInventory(Inventory storage, Block block) {
        storage.clear();
        List<ItemStack> storedItems = itemStorage.get(block);
        double totalValue = 0;

        String amountText = getConfig().getString("gui.amount-text", "&aAmount:");
        String amountTextWithColor = ChatColor.translateAlternateColorCodes('&', amountText);

        int slot = 0;
        for (ItemStack stack : storedItems) {
            ItemStack displayStack = stack.clone();
            ItemMeta meta = displayStack.getItemMeta();

            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                String formattedAmount = formatAmount(stack.getAmount());
                String itemName = meta.hasDisplayName() ? meta.getDisplayName() : displayStack.getType().name();

                meta.setDisplayName(itemName);
                lore.add("");
                lore.add(amountTextWithColor + " " + ChatColor.YELLOW + formattedAmount);

                meta.setLore(lore);
                displayStack.setItemMeta(meta);
            }

            storage.setItem(slot++, displayStack);

            double pricePerItem = getPricePerItem(displayStack.getType(), meta);
            totalValue += pricePerItem * displayStack.getAmount();
        }

        int pickUpSlot = getConfig().getInt("gui.pickup-slot", 26);
        int sellAllSlot = getConfig().getInt("gui.sellall-slot", 25);
        int bookSlot = getConfig().getInt("gui.book-slot", 24);

        ItemStack pickUpButton = new ItemStack(Material.BARRIER);
        ItemMeta pickUpMeta = pickUpButton.getItemMeta();
        pickUpMeta.setDisplayName(pickUpButtonName);
        pickUpMeta.setLore(pickUpButtonLore);
        pickUpButton.setItemMeta(pickUpMeta);
        storage.setItem(pickUpSlot, pickUpButton);

        ItemStack sellAllButton = new ItemStack(Material.EMERALD);
        ItemMeta sellAllMeta = sellAllButton.getItemMeta();
        sellAllMeta.setDisplayName(sellAllButtonName);
        sellAllMeta.setLore(sellAllButtonLore);
        sellAllButton.setItemMeta(sellAllMeta);
        storage.setItem(sellAllSlot, sellAllButton);

        boolean isBookEnabled = getConfig().getBoolean("gui.book-enabled", true);
        if (isBookEnabled) {
            ItemStack bookItem = new ItemStack(Material.BOOK);
            ItemMeta bookMeta = bookItem.getItemMeta();
            bookMeta.setDisplayName(bookItemName);

            List<SellerInfo> sellers = latestSellers.getOrDefault(block, new ArrayList<>());
            List<String> bookLore = formatBookLore(sellers);

            bookMeta.setLore(bookLore);
            bookItem.setItemMeta(bookMeta);
            storage.setItem(bookSlot, bookItem);
        }

        ItemStack grayGlassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta grayMeta = grayGlassPane.getItemMeta();
        grayMeta.setDisplayName(" ");
        grayGlassPane.setItemMeta(grayMeta);

        for (int i = 0; i < storage.getSize(); i++) {
            if (storage.getItem(i) == null || storage.getItem(i).getType() == Material.AIR) {
                storage.setItem(i, grayGlassPane);
            }
        }
    }


    private List<String> formatBookLore(List<SellerInfo> sellers) {
        List<String> formattedLore = new ArrayList<>();
        List<String> baseLore = getConfig().getStringList("collector.book.item-lore");

        for (SellerInfo seller : sellers) {
            String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(seller.getLastSaleDate());
            String formattedValue = formatPrice(seller.getTotalValueSold());
            for (String line : baseLore) {
                String formattedLine = ChatColor.translateAlternateColorCodes('&', line)
                        .replace("{player}", seller.getPlayerName())
                        .replace("{price}", formattedValue)
                        .replace("{date}", formattedDate);
                formattedLore.add(formattedLine);
            }
        }
        return formattedLore;
    }


    private class CollectorTask extends BukkitRunnable {
        private final Block beaconBlock;

        public CollectorTask(Block beaconBlock) {
            this.beaconBlock = beaconBlock;
        }

        @Override
        public void run() {
            if (!beaconBlock.getType().equals(Material.BEACON)) {
                occupiedChunks.remove(beaconBlock.getChunk());
                collectorInventories.remove(beaconBlock);
                itemStorage.remove(beaconBlock);
                beaconOwners.remove(beaconBlock);

                Hologram hologram = beaconHolograms.remove(beaconBlock);
                if (hologram != null) {
                    DHAPI.removeHologram(hologram.getId());
                }

                cancel();
                return;
            }

            Chunk chunk = beaconBlock.getChunk();
            Entity[] entities = chunk.getEntities();

            for (Entity entity : entities) {
                if (entity instanceof Item) {
                    Item item = (Item) entity;
                    ItemStack itemStack = item.getItemStack();

                    if (collectableItems.contains(itemStack.getType())) {
                        List<ItemStack> storedItems = itemStorage.get(beaconBlock);
                        if (storedItems == null) {
                            storedItems = new ArrayList<>();
                            itemStorage.put(beaconBlock, storedItems);
                        }

                        boolean merged = false;
                        for (ItemStack storedItem : storedItems) {
                            if (storedItem.isSimilar(itemStack)) {
                                storedItem.setAmount(storedItem.getAmount() + itemStack.getAmount());
                                merged = true;
                                break;
                            }
                        }

                        if (!merged) {
                            storedItems.add(itemStack);
                        }

                        item.remove();
                    }
                }
            }
            updateCollectorHologram(beaconBlock);
        }
    }




    private void loadButtonConfig() {
        pickUpButtonName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("buttons.pickup.name", "&cPick Up"));
        pickUpButtonLore = new ArrayList<>();
        for (String line : getConfig().getStringList("buttons.pickup.lore")) {
            pickUpButtonLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        sellAllButtonName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("buttons.sellall.name", "&aSell All"));
        sellAllButtonLore = new ArrayList<>();
        for (String line : getConfig().getStringList("buttons.sellall.lore")) {
            sellAllButtonLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
    }


    private void loadCollectableItems() {
        List<String> items = getConfig().getStringList("collectable-items");
        collectableItems = new ArrayList<>();
        for (String itemName : items) {
            Material material = Material.getMaterial(itemName.toUpperCase());
            if (material != null) {
                collectableItems.add(material);
            } else {
                getLogger().warning("Invalid item in config: " + itemName);
            }
        }
    }
    public static class SellerInfo {
        private final String playerName;
        private final double totalValueSold;
        private final Date lastSaleDate;

        public SellerInfo(String playerName, double totalValueSold, Date lastSaleDate) {
            this.playerName = playerName;
            this.totalValueSold = totalValueSold;
            this.lastSaleDate = lastSaleDate;
        }

        public String getPlayerName() {
            return playerName;
        }

        public double getTotalValueSold() {
            return totalValueSold;
        }

        public Date getLastSaleDate() {
            return lastSaleDate;
        }
    }

}
