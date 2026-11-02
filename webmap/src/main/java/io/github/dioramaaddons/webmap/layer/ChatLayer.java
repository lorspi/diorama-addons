package io.github.dioramaaddons.webmap.layer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Recent public chat for the web page, kept in memory only. Messages get increasing ids so the page asks for
 * what it has not seen yet. Thread-safe: written on the server thread, read by HTTP threads.
 */
public final class ChatLayer {
    public enum Kind { CHAT, JOIN, LEAVE }

    private record Entry(long id, long time, Kind kind, String name, String text) {}

    private final Deque<Entry> entries = new ArrayDeque<>();
    private long nextId = 1;

    public synchronized void add(Kind kind, String name, String text, int keep) {
        entries.addLast(new Entry(nextId++, System.currentTimeMillis(), kind, name, text));
        while (entries.size() > keep) {
            entries.removeFirst();
        }
    }

    public synchronized void clear() {
        entries.clear();
    }

    /** Messages with an id greater than {@code since}; everything kept when {@code since} is 0. */
    public synchronized String json(boolean enabled, long since) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        JsonArray list = new JsonArray();
        if (enabled) {
            for (Entry e : entries) {
                if (e.id() <= since) {
                    continue;
                }
                JsonObject m = new JsonObject();
                m.addProperty("id", e.id());
                m.addProperty("time", e.time());
                m.addProperty("kind", e.kind().name().toLowerCase(java.util.Locale.ROOT));
                m.addProperty("name", e.name());
                m.addProperty("text", e.text());
                list.add(m);
            }
        }
        o.addProperty("last", nextId - 1);
        o.add("messages", list);
        return o.toString();
    }
}
