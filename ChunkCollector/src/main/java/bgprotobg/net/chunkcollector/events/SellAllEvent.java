package bgprotobg.net.chunkcollector.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SellAllEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final OfflinePlayer offlinePlayer;
    private double sellPrice;

    public SellAllEvent(OfflinePlayer offlinePlayer, double sellPrice) {
        this.offlinePlayer = offlinePlayer;
        this.sellPrice = sellPrice;
    }

    public OfflinePlayer getOfflinePlayer() {
        return offlinePlayer;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = sellPrice;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public boolean isCancelled() {
        return false;
    }
}
