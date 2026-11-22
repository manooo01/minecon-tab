package dev.minecontab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.network.PlayerListEntry;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CapeDetector {

    public enum Minecon {
        Y2011("953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6", '\uE000'),
        Y2012("a2e8d97ec79100e90a75d369d1b3ba81273c4f82bc1b737e934eed4a854be1b6", '\uE001'),
        Y2013("153b1a0dfcbae953cdeb6f2c2bf6bf79943239b1372780da44bcbb29273131da", '\uE002'),
        Y2015("b0cc08840700447322d953a02b965f1d65a13a603bf64b17c803c21446fe1635", '\uE003'),
        Y2016("e7dfea16dc83c97df01a12fabbd1216359c0cd0ea42f9999b6e97c584963e980", '\uE004');

        public final String hash;
        public final char glyph;

        Minecon(String hash, char glyph) {
            this.hash = hash;
            this.glyph = glyph;
        }
    }

    private static final Map<UUID, Optional<Minecon>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Minecon>> LOOKUPS = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minecontab-lookup");
        t.setDaemon(true);
        return t;
    });

    private CapeDetector() {}

    public static void clear() {
        CACHE.clear();
    }

    /** @return the Minecon cape this player wears, or null. */
    public static Minecon detect(PlayerListEntry entry) {
        GameProfile profile = entry.getProfile();
        if (profile == null) return null;

        UUID id = profile.id();
        Optional<Minecon> cached = CACHE.get(id);
        if (cached != null) return cached.orElse(null);

        Collection<Property> props = profile.properties().get("textures");
        boolean offlineUuid = id.version() == 3; // cracked / offline-mode servers

        if (props != null && !props.isEmpty()) {
            Minecon found = null;
            for (Property p : props) {
                Minecon m = fromTextureValue(p.value());
                if (m != null) found = m;
            }
            if (found != null) {
                CACHE.put(id, Optional.of(found));
                return found;
            }
            if (!offlineUuid) {
                CACHE.put(id, Optional.empty());
                return null;
            }
        } else if (!offlineUuid) {
            return null; // not populated yet, retry next frame
        }

        // Offline-mode UUID: the server may have stripped capes, so ask Mojang by name.
        return viaMojang(profile.name());
    }

    private static Minecon fromTextureValue(String base64) {
        try {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
            if (textures == null || !textures.has("CAPE")) return null;
            String url = textures.getAsJsonObject("CAPE").get("url").getAsString();
            String hash = url.substring(url.lastIndexOf('/') + 1);
            for (Minecon m : Minecon.values()) {
                if (m.hash.equalsIgnoreCase(hash)) return m;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Minecon viaMojang(String name) {
        if (name == null || name.isEmpty()) return null;
        String key = name.toLowerCase(Locale.ROOT);

        Optional<Minecon> done = LOOKUPS.get(key);
        if (done != null) return done.orElse(null);

        Long failedAt = FAILED.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < 60_000) return null;

        if (PENDING.add(key)) {
            EXEC.execute(() -> {
                try {
                    LOOKUPS.put(key, Optional.ofNullable(fetch(name)));
                    FAILED.remove(key);
                } catch (Exception e) {
                    FAILED.put(key, System.currentTimeMillis());
                } finally {
                    PENDING.remove(key);
                    try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
                }
            });
        }
        return null;
    }

    private static Minecon fetch(String name) throws Exception {
        HttpResponse<String> r = get("https://api.minecraftservices.com/minecraft/profile/lookup/name/"
                + URLEncoder.encode(name, StandardCharsets.UTF_8));
        if (r.statusCode() == 404 || r.statusCode() == 204 || r.statusCode() == 400) return null;
        if (r.statusCode() != 200) throw new IllegalStateException("status " + r.statusCode());
        String uuid = JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();

        Thread.sleep(1100);

        r = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        if (r.statusCode() == 404 || r.statusCode() == 204) return null;
        if (r.statusCode() != 200) throw new IllegalStateException("status " + r.statusCode());

        JsonArray arr = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("properties");
        if (arr == null) return null;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            if ("textures".equals(o.get("name").getAsString())) {
                Minecon m = fromTextureValue(o.get("value").getAsString());
                if (m != null) return m;
            }
        }
        return null;
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8)).GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
