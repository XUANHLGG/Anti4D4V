package com.tendoarisu.anti4D4V;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PacketLossManager {
    private final Anti4D4V plugin;
    private final Set<UUID> packetLossPlayers = new HashSet<>();
    private final Map<UUID, Channel> injectedChannels = new ConcurrentHashMap<>();

    public PacketLossManager(Anti4D4V plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    private void loadFromConfig() {
        java.util.List<String> uuids = plugin.getConfig().getStringList("packet-loss-players");
        for (String s : uuids) {
            try {
                packetLossPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveToConfig() {
        java.util.List<String> uuids = new java.util.ArrayList<>();
        for (UUID uuid : packetLossPlayers) {
            uuids.add(uuid.toString());
        }
        plugin.getConfig().set("packet-loss-players", uuids);
        plugin.saveConfig();
    }

    public void addPlayer(UUID uuid) {
        packetLossPlayers.add(uuid);
        saveToConfig();
        Player onlinePlayer = org.bukkit.Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            injectPlayer(onlinePlayer);
        }
    }

    public void removePlayer(UUID uuid) {
        packetLossPlayers.remove(uuid);
        saveToConfig();
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null) {
            uninjectPlayer(player);
        }
    }

    public boolean isPacketLoss(UUID uuid) {
        return packetLossPlayers.contains(uuid);
    }

    public Set<UUID> getPacketLossPlayers() {
        return new HashSet<>(packetLossPlayers);
    }

    public void injectPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Field connectionField = findField(handle.getClass(), "connection");
            if (connectionField == null) {
                plugin.getLogger().warning("Could not find connection field for " + player.getName());
                return;
            }
            Object connection = connectionField.get(handle);
            
            // NetworkManager
            // Paper 1.21.11 使用 Mojang 映射，连接字段可能叫 connection，也可能因服务端构建而变化。
            Field networkManagerField = null;
            for (Field f : connection.getClass().getDeclaredFields()) {
                if (f.getType().getName().contains("NetworkManager") || f.getType().getSimpleName().equals("NetworkManager")) {
                    networkManagerField = f;
                    break;
                }
            }
            
            if (networkManagerField == null) {
                networkManagerField = findField(connection.getClass(), "connection");
            }
            if (networkManagerField == null) {
                plugin.getLogger().warning("Could not find NetworkManager field for " + player.getName());
                return;
            }
            networkManagerField.setAccessible(true);
            Object networkManager = networkManagerField.get(connection);

            // Channel
            Field channelField = null;
            for (Field f : networkManager.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("Channel") || f.getType().getName().equals("io.netty.channel.Channel")) {
                    channelField = f;
                    break;
                }
            }
            if (channelField == null) {
                channelField = findField(networkManager.getClass(), "channel");
            }
            if (channelField == null) {
                plugin.getLogger().warning("Could not find Channel field for " + player.getName());
                return;
            }
            channelField.setAccessible(true);
            Channel channel = (Channel) channelField.get(networkManager);

            if (channel.pipeline().get("anti4d4v_packet_loss") != null) {
                channel.pipeline().remove("anti4d4v_packet_loss");
            }

            // 尝试在 decoder 之后立即拦截，这样可以尽早抓到 Packet 对象，防止被其他 handler（如日志）处理
            String baseHandler = channel.pipeline().get("decoder") != null ? "decoder" : "packet_handler";
            
            ChannelDuplexHandler handler = new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (isPacketLoss(uuid)) {
                        String name = msg.getClass().getName();
                        // 允许心跳包和客户端状态包（复活/请求统计等）
                        if (name.contains("KeepAlive") || name.contains("PacketPlayInKeepAlive") || 
                            name.contains("ClientCommand") || name.contains("PacketPlayInClientCommand")) {
                            super.channelRead(ctx, msg);
                            return;
                        }
                        // 丢弃其他所有输入包 (Packet 对象)
                        // 如果是 ByteBuf，我们暂不处理，因为 decoder 之后应该是 Packet
                        if (!(msg instanceof io.netty.buffer.ByteBuf)) {
                            return;
                        }
                    }
                    super.channelRead(ctx, msg);
                }
            };

            if (baseHandler.equals("decoder")) {
                channel.pipeline().addAfter("decoder", "anti4d4v_packet_loss", handler);
            } else {
                channel.pipeline().addBefore("packet_handler", "anti4d4v_packet_loss", handler);
            }

            injectedChannels.put(uuid, channel);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to inject packet loss handler for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void uninjectPlayer(Player player) {
        Channel channel = injectedChannels.remove(player.getUniqueId());
        if (channel != null && channel.pipeline().get("anti4d4v_packet_loss") != null) {
            try {
                channel.pipeline().remove("anti4d4v_packet_loss");
            } catch (Exception ignored) {}
        }
    }

    private Field findField(Class<?> clazz, String name) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
        }
        return null;
    }
}
