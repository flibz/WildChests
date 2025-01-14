package com.bgsoftware.wildchests.hooks;

import com.bgsoftware.wildchests.WildChestsPlugin;
import com.bgsoftware.wildchests.api.hooks.PricesProvider;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.IEssentials;
import com.earth2me.essentials.Worth;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

public final class PricesProvider_Essentials implements PricesProvider {

    public PricesProvider_Essentials() {
        WildChestsPlugin.log("- Using Essentials as PricesProvider");
    }

    @Override
    public double getPrice(OfflinePlayer offlinePlayer, ItemStack itemStack) {
        Essentials plugin = Essentials.getPlugin(Essentials.class);
        Worth worth = plugin.getWorth();
        BigDecimal price = null;

        try {
            price = worth.getPrice(itemStack);
        } catch (Throwable ex) {
            try {
                price = (BigDecimal) worth.getClass().getMethod("getPrice", IEssentials.class, ItemStack.class)
                        .invoke(worth, plugin, itemStack);
            } catch (Exception ignored) {
            }
        }

        return price == null ? -1 : price.doubleValue() * itemStack.getAmount();
    }

}
