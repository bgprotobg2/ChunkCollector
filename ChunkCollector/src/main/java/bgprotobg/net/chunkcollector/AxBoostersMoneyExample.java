package bgprotobg.net.chunkcollector;

import com.artillexstudios.axboosters.hooks.booster.BoosterHook;
import org.bukkit.event.Listener;

public class AxBoostersMoneyExample implements Listener, BoosterHook {

    @Override
    public String getName() {
        return "axboosters:money";
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}

