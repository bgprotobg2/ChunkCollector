package bgprotobg.net.chunkcollector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SellWand implements CommandExecutor, Listener {

    private final JavaPlugin plugin;

    public SellWand(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();

        if (command.getName().equalsIgnoreCase("sellwand")) {
            if (args.length == 6 && args[0].equalsIgnoreCase("give")) {
                if (!sender.hasPermission("sellwand.give")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no_permission")));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    String rarity = args[2].toLowerCase();
                    int amount;
                    double multiplier;
                    int uses;

                    try {
                        amount = Integer.parseInt(args[3]);
                        multiplier = Double.parseDouble(args[4]);
                        if (args[5].equalsIgnoreCase("infinity")) {
                            uses = -1;
                        } else {
                            uses = Integer.parseInt(args[5]);
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.invalid_number_format")));
                        return false;
                    }

                    if (!isValidRarity(rarity)) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.invalid_rarity")));
                        return false;
                    }

                    ItemStack sellWand = createSellWand(rarity, multiplier, uses);
                    for (int i = 0; i < amount; i++) {
                        target.getInventory().addItem(sellWand);
                    }
                    String message = config.getString("messages.sellwand_given").replace("%player%", target.getName());
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    return true;
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player_not_found")));
                    return false;
                }
            }
        }
        return false;
    }

    private boolean isValidRarity(String rarity) {
        return rarity.equals("common") || rarity.equals("rare") || rarity.equals("legendary");
    }

    public ItemStack createSellWand(String rarity, double multiplier, int uses) {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString("sellwand.material", "STICK");
        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null) {
            material = Material.STICK;
        }

        ItemStack sellWand = new ItemStack(material);
        ItemMeta meta = sellWand.getItemMeta();
        if (meta != null) {
            String displayName = ChatColor.translateAlternateColorCodes('&', config.getString("sellwand." + rarity + ".name"));
            meta.setDisplayName(displayName);
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            int customModelData = config.getInt("sellwand." + rarity + ".custom_model_data", -1);
            if (customModelData != -1) {
                meta.setCustomModelData(customModelData);
            }

            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList("sellwand." + rarity + ".lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line
                        .replace("%multiplier%", String.valueOf(multiplier))
                        .replace("%uses%", uses == -1 ? "Infinity Uses" : String.valueOf(uses))
                        .replace("%earnings%", formatPrice(0))));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "multiplier"), PersistentDataType.DOUBLE, multiplier);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "uses"), PersistentDataType.INTEGER, uses);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rarity"), PersistentDataType.STRING, rarity);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "earnings"), PersistentDataType.DOUBLE, 0.0);
            sellWand.setItemMeta(meta);
        }
        return sellWand;
    }

    public double getMultiplier(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            return meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "multiplier"), PersistentDataType.DOUBLE, 1.0);
        }
        return 1.0;
    }

    public int getUses(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Integer uses = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "uses"), PersistentDataType.INTEGER);
            if (uses != null) {
                return uses;
            }
        }
        return 0;
    }

    private String formatPrice(double price) {
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


    public double getEarnings(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Double earnings = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "earnings"), PersistentDataType.DOUBLE);
            if (earnings != null) {
                return earnings;
            }
        }
        return 0.0;
    }

    public void setEarnings(ItemStack item, double earnings) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "earnings"), PersistentDataType.DOUBLE, earnings);
            List<String> lore = meta.getLore();
            if (lore != null) {
                String rarity = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "rarity"), PersistentDataType.STRING);
                if (rarity != null) {
                    List<String> configLore = plugin.getConfig().getStringList("sellwand." + rarity + ".lore");
                    lore.clear();
                    for (String line : configLore) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', line
                                .replace("%multiplier%", String.valueOf(getMultiplier(item)))
                                .replace("%uses%", getUses(item) == -1 ? "Infinity Uses" : String.valueOf(getUses(item)))
                                .replace("%earnings%", formatPrice(earnings))));
                    }
                    meta.setLore(lore);
                }
            }
            item.setItemMeta(meta);
        }
    }

    public void decrementUses(ItemStack item) {
        int uses = getUses(item);
        if (uses == -1) {
            return;
        }
        setUses(item, uses - 1);
    }

    public void setUses(ItemStack item, int uses) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "uses"), PersistentDataType.INTEGER, uses);
            List<String> lore = meta.getLore();
            if (lore != null) {
                String rarity = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "rarity"), PersistentDataType.STRING);
                if (rarity != null) {
                    List<String> configLore = plugin.getConfig().getStringList("sellwand." + rarity + ".lore");
                    lore.clear();
                    for (String line : configLore) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', line
                                .replace("%multiplier%", String.valueOf(getMultiplier(item)))
                                .replace("%uses%", uses == -1 ? "Infinity Uses" : String.valueOf(uses))
                                .replace("%earnings%", formatPrice(getEarnings(item)))));
                    }
                    meta.setLore(lore);
                }
            }
            item.setItemMeta(meta);
        }
    }

    public boolean isSellWand(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material material = item.getType();
        String materialName = plugin.getConfig().getString("sellwand.material", "STICK").toUpperCase();
        if (material != Material.getMaterial(materialName)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "multiplier"), PersistentDataType.DOUBLE);
    }
}
