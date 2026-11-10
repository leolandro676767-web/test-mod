package de.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Gemeinsames Einstellungs-Panel: Titelleiste, Beschreibung, einklappbare Abschnitte,
 * Zeilen mit Beschriftung links, Steuerelement in der Mitte und Reset-Knopf rechts.
 * Alle Zeilen werden einmal angelegt; Ein-/Ausklappen, Einblenden und Schliessen
 * laufen als Animation ueber Verschiebung und Transparenz pro Frame.
 */
abstract class Form extends Screen {
    private static final Set<String> COLLAPSED = new HashSet<>();
    private static final int PW = 320;
    private static final int PAD = 10;
    private static final int LABEL_W = 118;
    private static final int ROW_H = 24;
    private static final int SEC_H = 20;
    private static final int LINE = 0xFFD8D4E4;
    private static final long CLOSE_MS = 150;

    @FunctionalInterface
    protected interface Draw {
        void draw(GuiGraphicsExtractor g, int ox, int oy, int mx, int my);
    }

    /** Ein Abschnitt: Kopfzeile plus Inhalt (contentY..contentY+contentH). */
    private static final class Sec {
        final String key;
        int contentY;
        int contentH;

        Sec(String key) {
            this.key = key;
        }
    }

    private record Placed(AbstractWidget w, int rx, int ry, int row, int sec) {
    }

    private record Item(Draw d, int row, int sec, int y) {
    }

    protected final Screen parent;
    private final List<Placed> placed = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private final List<EditBox> boxes = new ArrayList<>();
    private final List<Sec> secs = new ArrayList<>();
    private final Map<EditBox, Float> boxAlpha = new IdentityHashMap<>();
    private int pw, px, ph, relY;
    private int curRow, curSec, rowY;
    private boolean syncing;
    private final long openedAt = System.currentTimeMillis();
    private final long rowAnim = openedAt;
    private boolean closing;
    private boolean closed;
    private long closeAt;

    protected Form(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Baut alle Zeilen mit den Methoden dieser Klasse auf. */
    protected abstract void build();

    // ---------- Aufbau ----------

    @Override
    protected void init() {
        placed.clear();
        items.clear();
        boxes.clear();
        secs.clear();
        boxAlpha.clear();
        curRow = 0;
        curSec = -1;
        rowY = 0;
        pw = Math.min(PW, this.width - 12);
        relY = Ui.HEADER_H + 8;
        build();
        endSection();
        relY += 6;
        ph = relY;
        px = (this.width - pw) / 2;
        int py0 = Math.max(4, (this.height - ph) / 2);
        for (Placed p : placed) {
            p.w().setX(px + p.rx());
            p.w().setY(py0 + p.ry());
        }
    }

    private <T extends AbstractWidget> T place(T w, int rx, int ry) {
        addWidget(w);
        placed.add(new Placed(w, rx, ry, curRow, curSec));
        return w;
    }

    private EditBox placeBox(EditBox b, int rx, int ry) {
        addRenderableWidget(b);
        placed.add(new Placed(b, rx, ry, curRow, curSec));
        boxes.add(b);
        return b;
    }

    private void addDraw(Draw d) {
        items.add(new Item(d, curRow, curSec, rowY));
    }

    private int beginRow() {
        curRow++;
        int y = relY;
        rowY = y;
        relY += ROW_H;
        return y;
    }

    private void endSection() {
        if (curSec >= 0) {
            Sec s = secs.get(curSec);
            s.contentH = relY - s.contentY;
            curSec = -1;
        }
    }

    private void label(GuiGraphicsExtractor g, int oy, int y, String text) {
        Ui.text(g, this.font, text, px + PAD, oy + y + 6, Ui.TEXT, true);
    }

    private static String clean(String s, boolean allowNeg) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (c == '-' && allowNeg && sb.length() == 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---------- Bausteine ----------

    protected void description(String text) {
        endSection();
        curRow++;
        for (String line : text.split("\n")) {
            int y = relY;
            rowY = y;
            addDraw((g, ox, oy, mx, my) -> Ui.text(g, this.font, line, ox + PAD, oy + y, Ui.TEXT, true));
            relY += 12;
        }
        relY += 6;
    }

    protected void section(String name) {
        endSection();
        curRow++;
        String key = getClass().getSimpleName() + ":" + name;
        int y = relY;
        rowY = y;
        place(Button.builder(Component.empty(), b -> {
            if (!COLLAPSED.remove(key)) COLLAPSED.add(key);
        }).bounds(0, 0, pw - 2 * PAD, SEC_H).build(), PAD, y);
        addDraw((g, ox, oy, mx, my) -> drawSection(g, ox, oy, y, name, key));
        relY += SEC_H + 2;
        Sec sec = new Sec(key);
        sec.contentY = relY;
        secs.add(sec);
        curSec = secs.size() - 1;
    }

    private void drawSection(GuiGraphicsExtractor g, int ox, int oy, int y, String name, String key) {
        int lw = this.font.width(name);
        int x1 = ox + PAD;
        int x2 = ox + pw - PAD - 18;
        int cx = (x1 + x2) / 2;
        int ly = oy + y + 9;
        Ui.fill(g, x1, ly, cx - lw / 2 - 4, ly + 1, LINE);
        Ui.fill(g, cx + lw / 2 + 4, ly, x2, ly + 1, LINE);
        Ui.text(g, this.font, name, cx - lw / 2, oy + y + 5, 0xFFFFFFFF, true);
        float t = Ui.ease(key, 0, COLLAPSED.contains(key) ? 1f : 0f, 0.25f);
        drawArrow(g, ox + pw - PAD - 6, oy + y + 9, t);
    }

    /** Dreieck, das beim Einklappen weich von "nach unten" auf "nach rechts" dreht. */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, float t) {
        double a = -Math.PI / 2 * t;
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        double[][] v = {{-5, -3}, {5, -3}, {0, 3}};
        double[][] r = new double[3][2];
        for (int i = 0; i < 3; i++) {
            r[i][0] = v[i][0] * cos - v[i][1] * sin;
            r[i][1] = v[i][0] * sin + v[i][1] * cos;
        }
        for (int yy = -6; yy <= 6; yy++) {
            for (int xx = -6; xx <= 6; xx++) {
                if (inTriangle(xx + 0.5, yy + 0.5, r)) {
                    Ui.fill(g, cx + xx, cy + yy, cx + xx + 1, cy + yy + 1, LINE);
                }
            }
        }
    }

    private static boolean inTriangle(double x, double y, double[][] t) {
        double d1 = side(x, y, t[0], t[1]);
        double d2 = side(x, y, t[1], t[2]);
        double d3 = side(x, y, t[2], t[0]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static double side(double px, double py, double[] a, double[] b) {
        return (px - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (py - b[1]);
    }

    protected void note(Supplier<String> text) {
        curRow++;
        int y = relY;
        rowY = y;
        addDraw((g, ox, oy, mx, my) -> Ui.text(g, this.font, text.get(), ox + PAD, oy + y + 2, Ui.MUTED, true));
        relY += 14;
    }

    protected void note(String text) {
        note(() -> text);
    }

    /** Eigene Zeile mit fester Hoehe, die selbst zeichnet (y = Zeilenposition im Panel). */
    protected void customRow(int height, IntFunction<Draw> drawAt) {
        curRow++;
        int y = relY;
        rowY = y;
        addDraw(drawAt.apply(y));
        relY += height;
    }

    private void resetButton(int y, Runnable reset) {
        Button b = place(Button.builder(Component.empty(), x -> {
            reset.run();
            TestConfig.save();
            this.rebuildWidgets();
        }).bounds(0, 0, 20, 20).build(), pw - PAD - 20, y);
        addDraw((g, ox, oy, mx, my) -> Ui.resetIcon(g, b, b.getX(), b.getY(), mx, my));
    }

    protected void toggle(String name, BooleanSupplier get, Consumer<Boolean> set, boolean def) {
        int y = beginRow();
        Button b = place(Button.builder(Component.empty(), x -> {
            set.accept(!get.getAsBoolean());
            TestConfig.save();
        }).bounds(0, 0, 20, 20).build(), PAD + LABEL_W, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, oy, y, name);
            Ui.checkbox(g, b, b.getX(), b.getY(), get.getAsBoolean(), mx, my);
        });
        resetButton(y, () -> set.accept(def));
    }

    protected void choice(String name, String[] names, IntSupplier get, IntConsumer set, int def) {
        int y = beginRow();
        Button b = place(Button.builder(Component.empty(), x -> {
            set.accept((get.getAsInt() + 1) % names.length);
            TestConfig.save();
            this.rebuildWidgets(); // z.B. Hex-Felder nach Farbschema-Wechsel neu fuellen
        }).bounds(0, 0, 110, 20).build(), PAD + LABEL_W, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, oy, y, name);
            int bx = b.getX();
            int by = b.getY();
            Ui.box20(g, b, bx, by, 110, mx, my, true);
            Ui.text(g, this.font, names[Math.floorMod(get.getAsInt(), names.length)], bx + 8, by + 6, Ui.TEXT, true);
            for (int i = 0; i < 5; i++) {
                Ui.fill(g, bx + 92 + i, by + 8 + i, bx + 104 - i, by + 9 + i, Ui.TEXT);
            }
        });
        resetButton(y, () -> set.accept(def));
    }

    protected void slider(String name, int min, int max, IntSupplier get, IntConsumer set, int def) {
        int y = beginRow();
        int cx = PAD + LABEL_W;
        int bw = 40;
        int sx = cx + bw + 6;
        int sw = Math.max(40, pw - PAD - 26 - sx);
        int start = Math.max(min, Math.min(max, get.getAsInt()));

        EditBox box = new EditBox(this.font, 0, 0, bw, 20, Component.literal(name));
        box.setMaxLength(6);
        box.setValue(Integer.toString(start));
        Slide slide = new Slide(sw, min, max, start, v -> {
            set.accept(v);
            syncing = true;
            box.setValue(Integer.toString(v));
            syncing = false;
        });
        box.setResponder(s -> {
            if (syncing) return;
            String f = clean(s, min < 0);
            if (!f.equals(s)) {
                syncing = true;
                box.setValue(f);
                syncing = false;
            }
            try {
                int v = Math.max(min, Math.min(max, Integer.parseInt(f)));
                set.accept(v);
                slide.setFraction((v - min) / (double) (max - min));
            } catch (NumberFormatException e) {
                // leer oder nur "-": nichts uebernehmen
            }
        });
        placeBox(box, cx, y);
        place(slide, sx, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, oy, y, name);
            int x1 = slide.getX() + 4;
            int x2 = slide.getX() + slide.getWidth() - 4;
            int ty = slide.getY() + 9;
            int hx = x1 + Math.round((float) slide.shownFraction() * (x2 - x1));
            Ui.fill(g, x1, ty, x2, ty + 2, 0xFF3A3450);
            Ui.fill(g, x1, ty, hx, ty + 2, Theme.accent());
            boolean over = mx >= slide.getX() && mx < slide.getX() + slide.getWidth()
                    && my >= slide.getY() && my < slide.getY() + 20;
            int grow = Math.round(Ui.ease(slide, 0, over ? 1f : 0f, 0.3f) * 2f);
            Ui.rounded(g, hx - 5 - grow, ty - 4 - grow, 10 + 2 * grow, 10 + 2 * grow, Theme.accent());
        });
        resetButton(y, () -> set.accept(def));
    }

    protected void number(String name, IntSupplier get, IntConsumer set, int def) {
        int y = beginRow();
        EditBox box = new EditBox(this.font, 0, 0, 90, 20, Component.literal(name));
        box.setMaxLength(8);
        box.setValue(Integer.toString(get.getAsInt()));
        box.setResponder(s -> {
            if (syncing) return;
            String f = clean(s, true);
            if (!f.equals(s)) {
                syncing = true;
                box.setValue(f);
                syncing = false;
            }
            try {
                set.accept(Integer.parseInt(f));
            } catch (NumberFormatException e) {
                // leer oder nur "-"
            }
        });
        placeBox(box, PAD + LABEL_W, y);
        addDraw((g, ox, oy, mx, my) -> label(g, oy, y, name));
        resetButton(y, () -> set.accept(def));
    }

    /** Hex-Farbe RRGGBB; leer bedeutet -1 (Standard). */
    protected void hex(String name, IntSupplier get, IntConsumer set) {
        int y = beginRow();
        EditBox box = new EditBox(this.font, 0, 0, 70, 20, Component.literal(name));
        box.setMaxLength(6);
        int cur = get.getAsInt();
        box.setValue(cur >= 0 ? String.format("%06X", cur) : "");
        box.setResponder(s -> {
            if (syncing) return;
            String f = s.replaceAll("[^0-9a-fA-F]", "");
            if (f.length() > 6) f = f.substring(0, 6);
            if (!f.equals(s)) {
                syncing = true;
                box.setValue(f);
                syncing = false;
            }
            set.accept(Theme.parseHex(f));
        });
        placeBox(box, PAD + LABEL_W, y);
        addDraw((g, ox, oy, mx, my) -> label(g, oy, y, name));
        resetButton(y, () -> set.accept(-1));
    }

    /** Drei Zahlenfelder X/Y/Z mit "Hier"-Knopf (aktuelle Spielerposition). */
    protected void coords(String name, Supplier<int[]> get, Consumer<int[]> set) {
        int y = beginRow();
        int cx = PAD + LABEL_W;
        int[] cur = get.get();
        String[] hints = {"X", "Y", "Z"};
        EditBox[] fs = new EditBox[3];
        for (int i = 0; i < 3; i++) {
            EditBox b = new EditBox(this.font, 0, 0, 40, 20, Component.literal(hints[i]));
            b.setMaxLength(8);
            b.setHint(Component.literal(hints[i]));
            b.setValue(Integer.toString(cur[i]));
            fs[i] = b;
        }
        for (int i = 0; i < 3; i++) {
            EditBox b = fs[i];
            b.setResponder(s -> {
                if (syncing) return;
                String f = clean(s, true);
                if (!f.equals(s)) {
                    syncing = true;
                    b.setValue(f);
                    syncing = false;
                }
                try {
                    int[] v = new int[3];
                    for (int k = 0; k < 3; k++) v[k] = Integer.parseInt(fs[k].getValue());
                    set.accept(v);
                } catch (NumberFormatException e) {
                    // unvollstaendig: nichts uebernehmen
                }
            });
            placeBox(b, cx + i * 44, y);
        }
        int hx = cx + 3 * 44;
        Button here = place(Button.builder(Component.literal("Hier"), x -> {
            if (this.minecraft == null || this.minecraft.player == null) return;
            var p = this.minecraft.player.blockPosition();
            fs[0].setValue(Integer.toString(p.getX()));
            fs[1].setValue(Integer.toString(p.getY()));
            fs[2].setValue(Integer.toString(p.getZ()));
            TestConfig.save();
        }).bounds(0, 0, Math.max(30, pw - PAD - hx), 20).build(), hx, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, oy, y, name);
            Ui.button(g, this.font, here, mx, my, Ui.STYLE_NORMAL);
        });
    }

    /** Breiter Knopf ueber die ganze Panelbreite (beendet den aktuellen Abschnitt). */
    protected Button wideButton(String text, boolean primary, Runnable action) {
        endSection();
        int y = beginRow();
        Button b = place(Button.builder(Component.literal(text), x -> action.run())
                .bounds(0, 0, pw - 2 * PAD, 20).build(), PAD, y);
        addDraw((g, ox, oy, mx, my) ->
                Ui.button(g, this.font, b, mx, my, primary ? Ui.STYLE_PRIMARY : Ui.STYLE_NORMAL));
        return b;
    }

    /** Fusszeile: optional "Aktiv"-Haken links, immer "Zurueck" rechts. */
    protected void footer(BooleanSupplier active, Consumer<Boolean> setActive) {
        endSection();
        curRow++;
        relY += 4;
        int sepY = relY;
        rowY = sepY;
        addDraw((g, ox, oy, mx, my) -> Ui.fill(g, ox, oy + sepY, ox + pw, oy + sepY + 1, Ui.TEXT));
        relY += 6;
        curRow++;
        int y = relY;
        rowY = y;
        relY += ROW_H;
        if (active != null) {
            Button b = place(Button.builder(Component.empty(), x -> {
                setActive.accept(!active.getAsBoolean());
                TestConfig.save();
            }).bounds(0, 0, 20, 20).build(), PAD + 46, y);
            addDraw((g, ox, oy, mx, my) -> {
                Ui.text(g, this.font, "Aktiv:", ox + PAD, oy + y + 6, Ui.TEXT, true);
                Ui.checkbox(g, b, b.getX(), b.getY(), active.getAsBoolean(), mx, my);
            });
        }
        Button back = place(Button.builder(CommonComponents.GUI_BACK, x -> onClose())
                .bounds(0, 0, 90, 20).build(), pw - PAD - 90, y);
        addDraw((g, ox, oy, mx, my) -> Ui.button(g, this.font, back, mx, my, Ui.STYLE_NORMAL));
    }

    // ---------- Animation / Zeichnen ----------

    private float rowAlpha(long now, int row) {
        if (!Theme.animations) return 1f;
        float r = (now - rowAnim - Math.min(row * 22L, 260L)) / 150f;
        r = Math.max(0f, Math.min(1f, r));
        return 1f - (1f - r) * (1f - r);
    }

    /** Wie weit ein Element nach oben rutscht, weil Abschnitte davor (oder der eigene) einklappen. */
    private float shift(int y, int sec, float[] open) {
        float sh = 0f;
        for (int j = 0; j < secs.size(); j++) {
            Sec s = secs.get(j);
            if (j != sec && s.contentY + s.contentH <= y) sh += (1f - open[j]) * s.contentH;
        }
        if (sec >= 0) sh += (1f - open[sec]) * (y - secs.get(sec).contentY);
        return -sh;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Ui.beginFrame();
        long now = System.currentTimeMillis();

        // Ein- bzw. Ausblenden (Schliessen laeuft rueckwaerts)
        float t;
        float closeProgress = 0f;
        if (closing) {
            closeProgress = Theme.animations ? Math.min(1f, (now - closeAt) / (float) CLOSE_MS) : 1f;
            t = 1f - closeProgress;
        } else {
            t = Theme.animations ? Math.min(1f, (now - openedAt) / 180f) : 1f;
        }
        float eased = 1f - (1f - t) * (1f - t);
        int dy = Math.round((1f - eased) * 10f);
        float ga = Math.max(0.06f, eased);

        // Aufklapp-Anteil je Abschnitt (1 = offen) und daraus die aktuelle Panelhoehe
        float[] open = new float[secs.size()];
        float curPhF = ph;
        for (int j = 0; j < secs.size(); j++) {
            Sec s = secs.get(j);
            open[j] = Ui.ease(s.key, 1, COLLAPSED.contains(s.key) ? 0f : 1f, 0.25f);
            curPhF -= (1f - open[j]) * s.contentH;
        }
        int curPh = Math.round(curPhF);
        int curPy = Math.max(4, (this.height - curPh) / 2);

        // Widgets verschieben, ein-/ausblenden; versteckte Zeilen sind nicht bedienbar
        boxAlpha.clear();
        for (Placed p : placed) {
            float v = p.sec() >= 0 ? open[p.sec()] : 1f;
            int off = Math.round(shift(p.ry(), p.sec(), open));
            p.w().setY(curPy + dy + p.ry() + off);
            boolean show = v > 0.03f;
            p.w().visible = show;
            p.w().active = show;
            if (p.w() instanceof EditBox eb) {
                float a = ga * rowAlpha(now, p.row()) * v;
                eb.setAlpha(a);
                boxAlpha.put(eb, a);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);

        List<int[]> holes = new ArrayList<>();
        for (EditBox b : boxes) {
            if (b.visible) holes.add(Ui.hole(b));
        }
        Ui.alpha = ga;
        Ui.panel(g, this.font, px, curPy + dy, pw, curPh, this.title.getString(), holes);
        for (EditBox b : boxes) {
            if (!b.visible) continue;
            Ui.alpha = boxAlpha.getOrDefault(b, ga);
            Ui.frame(g, b);
        }
        for (Item it : items) {
            float v = it.sec() >= 0 ? open[it.sec()] : 1f;
            if (v < 0.03f) continue;
            int off = Math.round(shift(it.y(), it.sec(), open));
            Ui.alpha = ga * rowAlpha(now, it.row()) * v;
            it.d().draw(g, px, curPy + dy + off, mouseX, mouseY);
        }
        Ui.alpha = 1f;

        if (closing && closeProgress >= 1f) finishClose();
    }

    private void finishClose() {
        if (closed) return;
        closed = true;
        this.minecraft.gui.setScreen(parent);
    }

    public void tick() {
        if (closing && System.currentTimeMillis() - closeAt >= CLOSE_MS) finishClose();
    }

    public void removed() {
        TestConfig.save();
    }

    @Override
    public void onClose() {
        TestConfig.save();
        if (!Theme.animations) {
            finishClose();
            return;
        }
        if (closing) return;
        closing = true;
        closeAt = System.currentTimeMillis();
    }

    /** Regler (Ziehen mit der Maus); gezeichnet wird er von Form selbst. */
    private static final class Slide extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final IntConsumer onChange;
        private double shown = -1;

        Slide(int width, int min, int max, int current, IntConsumer onChange) {
            super(0, 0, width, 20, Component.empty(), (current - min) / (double) Math.max(1, max - min));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
        }

        @Override
        protected void updateMessage() {
        }

        @Override
        protected void applyValue() {
            onChange.accept(min + (int) Math.round(this.value * (max - min)));
        }

        void setFraction(double f) {
            this.value = Math.max(0.0, Math.min(1.0, f));
        }

        /** Angezeigte Position: folgt dem echten Wert weich. */
        double shownFraction() {
            double f = this.value;
            if (shown < 0 || !Theme.animations) {
                shown = f;
            } else {
                shown += (f - shown) * Ui.step(0.5f);
                if (Math.abs(f - shown) < 0.002) shown = f;
            }
            return shown;
        }
    }
}
