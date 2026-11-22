package dev.minecontab;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.network.PlayerListEntry;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the cape URL out of the "textures" property that the server already
 * sent in the player-info packet, and matches its hash against the Minecon capes.
 */
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
        if (props == null || props.isEmpty()) {
            return null; // profile not fully populated yet; try again next frame
        }

        Minecon result = null;
        try {
            for (Property p : props) {
                String json = new String(Base64.getDecoder().decode(p.value()), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                if (textures == null || !textures.has("CAPE")) continue;
                String url = textures.getAsJsonObject("CAPE").get("url").getAsString();
                String hash = url.substring(url.lastIndexOf('/') + 1);
                for (Minecon m : Minecon.values()) {
                    if (m.hash.equalsIgnoreCase(hash)) {
                        result = m;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // malformed profile data: treat as "no Minecon cape"
        }

        CACHE.put(id, Optional.ofNullable(result));
        return result;
    }
}
