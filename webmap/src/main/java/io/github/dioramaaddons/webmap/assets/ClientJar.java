package io.github.dioramaaddons.webmap.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * The vanilla client jar, whose textures and models give blocks their colours. A dedicated server does not
 * have them, so it is downloaded once from Mojang's official servers (same file the launcher uses) and
 * checked against the published SHA-1.
 */
public final class ClientJar {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private ClientJar() {}

    /** Returns the jar, downloading it if needed and allowed. Blocking; call off the server thread. */
    public static Optional<Path> ensure(Path dir, String version, boolean download) {
        Path jar = dir.resolve("minecraft-client-" + version + ".jar");
        if (Files.isRegularFile(jar)) {
            return Optional.of(jar);
        }
        if (!download) {
            LOGGER.info("Web map: client assets download is off; using map colours instead of textures");
            return Optional.empty();
        }
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            JsonObject manifest = json(http, MANIFEST);
            String versionUrl = null;
            for (var v : manifest.getAsJsonArray("versions")) {
                if (v.getAsJsonObject().get("id").getAsString().equals(version)) {
                    versionUrl = v.getAsJsonObject().get("url").getAsString();
                }
            }
            if (versionUrl == null) {
                throw new IllegalStateException("version " + version + " not in Mojang's manifest");
            }
            JsonObject client = json(http, versionUrl).getAsJsonObject("downloads").getAsJsonObject("client");
            String url = client.get("url").getAsString();
            String sha1 = client.get("sha1").getAsString();
            LOGGER.info("Web map: downloading the Minecraft {} client jar from Mojang for block textures ({} MB)",
                    version, client.get("size").getAsLong() / (1024 * 1024));
            Files.createDirectories(dir);
            Path tmp = dir.resolve(jar.getFileName() + ".part");
            HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = res.body(); var out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) {
                    digest.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            }
            String got = HexFormat.of().formatHex(digest.digest());
            if (!got.equalsIgnoreCase(sha1)) {
                Files.deleteIfExists(tmp);
                throw new IllegalStateException("SHA-1 mismatch: expected " + sha1 + ", got " + got);
            }
            Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Web map: client jar saved to {}", jar);
            return Optional.of(jar);
        } catch (Exception e) {
            LOGGER.warn("Web map: could not get the client jar ({}); using map colours instead of textures", e.toString());
            return Optional.empty();
        }
    }

    private static JsonObject json(HttpClient http, String url) throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException(url + " answered " + res.statusCode());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }
}
