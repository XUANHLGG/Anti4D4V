package com.tendoarisu.anti4D4V;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Level;

public class ConfigManager {
    private static final Set<String> VALID_ACTIONS = Set.of("BAN_IP", "BAN", "KICK");

    private final Anti4D4V plugin;
    private List<String> bannedPrefixes;
    private List<Pattern> bannedNamePatterns;
    private List<Pattern> bannedChatPatterns;
    private List<String> bannedNameRegexStrings;
    private List<String> bannedChatRegexStrings;
    private List<String> bannedIpRanges;
    private List<IpRangeMatcher> bannedIpMatchers;
    private List<String> actions;
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
        this.actions = parseActions(config.get("action"));
        this.broadcast = config.getBoolean("broadcast", true);
        this.kickReason = config.getString("messages.kick-reason", "Security Kick");
        this.broadcastMsg = config.getString("messages.broadcast-msg", "Player %player% was blocked.");

        this.bannedNameRegexStrings = config.getStringList("banned-name-regex");
        this.bannedChatRegexStrings = config.getStringList("banned-chat-regex");
        this.bannedIpRanges = new ArrayList<>(config.getStringList("banned-ip-ranges"));

        this.bannedNamePatterns = compilePatterns(bannedNameRegexStrings, "banned-name-regex");
        this.bannedChatPatterns = compilePatterns(bannedChatRegexStrings, "banned-chat-regex");
        this.bannedIpMatchers = compileIpRanges(bannedIpRanges);

        plugin.getLogger().info("配置已加载，预编译了 " + bannedNamePatterns.size()
                + " 个名字正则、" + bannedChatPatterns.size() + " 个聊天正则和 "
                + bannedIpMatchers.size() + " 个 IP 段规则。");
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

    private List<IpRangeMatcher> compileIpRanges(List<String> ranges) {
        List<IpRangeMatcher> matchers = new ArrayList<>();
        for (String range : ranges) {
            try {
                matchers.add(IpRangeMatcher.compile(range));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("配置文件中的 IP 段规则无效: " + range + " (" + e.getMessage() + ")");
            }
        }
        return matchers;
    }

    private List<String> parseActions(Object rawValue) {
        List<String> configured = new ArrayList<>();
        if (rawValue instanceof List<?>) {
            for (Object value : (List<?>) rawValue) {
                if (value != null) {
                    addActionValues(configured, String.valueOf(value));
                }
            }
        } else if (rawValue != null) {
            addActionValues(configured, String.valueOf(rawValue));
        }

        List<String> normalized = new ArrayList<>();
        for (String action : configured) {
            String value = action.toUpperCase(Locale.ROOT);
            if (VALID_ACTIONS.contains(value) && !normalized.contains(value)) {
                normalized.add(value);
            } else if (!VALID_ACTIONS.contains(value)) {
                plugin.getLogger().warning("忽略无效处罚动作: " + action);
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("BAN_IP");
        }
        return normalized;
    }

    private void addActionValues(List<String> target, String rawValue) {
        for (String value : rawValue.split("[,\\s]+")) {
            if (!value.isBlank()) {
                target.add(value.trim());
            }
        }
    }

    public void saveConfig() {
        FileConfiguration config = plugin.getConfig();
        config.set("banned-name-prefixes", bannedPrefixes);
        config.set("banned-name-regex", bannedNameRegexStrings);
        config.set("banned-chat-regex", bannedChatRegexStrings);
        config.set("banned-ip-ranges", bannedIpRanges);
        config.set("action", actions.size() == 1 ? actions.get(0) : new ArrayList<>(actions));
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
        setActions(List.of(action));
    }

    public void setActions(List<String> actions) {
        this.actions = parseActions(actions);
        saveConfig();
    }

    public boolean isValidIpRange(String range) {
        try {
            IpRangeMatcher.compile(range);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isIpBanned(InetAddress address) {
        if (address == null) {
            return false;
        }
        for (IpRangeMatcher matcher : bannedIpMatchers) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getBannedIpRanges() {
        return new ArrayList<>(bannedIpRanges);
    }

    public List<String> getBannedPrefixes() { return bannedPrefixes; }
    public List<Pattern> getBannedNamePatterns() { return bannedNamePatterns; }
    public List<Pattern> getBannedChatPatterns() { return bannedChatPatterns; }
    public String getAction() { return String.join(",", actions); }
    public List<String> getActions() { return new ArrayList<>(actions); }
    public boolean isBroadcast() { return broadcast; }
    public String getKickReason() { return kickReason; }
    public String getBroadcastMsg() { return broadcastMsg; }
}
