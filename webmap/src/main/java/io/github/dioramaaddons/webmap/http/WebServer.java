package io.github.dioramaaddons.webmap.http;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.dioramaaddons.webmap.WebMapMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Read-only HTTP server built on the JDK's {@code com.sun.net.httpserver}. Serves the bundled web page,
 * tiles from the disk cache and small JSON endpoints. Only GET and HEAD are accepted.
 */
public final class WebServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern TILE = Pattern.compile("^/(tiles|hits)/([a-z0-9_.+~-]{1,64})/(\\d)/(-?\\d{1,7})/(-?\\d{1,7})\\.png$");
    private static final Map<String, String> STATIC = Map.ofEntries(
            Map.entry("/index.html", "text/html; charset=utf-8"),
            Map.entry("/app.js", "text/javascript; charset=utf-8"),
            Map.entry("/style.css", "text/css; charset=utf-8"),
            Map.entry("/vendor/leaflet/leaflet.js", "text/javascript; charset=utf-8"),
            Map.entry("/vendor/leaflet/leaflet.css", "text/css; charset=utf-8"),
            Map.entry("/vendor/leaflet/images/layers.png", "image/png"),
            Map.entry("/vendor/leaflet/images/layers-2x.png", "image/png"),
            Map.entry("/vendor/leaflet/images/marker-icon.png", "image/png"),
            Map.entry("/vendor/leaflet/images/marker-icon-2x.png", "image/png"),
            Map.entry("/vendor/leaflet/images/marker-shadow.png", "image/png"),
            Map.entry("/favicon.png", "image/png"));
    /** 1×1 transparent PNG, answered for tiles that do not exist (yet) so browsers log no errors. */
    private static final byte[] EMPTY_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII=");
    private static final Pattern SINCE = Pattern.compile("(?:^|&)since=(-?\\d{1,19})(?:&|$)");

    /** Provides the data the server publishes. Implementations must be thread-safe. */
    public interface Source {
        Set<String> dimensionKeys();

        Path tilePath(String dimKey, int zoom, int x, int y);

        /** Hit map next to a tile (angled style only), or null. */
        @Nullable Path hitPath(String dimKey, int zoom, int x, int y);

        String infoJson();

        String claimsJson();

        String playersJson();

        String chatJson(long since);

        String changesJson(long since);

        /** Page title, plain text. */
        String title();

        /** Page icon, or null for the bundled one. */
        @Nullable Favicon favicon();
    }

    /** Either a redirect to an external URL or an image to serve. */
    public record Favicon(@Nullable String url, byte @Nullable [] bytes, String contentType) {}

    private final Source source;
    private final Map<String, byte[]> staticFiles = new HashMap<>();
    private @Nullable HttpServer server;
    private @Nullable ExecutorService executor;

    public WebServer(Source source) {
        this.source = source;
        for (String path : STATIC.keySet()) {
            try (InputStream in = WebServer.class.getResourceAsStream("/web" + path)) {
                if (in != null) {
                    staticFiles.put(path, in.readAllBytes());
                } else {
                    LOGGER.error("Web map resource missing: /web{}", path);
                }
            } catch (IOException e) {
                LOGGER.error("Could not read /web{}", path, e);
            }
        }
    }

    /** Returns false (after logging why) when the port cannot be opened. Never throws. */
    public boolean start(String bind, int port, int threads) {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(bind, port), 0);
            executor = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "dioaddons-webmap-http");
                t.setDaemon(true);
                return t;
            });
            http.setExecutor(executor);
            http.createContext("/", this::handle);
            http.start();
            server = http;
            LOGGER.info("Web map available at http://{}:{}/", bind.equals("0.0.0.0") ? "localhost" : bind, port);
            return true;
        } catch (Exception e) {
            LOGGER.error("Web map could not listen on {}:{} ({}). Change http.port or http.bind in "
                    + "config/{}-common.toml. The game server keeps running without the web map.",
                    bind, port, e.toString(), WebMapMod.MOD_ID);
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            return false;
        }
    }

    public boolean running() {
        return server != null;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String method = ex.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                ex.getResponseHeaders().add("Allow", "GET, HEAD");
                send(ex, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                index(ex);
                return;
            }
            if (path.equals("/favicon") || path.equals("/favicon.ico")) {
                favicon(ex);
                return;
            }
            byte[] file = staticFiles.get(path);
            if (file != null) {
                send(ex, 200, STATIC.get(path), file, "public, max-age=300");
                return;
            }
            switch (path) {
                case "/api/info" -> json(ex, source::infoJson);
                case "/api/claims" -> json(ex, source::claimsJson);
                case "/api/players" -> json(ex, source::playersJson);
                case "/api/chat" -> json(ex, () -> source.chatJson(since(ex)));
                case "/api/changes" -> json(ex, () -> source.changesJson(since(ex)));
                default -> tile(ex, path);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Web map request failed", e);
        }
    }

    private void index(HttpExchange ex) throws IOException {
        byte[] page = staticFiles.get("/index.html");
        if (page == null) {
            notFound(ex);
            return;
        }
        String html = new String(page, StandardCharsets.UTF_8).replace("<title>World Map</title>",
                "<title>" + escapeHtml(source.title()) + "</title>");
        send(ex, 200, STATIC.get("/index.html"), html.getBytes(StandardCharsets.UTF_8), "no-cache");
    }

    private void favicon(HttpExchange ex) throws IOException {
        Favicon icon = source.favicon();
        if (icon != null && icon.url() != null) {
            ex.getResponseHeaders().add("Location", icon.url());
            send(ex, 302, "text/plain; charset=utf-8", new byte[0], "no-cache");
        } else if (icon != null && icon.bytes() != null) {
            send(ex, 200, icon.contentType(), icon.bytes(), "no-cache");
        } else {
            byte[] own = staticFiles.get("/favicon.png");
            if (own == null) {
                notFound(ex);
            } else {
                send(ex, 200, "image/png", own, "public, max-age=300");
            }
        }
    }

    static long since(HttpExchange ex) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) {
            return -1;
        }
        Matcher m = SINCE.matcher(query);
        if (!m.find()) {
            return -1;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String escapeHtml(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    private void json(HttpExchange ex, Supplier<String> body) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", body.get().getBytes(StandardCharsets.UTF_8), "no-cache");
    }

    private void tile(HttpExchange ex, String path) throws IOException {
        Matcher m = TILE.matcher(path);
        if (!m.matches() || !source.dimensionKeys().contains(m.group(2))) {
            notFound(ex);
            return;
        }
        int zoom = Integer.parseInt(m.group(3));
        int x = Integer.parseInt(m.group(4));
        int y = Integer.parseInt(m.group(5));
        Path file = m.group(1).equals("tiles") ? source.tilePath(m.group(2), zoom, x, y) : source.hitPath(m.group(2), zoom, x, y);
        if (file == null || !Files.isRegularFile(file)) {
            // Nothing rendered there (yet): a transparent pixel instead of a 404, which browsers log as an error.
            send(ex, 200, "image/png", EMPTY_PNG, "no-cache");
            return;
        }
        String etag = "\"" + Files.getLastModifiedTime(file).toMillis() + "-" + Files.size(file) + "\"";
        ex.getResponseHeaders().add("ETag", etag);
        if (etag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
            ex.getResponseHeaders().add("Cache-Control", "no-cache");
            ex.sendResponseHeaders(304, -1);
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            send(ex, 200, "image/png", EMPTY_PNG, "no-cache");
            return;
        }
        send(ex, 200, "image/png", bytes, "no-cache");
    }

    private static void notFound(HttpExchange ex) throws IOException {
        send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8), "no-cache");
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body, @Nullable String cache)
            throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
        if (cache != null) {
            ex.getResponseHeaders().add("Cache-Control", cache);
        }
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
