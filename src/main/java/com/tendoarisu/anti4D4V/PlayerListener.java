package com.tendoarisu.anti4D4V;

import io.papermc.paper.ban.BanListType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ban.IpBanList;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class PlayerListener implements Listener {
    private static final String NOTIFY_PERMISSION = "anti4d4v.notify";
    private static final String BAN_SOURCE = "Anti4D4V";
    private final Anti4D4V plugin;

    public PlayerListener(Anti4D4V plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        ConfigManager config = plugin.getConfigManager();

        // 检查是否有豁免权限（通过UUID检查）
        if (plugin.hasExemptPermission(event.getUniqueId())) {
            return;
        }

        if (config.isIpBanned(event.getAddress())) {
            handleIpRangeViolation(event, name, event.getAddress());
            return;
        }

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
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPacketLossManager().isPacketLoss(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // 检查是否有豁免权限
        if (player.hasPermission("anti4d4v.exempt")) {
            return;
        }

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        ConfigManager config = plugin.getConfigManager();

        for (Pattern pattern : config.getBannedChatPatterns()) {
            if (pattern.matcher(message).find()) {
                event.setCancelled(true);
                // 异步聊天事件中执行封禁需要回到主线程
                FoliaScheduler.getScheduler().runTask(() -> {
                    executeAction(player.getUniqueId(), player.getName(), player.getAddress().getAddress(), player);
                });
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.getPacketLossManager().isPacketLoss(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getPacketLossManager().isPacketLoss(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getPacketLossManager().isPacketLoss(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getPacketLossManager().isPacketLoss(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (plugin.getPacketLossManager().isPacketLoss(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getPacketLossManager().isPacketLoss(event.getPlayer().getUniqueId())) {
            plugin.getPacketLossManager().injectPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPacketLossManager().uninjectPlayer(event.getPlayer());
    }

    private void handleIpRangeViolation(AsyncPlayerPreLoginEvent event, String name, InetAddress address) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text(plugin.getConfigManager().getKickReason()));

        FoliaScheduler.getScheduler().runTask(() -> {
            executeIpRangeBan(event.getUniqueId(), name, address, null);
        });
    }

    private void handleViolation(AsyncPlayerPreLoginEvent event, String name, InetAddress address) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text(plugin.getConfigManager().getKickReason()));
        
        // 异步登录事件中执行封禁动作
        FoliaScheduler.getScheduler().runTask(() -> {
            executeAction(event.getUniqueId(), name, address, null);
        });
    }

    private void executeIpRangeBan(UUID uuid, String name, InetAddress address, Player player) {
        ConfigManager config = plugin.getConfigManager();
        Component kickReason = Component.text(config.getKickReason());

        if (address != null) {
            banIp(address, config.getKickReason());
        }
        banProfile(uuid, name, config.getKickReason());
        if (player != null && player.isOnline()) {
            player.kick(kickReason);
        }

        if (config.isBroadcast()) {
            String msg = config.getBroadcastMsg().replace("%player%", name);
            sendInterceptNotification(msg);
        }
    }

    private void executeAction(UUID uuid, String name, InetAddress address, Player player) {
        ConfigManager config = plugin.getConfigManager();
        List<String> actions = config.getActions();
        Component kickReason = Component.text(config.getKickReason());

        if (actions.contains("BAN_IP")) {
            if (address != null) {
                banIp(address, config.getKickReason());
            }
            banProfile(uuid, name, config.getKickReason());
        } else if (actions.contains("BAN")) {
            banProfile(uuid, name, config.getKickReason());
        }

        if (actions.contains("BAN_IP") || actions.contains("BAN") || actions.contains("KICK")) {
            if (player != null && player.isOnline()) {
                player.kick(kickReason);
            }
        }

        if (config.isBroadcast()) {
            String msg = config.getBroadcastMsg().replace("%player%", name);
            sendInterceptNotification(msg);
        }
    }

    private void banIp(InetAddress address, String reason) {
        IpBanList ipBans = Bukkit.getBanList(BanListType.IP);
        ipBans.addBan(address, reason, (Instant) null, BAN_SOURCE);
    }

    private void banProfile(UUID uuid, String name, String reason) {
        ProfileBanList profileBans = Bukkit.getBanList(BanListType.PROFILE);
        profileBans.addBan(Bukkit.createProfile(uuid, name), reason, (Instant) null, BAN_SOURCE);
    }

    private void sendInterceptNotification(String msg) {
        Component message = Component.text(msg);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission(NOTIFY_PERMISSION)) {
                onlinePlayer.sendMessage(message);
            }
        }
    }
}
