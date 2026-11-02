package io.github.dioramaaddons.webmap.network;

import net.minecraft.network.FriendlyByteBuf;

/** The web page settings editable from the in-game screen. */
public record WebSettings(String title, String favicon, boolean claims, boolean players, boolean online,
        boolean chat) {
    public static final int MAX_TITLE = 128;
    public static final int MAX_FAVICON = 512;

    void write(FriendlyByteBuf buf) {
        buf.writeUtf(title, MAX_TITLE);
        buf.writeUtf(favicon, MAX_FAVICON);
        buf.writeBoolean(claims);
        buf.writeBoolean(players);
        buf.writeBoolean(online);
        buf.writeBoolean(chat);
    }

    static WebSettings read(FriendlyByteBuf buf) {
        return new WebSettings(buf.readUtf(MAX_TITLE), buf.readUtf(MAX_FAVICON), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean());
    }

    /** Trims and removes control characters; never trust the client. */
    public WebSettings sanitized() {
        return new WebSettings(clean(title, MAX_TITLE), clean(favicon, MAX_FAVICON), claims, players, online, chat);
    }

    private static String clean(String s, int max) {
        StringBuilder b = new StringBuilder(Math.min(s.length(), max));
        s.strip().codePoints().filter(c -> !Character.isISOControl(c)).limit(max).forEach(b::appendCodePoint);
        return b.toString();
    }
}
