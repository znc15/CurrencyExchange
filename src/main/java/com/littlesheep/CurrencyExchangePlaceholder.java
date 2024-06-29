package com.littlesheep;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CurrencyExchangePlaceholder extends PlaceholderExpansion {

    private final CurrencyExchange plugin;

    public CurrencyExchangePlaceholder(CurrencyExchange plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "currencyexchange";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier.equals("rate")) {
            double gameRate = plugin.getExchangeRate() * plugin.getRateMultiplier();
            return String.valueOf(gameRate);
        }

        return null;
    }
}
