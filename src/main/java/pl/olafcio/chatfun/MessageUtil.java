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

    public static String replaceLegacyFormatting(String input) {
        return input
                    .replace("&l", "<bold>")
                    .replace("&o", "<italic>")
                    .replace("&n", "<underlined>")
                    .replace("&m", "<strikethrough>")
                    .replace("&k", "<obfuscated>")
                    .replace("&r", "<reset>");
    }

    public static String replaceLegacyHex(String input) {
        return input.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
    }

    private static String decolor(String input) {
        return input.replaceAll("<(|/).+?>", "");
    }

    private static final int CHAT_WIDTH = 53;
    public static String parseCenters(String input) {
        var lines = input.split("\n");
        for (var n = 0; n < lines.length; n++) {
            var line = lines[n];
            if (line.contains("<center>")) {
                final var CENTER_REGEX = "<(|/)center>";

                var raw = decolor(line);
                var space = CHAT_WIDTH - raw.replaceAll(CENTER_REGEX, "").length();

                if (space <= 0)
                    continue;

                var count = line.split(CENTER_REGEX).length - 1;
                lines[n] = line.replaceAll(CENTER_REGEX, " ".repeat(space / count));
            }
        }

        return String.join("\n", lines);
    }

    public static String parseMultiplies(String input) {
        var chars = input.toCharArray();
        var out = new StringBuilder();

        var mulPhase = 0;
        var mulStr = new StringBuilder();
        var mulNum = new StringBuilder();

        for (var ch : chars) {
            if (mulPhase > 0) {
                if (mulPhase == 1) {
                    if (ch == '{') {
                        out.append("{{");
                        mulPhase = 0;
                    } else {
                        mulStr.append(ch);
                        mulPhase = 2;
                    }
                } else if (mulPhase == 2) {
                    if (ch == '*')
                        mulPhase = 3;
                    else mulStr.append(ch);
                } else if (ch == '}') {
                    var mulNuw = mulNum.toString();
                    var mulStw = mulStr.toString();

                    int amount;
                    if (mulNuw.endsWith("%")) {
                        amount = CHAT_WIDTH *
                                 Integer.parseInt(mulNuw.substring(
                                         0,
                                         mulNuw.length() - 1
                                 )) / 100;
                    } else amount = Integer.parseInt(mulNuw);

                    out.append(mulStw.repeat(amount));
                    mulPhase = 0;

                    mulStr.setLength(0);
                    mulNum.setLength(0);
                } else {
                    mulNum.append(ch);
                }
            } else if (ch == '{') {
                mulPhase = 1;
            } else {
                out.append(ch);
            }
        }

        return out.toString();
    }
}
