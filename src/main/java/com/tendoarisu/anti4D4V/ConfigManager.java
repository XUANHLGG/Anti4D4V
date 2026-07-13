package com.tendoarisu.anti4D4V;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Level;
import java.io.IOException;

public class ConfigManager {
    private final Anti4D4V plugin;
    private List<String> bannedPrefixes;
    private List<Pattern> bannedNamePatterns;
    private List<Pattern> bannedChatPatterns;
    private List<String> bannedNameRegexStrings;
    private List<String> bannedChatRegexStrings;
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

        // 保存原始字符串列表
        this.bannedNameRegexStrings = config.getStringList("banned-name-regex");
        this.bannedChatRegexStrings = config.getStringList("banned-chat-regex");

        // 预编译名字正则
        this.bannedNamePatterns = compilePatterns(bannedNameRegexStrings, "banned-name-regex");

        // 预编译聊天正则
        this.bannedChatPatterns = compilePatterns(bannedChatRegexStrings, "banned-chat-regex");

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

    public void saveConfig() {
        FileConfiguration config = plugin.getConfig();
        config.set("banned-name-prefixes", bannedPrefixes);
        config.set("banned-name-regex", bannedNameRegexStrings);
        config.set("banned-chat-regex", bannedChatRegexStrings);
        config.set("action", action);
        config.set("broadcast", broadcast);
        config.set("messages.kick-reason", kickReason);
        config.set("messages.broadcast-msg", broadcastMsg);
        try {
            plugin.saveConfig();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "保存配置文件失败", e);
        }
    }

    public boolean addChatBlacklist(String regex) {
        try {
            Pattern.compile(regex);
            if (!bannedChatRegexStrings.contains(regex)) {
                bannedChatRegexStrings.add(regex);
                bannedChatPatterns = compilePatterns(bannedChatRegexStrings, "banned-chat-regex");
                saveConfig();
                return true;
            }
            return false;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    public boolean removeChatBlacklist(String regex) {
        if (bannedChatRegexStrings.remove(regex)) {
            bannedChatPatterns = compilePatterns(bannedChatRegexStrings, "banned-chat-regex");
            saveConfig();
            return true;
        }
        return false;
    }

    public List<String> getChatBlacklist() {
        return new ArrayList<>(bannedChatRegexStrings);
    }

    public void setAction(String action) {
        this.action = action.toUpperCase();
        saveConfig();
    }

    public List<String> getBannedPrefixes() { return bannedPrefixes; }
    public List<Pattern> getBannedNamePatterns() { return bannedNamePatterns; }
    public List<Pattern> getBannedChatPatterns() { return bannedChatPatterns; }
    public String getAction() { return action; }
    public boolean isBroadcast() { return broadcast; }
    public String getKickReason() { return kickReason; }
    public String getBroadcastMsg() { return broadcastMsg; }
}
