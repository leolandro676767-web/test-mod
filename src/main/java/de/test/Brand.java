package de.test;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Absender der Chat-Meldungen: "1912 Client" mit Farbverlauf rosa - lila - blau (dieselben Farben wie das
 * Logo), dahinter ein grauer Pfeil und die eigentliche Meldung.
 */
final class Brand {
    private Brand() {
    }

    static final String NAME = "1912 Client";
    private static final String PREFIX = "1912: ";

    /** "1912 Client" Buchstabe fuer Buchstabe im Farbverlauf, fett. */
    static MutableComponent name() {
        MutableComponent out = Component.literal("");
        int n = NAME.length();
        for (int i = 0; i < n; i++) {
            int rgb = Logo.gradient(i / (float) (n - 1)) & 0xFFFFFF;
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true);
            out.append(Component.literal(NAME.substring(i, i + 1)).withStyle(style));
        }
        return out;
    }

    /**
     * Macht aus einem Text wie "§c1912: Totem verbraucht!" die Meldung "1912 Client » Totem verbraucht!"
     * (Farbcode bleibt fuer die Meldung erhalten). Gibt null zurueck, wenn der Text nicht mit "1912: " beginnt.
     */
    static MutableComponent chat(String text) {
        if (text == null) return null;
        String code = "";
        String rest = text;
        if (rest.length() >= 2 && rest.charAt(0) == '§') {
            code = rest.substring(0, 2);
            rest = rest.substring(2);
        }
        if (!rest.startsWith(PREFIX)) return null;
        rest = rest.substring(PREFIX.length());
        MutableComponent out = name();
        out.append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY));
        out.append(Component.literal(code + rest));
        return out;
    }
}
