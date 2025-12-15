package pl.olafcio.chatfun;

public enum MessageUtil {
    ;

    public static String replaceLegacyColors(String input) {
        return input
                    .replace("&c", "<red>")
                    .replace("&4", "<dark_red>")
                    .replace("&6", "<gold>")
                    .replace("&e", "<yellow>")
                    .replace("&a", "<green>")
                    .replace("&2", "<dark_green>")
                    .replace("&b", "<aqua>")
                    .replace("&3", "<dark_aqua>")
                    .replace("&9", "<blue>")
                    .replace("&1", "<dark_blue>")
                    .replace("&d", "<light_purple>")
                    .replace("&5", "<dark_purple>")
                    .replace("&7", "<gray>")
                    .replace("&8", "<dark_gray>")
                    .replace("&0", "<black>")
                    .replace("&f", "<white>");
    }

    public static String replaceLegacyHex(String input) {
        return input.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
    }

    private static String decolor(String input) {
        return input.replaceAll("<(|/).+?>", "");
    }

    private static final int CHAT_WIDTH = 91;
    public static String parseFull(String input) {
        var lines = input.split("\n");
        for (var n = 0; n < lines.length; n++) {
            var line = lines[n];
            var raw = decolor(line);

            var parts = line.splitWithDelimiters("<(|/)full>", 5);
            var out = new StringBuilder();

            for (var i = 0; i < parts.length; i++) {
                if ((i-1)%2 == 0) {
                    var nextFull = i + 2;
                    var next = parts.length > nextFull
                                ? decolor(parts[i + 1]).length()
                                : raw.length();

                    out.append(" ".repeat(Math.max(0,
                            CHAT_WIDTH -
                            raw.substring(out.length(), next).length()
                    )));
                }

                out.append(parts[i]);
            }

            lines[n] = out.toString();
        }

        return String.join("\n", lines);
    }
}
