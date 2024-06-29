package com.littlesheep;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {

    private final FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.config = plugin.getConfig();
    }

    public boolean useCustomRate() {
        return config.getBoolean("useCustomRate", false);
    }

    public double getCustomRate() {
        return config.getDouble("customRate", 100.0);
    }

    public String getSourceCountry() {
        return config.getString("sourceCountry", "USD");
    }

    public String getTargetCountry() {
        return config.getString("targetCountry", "CNY");
    }

    public int getUpdateInterval() {
        return config.getInt("updateInterval", 86400); // 24小时更新一次
    }
}
