package com.tendoarisu.anti4D4V;

import java.net.InetAddress;
import java.util.Locale;
import java.util.regex.Pattern;

public final class IpRangeMatcher {
    private enum Type {
        WILDCARD,
        CIDR
    }

    private final String rule;
    private final Type type;
    private final Pattern wildcardPattern;
    private final int network;
    private final int mask;

    private IpRangeMatcher(String rule, Pattern wildcardPattern) {
        this.rule = rule;
        this.type = Type.WILDCARD;
        this.wildcardPattern = wildcardPattern;
        this.network = 0;
        this.mask = 0;
    }

    private IpRangeMatcher(String rule, int network, int mask) {
        this.rule = rule;
        this.type = Type.CIDR;
        this.wildcardPattern = null;
        this.network = network;
        this.mask = mask;
    }

    public static IpRangeMatcher compile(String rawRule) {
        if (rawRule == null) {
            throw new IllegalArgumentException("规则不能为空");
        }

        String rule = rawRule.trim().toLowerCase(Locale.ROOT);
        if (rule.isEmpty()) {
            throw new IllegalArgumentException("规则不能为空");
        }

        if (rule.contains("/")) {
            return compileCidr(rule);
        }

        return compileWildcard(rule);
    }

    public boolean matches(InetAddress address) {
        if (address == null || address.getAddress().length != 4) {
            return false;
        }

        if (type == Type.CIDR) {
            int ip = toInt(address.getAddress());
            return (ip & mask) == network;
        }

        return wildcardPattern.matcher(address.getHostAddress()).matches();
    }

    public String getRule() {
        return rule;
    }

    private static IpRangeMatcher compileCidr(String rule) {
        String[] parts = rule.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("CIDR 格式应为 113.135.0.0/16");
        }
        if (parts[0].contains("x")) {
            throw new IllegalArgumentException("CIDR 不能包含 x 通配符");
        }

        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("CIDR 掩码必须是 0-32 的数字");
        }

        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("CIDR 掩码必须在 0-32 之间");
        }

        int ip = parseIpv4(parts[0]);
        int mask = prefix == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefix));
        return new IpRangeMatcher(rule, ip & mask, mask);
    }

    private static IpRangeMatcher compileWildcard(String rule) {
        String[] octets = rule.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("IP 段必须包含 4 个字段");
        }

        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < octets.length; i++) {
            if (i > 0) {
                regex.append("\\.");
            }
            regex.append(compileWildcardOctet(octets[i]));
        }
        regex.append("$");

        return new IpRangeMatcher(rule, Pattern.compile(regex.toString()));
    }

    private static String compileWildcardOctet(String rawOctet) {
        String octet = rawOctet.toLowerCase(Locale.ROOT);
        if (octet.isEmpty() || octet.length() > 3 || !octet.matches("[0-9x]+")) {
            throw new IllegalArgumentException("IP 字段只能包含数字或 x，且长度不能超过 3");
        }

        if (!octet.contains("x")) {
            parseOctet(octet);
            return octet;
        }

        if (octet.equals("x")) {
            return "\\d{1,3}";
        }

        String regex = octet.replace("x", "\\d");
        if (!canMatchAnyOctet(regex)) {
            throw new IllegalArgumentException("IP 字段超出 0-255 范围: " + rawOctet);
        }
        return regex;
    }

    private static boolean canMatchAnyOctet(String regex) {
        Pattern pattern = Pattern.compile("^" + regex + "$");
        for (int i = 0; i <= 255; i++) {
            if (pattern.matcher(String.valueOf(i)).matches()) {
                return true;
            }
        }
        return false;
    }

    private static int parseIpv4(String ip) {
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("IPv4 地址必须包含 4 个字段");
        }

        int result = 0;
        for (String octet : octets) {
            result = (result << 8) | parseOctet(octet);
        }
        return result;
    }

    private static int parseOctet(String rawOctet) {
        if (!rawOctet.matches("\\d{1,3}")) {
            throw new IllegalArgumentException("IP 字段必须是 0-255 的数字");
        }

        int value;
        try {
            value = Integer.parseInt(rawOctet);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("IP 字段必须是 0-255 的数字");
        }

        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("IP 字段必须在 0-255 之间");
        }
        return value;
    }

    private static int toInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }
}
