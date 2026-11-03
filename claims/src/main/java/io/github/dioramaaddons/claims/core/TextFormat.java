package io.github.dioramaaddons.claims.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Formatting codes of claim greetings:
 * {@code &0}–{@code &f} colours, {@code &#RRGGBB} any colour, {@code &l} bold, {@code &o} italic, {@code &n}
 * underline, {@code &m} strikethrough and {@code &r} reset. Placeholders such as {@code {player}} are filled with
 * {@link #fill}. Pure logic: {@link #runs} splits a text into styled runs. Taken from Mochos Esenciales (MIT).
 */
public final class TextFormat {
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int STRIKE = 4;
    public static final int UNDERLINE = 16;

    /** Colours of the codes 0–f (Minecraft's legacy palette). */
    public static final int[] PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    /** A piece of text with one style. {@code color} is -1 when it keeps the default colour. */
    public record Run(String text, int color, int flags) {}

    private TextFormat() {}

    public static List<Run> runs(String text) {
        List<Run> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        int color = -1;
        int flags = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char k = Character.toLowerCase(text.charAt(i + 1));
                int newColor = color;
                int newFlags = flags;
                int len = 2;
                boolean code = true;
                int hex = Character.digit(k, 16);
                if (k == '#' && i + 8 <= text.length() && isHex(text, i + 2, 6)) {
                    newColor = Integer.parseInt(text.substring(i + 2, i + 8), 16);
                    newFlags = 0;
                    len = 8;
                } else if (hex >= 0) {
                    newColor = PALETTE[hex];
                    newFlags = 0;
                } else if (k == 'l') {
                    newFlags |= BOLD;
                } else if (k == 'o') {
                    newFlags |= ITALIC;
                } else if (k == 'n') {
                    newFlags |= UNDERLINE;
                } else if (k == 'm') {
                    newFlags |= STRIKE;
                } else if (k == 'r') {
                    newColor = -1;
                    newFlags = 0;
                } else {
                    code = false;
                }
                if (code) {
                    if (!cur.isEmpty()) {
                        out.add(new Run(cur.toString(), color, flags));
                        cur.setLength(0);
                    }
                    color = newColor;
                    flags = newFlags;
                    i += len;
                    continue;
                }
            }
            cur.append(c);
            i++;
        }
        if (!cur.isEmpty()) {
            out.add(new Run(cur.toString(), color, flags));
        }
        return out;
    }

    private static boolean isHex(String s, int from, int n) {
        for (int i = from; i < from + n; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** The text without formatting codes (Markdown is kept). */
    public static String strip(String text) {
        StringBuilder sb = new StringBuilder();
        for (Run r : runs(text)) {
            sb.append(r.text());
        }
        return sb.toString();
    }

    /** Replaces {@code {key}} with its value; unknown placeholders stay as they are. */
    public static String fill(String text, Map<String, String> values) {
        if (text == null || text.indexOf('{') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{') {
                int end = text.indexOf('}', i + 1);
                if (end > i) {
                    String v = values.get(text.substring(i + 1, end));
                    if (v != null) {
                        sb.append(v);
                        i = end + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
