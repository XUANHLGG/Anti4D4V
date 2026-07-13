package com.tendoarisu.anti4D4V;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Anti4D4V extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ConfigManager configManager;
    private PacketLossManager packetLossManager;
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.packetLossManager = new PacketLossManager(this);
        FoliaScheduler.init(this);

        // 尝试加载LuckPerms
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                this.luckPerms = LuckPermsProvider.get();
                getLogger().info("已成功挂钩到 LuckPerms！");
            } catch (Exception e) {
                getLogger().warning("LuckPerms 已安装但无法加载其API。");
            }
        } else {
            getLogger().warning("未检测到 LuckPerms，权限豁免功能将不可用。");
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getCommand("anti4d4v").setExecutor(this);
        getCommand("anti4d4v").setTabCompleter(this);

        getLogger().info("Anti4D4V 插件已启用！");
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

    /**
     * 检查玩家是否拥有豁免权限
     */
    public boolean hasExemptPermission(UUID uuid) {
        if (luckPerms == null) {
            return false;
        }
        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user == null) {
                return false;
            }
            return user.getCachedData().getPermissionData().checkPermission("anti4d4v.exempt").asBoolean();
        } catch (Exception e) {
            getLogger().warning("检查权限时出错: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("anti4d4v.admin")) {
            sender.sendMessage(Component.text("您没有权限执行此命令。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                configManager.loadConfig();
                sender.sendMessage(Component.text("Anti4D4V 配置已重载！", NamedTextColor.GREEN));
                return true;

            case "blacklist":
                return handleBlacklistCommand(sender, args);

            case "action":
                return handleActionCommand(sender, args);

            case "packetloss":
                return handlePacketLossCommand(sender, args);

            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(Component.text("=== Anti4D4V 帮助 ===", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("- /anti4d4v reload", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- /anti4d4v blacklist <add|remove|list> [正则表达式]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- /anti4d4v action <BAN_IP|BAN|KICK>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- /anti4d4v packetloss <add|remove|list> [玩家]", NamedTextColor.WHITE));
    }

    private boolean handleBlacklistCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /anti4d4v blacklist <add|remove|list> [正则表达式]", NamedTextColor.YELLOW));
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 3) {
                    sender.sendMessage(Component.text("请提供要添加的正则表达式。", NamedTextColor.RED));
                    return true;
                }
                String addRegex = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (configManager.addChatBlacklist(addRegex)) {
                    sender.sendMessage(Component.text("已添加聊天黑名单: " + addRegex, NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("添加失败：正则表达式无效或已存在。", NamedTextColor.RED));
                }
                return true;

            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(Component.text("请提供要移除的正则表达式。", NamedTextColor.RED));
                    return true;
                }
                String removeRegex = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (configManager.removeChatBlacklist(removeRegex)) {
                    sender.sendMessage(Component.text("已移除聊天黑名单: " + removeRegex, NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("移除失败：未找到该正则表达式。", NamedTextColor.RED));
                }
                return true;

            case "list":
                List<String> blacklist = configManager.getChatBlacklist();
                if (blacklist.isEmpty()) {
                    sender.sendMessage(Component.text("当前没有聊天黑名单。", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("=== 聊天黑名单 ===", NamedTextColor.YELLOW));
                    for (int i = 0; i < blacklist.size(); i++) {
                        sender.sendMessage(Component.text((i + 1) + ". " + blacklist.get(i), NamedTextColor.WHITE));
                    }
                }
                return true;

            default:
                sender.sendMessage(Component.text("用法: /anti4d4v blacklist <add|remove|list> [正则表达式]", NamedTextColor.YELLOW));
                return true;
        }
    }

    private boolean handleActionCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("当前措施: " + configManager.getAction(), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("用法: /anti4d4v action <BAN_IP|BAN|KICK>", NamedTextColor.YELLOW));
            return true;
        }

        String newAction = args[1].toUpperCase();
        if (!newAction.equals("BAN_IP") && !newAction.equals("BAN") && !newAction.equals("KICK")) {
            sender.sendMessage(Component.text("无效的措施。可用选项: BAN_IP, BAN, KICK", NamedTextColor.RED));
            return true;
        }

        configManager.setAction(newAction);
        sender.sendMessage(Component.text("已将处罚措施设置为: " + newAction, NamedTextColor.GREEN));
        return true;
    }

    private boolean handlePacketLossCommand(CommandSender sender, String[] args) {
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
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("玩家不在线。", NamedTextColor.RED));
                return true;
            }
            packetLossManager.addPlayer(target);
            sender.sendMessage(Component.text("已将玩家 " + target.getName() + " 加入丢包模式。", NamedTextColor.GREEN));
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
        sender.sendMessage(Component.text("用法: /anti4d4v packetloss <add|remove|list> [玩家]", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("anti4d4v.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = Arrays.asList("reload", "blacklist", "action", "packetloss");
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("blacklist")) {
                List<String> subs = Arrays.asList("add", "remove", "list");
                return subs.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("action")) {
                List<String> actions = Arrays.asList("BAN_IP", "BAN", "KICK");
                return actions.stream()
                        .filter(s -> s.startsWith(args[1].toUpperCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("packetloss")) {
                List<String> subs = Arrays.asList("add", "remove", "list");
                return subs.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("packetloss")) {
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
            } else if (args[0].equalsIgnoreCase("blacklist") && args[1].equalsIgnoreCase("remove")) {
                // 为remove命令提供现有黑名单的补全
                return configManager.getChatBlacklist().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}
