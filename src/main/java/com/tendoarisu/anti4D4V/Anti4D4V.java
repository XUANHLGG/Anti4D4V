package com.tendoarisu.anti4D4V;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Anti4D4V extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ConfigManager configManager;
    private PacketLossManager packetLossManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.packetLossManager = new PacketLossManager(this);
        FoliaScheduler.init(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        getCommand("anti4d4v").setExecutor(this);
        getCommand("anti4d4v").setTabCompleter(this);

        getLogger().info("Anti4D4V 插件已启用！");

        // 插件启动时，为所有在线且在列表中的玩家注入拦截器
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (packetLossManager.isPacketLoss(player.getUniqueId())) {
                packetLossManager.injectPlayer(player);
            }
        }
    }

    @Override
    public void onDisable() {
        if (packetLossManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                packetLossManager.uninjectPlayer(player);
            }
        }
        getLogger().info("Anti4D4V 插件已禁用！");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PacketLossManager getPacketLossManager() {
        return packetLossManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("anti4d4v.admin")) {
                    sender.sendMessage(Component.text("您没有权限执行此命令。", NamedTextColor.RED));
                    return true;
                }
                configManager.loadConfig();
                sender.sendMessage(Component.text("Anti4D4V 配置已重载！", NamedTextColor.GREEN));
                return true;
            }

            if (args[0].equalsIgnoreCase("packetloss")) {
                if (!sender.hasPermission("anti4d4v.admin")) {
                    sender.sendMessage(Component.text("您没有权限执行此命令。", NamedTextColor.RED));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(Component.text("用法: /anti4d4v packetloss <add|remove|list> [玩家]", NamedTextColor.YELLOW));
                    return true;
                }

                String sub = args[1].toLowerCase();
                if (sub.equals("add")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("请指定玩家名。", NamedTextColor.RED));
                        return true;
                    }
                    String targetName = args[2];
                    UUID targetUUID = null;
                    
                    // 尝试从在线玩家获取
                    Player target = Bukkit.getPlayer(targetName);
                    if (target != null) {
                        targetUUID = target.getUniqueId();
                    } else {
                        // 尝试从离线玩家获取
                        @SuppressWarnings("deprecation")
                        org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                        if (offlineTarget.hasPlayedBefore() || offlineTarget.isOnline()) {
                            targetUUID = offlineTarget.getUniqueId();
                        } else {
                            sender.sendMessage(Component.text("找不到玩家: " + targetName + " (该玩家从未进入过服务器)", NamedTextColor.RED));
                            return true;
                        }
                    }
                    
                    if (packetLossManager.isPacketLoss(targetUUID)) {
                        sender.sendMessage(Component.text("玩家 " + targetName + " 已经在丢包模式中。", NamedTextColor.YELLOW));
                        return true;
                    }
                    
                    packetLossManager.addPlayer(targetUUID);
                    sender.sendMessage(Component.text("已将玩家 " + targetName + " 加入丢包模式。", NamedTextColor.GREEN));
                    return true;
                } else if (sub.equals("remove")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("请指定玩家名。", NamedTextColor.RED));
                        return true;
                    }
                    String targetName = args[2];
                    UUID targetUUID = null;
                    
                    // 先尝试从在线玩家找
                    Player onlineTarget = Bukkit.getPlayer(targetName);
                    if (onlineTarget != null) {
                        targetUUID = onlineTarget.getUniqueId();
                    } else {
                        // 否则从丢包列表中通过名字匹配
                        for (UUID uuid : packetLossManager.getPacketLossPlayers()) {
                            if (targetName.equalsIgnoreCase(Bukkit.getOfflinePlayer(uuid).getName())) {
                                targetUUID = uuid;
                                break;
                            }
                        }
                    }

                    if (targetUUID == null) {
                        sender.sendMessage(Component.text("找不到处于丢包模式的玩家: " + targetName, NamedTextColor.RED));
                        return true;
                    }
                    
                    packetLossManager.removePlayer(targetUUID);
                    sender.sendMessage(Component.text("已将玩家 " + targetName + " 移出丢包模式。", NamedTextColor.GREEN));
                    return true;
                } else if (sub.equals("list")) {
                    Set<UUID> players = packetLossManager.getPacketLossPlayers();
                    if (players.isEmpty()) {
                        sender.sendMessage(Component.text("当前没有玩家处于丢包模式。", NamedTextColor.YELLOW));
                        return true;
                    }
                    sender.sendMessage(Component.text("当前处于丢包模式的玩家:", NamedTextColor.YELLOW));
                    for (UUID uuid : players) {
                        sender.sendMessage(Component.text("- " + Bukkit.getOfflinePlayer(uuid).getName(), NamedTextColor.WHITE));
                    }
                    return true;
                }
            }

            if (args[0].equalsIgnoreCase("iprange")) {
                if (!sender.hasPermission("anti4d4v.admin")) {
                    sender.sendMessage(Component.text("您没有权限执行此命令。", NamedTextColor.RED));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(Component.text("用法: /anti4d4v iprange <add|remove|list> [IP段]", NamedTextColor.YELLOW));
                    return true;
                }

                String sub = args[1].toLowerCase();
                FileConfiguration config = getConfig();
                List<String> ranges = new ArrayList<>(config.getStringList("banned-ip-ranges"));

                if (sub.equals("add")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("请指定 IP 段，例如 113.135.x.x、36.4x.x.x 或 113.135.0.0/16。", NamedTextColor.RED));
                        return true;
                    }

                    String range = args[2].trim();
                    if (!configManager.isValidIpRange(range)) {
                        sender.sendMessage(Component.text("IP 段格式无效，支持 x 通配符和 CIDR，例如 113.135.x.x、36.4x.x.x、1.2.3.x、113.135.0.0/16。", NamedTextColor.RED));
                        return true;
                    }

                    for (String existing : ranges) {
                        if (existing.equalsIgnoreCase(range)) {
                            sender.sendMessage(Component.text("IP 段 " + range + " 已存在。", NamedTextColor.YELLOW));
                            return true;
                        }
                    }

                    ranges.add(range);
                    config.set("banned-ip-ranges", ranges);
                    saveConfig();
                    configManager.loadConfig();
                    sender.sendMessage(Component.text("已添加 IP 段封禁规则: " + range, NamedTextColor.GREEN));
                    return true;
                } else if (sub.equals("remove")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("请指定要移除的 IP 段。", NamedTextColor.RED));
                        return true;
                    }

                    String range = args[2].trim();
                    boolean removed = ranges.removeIf(existing -> existing.equalsIgnoreCase(range));
                    if (!removed) {
                        sender.sendMessage(Component.text("找不到 IP 段封禁规则: " + range, NamedTextColor.RED));
                        return true;
                    }

                    config.set("banned-ip-ranges", ranges);
                    saveConfig();
                    configManager.loadConfig();
                    sender.sendMessage(Component.text("已移除 IP 段封禁规则: " + range, NamedTextColor.GREEN));
                    return true;
                } else if (sub.equals("list")) {
                    if (ranges.isEmpty()) {
                        sender.sendMessage(Component.text("当前没有 IP 段封禁规则。", NamedTextColor.YELLOW));
                        return true;
                    }

                    sender.sendMessage(Component.text("当前 IP 段封禁规则:", NamedTextColor.YELLOW));
                    for (String range : ranges) {
                        sender.sendMessage(Component.text("- " + range, NamedTextColor.WHITE));
                    }
                    return true;
                }
            }
        }
        sender.sendMessage(Component.text("使用方法:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("- /anti4d4v reload", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- /anti4d4v packetloss <add|remove|list> [玩家]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- /anti4d4v iprange <add|remove|list> [IP段]", NamedTextColor.WHITE));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("reload");
            completions.add("packetloss");
            completions.add("iprange");
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("packetloss")) {
            List<String> subs = new ArrayList<>();
            subs.add("add");
            subs.add("remove");
            subs.add("list");
            return subs.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("packetloss")) {
            if (args[1].equalsIgnoreCase("add")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[1].equalsIgnoreCase("remove")) {
                List<String> players = packetLossManager.getPacketLossPlayers().stream()
                        .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                        .filter(name -> name != null && name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                return players;
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("iprange")) {
            List<String> subs = new ArrayList<>();
            subs.add("add");
            subs.add("remove");
            subs.add("list");
            return subs.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("iprange") && args[1].equalsIgnoreCase("remove")) {
            return configManager.getBannedIpRanges().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
