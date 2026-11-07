package de.test;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Das 1912-Logo (Pixel-Art), gezeichnet aus Rechtecken - keine Textur noetig. */
final class Logo {
    private Logo() {
    }

    static final int W = 55;
    static final int H = 17;

    /** '.' leer, '#' schwarzer Rand, a-d = Zifferfarben (1, 9, 1, 2), e-h = passende Schatten. */
    private static final String[] ROWS = {
        "...####.........########........###........########....",
        "...aaaa#........bbbbbbbb#.......ccc#.......dddddddd#...",
        ".##aaaa#.......#bbbbbbbb##....##ccc#.....##dddddddd##..",
        ".aaaaaa#e.....bbbb####bbbb#...ccccc#g....dddd####dddd#.",
        ".aaaaaa#e.....bbbb#ff#bbbb#...ccccc#g....dddd#hh#dddd#.",
        "..#aaaa#e.....bbbb#f..bbbb#f...#ccc#g....####hh..dddd#h",
        "...aaaa#e.....bbbb##.#bbbb#f....ccc#g......hh####dddd#h",
        "...aaaa#e......#bbbbbbbbbb#f....ccc#g.......#dddddd##hh",
        "...aaaa#e.......bbbbbbbbbb#f....ccc#g......##dddddd#hhh",
        "...aaaa#e.......######bbbb#f....ccc#g......dddd####hh..",
        "...aaaa#e.........fff#bbbb#f....ccc#g.....#dddd#h####..",
        "...aaaa#e..........#bbbb##ff....ccc#g....dddd##h#dddd#.",
        "..#aaaa##..........#bbbb#fff...#ccc##....dddd#hh#dddd#h",
        "aaaaaaaaaaaa#...bbbbbb##ff...cccccccccc#.dddddddddddd.h",
        "aaaaaaaaaaaa#e..bbbbbb#fff...cccccccccc#gdddddddddddd#h",
        ".###########ee..######ff......#########gg.###########hh",
        "..eeeeeeeeeeee...fffffff.......gggggggggg..hhhhhhhhhhhh"
    };

    private static final int[] PALETTE = {
        0xFFAC00A6,
        0xFF8B1AC6,
        0xFF7038E4,
        0xFF5253FE,
        0xFF380238,
        0xFF2E0443,
        0xFF260D4D,
        0xFF191853
    };

    /** Hauptfarbe der i-ten Ziffer (0..3), z.B. fuer Spaltenkoepfe. */
    static int fillColor(int i) {
        return PALETTE[Math.max(0, Math.min(3, i))];
    }

    private static int color(char c) {
        if (c == '#') return 0xFF000000;
        int i = c - 'a';
        return i >= 0 && i < PALETTE.length ? PALETTE[i] : 0;
    }

    /** Zeichnet das Logo. scale = Pixelgroesse, alpha = 0..1, dy = senkrechter Versatz. */
    static void draw(GuiGraphicsExtractor g, int x, int y, int scale, float alpha, int dy) {
        for (int r = 0; r < ROWS.length; r++) {
            String row = ROWS[r];
            int c = 0;
            while (c < row.length()) {
                char ch = row.charAt(c);
                int start = c;
                while (c < row.length() && row.charAt(c) == ch) c++;
                if (ch == '.') continue;
                int col = color(ch);
                int a = Math.round(((col >>> 24) & 0xFF) * alpha);
                g.fill(x + start * scale, y + r * scale + dy, x + c * scale, y + (r + 1) * scale + dy,
                        (a << 24) | (col & 0xFFFFFF));
            }
        }
    }
}
