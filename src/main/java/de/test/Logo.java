package de.test;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Das 1912-Logo: kraeftige, leicht schraege Pixel-Ziffern mit Farbverlauf (rosa - lila - blau),
 * Glanzkante oben, 3D-Tiefe zur Maus hin und weichem Leuchten dahinter. Darunter der Schriftzug
 * "CLIENT". Alles aus Rechtecken gezeichnet - keine Textur noetig.
 */
final class Logo {
    private Logo() {
    }

    // ---------- Ziffern (je 18 Zeilen hoch, Striche 4 breit / 3 hoch) ----------

    private static final String[] ONE = {
        "...####..",
        "..#####..",
        ".######..",
        "##.####..",
        "...####..",
        "...####..",
        "...####..",
        "...####..",
        "...####..",
        "...####..",
        "...####..",
        "...####..",
        "...####..",
        "...####..",
        "...####..",
        "#########",
        "#########",
        "#########"
    };

    private static final String[] NINE = {
        "...#######...",
        ".###########.",
        ".###########.",
        "####.....####",
        "####.....####",
        "####.....####",
        "####.....####",
        ".############",
        ".############",
        "...##########",
        ".........####",
        ".........####",
        ".........####",
        "####.....####",
        "####.....####",
        ".###########.",
        ".###########.",
        "...#######..."
    };

    private static final String[] TWO = {
        "...#######...",
        ".###########.",
        ".###########.",
        "####.....####",
        "####.....####",
        ".........####",
        "........#####",
        "......######.",
        "....#######..",
        "..#######....",
        ".######......",
        "#####........",
        "####.........",
        "####.........",
        "####.........",
        "#############",
        "#############",
        "#############"
    };

    private static final int GAP = 3;     // Abstand zwischen den Ziffern
    private static final int SLANT = 4;   // so viele Pixel ist die oberste Zeile nach rechts versetzt

    static final int H = ONE.length;
    static final int W;
    /** Gefuellte Pixel; RUNS[r] = Paare (Anfang, Ende) zusammenhaengender Pixel je Zeile. */
    private static final boolean[][] MASK;
    private static final int[][] RUNS;

    static {
        String[][] glyphs = {ONE, NINE, ONE, TWO};
        int plain = 0;
        for (String[] gl : glyphs) plain += gl[0].length();
        plain += GAP * (glyphs.length - 1);
        W = plain + SLANT;
        MASK = new boolean[H][W];
        for (int r = 0; r < H; r++) {
            int off = shift(r);
            int x = 0;
            for (String[] gl : glyphs) {
                String row = gl[r];
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) == '#') MASK[r][x + c + off] = true;
                }
                x += row.length() + GAP;
            }
        }
        RUNS = new int[H][];
        for (int r = 0; r < H; r++) {
            int[] tmp = new int[W * 2];
            int n = 0;
            int c = 0;
            while (c < W) {
                if (!MASK[r][c]) {
                    c++;
                    continue;
                }
                int s = c;
                while (c < W && MASK[r][c]) c++;
                tmp[n++] = s;
                tmp[n++] = c;
            }
            RUNS[r] = java.util.Arrays.copyOf(tmp, n);
        }
    }

    /** Schraege: obere Zeilen weiter rechts (in Stufen, wie bei Pixel-Schrift). */
    private static int shift(int r) {
        return Math.round((H - 1 - r) * SLANT / (float) (H - 1));
    }

    private static boolean filled(int r, int c) {
        return r >= 0 && r < H && c >= 0 && c < W && MASK[r][c];
    }

    // ---------- Farben ----------

    /** Verlauf des Logos: rosa - lila - blau. */
    static final int PINK = 0xFFFF5CC8;
    static final int PURPLE = 0xFFAA5CFF;
    static final int BLUE = 0xFF4F8DFF;

    /** Farbe im Verlauf rosa (0) - lila (0.5) - blau (1). */
    static int gradient(float f) {
        f = Math.max(0f, Math.min(1f, f));
        return f < 0.5f ? Ui.lerp(PINK, PURPLE, f * 2f) : Ui.lerp(PURPLE, BLUE, (f - 0.5f) * 2f);
    }

    /** Farben des Farbschemas "Logo" (Spaltenkoepfe usw.) - bewusst etwas dunkler als das Logo selbst. */
    private static final int[] THEME = {0xFFAC00A6, 0xFF8B1AC6, 0xFF7038E4, 0xFF5253FE};

    /** Hauptfarbe der i-ten Ziffer (0..3), z.B. fuer Spaltenkoepfe. */
    static int fillColor(int i) {
        return THEME[Math.max(0, Math.min(3, i))];
    }

    private static int withAlpha(int col, float alpha) {
        int a = Math.round(((col >>> 24) & 0xFF) * alpha);
        return (a << 24) | (col & 0xFFFFFF);
    }

    // ---------- Groesse ----------

    private static final int SUB_H = 8;    // Hoehe der Schrift "CLIENT"

    /** Abstand Ziffern - "CLIENT" (waechst mit, weil die 3D-Tiefe bei groesserem Logo tiefer reicht). */
    private static int subGap(int scale) {
        return 3 + scale * 2;
    }

    /** Gesamthoehe mit Schriftzug "CLIENT" darunter. */
    static int height(int scale) {
        return H * scale + subGap(scale) + SUB_H;
    }

    // ---------- Zeichnen ----------

    /** Zeichnet das Logo ohne Neigung. scale = Pixelgroesse, alpha = 0..1, dy = senkrechter Versatz. */
    static void draw(GuiGraphicsExtractor g, int x, int y, int scale, float alpha, int dy) {
        draw3d(g, x, y, scale, alpha, dy, 0f, 0f);
    }

    /** Logo mittig bei cx mit dem Schriftzug "CLIENT" darunter (fuer das Hauptmenue). */
    static void drawMenu(GuiGraphicsExtractor g, Font font, int cx, int y, int scale, float alpha, int dy,
            float tiltX, float tiltY) {
        int w = W * scale;
        draw3d(g, cx - w / 2, y, scale, alpha, dy, tiltX, tiltY);
        subtitle(g, font, cx, y + H * scale + subGap(scale) + dy, w, alpha);
    }

    /**
     * 3D-Logo: Tiefe aus mehreren Schichten in dunklen Farbtoenen, die von der Maus weg zeigt (tiltX/tiltY
     * -1..1) - so wirkt es, als wuerde sich das Logo zur Maus drehen. Dazu leichtes Schweben, ein langsam
     * wandernder Farbverlauf, eine helle Kante oben und ein Glanz, der darueber laeuft.
     */
    static void draw3d(GuiGraphicsExtractor g, int x, int y, int scale, float alpha, int dy, float tiltX, float tiltY) {
        float t = Theme.animations ? Fx.time() : 0f;
        float bob = (float) Math.sin(t * 1.4) * scale * 0.7f;
        float flow = Theme.animations ? (float) Math.sin(t * 0.6) * 0.12f : 0f;
        int depth = 2 + scale * 2;
        float sx = -tiltX * 0.5f * scale / 2f;
        float sy = 0.55f * scale / 2f - tiltY * 0.3f * scale / 2f;
        int w = W * scale;
        int h = H * scale;
        int top = y + dy;

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(0f, bob);

        // farbiges Leuchten hinter der Schrift (rosa links, lila Mitte, blau rechts) und weicher Schatten
        float pulse = Theme.animations ? 0.85f + 0.15f * (float) Math.sin(t * 1.1) : 1f;
        int ga = Math.round(0x46 * alpha * pulse);
        Fx.glow(g, x + w * 0.18f, top + h * 0.5f, w * 0.42f, Fx.withAlpha(PINK, ga));
        Fx.glow(g, x + w * 0.5f, top + h * 0.5f, w * 0.46f, Fx.withAlpha(PURPLE, ga));
        Fx.glow(g, x + w * 0.82f, top + h * 0.5f, w * 0.42f, Fx.withAlpha(BLUE, ga));
        Fx.glow(g, x + w / 2f + sx * depth, top + h + scale * 3, w * 0.4f,
                Fx.withAlpha(0xFF000000, Math.round(0x58 * alpha)));

        // Tiefe: Schichten von hinten nach vorne, dunkle Toene der Verlaufsfarbe
        for (int d = depth; d >= 1; d--) {
            float back = d / (float) depth;               // 1 = ganz hinten
            float dark = 0.58f + 0.22f * back;
            pose.pushMatrix();
            pose.translate(sx * d, sy * d);
            for (int r = 0; r < H; r++) {
                int[] runs = RUNS[r];
                for (int i = 0; i < runs.length; i += 2) {
                    float mid = (runs[i] + runs[i + 1]) * 0.5f / W + flow;
                    int col = Ui.lerp(gradient(mid), 0xFF12051F, dark);
                    g.fill(x + runs[i] * scale, top + r * scale, x + runs[i + 1] * scale, top + (r + 1) * scale,
                            withAlpha(col, alpha));
                }
            }
            pose.popMatrix();
        }

        // dunkler Rand um die Vorderseite (1 Pixel), damit die Schrift sich vom Hintergrund abhebt
        int rim = withAlpha(0xE01A0830, alpha);
        for (int r = 0; r < H; r++) {
            int[] runs = RUNS[r];
            for (int i = 0; i < runs.length; i += 2) {
                g.fill(x + runs[i] * scale - 1, top + r * scale - 1, x + runs[i + 1] * scale + 1,
                        top + (r + 1) * scale + 1, rim);
            }
        }

        // Vorderseite: Verlauf von links nach rechts, oben heller, unten dunkler
        float shine = Theme.animations ? (t * 0.32f) % 1.7f - 0.35f : -1f;
        for (int r = 0; r < H; r++) {
            float v = r / (float) (H - 1);
            float light = Math.max(0f, 0.30f - v * 0.6f);        // obere Zeilen aufhellen
            float shadow = Math.max(0f, v - 0.55f) * 0.42f;       // untere Zeilen abdunkeln
            int y1 = top + r * scale;
            int y2 = y1 + scale;
            for (int c = 0; c < W; c++) {
                if (!MASK[r][c]) continue;
                int col = gradient((c + 0.5f) / W + flow);
                if (light > 0f) col = Ui.lerp(col, 0xFFFFFFFF, light);
                if (shadow > 0f) col = Ui.lerp(col, 0xFF1A0730, shadow);
                int x1 = x + c * scale;
                int x2 = x1 + scale;
                g.fill(x1, y1, x2, y2, withAlpha(col, alpha));
                // helle Kante oben, dunkle Kante unten (wie ein leicht gewoelbtes Glas)
                if (!filled(r - 1, c)) g.fill(x1, y1, x2, y1 + 1, withAlpha(0x8CFFFFFF, alpha));
                if (!filled(r + 1, c) && scale > 1) g.fill(x1, y2 - 1, x2, y2, withAlpha(0x50000000, alpha));
                // schraeger Glanzstreifen, der langsam ueber die Schrift laeuft
                if (shine > -0.5f) {
                    float pos = (c + 0.5f) / W - (1f - v) * 0.22f;
                    float f = 1f - Math.abs(pos - shine) / 0.09f;
                    if (f > 0f) g.fill(x1, y1, x2, y2, withAlpha(0xFFFFFF | (Math.round(0x66 * f) << 24), alpha));
                }
            }
        }
        pose.popMatrix();
    }

    /** "U T I L I T I E S" mit Farbverlauf, links und rechts eine feine Linie, die nach aussen ausblendet. */
    private static void subtitle(GuiGraphicsExtractor g, Font font, int cx, int y, int logoW, float alpha) {
        String text = "UTILITIES";
        int spacing = 3;
        int tw = 0;
        for (int i = 0; i < text.length(); i++) tw += font.width(text.substring(i, i + 1)) + (i > 0 ? spacing : 0);
        int x = cx - tw / 2;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            int col = gradient(i / (float) (text.length() - 1));
            col = Ui.lerp(col, 0xFFFFFFFF, 0.35f);
            g.text(font, ch, x, y, withAlpha(col, alpha * 0.95f), false);
            x += font.width(ch) + spacing;
        }
        // Linien: von der Schrift nach aussen bis zur Logo-Breite, ausblendend
        int gap = 6;
        int len = Math.max(8, logoW / 2 - tw / 2 - gap);
        int ly = y + 3;
        int steps = Math.max(1, len / 2);
        for (int s = 0; s < steps; s++) {
            float f = 1f - s / (float) steps;
            int a = Math.round(0xB0 * f * f * alpha);
            int lx = cx - tw / 2 - gap - (s + 1) * 2;
            g.fill(lx, ly, lx + 2, ly + 1, (a << 24) | (Ui.lerp(PINK, PURPLE, 1f - f) & 0xFFFFFF));
            int rx = cx + tw / 2 + gap + s * 2;
            g.fill(rx, ly, rx + 2, ly + 1, (a << 24) | (Ui.lerp(BLUE, PURPLE, 1f - f) & 0xFFFFFF));
        }
    }
}

/**
 * Absender der Chat-Meldungen: "1912 Utilities" mit Farbverlauf rosa - lila - blau (dieselben Farben wie das
 * Logo), dahinter ein grauer Pfeil und die eigentliche Meldung.
 */
final class Brand {
    private Brand() {
    }

    static final String NAME = "1912 Utilities";
    private static final String PREFIX = "1912: ";

    /** "1912 Utilities" Buchstabe fuer Buchstabe im Farbverlauf, fett. */
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
     * Macht aus einem Text wie "§c1912: Totem verbraucht!" die Meldung "1912 Utilities » Totem verbraucht!"
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
