package com.littlesheep;

import com.google.gson.Gson;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class CurrencyExchange extends JavaPlugin implements CommandExecutor, Listener {

    private Economy economy;
    private PlayerPointsAPI playerPointsAPI;
    private double exchangeRate = 100.0; // 默认汇率
    private double rateMultiplier = 1.0; // 汇率倍率
    private ConfigManager configManager;
    private FileConfiguration langConfig;

    private final Map<UUID, ExchangeRequest> pendingRequests = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        // 加载配置文件
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        updateRateMultiplier();

        // 加载语言文件
        loadLangConfig();

        // 初始化 Vault 和 PlayerPoints
        if (!setupEconomy()) {
            getLogger().severe("Vault 插件未找到！插件禁用中...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupPlayerPoints()) {
            getLogger().severe("PlayerPoints 插件未找到！插件禁用中...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Objects.requireNonNull(this.getCommand("rp")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        // 注册 PAPI 扩展
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CurrencyExchangePlaceholder(this).register();
        }

        // 注册 bStats
        if (getConfig().getBoolean("enablebStats", true)) {
            int pluginId = 22433; // bStats插件ID
            new Metrics(this, pluginId);
        }

        // 定时更新汇率，每24小时更新一次
        new BukkitRunnable() {
            @Override
            public void run() {
                updateExchangeRate();
            }
        }.runTaskTimer(this, 0, configManager.getUpdateInterval() * 20L); // 每24小时更新一次

        // 定时更新动态倍率
        if (getConfig().getString("rateMultiplierType", "fixed").equalsIgnoreCase("dynamic")) {
            long interval = getConfig().getLong("dynamicRateMultiplierUpdateInterval", 3600) * 20;
            new BukkitRunnable() {
                @Override
                public void run() {
                    updateRateMultiplier();
                }
            }.runTaskTimer(this, 0, interval); // 按配置的间隔更新动态倍率
        }

        // 检查更新
        if (getConfig().getBoolean("checkForUpdates", true)) {
            checkForUpdates();
        }

        // 输出插件信息
        logPluginInfo();
    }

    private void logPluginInfo() {
        getLogger().info("==========================================");
        getLogger().info(getDescription().getName());
        getLogger().info("Version/版本: " + getDescription().getVersion());
        getLogger().info("Author/作者: " + String.join(", ", getDescription().getAuthors()));
        getLogger().info("QQ Group/QQ群: 690216634");
        getLogger().info("Github: https://github.com/znc15/CurrencyExchange");
        getLogger().info("-v-");
        getLogger().info("==========================================");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private boolean setupPlayerPoints() {
        PlayerPoints playerPoints = (PlayerPoints) getServer().getPluginManager().getPlugin("PlayerPoints");
        if (playerPoints == null) {
            return false;
        }
        playerPointsAPI = playerPoints.getAPI();
        return playerPointsAPI != null;
    }

    private void loadLangConfig() {
        String languageFileName = getConfig().getString("language", "zh_cn") + ".yml";
        File langFile = new File(getDataFolder(), "lang/" + languageFileName);
        if (!langFile.exists()) {
            saveResource("lang/" + languageFileName, false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // 确保en_us.yml文件也被释放
        File enLangFile = new File(getDataFolder(), "lang/en_us.yml");
        if (!enLangFile.exists()) {
            saveResource("lang/en_us.yml", false);
        }
    }

    private void updateRateMultiplier() {
        if (getConfig().getString("rateMultiplierType", "fixed").equalsIgnoreCase("dynamic")) {
            double min = getConfig().getDouble("dynamicRateMultiplierRange.min", 0.5);
            double max = getConfig().getDouble("dynamicRateMultiplierRange.max", 2.0);
            rateMultiplier = min + (max - min) * random.nextDouble();
        } else {
            rateMultiplier = getConfig().getDouble("fixedRateMultiplier", 1.0);
        }
    }

    // 从网络获取汇率
    private void updateExchangeRate() {
        if (configManager.useCustomRate()) {
            exchangeRate = configManager.getCustomRate();
            return;
        }

        String sourceCountry = configManager.getSourceCountry();
        String targetCountry = configManager.getTargetCountry();
        String urlStr = "https://api.exchangerate-api.com/v4/latest/" + sourceCountry;

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            if (conn.getResponseCode() == 200) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                Map<String, Object> response = new Gson().fromJson(reader, Map.class);
                Map<String, Double> rates = (Map<String, Double>) response.get("rates");
                exchangeRate = rates.get(targetCountry); // 根据目标国家设置汇率
                reader.close();
            }
        } catch (Exception e) {
            getLogger().severe("无法获取汇率: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfigFiles();
            sender.sendMessage(getLangMessage("reloadSuccess"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getLangMessage("nonPlayer"));
                return true;
            }
            Player player = (Player) sender;
            if (pendingRequests.containsKey(player.getUniqueId())) {
                pendingRequests.remove(player.getUniqueId());
                player.sendMessage(getLangMessage("exchangeCancelled"));
            } else {
                player.sendMessage(getLangMessage("noPendingRequest"));
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getLangMessage("nonPlayer"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            handleConfirm(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("rate")) {
            double gameRate = exchangeRate * rateMultiplier;
            player.sendMessage(getLangMessage("currentExchangeRate")
                    .replace("%rate%", String.valueOf(gameRate)));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            player.sendMessage(getLangMessage("helpMessage"));
            return true;
        }

        if (args.length != 2) {
            openMainMenu(player);
            return true;
        }

        String type = args[0];
        double amount;

        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(getLangMessage("invalidAmount"));
            return true;
        }

        if (type.equalsIgnoreCase("v")) {
            handleCurrencyToPoints(player, amount);
        } else if (type.equalsIgnoreCase("p")) {
            handlePointsToCurrency(player, amount);
        } else {
            player.sendMessage(getLangMessage("usage"));
        }

        return true;
    }

    private void reloadConfigFiles() {
        reloadConfig();
        configManager = new ConfigManager(this);
        updateRateMultiplier();
        loadLangConfig();
    }

    private void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(getConfig().getString("gui.title"))));

        ItemStack currencyToPoints = new ItemStack(Material.DIAMOND);
        ItemMeta ctpMeta = currencyToPoints.getItemMeta();
        ctpMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(getConfig().getString("gui.currencyToPoints"))));
        currencyToPoints.setItemMeta(ctpMeta);

        ItemStack pointsToCurrency = new ItemStack(Material.EMERALD);
        ItemMeta ptcMeta = pointsToCurrency.getItemMeta();
        if (ptcMeta != null) {
            ptcMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(getConfig().getString("gui.pointsToCurrency"))));
        }
        pointsToCurrency.setItemMeta(ptcMeta);

        inv.setItem(3, currencyToPoints);
        inv.setItem(5, pointsToCurrency);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(getConfig().getString("gui.title"))))) {
            return;
        }

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String displayName = event.getCurrentItem().getItemMeta().getDisplayName();

        player.closeInventory();

        if (displayName.equals(ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(getConfig().getString("gui.currencyToPoints"))))) {
            player.sendMessage(getLangMessage("enterPointsAmount"));
        } else if (displayName.equals(ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(getConfig().getString("gui.pointsToCurrency"))))) {
            player.sendMessage(getLangMessage("enterCurrencyAmount"));
        }
    }

    private void handleCurrencyToPoints(Player player, double points) {
        double requiredCurrency = points * exchangeRate * rateMultiplier;
        player.sendMessage(getLangMessage("confirmCurrencyToPoints")
                .replace("%currency%", String.valueOf(Math.round(requiredCurrency)))
                .replace("%points%", String.valueOf(Math.round(points))));
        pendingRequests.put(player.getUniqueId(), new ExchangeRequest(requiredCurrency, points, true));
        startTimeout(player);
    }

    private void handlePointsToCurrency(Player player, double currency) {
        double requiredPoints = currency / exchangeRate / rateMultiplier;
        player.sendMessage(getLangMessage("confirmPointsToCurrency")
                .replace("%currency%", String.valueOf(Math.round(currency)))
                .replace("%points%", String.valueOf(Math.round(requiredPoints))));
        pendingRequests.put(player.getUniqueId(), new ExchangeRequest(currency, requiredPoints, false));
        startTimeout(player);
    }

    private void handleConfirm(Player player) {
        ExchangeRequest request = pendingRequests.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage(getLangMessage("noPendingRequest"));
            return;
        }

        if (request.isCurrencyToPoints()) {
            if (economy.getBalance(player) >= Math.round(request.getCurrency())) {
                economy.withdrawPlayer(player, Math.round(request.getCurrency()));
                playerPointsAPI.give(player.getUniqueId(), (int) Math.round(request.getPoints()));
                player.sendMessage(getLangMessage("successCurrencyToPoints")
                        .replace("%currency%", String.valueOf(Math.round(request.getCurrency())))
                        .replace("%points%", String.valueOf(Math.round(request.getPoints()))));
            } else {
                player.sendMessage(getLangMessage("notEnoughCurrency"));
            }
        } else {
            if (playerPointsAPI.look(player.getUniqueId()) >= Math.round(request.getPoints())) {
                playerPointsAPI.take(player.getUniqueId(), (int) Math.round(request.getPoints()));
                economy.depositPlayer(player, Math.round(request.getCurrency()));
                player.sendMessage(getLangMessage("successPointsToCurrency")
                        .replace("%points%", String.valueOf(Math.round(request.getPoints())))
                        .replace("%currency%", String.valueOf(Math.round(request.getCurrency()))));
            } else {
                player.sendMessage(getLangMessage("notEnoughPoints"));
            }
        }
    }

    private void startTimeout(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingRequests.containsKey(player.getUniqueId())) {
                    pendingRequests.remove(player.getUniqueId());
                    player.sendMessage(getLangMessage("requestTimeout"));
                }
            }
        }.runTaskLater(this, 20 * 30); // 30秒后执行
    }

    private void checkForUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://api.tcbmc.cc/update/currencyexchange/update.json");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.connect();

                    if (conn.getResponseCode() == 200) {
                        InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                        Map<String, String> response = new Gson().fromJson(reader, Map.class);
                        String latestVersion = response.get("version");

                        if (latestVersion != null && !latestVersion.isEmpty() && isNewerVersion(latestVersion, getDescription().getVersion())) {
                            getLogger().warning(getLangMessage("updateAvailable"));
                        } else {
                            getLogger().info(getLangMessage("noUpdateAvailable"));
                        }

                        reader.close();
                    } else {
                        getLogger().warning(getLangMessage("updateCheckFailed"));
                    }
                } catch (Exception e) {
                    getLogger().severe("检查更新失败: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(this);
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
        String[] latestParts = latestVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;

            if (latestPart > currentPart) {
                return true;
            } else if (latestPart < currentPart) {
                return false;
            }
        }

        return false;
    }

    public Economy getEconomy() {
        return economy;
    }

    public PlayerPointsAPI getPlayerPointsAPI() {
        return playerPointsAPI;
    }

    public double getExchangeRate() {
        return exchangeRate;
    }

    public double getRateMultiplier() {
        return rateMultiplier;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public String getLangMessage(String key) {
        String message = langConfig.getString("messages." + key);
        return ChatColor.translateAlternateColorCodes('&', message != null ? message : "缺少消息键: " + key);
    }
}
