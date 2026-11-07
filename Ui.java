package de.test;

import java.util.ArrayList;
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
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
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
                    if (h[0] > cx) g.fill(cx, b1, Math.min(h[0], x2), b2, color);
                    cx = Math.max(cx, h[2]);
                }
                if (cx < x2) g.fill(cx, b1, x2, b2, color);
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
        g.fill(h[0], h[1], h[2], h[1] + 1, c);
        g.fill(h[0], h[3] - 1, h[2], h[3], c);
        g.fill(h[0], h[1], h[0] + 1, h[3], c);
        g.fill(h[2] - 1, h[1], h[2], h[3], c);
    }

    /** Panel mit Kopfzeile. Wird NACH super.extractRenderState gezeichnet; die Loecher schuetzen die Eingabefelder. */
    static void panel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, List<int[]> holes) {
        int bodyH = h - HEADER_H;
        // Schatten und Rand
        g.fill(x + 2, y + 3, x + w + 2, y + h + 2, 0x55000000);
        rounded(g, x - 1, y - 1, w + 2, h + 2, Theme.border());

        // Koerper (untere Ecken abgerundet)
        fillExcept(g, x, y + HEADER_H, x + w, y + h - 2, PANEL, holes);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, PANEL);

        // Kopfzeile mit Verlauf
        for (int r = 0; r < HEADER_H; r++) {
            int inset = r == 0 ? 2 : r == 1 ? 1 : 0;
            g.fill(x + inset, y + r, x + w - inset, y + r + 1,
                    lerp(Theme.headTop(), Theme.headBottom(), r / (float) (HEADER_H - 1)));
        }
        g.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, 0x40FFFFFF);
        g.text(font, title, x + 8, y + 7, 0xFFFFFFFF, true);
    }

    static void button(GuiGraphicsExtractor g, Font font, Button b, int mx, int my, int style) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean active = b.active;
        boolean over = active && mx >= x && mx < x + w && my >= y && my < y + h;

        rounded(g, x, y, w, h, over ? Theme.accent() : Theme.border());
        int ix = x + 1, iy = y + 1, iw = w - 2, ih = h - 2;

        int textColor = TEXT;
        if (!active) {
            g.fill(ix, iy, ix + iw, iy + ih, BTN_OFF);
            textColor = MUTED;
        } else if (style == STYLE_PRIMARY) {
            for (int r = 0; r < ih; r++) {
                int c = lerp(Theme.headTop(), Theme.headBottom(), r / (float) Math.max(1, ih - 1));
                if (over) c = lerp(c, 0xFFFFFFFF, 0.15f);
                g.fill(ix, iy + r, ix + iw, iy + r + 1, c);
            }
            textColor = 0xFFFFFFFF;
        } else if (style == STYLE_ON) {
            g.fill(ix, iy, ix + iw, iy + ih, over ? Theme.btnHover() : Theme.onBg());
            g.fill(ix, iy, ix + 2, iy + ih, Theme.accent());
            textColor = TEXT_ON;
        } else {
            g.fill(ix, iy, ix + iw, iy + ih, over ? Theme.btnHover() : BTN);
            if (over) textColor = 0xFFFFFFFF;
        }

        String s = b.getMessage().getString();
        int max = w - 10;
        if (font.width(s) > max) {
            while (s.length() > 1 && font.width(s + "...") > max) {
                s = s.substring(0, s.length() - 1);
            }
            s = s + "...";
        }
        g.text(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, textColor, true);
    }
}
