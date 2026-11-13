package de.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

/**
 * Gemeinsamer Stil fuer alle Menues: Farben, weiche Verlaeufe, runde Flaechen und die
 * einheitlich gezeichneten Bedienelemente. Texte werden nie mit Punkten abgeschnitten -
 * die Layouts sorgen fuer genug Platz, und nur im aeussersten Notfall wird ein Text
 * etwas kleiner geschrieben.
 */
final class Ui {
    private Ui() {
    }

    static final int PANEL = 0xE0140F1F;
    static final int BTN = 0xE0211A35;
    static final int BTN_OFF = 0xA0171220;
    static final int TEXT = 0xFFE8E2F3;
    static final int TEXT_ON = 0xFFE3D4FF;
    static final int MUTED = 0xFF8F84A8;
    /** Ruhige Linie/Rahmenfarbe fuer nicht aktive Elemente. */
    static final int LINE = 0xFF3A3450;
    static final int HAIR = 0x26FFFFFF;

    static final int STYLE_NORMAL = 0;
    static final int STYLE_PRIMARY = 1;
    static final int STYLE_ON = 2;

    static final int HEADER_H = 22;
    /** Eckenradius der Panels. */
    static final int RADIUS = 5;

    private static final float HALF_PI = (float) (Math.PI / 2);

    // ---------- Animation ----------

    /** Globale Transparenz fuer die Einblend-Animation; wird pro Frame vom Screen gesetzt. */
    static float alpha = 1f;

    private static final Map<Object, float[]> WIDGET_STATE = new WeakHashMap<>();
    private static final Map<String, float[]> NAMED_STATE = new HashMap<>();

    private static long lastFrameNs = 0;
    private static float frameScale = 1f;

    /** Einmal pro Frame aufrufen: misst die Frame-Zeit (1.0 = 60 FPS), damit Animationen ueberall gleich schnell laufen. */
    static void beginFrame() {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 1f : (now - lastFrameNs) / 16_666_667f;
        lastFrameNs = now;
        frameScale = Math.max(0.1f, Math.min(4f, dt));
    }

    /** Anteil, den ein Wert pro Frame zum Ziel aufholt (speed bezieht sich auf 60 FPS). 1 = sofort. */
    static float step(float speed) {
        if (!Theme.animations) return 1f;
        return 1f - (float) Math.pow(1f - speed, frameScale);
    }

    /**
     * Weicher Uebergang zum Zielwert (0..1), je Objekt und Kanal gemerkt.
     * String-Schluessel bleiben erhalten, andere Objekte (z.B. Buttons) werden schwach referenziert.
     */
    static float ease(Object key, int channel, float target, float speed) {
        float[] st;
        if (key instanceof String name) {
            st = NAMED_STATE.computeIfAbsent(name, k -> new float[] {Float.NaN, Float.NaN, Float.NaN});
        } else {
            st = WIDGET_STATE.computeIfAbsent(key, k -> new float[] {Float.NaN, Float.NaN, Float.NaN});
        }
        float v = Float.isNaN(st[channel]) ? target : st[channel];
        if (!Theme.animations) {
            v = target;
        } else {
            v += (target - v) * step(speed);
            if (Math.abs(target - v) < 0.01f) v = target;
        }
        st[channel] = v;
        return v;
    }

    // ---------- Farben ----------

    /** Farbe mal globale Transparenz (fuer Text: nie ganz durchsichtig, das zeichnet Minecraft sonst deckend). */
    static int mul(int color) {
        int a0 = color >>> 24;
        if (alpha >= 0.999f || a0 < 8) return color;
        int a = Math.max(8, Math.round(a0 * Math.max(0f, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** Farbe mal globale Transparenz, exakt (fuer Flaechen und Verlaeufe, darf bis 0 gehen). */
    static int mulA(int color) {
        if (alpha >= 0.999f) return color;
        int a = Math.round((color >>> 24) * Math.max(0f, alpha));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** Dieselbe Farbe mit anderer Deckkraft (0..255). */
    static int withA(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = a >>> 24, r1 = (a >> 16) & 255, g1 = (a >> 8) & 255, b1 = a & 255;
        int br = b >>> 24, r2 = (b >> 16) & 255, g2 = (b >> 8) & 255, b2 = b & 255;
        return (Math.round(ar + (br - ar) * t) << 24)
                | (Math.round(r1 + (r2 - r1) * t) << 16)
                | (Math.round(g1 + (g2 - g1) * t) << 8)
                | Math.round(b1 + (b2 - b1) * t);
    }

    // ---------- Flaechen ----------

    static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, mul(color));
    }

    /** Stufenloser senkrechter Verlauf (oben -> unten). */
    static void gradientV(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top, int bottom) {
        if (x2 <= x1 || y2 <= y1) return;
        g.fillGradient(x1, y1, x2, y2, mulA(top), mulA(bottom));
    }

    /** Stufenloser waagerechter Verlauf (links -> rechts): ein gedrehter senkrechter Verlauf. */
    static void gradientH(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int left, int right) {
        if (x2 <= x1 || y2 <= y1) return;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x1, y2);
        pose.rotate(-HALF_PI);
        g.fillGradient(0, 0, y2 - y1, x2 - x1, mulA(left), mulA(right));
        pose.popMatrix();
    }

    /** Rechteck mit abgeschnittenen Ecken (je 1 Pixel). */
    static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        fill(g, x + 1, y, x + w - 1, y + h, color);
        fill(g, x, y + 1, x + w, y + h - 1, color);
    }

    /** Wie weit Zeile i (0 = aeusserste) einer runden Ecke mit Radius r eingerueckt ist. */
    static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
    }

    /** Rechteck mit echten runden Ecken (Radius r). */
    static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        roundRect(g, x, y, w, h, r, color, true, true);
    }

    /** Rechteck mit runden Ecken oben und/oder unten. */
    static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color,
            boolean top, boolean bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int rt = top ? r : 0;
        int rb = bottom ? r : 0;
        for (int i = 0; i < rt; i++) {
            int in = inset(r, i);
            fill(g, x + in, y + i, x + w - in, y + i + 1, color);
        }
        if (h - rt - rb > 0) fill(g, x, y + rt, x + w, y + h - rb, color);
        for (int i = 0; i < rb; i++) {
            int in = inset(r, i);
            fill(g, x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    /** Waagerechter Farbverlauf mit runden Ecken oben und/oder unten. */
    static void roundGradientH(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int left, int right,
            boolean top, boolean bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int rt = top ? r : 0;
        int rb = bottom ? r : 0;
        for (int i = 0; i < rt; i++) {
            int in = inset(r, i);
            float f = in / (float) w;
            gradientH(g, x + in, y + i, x + w - in, y + i + 1, lerp(left, right, f), lerp(left, right, 1f - f));
        }
        gradientH(g, x, y + rt, x + w, y + h - rb, left, right);
        for (int i = 0; i < rb; i++) {
            int in = inset(r, i);
            float f = in / (float) w;
            gradientH(g, x + in, y + h - 1 - i, x + w - in, y + h - i, lerp(left, right, f), lerp(left, right, 1f - f));
        }
    }

    // ---------- Text ----------

    static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
        g.text(font, s, x, y, mul(color), shadow);
    }

    /**
     * Text, der immer ganz zu sehen ist: passt er nicht in die Breite, wird er verkleinert
     * (nie gekuerzt und nie mit Punkten abgeschnitten).
     */
    static void fitText(GuiGraphicsExtractor g, Font font, String s, int x, int y, int max, int color, boolean shadow) {
        int w = font.width(s);
        if (max <= 0 || w <= max) {
            text(g, font, s, x, y, color, shadow);
            return;
        }
        float sc = max / (float) w;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y + (1f - sc) * 4f);
        pose.scale(sc, sc);
        text(g, font, s, 0, 0, color, shadow);
        pose.popMatrix();
    }

    /** Wie {@link #fitText}, aber um cx zentriert. */
    static void fitCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int max, int color,
            boolean shadow) {
        int w = font.width(s);
        if (max <= 0 || w <= max) {
            text(g, font, s, cx - w / 2, y, color, shadow);
            return;
        }
        fitText(g, font, s, cx - max / 2, y, max, color, shadow);
    }

    /** Bricht einen Text an Leerzeichen so um, dass jede Zeile in die Breite passt. */
    static List<String> wrap(Font font, String text, int max) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (max <= 0 || font.width(para) <= max) {
                out.add(para);
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && font.width(tryLine) > max) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(tryLine);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    // ---------- Panel ----------

    /**
     * Panel mit runden Ecken, weichem Schatten und Kopfzeile im Farbverlauf des Themes.
     * reserveRight: so viel Platz bleibt rechts in der Kopfzeile frei (z.B. fuer ein Suchfeld).
     */
    static void panel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, int reserveRight) {
        int r = RADIUS;
        Fx.shadow(g, x, y, w, h);
        roundRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, Theme.border());
        roundRect(g, x, y + HEADER_H, w, h - HEADER_H, r, PANEL, false, true);
        header(g, x, y, w, 0f, 1f, false);
        fitText(g, font, title, x + 10, y + 7, w - 20 - reserveRight, 0xFFFFFFFF, true);
    }

    /**
     * Kopfzeile: stufenloser waagerechter Verlauf durch die Theme-Farben. t0..t1 waehlt den
     * Ausschnitt des Verlaufs (0 = erste Farbe, 1 = letzte), so koennen mehrere Koepfe
     * nebeneinander einen durchgehenden Verlauf bilden.
     */
    static void header(GuiGraphicsExtractor g, int x, int y, int w, float t0, float t1, boolean roundBottom) {
        int left = Theme.headAt(t0);
        int right = Theme.headAt(t1);
        for (int row = 0; row < HEADER_H; row++) {
            int in = row < RADIUS ? inset(RADIUS, row) : 0;
            if (roundBottom && HEADER_H - 1 - row < RADIUS) in = Math.max(in, inset(RADIUS, HEADER_H - 1 - row));
            float f = in / (float) Math.max(1, w);
            float v = row / (float) (HEADER_H - 1);
            gradientH(g, x + in, y + row, x + w - in, y + row + 1,
                    shade(lerp(left, right, f), v), shade(lerp(left, right, 1f - f), v));
        }
    }

    /** Sanfte Schattierung von oben (etwas heller) nach unten (etwas dunkler); v = 0..1. */
    private static int shade(int color, float v) {
        if (v < 0.5f) return lerp(color, 0xFFFFFFFF, 0.14f * (1f - v * 2f));
        return lerp(color, 0xFF000000, 0.16f * (v * 2f - 1f));
    }

    // ---------- Bedienelemente ----------

    /** Kleines Plus/Minus (zum Ein-/Ausklappen); t = 1 Plus (zu), t = 0 Minus (offen), dazwischen animiert. */
    static void plusMinusIcon(GuiGraphicsExtractor g, int cx, int cy, float t, int color) {
        fill(g, cx - 3, cy, cx + 4, cy + 1, color);
        int v = Math.round(3f * Math.max(0f, Math.min(1f, t)));
        if (v > 0) fill(g, cx, cy - v, cx + 1, cy + 1 + v, color);
    }

    /** Runder An/Aus-Schalter (16x8). t = 0 aus, 1 an. */
    static void switchPill(GuiGraphicsExtractor g, int x, int y, float t) {
        roundRect(g, x, y, 16, 8, 4, lerp(LINE, Theme.accent(), t));
        int kx = x + 1 + Math.round(t * 8f);
        roundRect(g, kx, y + 1, 6, 6, 3, lerp(0xFFB9B0D0, 0xFFFFFFFF, t));
    }

    /** Schmaler Scrollbalken: Spur plus Griff. hover 0..1 hebt den Griff hervor. */
    static void scrollbar(GuiGraphicsExtractor g, int x, int trackY, int trackH, int thumbY, int thumbH, float hover) {
        roundRect(g, x, trackY, 3, trackH, 1, 0x16FFFFFF);
        roundRect(g, x, thumbY, 3, thumbH, 1, lerp(0x70FFFFFF, Theme.accent(), hover));
    }

    /**
     * Eigener Rahmen fuer ein Eingabefeld. Er liegt genau ueber dem Rand, den Minecraft selbst
     * zeichnet, und leuchtet weich in der Akzentfarbe auf, wenn das Feld aktiv ist.
     */
    static void field(GuiGraphicsExtractor g, EditBox b) {
        float f = ease(b, 0, b.isFocused() ? 1f : 0f, 0.3f);
        int c = lerp(LINE, 0xFF000000 | Theme.accent(), f);
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        fill(g, x, y, x + w, y + 1, c);
        fill(g, x, y + h - 1, x + w, y + h, c);
        fill(g, x, y + 1, x + 1, y + h - 1, c);
        fill(g, x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    static final String[] ICON_RESET = {
        "..####.#.",
        ".#....###",
        "#......#.",
        "#........",
        "#.......#",
        ".#.....#.",
        "..#####.."
    };

    /** Zeichnet ein kleines Pixelbild (# = Pixel) mittig in ein Feld. */
    static void pattern(GuiGraphicsExtractor g, String[] rows, int x, int y, int w, int h, int color) {
        int pw = rows[0].length();
        int ox = x + (w - pw) / 2;
        int oy = y + (h - rows.length) / 2;
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < pw) {
                if (row.charAt(c) != '#') {
                    c++;
                    continue;
                }
                int start = c;
                while (c < pw && row.charAt(c) == '#') c++;
                fill(g, ox + start, oy + r, ox + c, oy + r + 1, color);
            }
        }
    }

    /** Rahmen + Hintergrund eines 20 Pixel hohen Feldes. Gibt die Hover-Staerke 0..1 zurueck. */
    static float box20(GuiGraphicsExtractor g, Object key, int x, int y, int w, int mx, int my, boolean active) {
        boolean over = active && mx >= x && mx < x + w && my >= y && my < y + 20;
        float hv = ease(key, 0, over ? 1f : 0f, 0.3f);
        roundRect(g, x, y, w, 20, 3, lerp(LINE, Theme.accent(), hv * 0.8f));
        roundRect(g, x + 1, y + 1, w - 2, 18, 2, active ? lerp(BTN, Theme.btnHover(), hv) : BTN_OFF);
        return hv;
    }

    /** Haken-Feld: die Fuellung blendet weich ein und aus. */
    static void checkbox(GuiGraphicsExtractor g, Object key, int x, int y, boolean on, int mx, int my) {
        float hv = box20(g, key, x, y, 20, mx, my, true);
        float t = ease(key, 1, on ? 1f : 0f, 0.35f);
        if (t > 0.02f) {
            int c = withA(lerp(Theme.accent(), 0xFFFFFFFF, hv * 0.15f), Math.round(255 * t));
            int half = 3 + Math.round(2f * t);
            roundRect(g, x + 10 - half, y + 10 - half, half * 2, half * 2, 2, c);
        }
    }

    /**
     * Zuruecksetzen-Symbol ohne Kasten. show 0..1 blendet es ein (nur sichtbar, wenn der Wert
     * vom Standard abweicht); unter der Maus leuchtet es in der Akzentfarbe.
     */
    static void resetIcon(GuiGraphicsExtractor g, Object key, int x, int y, int mx, int my, float show) {
        if (show <= 0.02f) return;
        boolean over = mx >= x && mx < x + 16 && my >= y && my < y + 20;
        float hv = ease(key, 0, over ? 1f : 0f, 0.3f);
        if (hv > 0.02f) roundRect(g, x, y + 2, 16, 16, 3, withA(Theme.accent(), Math.round(0x30 * hv * show)));
        pattern(g, ICON_RESET, x, y, 16, 20, withA(lerp(MUTED, 0xFFFFFFFF, hv), Math.round(255 * show)));
    }

    /** Plus/Minus-Knopf der Auswahllisten. */
    static void plusMinus(GuiGraphicsExtractor g, Object key, int x, int y, boolean plus, boolean active, int mx, int my) {
        float hv = box20(g, key, x, y, 20, mx, my, active);
        int c = !active ? LINE : lerp(TEXT, plus ? 0xFF7CF29A : 0xFFFF8A8A, 0.35f + 0.65f * hv);
        fill(g, x + 6, y + 9, x + 14, y + 11, c);
        if (plus) fill(g, x + 9, y + 6, x + 11, y + 14, c);
    }

    /** So breit muss ein Knopf sein, damit sein Text ganz hineinpasst. */
    static int buttonWidth(Font font, String text) {
        return font.width(text) + 16;
    }

    static void button(GuiGraphicsExtractor g, Font font, Button b, int mx, int my, int style) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean active = b.active;
        boolean over = active && mx >= x && mx < x + w && my >= y && my < y + h;
        float hv = ease(b, 0, over ? 1f : 0f, 0.3f);

        int textColor;
        if (!active) {
            roundRect(g, x, y, w, h, 3, LINE);
            roundRect(g, x + 1, y + 1, w - 2, h - 2, 2, BTN_OFF);
            textColor = MUTED;
        } else if (style == STYLE_PRIMARY) {
            roundRect(g, x, y, w, h, 3, lerp(Theme.border(), 0xFFFFFFFF, 0.25f * hv));
            roundGradientH(g, x + 1, y + 1, w - 2, h - 2, 2, lerp(Theme.headAt(0.1f), 0xFFFFFFFF, 0.12f * hv),
                    lerp(Theme.headAt(0.9f), 0xFFFFFFFF, 0.12f * hv), true, true);
            textColor = 0xFFFFFFFF;
        } else if (style == STYLE_ON) {
            roundRect(g, x, y, w, h, 3, lerp(Theme.border(), Theme.accent(), hv));
            roundRect(g, x + 1, y + 1, w - 2, h - 2, 2, lerp(Theme.onBg(), Theme.btnHover(), hv));
            textColor = TEXT_ON;
        } else {
            roundRect(g, x, y, w, h, 3, lerp(LINE, Theme.accent(), hv * 0.8f));
            roundRect(g, x + 1, y + 1, w - 2, h - 2, 2, lerp(BTN, Theme.btnHover(), hv));
            textColor = lerp(TEXT, 0xFFFFFFFF, hv);
        }
        fitCentered(g, font, b.getMessage().getString(), x + w / 2, y + (h - 8) / 2, w - 10, textColor, true);
    }
}
