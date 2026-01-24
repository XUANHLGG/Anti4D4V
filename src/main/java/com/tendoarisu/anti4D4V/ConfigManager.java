package com.tendoarisu.anti4D4V;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Level;

public class ConfigManager {
    private final Anti4D4V plugin;
    private List<String> bannedPrefixes;
    private List<Pattern> bannedNamePatterns;
    private List<Pattern> bannedChatPatterns;
    private String action;
    private boolean broadcast;
    private String kickReason;
    private String broadcastMsg;

    public ConfigManager(Anti4D4V plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.bannedPrefixes = config.getStringList("banned-name-prefixes");
        this.action = config.getString("action", "BAN_IP").toUpperCase();
        this.broadcast = config.getBoolean("broadcast", true);
        this.kickReason = config.getString("messages.kick-reason", "Security Kick");
        this.broadcastMsg = config.getString("messages.broadcast-msg", "Player %player% was blocked.");

        // 预编译名字正则
        this.bannedNamePatterns = compilePatterns(config.getStringList("banned-name-regex"), "banned-name-regex");
        
        // 预编译聊天正则
        this.bannedChatPatterns = compilePatterns(config.getStringList("banned-chat-regex"), "banned-chat-regex");
        
        plugin.getLogger().info("配置已加载，预编译了 " + bannedNamePatterns.size() + " 个名字正则和 " + bannedChatPatterns.size() + " 个聊天正则。");
    }

    private List<Pattern> compilePatterns(List<String> regexList, String path) {
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : regexList) {
            try {
                patterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                plugin.getLogger().log(Level.SEVERE, "配置文件中 " + path + " 的正则表达式异常: " + regex, e);
            }
        }
        return patterns;
    }

    public List<String> getBannedPrefixes() { return bannedPrefixes; }
    public List<Pattern> getBannedNamePatterns() { return bannedNamePatterns; }
    public List<Pattern> getBannedChatPatterns() { return bannedChatPatterns; }
    public String getAction() { return action; }
    public boolean isBroadcast() { return broadcast; }
    public String getKickReason() { return kickReason; }
    public String getBroadcastMsg() { return broadcastMsg; }
}
