package com.littlesheep;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;

public class ConfirmationListener implements Listener {

    private final CurrencyExchange plugin;
    private final Player player;
    private final double currency;
    private final double points;
    private final boolean currencyToPoints;

    public ConfirmationListener(CurrencyExchange plugin, Player player, double currency, double points, boolean currencyToPoints) {
        this.plugin = plugin;
        this.player = player;
        this.currency = currency;
        this.points = points;
        this.currencyToPoints = currencyToPoints;
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        if (event.getPlayer().equals(player)) {
            String message = event.getMessage();
            if (message.equalsIgnoreCase("yes")) {
                if (currencyToPoints) {
                    if (plugin.getEconomy().getBalance(player) >= currency) {
                        plugin.getEconomy().withdrawPlayer(player, currency);
                        plugin.getPlayerPointsAPI().give(player.getUniqueId(), (int) points);
                        player.sendMessage(plugin.getLangMessage("successCurrencyToPoints")
                                .replace("%currency%", String.valueOf(currency))
                                .replace("%points%", String.valueOf(points)));
                    } else {
                        player.sendMessage(plugin.getLangMessage("notEnoughCurrency"));
                    }
                } else {
                    if (plugin.getPlayerPointsAPI().look(player.getUniqueId()) >= points) {
                        plugin.getPlayerPointsAPI().take(player.getUniqueId(), (int) points);
                        plugin.getEconomy().depositPlayer(player, currency);
                        player.sendMessage(plugin.getLangMessage("successPointsToCurrency")
                                .replace("%points%", String.valueOf(points))
                                .replace("%currency%", String.valueOf(currency)));
                    } else {
                        player.sendMessage(plugin.getLangMessage("notEnoughPoints"));
                    }
                }
            } else {
                player.sendMessage(plugin.getLangMessage("exchangeCancelled"));
            }

            HandlerList.unregisterAll(this);
            event.setCancelled(true);
        }
    }
}
