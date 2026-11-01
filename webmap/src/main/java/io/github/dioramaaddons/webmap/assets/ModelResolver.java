package io.github.dioramaaddons.webmap.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal reader of block states and block models: enough to find the texture on a block's top and south
 * faces and whether they are tinted. Uses the default (first) variant of each block.
 */
final class ModelResolver {
    record Face(String texture, boolean tinted) {}

    /** Model after following parents: textures merged, elements from the nearest model that has some. */
    record Model(Map<String, String> textures, @Nullable JsonArray elements) {}

    private final AssetSource assets;
    private final Map<String, Optional<Model>> models = new HashMap<>();

    ModelResolver(AssetSource assets) {
        this.assets = assets;
    }

    private static String[] split(String id, String defaultNs) {
        int c = id.indexOf(':');
        return c < 0 ? new String[] {defaultNs, id} : new String[] {id.substring(0, c), id.substring(c + 1)};
    }

    private Optional<JsonObject> json(String path) {
        Optional<InputStream> in = assets.open(path);
        if (in.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream s = in.get()) {
            JsonElement e = JsonParser.parseReader(new InputStreamReader(s, StandardCharsets.UTF_8));
            return e.isJsonObject() ? Optional.of(e.getAsJsonObject()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Models of a block's default look, e.g. {@code minecraft:block/stone}. */
    List<String> modelsFor(String blockId) {
        String[] id = split(blockId, "minecraft");
        List<String> out = new ArrayList<>();
        Optional<JsonObject> state = json("assets/" + id[0] + "/blockstates/" + id[1] + ".json");
        if (state.isEmpty()) {
            return out;
        }
        JsonObject s = state.get();
        if (s.has("variants")) {
            JsonObject variants = s.getAsJsonObject("variants");
            JsonElement v = variants.has("") ? variants.get("") : variants.entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(null);
            addModel(v, out);
        } else if (s.has("multipart")) {
            JsonArray parts = s.getAsJsonArray("multipart");
            for (JsonElement p : parts) {
                if (!p.getAsJsonObject().has("when")) {
                    addModel(p.getAsJsonObject().get("apply"), out);
                }
            }
            if (out.isEmpty() && !parts.isEmpty()) {
                addModel(parts.get(0).getAsJsonObject().get("apply"), out);
            }
        }
        return out;
    }

    private static void addModel(@Nullable JsonElement v, List<String> out) {
        if (v == null) {
            return;
        }
        if (v.isJsonArray() && !v.getAsJsonArray().isEmpty()) {
            v = v.getAsJsonArray().get(0);
        }
        if (v.isJsonObject() && v.getAsJsonObject().has("model")) {
            out.add(v.getAsJsonObject().get("model").getAsString());
        }
    }

    Optional<Model> model(String modelId) {
        return models.computeIfAbsent(modelId, this::load);
    }

    private Optional<Model> load(String modelId) {
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        String current = modelId;
        for (int depth = 0; current != null && depth < 24; depth++) {
            String[] id = split(current, "minecraft");
            Optional<JsonObject> m = json("assets/" + id[0] + "/models/" + id[1] + ".json");
            if (m.isEmpty()) {
                break;
            }
            JsonObject o = m.get();
            if (o.has("textures")) {
                for (var e : o.getAsJsonObject("textures").entrySet()) {
                    textures.putIfAbsent(e.getKey(), e.getValue().getAsString());
                }
            }
            if (elements == null && o.has("elements")) {
                elements = o.getAsJsonArray("elements");
            }
            current = o.has("parent") ? o.get("parent").getAsString() : null;
        }
        if (textures.isEmpty() && elements == null) {
            return Optional.empty();
        }
        return Optional.of(new Model(textures, elements));
    }

    /** Follows {@code #name} references to a texture id such as {@code minecraft:block/stone}. */
    static @Nullable String texture(Model m, String ref) {
        String t = ref;
        for (int i = 0; i < 12 && t != null && t.startsWith("#"); i++) {
            t = m.textures().get(t.substring(1));
        }
        return t == null || t.startsWith("#") ? null : t;
    }

    /** The texture on the largest face pointing in {@code dir} ("up", "south"...), if any. */
    static Optional<Face> face(Model m, String dir) {
        if (m.elements() == null) {
            return Optional.empty();
        }
        Face best = null;
        double bestScore = -1;
        for (JsonElement e : m.elements()) {
            JsonObject el = e.getAsJsonObject();
            JsonObject faces = el.getAsJsonObject("faces");
            if (faces == null || !faces.has(dir)) {
                continue;
            }
            JsonArray from = el.getAsJsonArray("from");
            JsonArray to = el.getAsJsonArray("to");
            double dx = Math.abs(to.get(0).getAsDouble() - from.get(0).getAsDouble());
            double dy = Math.abs(to.get(1).getAsDouble() - from.get(1).getAsDouble());
            double dz = Math.abs(to.get(2).getAsDouble() - from.get(2).getAsDouble());
            double area = dir.equals("up") || dir.equals("down") ? dx * dz : dir.equals("north") || dir.equals("south") ? dx * dy : dz * dy;
            // Prefer the highest top face, then the biggest.
            double score = area + (dir.equals("up") ? to.get(1).getAsDouble() * 1000 : 0);
            JsonObject f = faces.getAsJsonObject(dir);
            String tex = f.has("texture") ? texture(m, f.get("texture").getAsString()) : null;
            if (tex != null && score > bestScore) {
                bestScore = score;
                best = new Face(tex, f.has("tintindex"));
            }
        }
        return Optional.ofNullable(best);
    }

    /** Any face at all, for blocks seen from above only partly (plants are crossed planes). */
    static Optional<Face> anyFace(Model m) {
        for (String dir : new String[] {"north", "south", "east", "west", "down"}) {
            Optional<Face> f = face(m, dir);
            if (f.isPresent()) {
                return f;
            }
        }
        String particle = texture(m, "#particle");
        return particle == null ? Optional.empty() : Optional.of(new Face(particle, false));
    }
}
