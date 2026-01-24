package com.tendoarisu.anti4D4V;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.net.InetAddress;
import java.util.regex.Pattern;

public class PlayerListener implements Listener {
    private final Anti4D4V plugin;

    public PlayerListener(Anti4D4V plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        ConfigManager config = plugin.getConfigManager();

        // 检查前缀
        for (String prefix : config.getBannedPrefixes()) {
            if (name.toLowerCase().startsWith(prefix.toLowerCase())) {
                handleViolation(event, name, event.getAddress());
                return;
            }
        }

        // 检查名字正则
        for (Pattern pattern : config.getBannedNamePatterns()) {
            if (pattern.matcher(name).matches()) {
                handleViolation(event, name, event.getAddress());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        ConfigManager config = plugin.getConfigManager();

        for (Pattern pattern : config.getBannedChatPatterns()) {
            if (pattern.matcher(message).find()) {
                event.setCancelled(true);
                // 异步聊天事件中执行封禁需要回到主线程
                Bukkit.getScheduler().runTask(plugin, () -> {
                    executeAction(player.getName(), player.getAddress().getAddress(), player);
                });
                return;
            }
        }
    }

    private void handleViolation(AsyncPlayerPreLoginEvent event, String name, InetAddress address) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text(plugin.getConfigManager().getKickReason()));
        
        // 异步登录事件中执行封禁动作
        Bukkit.getScheduler().runTask(plugin, () -> {
            executeAction(name, address, null);
        });
    }

    private void executeAction(String name, InetAddress address, Player player) {
        ConfigManager config = plugin.getConfigManager();
        String action = config.getAction();
        Component kickReason = Component.text(config.getKickReason());

        switch (action) {
            case "BAN_IP":
                if (address != null) {
                    Bukkit.banIP(address.getHostAddress());
                }
                Bukkit.getBanList(BanList.Type.NAME).addBan(name, config.getKickReason(), null, "Anti4D4V");
                if (player != null && player.isOnline()) {
                    player.kick(kickReason);
                }
                break;
            case "BAN":
                Bukkit.getBanList(BanList.Type.NAME).addBan(name, config.getKickReason(), null, "Anti4D4V");
                if (player != null && player.isOnline()) {
                    player.kick(kickReason);
                }
                break;
            case "KICK":
            default:
                if (player != null && player.isOnline()) {
                    player.kick(kickReason);
                }
                break;
        }

        if (config.isBroadcast()) {
            String msg = config.getBroadcastMsg().replace("%player%", name);
            Bukkit.broadcast(Component.text(msg));
        }
    }
}
