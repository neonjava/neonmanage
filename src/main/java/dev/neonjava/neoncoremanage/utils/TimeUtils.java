package dev.neonjava.neoncoremanage.utils;

public class TimeUtils {
    public static long parseDuration(String input) {
        if (input == null || input.isBlank()) return -1;
        input = input.toLowerCase().trim();
        long totalMs = 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([dhms])").matcher(input);
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            long val = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "d" -> totalMs += val * 24 * 60 * 60 * 1000;
                case "h" -> totalMs += val * 60 * 60 * 1000;
                case "m" -> totalMs += val * 60 * 1000;
                case "s" -> totalMs += val * 1000;
            }
        }
        return matched ? totalMs : -1;
    }

    public static String formatRemaining(long expiresAt) {
        if (expiresAt <= 0) return "Permanent";
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) return "Expired";
        
        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        seconds %= 60;
        minutes %= 60;
        hours %= 24;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
