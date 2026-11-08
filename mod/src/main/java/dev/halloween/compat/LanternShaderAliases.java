package dev.halloween.compat;

import java.util.regex.Pattern;

/** Extends the active pack in memory; shader ZIPs are never modified. */
public final class LanternShaderAliases {
    private static final Pattern LANTERN = Pattern.compile("(?<![\\w:/.#-])(?:minecraft:)?lantern(?=\\s|$)");

    public static String addAlias(String filename, String source) {
        for (String target : new String[]{"halloween:bat_lantern", "halloween:pumpkin_lantern", "halloween:halloween_garland"}) {
            source = addTarget(filename, source, target);
        }
        return source;
    }

    private static String addTarget(String filename, String source, String target) {
        if (source == null || !(filename.equals("block.properties") || filename.equals("item.properties"))) {
            return source;
        }
        // Respect explicit shaderpack support, including a custom pumpkin colour.
        if (source.lines().filter(line -> !line.stripLeading().startsWith("#")).anyMatch(line -> line.contains(target))) {
            return source;
        }
        StringBuilder result = new StringBuilder();
        boolean inMapping = false;
        for (String line : source.split("\\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("#")) {
                result.append(line).append('\n');
                continue;
            }
            if (line.contains("=")) {
                inMapping = trimmed.matches("(?:block|item)\\.\\d+\\s*=.*");
            }
            result.append(inMapping ? LANTERN.matcher(line).replaceAll("$0 " + target) : line).append('\n');
            if (!line.stripTrailing().endsWith("\\")) inMapping = false;
        }
        return result.toString();
    }

    private LanternShaderAliases() { }
}
