package relic.client.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class BiomeTagResolver {

    private static final Map<String, Set<String>> cache = new HashMap<>();

    private BiomeTagResolver() {}

    static synchronized Set<String> resolveTag(String tagId) {
        Set<String> cached = cache.get(tagId);
        if (cached != null) return cached;
        Set<String> out = new HashSet<>();
        cache.put(tagId, out);
        resolveInto(tagId, out, new HashSet<>());
        return out;
    }

    private static void resolveInto(String tagId, Set<String> out, Set<String> visiting) {
        if (!visiting.add(tagId)) return;
        String[] nsPath = splitId(tagId);
        String resource = "/data/" + nsPath[0] + "/tags/worldgen/biome/" + nsPath[1] + ".json";
        try (InputStream in = BiomeTagResolver.class.getResourceAsStream(resource)) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray values = root.getAsJsonArray("values");
            if (values == null) return;
            for (JsonElement e : values) {
                String v = elementId(e);
                if (v == null) continue;
                if (v.startsWith("#")) resolveInto(v.substring(1), out, visiting);
                else out.add(pathOf(v));
            }
        } catch (Exception ignored) {

        }
    }

    private static String elementId(JsonElement e) {
        if (e.isJsonPrimitive()) return e.getAsString();
        if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
            return e.getAsJsonObject().get("id").getAsString();
        }
        return null;
    }

    private static String[] splitId(String id) {
        int c = id.indexOf(':');
        return c < 0 ? new String[]{"minecraft", id} : new String[]{id.substring(0, c), id.substring(c + 1)};
    }

    private static String pathOf(String biomeId) {
        int c = biomeId.indexOf(':');
        return c < 0 ? biomeId : biomeId.substring(c + 1);
    }
}
