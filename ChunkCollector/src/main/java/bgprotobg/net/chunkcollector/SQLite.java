package bgprotobg.net.chunkcollector;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

public class SQLite {

    private final String dbPath;

    public SQLite(String dbPath) {
        this.dbPath = dbPath;
        createTables();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public void createTables() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS collectors (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "blockX INTEGER," +
                    "blockY INTEGER," +
                    "blockZ INTEGER," +
                    "world TEXT," +
                    "ownerUUID TEXT," +
                    "hologramX DOUBLE," +
                    "hologramY DOUBLE," +
                    "hologramZ DOUBLE," +
                    "sold TEXT DEFAULT 0," +
                    "sellers TEXT" +
                    ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS items (" +
                    "collectorID INTEGER," +
                    "material TEXT," +
                    "amount INTEGER," +
                    "name TEXT," +
                    "lore TEXT," +
                    "PRIMARY KEY (collectorID, material, name, lore)," +
                    "FOREIGN KEY (collectorID) REFERENCES collectors (id)" +
                    ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    public void saveCollector(Block block, UUID ownerUUID, Location hologramLocation, List<ItemStack> storedItems, String sold, List<ChunkCollector.SellerInfo> sellers) {
        try (Connection connection = getConnection();
             PreparedStatement insertCollector = connection.prepareStatement(
                     "INSERT OR REPLACE INTO collectors (blockX, blockY, blockZ, world, ownerUUID, hologramX, hologramY, hologramZ, sold, sellers) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement insertItem = connection.prepareStatement(
                     "INSERT OR REPLACE INTO items (collectorID, material, amount, name, lore) VALUES (?, ?, ?, ?, ?)")) {

            connection.setAutoCommit(false);

            insertCollector.setInt(1, block.getX());
            insertCollector.setInt(2, block.getY());
            insertCollector.setInt(3, block.getZ());
            insertCollector.setString(4, block.getWorld().getName());
            insertCollector.setString(5, ownerUUID.toString());
            insertCollector.setDouble(6, hologramLocation.getX());
            insertCollector.setDouble(7, hologramLocation.getY());
            insertCollector.setDouble(8, hologramLocation.getZ());
            insertCollector.setString(9, sold);

            String sellersString = sellers.stream()
                    .map(seller -> seller.getPlayerName() + ":" + formatPrice(seller.getTotalValueSold()) + ":" + seller.getLastSaleDate().getTime())
                    .collect(Collectors.joining(","));
            insertCollector.setString(10, sellersString);

            insertCollector.executeUpdate();

            ResultSet generatedKeys = insertCollector.getGeneratedKeys();
            if (generatedKeys.next()) {
                int collectorId = generatedKeys.getInt(1);

                Map<String, ItemStack> combinedItems = new HashMap<>();
                for (ItemStack itemStack : storedItems) {
                    Material material = itemStack.getType();
                    int amount = itemStack.getAmount();
                    String name = itemStack.getItemMeta().hasDisplayName() ? itemStack.getItemMeta().getDisplayName() : "";
                    String lore = itemStack.getItemMeta().hasLore() ? String.join(",", itemStack.getItemMeta().getLore()) : "";
                    String key = material.toString() + ":" + name + ":" + lore;

                    if (combinedItems.containsKey(key)) {
                        ItemStack existingItem = combinedItems.get(key);
                        existingItem.setAmount(existingItem.getAmount() + amount);
                    } else {
                        combinedItems.put(key, itemStack.clone());
                    }
                }

                for (ItemStack itemStack : combinedItems.values()) {
                    Material material = itemStack.getType();
                    int amount = itemStack.getAmount();
                    String name = itemStack.getItemMeta().hasDisplayName() ? itemStack.getItemMeta().getDisplayName() : "";
                    String lore = itemStack.getItemMeta().hasLore() ? String.join(",", itemStack.getItemMeta().getLore()) : "";

                    insertItem.setInt(1, collectorId);
                    insertItem.setString(2, material.toString());
                    insertItem.setInt(3, amount);
                    insertItem.setString(4, name);
                    insertItem.setString(5, lore);
                    insertItem.executeUpdate();
                }
            }

            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }





    public Map<Block, CollectorData> loadCollectors() {
        Map<Block, CollectorData> collectors = new HashMap<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet collectorResultSet = statement.executeQuery("SELECT * FROM collectors")) {

            while (collectorResultSet.next()) {
                int id = collectorResultSet.getInt("id");
                int blockX = collectorResultSet.getInt("blockX");
                int blockY = collectorResultSet.getInt("blockY");
                int blockZ = collectorResultSet.getInt("blockZ");
                String world = collectorResultSet.getString("world");
                UUID ownerUUID = UUID.fromString(collectorResultSet.getString("ownerUUID"));
                Location hologramLocation = new Location(Bukkit.getWorld(world),
                        collectorResultSet.getDouble("hologramX"),
                        collectorResultSet.getDouble("hologramY"),
                        collectorResultSet.getDouble("hologramZ"));

                String sold = collectorResultSet.getString("sold");
                String sellersString = collectorResultSet.getString("sellers");

                Block block = new Location(Bukkit.getWorld(world), blockX, blockY, blockZ).getBlock();
                List<ItemStack> items = loadItems(id);

                List<ChunkCollector.SellerInfo> sellers = new ArrayList<>();
                if (sellersString != null && !sellersString.isEmpty()) {
                    sellers = Arrays.stream(sellersString.split(","))
                            .map(sellerData -> {
                                String[] parts = sellerData.split(":");
                                if (parts.length == 3) {
                                    String playerName = parts[0];
                                    double totalValueSold = parseSold(parts[1]);
                                    Date lastSaleDate = new Date(Long.parseLong(parts[2]));
                                    return new ChunkCollector.SellerInfo(playerName, totalValueSold, lastSaleDate);
                                } else {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                }

                collectors.put(block, new CollectorData(id, ownerUUID, hologramLocation, items, sold, sellers));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return collectors;
    }

    private List<ItemStack> loadItems(int collectorID) {
        List<ItemStack> items = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT material, amount, name, lore FROM items WHERE collectorID = ?")) {
            statement.setInt(1, collectorID);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Material material = Material.valueOf(resultSet.getString("material"));
                    int amount = resultSet.getInt("amount");
                    String name = resultSet.getString("name");
                    String loreString = resultSet.getString("lore");
                    List<String> lore = loreString.isEmpty() ? new ArrayList<>() : Arrays.asList(loreString.split(","));

                    ItemStack itemStack = new ItemStack(material, amount);
                    ItemMeta meta = itemStack.getItemMeta();
                    if (!name.isEmpty()) {
                        meta.setDisplayName(name);
                    }
                    if (!lore.isEmpty()) {
                        meta.setLore(lore);
                    }
                    itemStack.setItemMeta(meta);

                    boolean itemExists = false;
                    for (ItemStack storedItem : items) {
                        if (storedItem.isSimilar(itemStack)) {
                            storedItem.setAmount(storedItem.getAmount() + amount);
                            itemExists = true;
                            break;
                        }
                    }

                    if (!itemExists) {
                        items.add(itemStack);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }




    protected String formatPrice(double sold) {
        if (sold >= 1_000_000_000_000_000_000.0) {
            return String.format("%.1fQ", sold / 1_000_000_000_000_000_000.0);
        } else if (sold >= 1_000_000_000_000_000.0) {
            return String.format("%.1fq", sold / 1_000_000_000_000_000.0);
        } else if (sold >= 1_000_000_000_000.0) {
            return String.format("%.1fT", sold / 1_000_000_000_000.0);
        } else if (sold >= 1_000_000_000.0) {
            return String.format("%.1fB", sold / 1_000_000_000.0);
        } else if (sold >= 1_000_000.0) {
            return String.format("%.1fM", sold / 1_000_000.0);
        } else {
            return String.format("%.2f", sold);
        }
    }

    protected double parseSold(String sold) {
        if (sold.endsWith("Q")) {
            return Double.parseDouble(sold.replace("Q", "")) * 1_000_000_000_000_000_000.0;
        } else if (sold.endsWith("q")) {
            return Double.parseDouble(sold.replace("q", "")) * 1_000_000_000_000_000.0;
        } else if (sold.endsWith("T")) {
            return Double.parseDouble(sold.replace("T", "")) * 1_000_000_000_000.0;
        } else if (sold.endsWith("B")) {
            return Double.parseDouble(sold.replace("B", "")) * 1_000_000_000.0;
        } else if (sold.endsWith("M")) {
            return Double.parseDouble(sold.replace("M", "")) * 1_000_000.0;
        } else {
            return Double.parseDouble(sold);
        }
    }

    public static class CollectorData {
        private final int id;
        private final UUID owner;
        private final Location hologramLocation;
        private final List<ItemStack> items;
        private final String sold;
        private final List<ChunkCollector.SellerInfo> sellers;

        public CollectorData(int id, UUID owner, Location hologramLocation, List<ItemStack> items, String sold, List<ChunkCollector.SellerInfo> sellers) {
            this.id = id;
            this.owner = owner;
            this.hologramLocation = hologramLocation;
            this.items = items;
            this.sold = sold;
            this.sellers = sellers;
        }

        public int getId() {
            return id;
        }

        public UUID getOwner() {
            return owner;
        }

        public Location getHologramLocation() {
            return hologramLocation;
        }

        public List<ItemStack> getItems() {
            return items;
        }

        public String getSold() {
            return sold;
        }

        public List<ChunkCollector.SellerInfo> getSellers() {
            return sellers;
        }
    }
}
