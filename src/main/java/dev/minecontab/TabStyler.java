package dev.minecontab;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.text.TextColor;
import net.minecraft.util.Util;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class TabStyler {

    // Shimmer colours (gold -> white). Tweak to taste.
    private static final int DIM = 0xFFC400;
    private static final int BRIGHT = 0xFFFFFF;
    private static final double SPEED_MS = 330.0;  // lower = faster
    private static final double WAVE = 0.45;       // spread between letters

    private static final Map<CapeDetector.Minecon, Text> ICONS = new EnumMap<>(CapeDetector.Minecon.class);

    private TabStyler() {}

    private static Text icon(CapeDetector.Minecon m) {
        return ICONS.computeIfAbsent(m, k -> {
            JsonObject o = new JsonObject();
            o.addProperty("text", String.valueOf(k.glyph));
            o.addProperty("font", "minecontab:capes");
            return TextCodecs.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(Text.empty());
        });
    }

    public static Text decorate(Text original, CapeDetector.Minecon cape) {
        double t = Util.getMeasuringTimeMs() / SPEED_MS;
        MutableText out = Text.empty();
        out.append(icon(cape));
        out.append(Text.literal(" "));

        int[] index = {0};
        original.visit((style, str) -> {
            str.codePoints().forEach(cp -> {
                double k = (Math.sin(t - index[0] * WAVE) + 1.0) / 2.0;
                k = k * k; // sharper highlights
                int rgb = lerp(DIM, BRIGHT, k);
                out.append(Text.literal(new String(Character.toChars(cp)))
                        .setStyle(style.withColor(TextColor.fromRgb(rgb))));
                index[0]++;
            });
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static int lerp(int a, int b, double k) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
