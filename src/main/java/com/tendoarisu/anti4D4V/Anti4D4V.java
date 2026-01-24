package com.tendoarisu.anti4D4V;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Anti4D4V extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        getCommand("anti4d4v").setExecutor(this);
        getCommand("anti4d4v").setTabCompleter(this);

        getLogger().info("Anti4D4V 插件已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info("Anti4D4V 插件已禁用！");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("anti4d4v.admin")) {
                sender.sendMessage(Component.text("您没有权限执行此命令。", NamedTextColor.RED));
                return true;
            }
            configManager.loadConfig();
            sender.sendMessage(Component.text("Anti4D4V 配置已重载！", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("使用方法: /anti4d4v reload", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("reload");
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
