package de.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

/** Gemeinsamer Flieder/Lila-Stil fuer alle Untermenues. */
final class Ui {
    private Ui() {
    }

    static final int PANEL = 0xE0140F1F;
    static final int BTN = 0xE0211A35;
    static final int BTN_OFF = 0xA0171220;
    static final int TEXT = 0xFFE8E2F3;
    static final int TEXT_ON = 0xFFE3D4FF;
    static final int MUTED = 0xFF8F84A8;

    static final int STYLE_NORMAL = 0;
    static final int STYLE_PRIMARY = 1;
    static final int STYLE_ON = 2;

    static final int HEADER_H = 22;

    /** Ein sichtbar gezeichneter Button (das echte Button-Widget wird nur fuer Klicks genutzt). */
    static final class Btn {
        final Button button;
        final IntSupplier style;

        Btn(Button button, IntSupplier style) {
            this.button = button;
            this.style = style;
        }
    }


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

    static int mul(int color) {
        int a0 = color >>> 24;
        if (alpha >= 0.999f || a0 < 8) return color;
        int a = Math.max(8, Math.round(a0 * Math.max(0f, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, mul(color));
    }

    static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
        g.text(font, s, x, y, mul(color), shadow);
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

    static int lerp(int a, int b, float t) {
        int ar = a >>> 24, r1 = (a >> 16) & 255, g1 = (a >> 8) & 255, b1 = a & 255;
        int br = b >>> 24, r2 = (b >> 16) & 255, g2 = (b >> 8) & 255, b2 = b & 255;
        return (Math.round(ar + (br - ar) * t) << 24)
                | (Math.round(r1 + (r2 - r1) * t) << 16)
                | (Math.round(g1 + (g2 - g1) * t) << 8)
                | Math.round(b1 + (b2 - b1) * t);
    }

    /** Rechteck mit abgeschnittenen Ecken (je 1 Pixel). */
    static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        fill(g, x + 1, y, x + w - 1, y + h, color);
        fill(g, x, y + 1, x + w, y + h - 1, color);
    }

    /** Fuellt ein Rechteck, spart aber die angegebenen Loecher {x1,y1,x2,y2} aus. */
    static void fillExcept(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, List<int[]> holes) {
        TreeSet<Integer> ys = new TreeSet<>();
        ys.add(y1);
        ys.add(y2);
        for (int[] h : holes) {
            if (h[3] > y1 && h[1] < y2) {
                ys.add(Math.max(y1, h[1]));
                ys.add(Math.min(y2, h[3]));
            }
        }
        Integer prev = null;
        for (int y : ys) {
            if (prev != null) {
                int b1 = prev;
                int b2 = y;
                List<int[]> hs = new ArrayList<>();
                for (int[] h : holes) {
                    if (h[1] <= b1 && h[3] >= b2) hs.add(h);
                }
                hs.sort(Comparator.comparingInt(h -> h[0]));
                int cx = x1;
                for (int[] h : hs) {
                    if (h[0] > cx) fill(g, cx, b1, Math.min(h[0], x2), b2, color);
                    cx = Math.max(cx, h[2]);
                }
                if (cx < x2) fill(g, cx, b1, x2, b2, color);
            }
            prev = y;
        }
    }

    /** Loch fuer ein Eingabefeld (inkl. 1 Pixel Rahmen). */
    static int[] hole(EditBox b) {
        return new int[] {b.getX() - 1, b.getY() - 1, b.getX() + b.getWidth() + 1, b.getY() + b.getHeight() + 1};
    }

    static void frame(GuiGraphicsExtractor g, EditBox b) {
        int[] h = hole(b);
        int c = b.isFocused() ? Theme.accent() : Theme.border();
        fill(g, h[0], h[1], h[2], h[1] + 1, c);
        fill(g, h[0], h[3] - 1, h[2], h[3], c);
        fill(g, h[0], h[1], h[0] + 1, h[3], c);
        fill(g, h[2] - 1, h[1], h[2], h[3], c);
    }

    /** Panel mit Kopfzeile. Wird NACH super.extractRenderState gezeichnet; die Loecher schuetzen die Eingabefelder. */
    static void panel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, List<int[]> holes) {
        int bodyH = h - HEADER_H;
        // Schatten und Rand
        fill(g, x + 2, y + 3, x + w + 2, y + h + 2, 0x55000000);
        rounded(g, x - 1, y - 1, w + 2, h + 2, Theme.border());

        // Koerper (untere Ecken abgerundet)
        fillExcept(g, x, y + HEADER_H, x + w, y + h - 2, PANEL, holes);
        fill(g, x + 1, y + h - 2, x + w - 1, y + h - 1, PANEL);
        fill(g, x + 2, y + h - 1, x + w - 2, y + h, PANEL);

        // Kopfzeile mit Verlauf
        for (int r = 0; r < HEADER_H; r++) {
            int inset = r == 0 ? 2 : r == 1 ? 1 : 0;
            fill(g, x + inset, y + r, x + w - inset, y + r + 1,
                    lerp(Theme.headTop(), Theme.headBottom(), r / (float) (HEADER_H - 1)));
        }
        fill(g, x, y + HEADER_H - 1, x + w, y + HEADER_H, 0x40FFFFFF);
        Fx.sweep(g, x + 2, y + HEADER_H - 1, w - 4, 1, 0xFFFFFFFF);
        text(g, font, title, x + (w - font.width(title)) / 2, y + 7, 0xFFFFFFFF, true);
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
            for (int c = 0; c < pw; c++) {
                if (rows[r].charAt(c) == '#') fill(g, ox + c, oy + r, ox + c + 1, oy + r + 1, color);
            }
        }
    }

    /** Rahmen + Hintergrund eines 20x20-Feldes (oder breiter). Gibt die Hover-Staerke 0..1 zurueck. */
    static float box20(GuiGraphicsExtractor g, Object key, int x, int y, int w, int mx, int my, boolean active) {
        boolean over = active && mx >= x && mx < x + w && my >= y && my < y + 20;
        float hv = ease(key, 0, over ? 1f : 0f, 0.3f);
        rounded(g, x, y, w, 20, lerp(Theme.border(), Theme.accent(), hv));
        fill(g, x + 1, y + 1, x + w - 1, y + 19, active ? lerp(BTN, Theme.btnHover(), hv) : BTN_OFF);
        return hv;
    }

    static void checkbox(GuiGraphicsExtractor g, Object key, int x, int y, boolean on, int mx, int my) {
        box20(g, key, x, y, 20, mx, my, true);
        float t = ease(key, 1, on ? 1f : 0f, 0.35f);
        int half = Math.round(5f * t);
        if (half > 0) fill(g, x + 10 - half, y + 10 - half, x + 10 + half, y + 10 + half, Theme.accent());
    }

    static void resetIcon(GuiGraphicsExtractor g, Object key, int x, int y, int mx, int my) {
        float hv = box20(g, key, x, y, 20, mx, my, true);
        pattern(g, ICON_RESET, x, y, 20, 20, lerp(TEXT, 0xFFFFFFFF, hv));
    }

    static void plusMinus(GuiGraphicsExtractor g, Object key, int x, int y, boolean plus, boolean active, int mx, int my) {
        box20(g, key, x, y, 20, mx, my, active);
        int c = !active ? MUTED : plus ? 0xFF30E030 : 0xFFE03030;
        fill(g, x + 5, y + 9, x + 15, y + 11, c);
        if (plus) fill(g, x + 9, y + 5, x + 11, y + 15, c);
    }

    static void button(GuiGraphicsExtractor g, Font font, Button b, int mx, int my, int style) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean active = b.active;
        boolean over = active && mx >= x && mx < x + w && my >= y && my < y + h;
        float hv = ease(b, 0, over ? 1f : 0f, 0.3f);

        rounded(g, x, y, w, h, lerp(Theme.border(), Theme.accent(), hv));
        int ix = x + 1, iy = y + 1, iw = w - 2, ih = h - 2;

        int textColor = TEXT;
        if (!active) {
            fill(g, ix, iy, ix + iw, iy + ih, BTN_OFF);
            textColor = MUTED;
        } else if (style == STYLE_PRIMARY) {
            for (int r = 0; r < ih; r++) {
                int c = lerp(Theme.headTop(), Theme.headBottom(), r / (float) Math.max(1, ih - 1));
                if (hv > 0f) c = lerp(c, 0xFFFFFFFF, 0.15f * hv);
                fill(g, ix, iy + r, ix + iw, iy + r + 1, c);
            }
            textColor = 0xFFFFFFFF;
        } else if (style == STYLE_ON) {
            fill(g, ix, iy, ix + iw, iy + ih, lerp(Theme.onBg(), Theme.btnHover(), hv));
            fill(g, ix, iy, ix + 2, iy + ih, Theme.accent());
            textColor = TEXT_ON;
        } else {
            fill(g, ix, iy, ix + iw, iy + ih, lerp(BTN, Theme.btnHover(), hv));
            textColor = lerp(TEXT, 0xFFFFFFFF, hv);
        }

        String s = b.getMessage().getString();
        int max = w - 10;
        if (font.width(s) > max) {
            while (s.length() > 1 && font.width(s + "...") > max) {
                s = s.substring(0, s.length() - 1);
            }
            s = s + "...";
        }
        text(g, font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, textColor, true);
    }
}
