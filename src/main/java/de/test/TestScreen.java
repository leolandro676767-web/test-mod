package de.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/** Hauptmenue im ClickGUI-Stil: Kategorien als Spalten, darunter die Module. */
public class TestScreen extends Screen {
    private static final int GAP = 8;
    private static final int HEADER_H = 22;
    private static final int ENTRY_H = 18;
    private static final int MAX_ROWS = 4;

    // Flieder / Lila statt Rosa
    private static final int PANEL = 0xE0140F1F;
    private static final int TEXT = 0xFFE8E2F3;
    private static final int TEXT_ON = 0xFFE3D4FF;
    private static final int MUTED = 0xFF8F84A8;
    private static final int TRACK_OFF = 0xFF3A3450;

    private static final class Entry {
        final String name;
        final BooleanSupplier state; // null = reiner Button ohne An/Aus
        final Runnable action;
        int x, y;
        boolean last;
        float hover = 0f;
        float anim = 0f;

        Entry(String name, BooleanSupplier state, Runnable action) {
            this.name = name;
            this.state = state;
            this.action = action;
        }
    }

    private static final class Column {
        final String title;
        final List<Entry> entries = new ArrayList<>();
        int x, y;
        float open = 1f; // 0 = eingeklappt, 1 = ausgeklappt (animiert)

        Column(String title) {
            this.title = title;
        }
    }

    private final Screen parent;
    private final List<Column> columns = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();
    private int colW = 104;
    private int logoScale = 2;
    private float ga = 1f; // globale Transparenz (Einblenden)
    private int dy = 0;    // globaler Versatz (Hereingleiten)

    public TestScreen(Screen parent) {
        super(Component.literal("1912"));
        this.parent = parent;
    }

    private static Entry toggle(String name, BooleanSupplier state, Runnable flip) {
        return new Entry(name, state, () -> {
            flip.run();
            TestConfig.save();
        });
    }

    private Entry open(String name, Supplier<Screen> screen) {
        return new Entry(name, null, () -> this.minecraft.gui.setScreen(screen.get()));
    }

    @Override
    protected void init() {
        columns.clear();

        Column move = new Column("Bewegung");
        move.entries.add(toggle("AutoWalk", () -> TestClient.walking,
                () -> TestClient.walking = !TestClient.walking));
        move.entries.add(toggle("Sprinten", () -> TestClient.sprint,
                () -> TestClient.sprint = !TestClient.sprint));
        move.entries.add(toggle("Sprint-Jump", () -> TestClient.sprintJump,
                () -> TestClient.sprintJump = !TestClient.sprintJump));
        move.entries.add(toggle("Auto-Logout", () -> TestClient.autoLogout,
                () -> TestClient.autoLogout = !TestClient.autoLogout));

        Column world = new Column("Welt");
        world.entries.add(toggle("Bl\u00f6cke abbauen", () -> TestClient.autoMine,
                () -> TestClient.autoMine = !TestClient.autoMine));
        world.entries.add(open("Bereich", () -> new AreaScreen(this)));
        world.entries.add(open("Block", () -> new BlockScreen(this)));
        world.entries.add(open("Koordinaten", () -> new GotoScreen(this)));

        Column inv = new Column("Inventar");
        inv.entries.add(toggle("AutoDrop", () -> TestClient.autoDrop,
                () -> TestClient.autoDrop = !TestClient.autoDrop));
        inv.entries.add(open("Drop-Items", () -> new DropScreen(this)));
        inv.entries.add(toggle("AutoClicker", () -> TestClient.autoClicker,
                () -> TestClient.autoClicker = !TestClient.autoClicker));
        inv.entries.add(open("Clicker...", () -> new ClickerScreen(this)));

        Column misc = new Column("Men\u00fc");
        misc.entries.add(open("Design anpassen", () -> new DesignScreen(this)));
        misc.entries.add(open("Tasten \u00e4ndern",
                () -> new KeyBindsScreen(this, this.minecraft.options)));
        misc.entries.add(new Entry("Schlie\u00dfen", null, this::onClose));

        columns.add(move);
        columns.add(world);
        columns.add(inv);
        columns.add(misc);

        int n = columns.size();
        colW = Math.max(80, Math.min(Theme.columnWidth(), (this.width - 24 - (n - 1) * GAP) / n));
        int total = n * colW + (n - 1) * GAP;
        int x0 = (this.width - total) / 2;
        logoScale = Theme.logoSize > 0 ? Theme.logoSize : (this.height >= 220 ? 2 : 1);
        int logoH = Theme.showLogo ? Logo.H * logoScale : 12;
        int colsH = HEADER_H + MAX_ROWS * ENTRY_H;
        int y0 = (this.height - 16 - (logoH + 12 + colsH)) / 2 + logoH + 12;

        for (int c = 0; c < n; c++) {
            Column col = columns.get(c);
            col.x = x0 + c * (colW + GAP);
            col.y = y0;
            col.open = Theme.isCollapsed(c) ? 0f : 1f;
            final int ci = c;
            // Klick auf die Kopfzeile klappt die Spalte ein bzw. aus
            addWidget(Button.builder(Component.empty(), b -> {
                Theme.toggleCollapsed(ci);
                TestConfig.save();
            }).bounds(col.x, col.y, colW, HEADER_H).build());
            for (int e = 0; e < col.entries.size(); e++) {
                Entry en = col.entries.get(e);
                en.x = col.x;
                en.y = col.y + HEADER_H + e * ENTRY_H;
                en.last = e == col.entries.size() - 1;
                en.anim = en.state != null && en.state.getAsBoolean() ? 1f : 0f;
                Runnable action = () -> {
                    if (!Theme.isCollapsed(ci)) en.action.run();
                };
                // Unsichtbarer Klick-Bereich (wird nicht gezeichnet, nur fuer Mausklicks)
                addWidget(Button.builder(Component.empty(), b -> action.run())
                        .bounds(en.x, en.y, colW, ENTRY_H).build());
            }
        }
    }

    // ---------- Zeichen-Helfer ----------

    private int al(int argb) {
        int a = Math.round((argb >>> 24) * ga);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int ar = a >>> 24, r1 = (a >> 16) & 255, g1 = (a >> 8) & 255, b1 = a & 255;
        int br = b >>> 24, r2 = (b >> 16) & 255, g2 = (b >> 8) & 255, b2 = b & 255;
        return (Math.round(ar + (br - ar) * t) << 24)
                | (Math.round(r1 + (r2 - r1) * t) << 16)
                | (Math.round(g1 + (g2 - g1) * t) << 8)
                | Math.round(b1 + (b2 - b1) * t);
    }

    private void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1 + dy, x2, y2 + dy, al(color));
    }

    /** Rechteck, optional mit abgerundeten unteren Ecken. */
    private void fillRows(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, boolean roundBottom) {
        if (!roundBottom) {
            fill(g, x, y, x + w, y + h, color);
            return;
        }
        fill(g, x, y, x + w, y + h - 2, color);
        fill(g, x + 1, y + h - 2, x + w - 1, y + h - 1, color);
        fill(g, x + 2, y + h - 1, x + w - 2, y + h, color);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, s, x, y + dy, al(color), true);
    }

    // ---------- Rendern ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Ui.beginFrame();

        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        float ease = 1f - (1f - t) * (1f - t);
        if (!Theme.animations) {
            ease = 1f;
        }
        ga = Math.max(0.06f, ease);
        dy = Math.round((1f - ease) * 10f);

        // Logo (oder nur Schriftzug)
        int cx = this.width / 2;
        if (Theme.showLogo) {
            int logoW = Logo.W * logoScale;
            int logoY = columns.get(0).y - 12 - Logo.H * logoScale;
            Logo.draw(g, cx - logoW / 2, logoY, logoScale, ga, dy);
        } else {
            String title = "1 9 1 2";
            text(g, title, cx - this.font.width(title) / 2, columns.get(0).y - 18, 0xFFFFFFFF);
        }

        for (Column col : columns) {
            int colIndex = columns.indexOf(col);
            float openTarget = Theme.isCollapsed(colIndex) ? 0f : 1f;
            col.open += (openTarget - col.open) * Ui.step(0.3f);
            if (Math.abs(openTarget - col.open) < 0.01f) col.open = openTarget;
            int fullH = col.entries.size() * ENTRY_H;
            int bodyH = Math.round(fullH * col.open);
            boolean bodyShown = bodyH >= 3;

            // Schatten + Rand
            fillRows(g, col.x + 2, col.y + 3, colW, HEADER_H + bodyH, 0x55000000, true);
            fillRows(g, col.x - 1, col.y - 1, colW + 2, HEADER_H + bodyH + 2, Theme.border(), true);

            // Koerper
            if (bodyShown) {
                fillRows(g, col.x, col.y + HEADER_H, colW, bodyH, PANEL, true);
            }

            // Kopfzeile mit Farbverlauf und abgerundeten oberen Ecken
            for (int r = 0; r < HEADER_H; r++) {
                int inset = r == 0 ? 2 : r == 1 ? 1 : 0;
                if (!bodyShown) {
                    // ohne Koerper: auch die unteren Ecken der Kopfzeile abrunden
                    inset = Math.max(inset, r == HEADER_H - 1 ? 2 : r == HEADER_H - 2 ? 1 : 0);
                }
                int base = Theme.head(columns.indexOf(col));
                int color = lerp(lerp(base, 0xFFFFFFFF, 0.22f), lerp(base, 0xFF000000, 0.18f),
                        r / (float) (HEADER_H - 1));
                fill(g, col.x + inset, col.y + r, col.x + colW - inset, col.y + r + 1, color);
            }
            fill(g, col.x, col.y + HEADER_H - 1, col.x + colW, col.y + HEADER_H, 0x40FFFFFF);
            text(g, col.title, col.x + 8, col.y + 7, 0xFFFFFFFF);

            int active = 0, toggles = 0;
            for (Entry en : col.entries) {
                if (en.state != null) {
                    toggles++;
                    if (en.state.getAsBoolean()) active++;
                }
            }
            String mark = Theme.isCollapsed(colIndex) ? "+" : "-";
            text(g, mark, col.x + colW - 8 - this.font.width(mark), col.y + 7, 0xFFFFFFFF);
            if (toggles > 0) {
                String count = active + "/" + toggles;
                text(g, count, col.x + colW - 8 - this.font.width(mark) - 6 - this.font.width(count),
                        col.y + 7, 0xCCFFFFFF);
            }

            for (Entry en : col.entries) {
                // nur Zeilen zeichnen, die beim Ein-/Ausklappen schon komplett sichtbar sind
                if (en.y + ENTRY_H > col.y + HEADER_H + bodyH) continue;
                boolean on = en.state != null && en.state.getAsBoolean();
                boolean over = mouseX >= en.x && mouseX < en.x + colW
                        && mouseY >= en.y && mouseY < en.y + ENTRY_H;
                float speedH = Ui.step(0.3f);
                float speedA = Ui.step(0.35f);
                en.hover += ((over ? 1f : 0f) - en.hover) * speedH;
                en.anim += ((on ? 1f : 0f) - en.anim) * speedA;

                int ph = ENTRY_H - (en.last ? 2 : 0);
                if (on) {
                    fillRows(g, en.x, en.y, colW, ENTRY_H, Theme.onBg(), en.last);
                    // sanfter Schimmer von links
                    for (int i = 0; i < 6; i++) {
                        int a = 0x30 - i * 7;
                        fill(g, en.x + 2 + i * 5, en.y, en.x + 7 + i * 5, en.y + ph,
                                (a << 24) | (Theme.accent() & 0xFFFFFF));
                    }
                    fill(g, en.x, en.y + 1, en.x + 2, en.y + ph - 1, Theme.accent());
                } else if (en.hover > 0.02f) {
                    fillRows(g, en.x, en.y, colW, ENTRY_H, lerp(PANEL, Theme.hoverBg(), en.hover), en.last);
                }
                if (!en.last) {
                    fill(g, en.x + 6, en.y + ENTRY_H - 1, en.x + colW - 6, en.y + ENTRY_H, 0x14FFFFFF);
                }

                int textColor = on ? TEXT_ON : lerp(TEXT, 0xFFFFFFFF, en.hover);
                text(g, en.name, en.x + 8 + (on ? 2 : 0), en.y + 5, textColor);

                if (en.state != null) {
                    // Schalter rechts: Spur + Knopf
                    int sx = en.x + colW - 8 - 16;
                    int sy = en.y + (ENTRY_H - 8) / 2;
                    int track = lerp(TRACK_OFF, Theme.accent(), en.anim);
                    fill(g, sx + 1, sy, sx + 15, sy + 8, track);
                    fill(g, sx, sy + 1, sx + 16, sy + 7, track);
                    int kx = sx + 1 + Math.round(en.anim * 8f);
                    fill(g, kx, sy + 1, kx + 6, sy + 7, 0xFFFFFFFF);
                } else {
                    String chev = ">";
                    text(g, chev, en.x + colW - 8 - this.font.width(chev) + Math.round(en.hover * 2f),
                            en.y + 5, lerp(MUTED, Theme.accent(), en.hover));
                }
            }
        }

        // Fusszeile
        String keys = TestClient.toggleKey.getTranslatedKeyMessage().getString()
                + " / " + TestClient.guiKey.getTranslatedKeyMessage().getString();
        fill(g, 0, this.height - 16, this.width, this.height, 0x80000000);
        text(g, "1912 Client   |   Linksklick: umschalten / \u00f6ffnen   |   Tasten: " + keys, 6, this.height - 12, MUTED);
    }

    @Override
    public void onClose() {
        TestConfig.save();
        this.minecraft.gui.setScreen(parent);
    }
}
