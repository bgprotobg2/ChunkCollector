package bgprotobg.net.chunkcollector;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class CollectorExpansion extends PlaceholderExpansion {

    private final ChunkCollector plugin;

    public CollectorExpansion(ChunkCollector plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean register() {
        return super.register();
    }

    @Override
    public String getIdentifier() {
        return "collector";
    }

    @Override
    public String getAuthor() {
        return "YourName";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player.isOnline()) {
            Player onlinePlayer = player.getPlayer();
            Block block = plugin.getCollectorBlockForPlayer(onlinePlayer);

            if (block != null) {
                if (identifier.equals("itemcount")) {
                    int itemCount = plugin.getTotalAmount(block);
                    return String.valueOf(itemCount);
                }

                if (identifier.equals("itemsold")) {
                    int totalSold = Integer.parseInt(plugin.getTotalSoldAmount(block));
                    return String.valueOf(totalSold);
                }
            }
        }

        return "0";
    }
}
