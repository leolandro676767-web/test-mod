package de.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * Gemeinsames Einstellungs-Panel: Kopfzeile (mit Suche), Beschreibung, einklappbare Abschnitte,
 * Zeilen mit Beschriftung links, Bedienelement daneben und Zuruecksetzen-Symbol rechts, unten
 * eine feste Fusszeile.
 * <p>Die Breite richtet sich nach dem Inhalt: zuerst wird gemessen, wie viel Platz die laengste
 * Beschriftung und das breiteste Bedienelement brauchen, erst dann wird aufgebaut. So ist jeder
 * Text ganz zu lesen - nichts wird abgeschnitten. Ist der Inhalt hoeher als der Bildschirm,
 * laesst er sich mit dem Mausrad scrollen; Kopf- und Fusszeile bleiben stehen.
 */
abstract class Form extends Screen {
    private static final Set<String> COLLAPSED = new HashSet<>();
    /** Abschnitte, die schon einmal angezeigt wurden (fuer "anfangs zugeklappt"). */
    private static final Set<String> SEEN = new HashSet<>();
    private static final int PAD = 10;
    private static final int ROW_H = 24;
    private static final int SEC_H = 20;
    private static final int TOP_PAD = 8;
    private static final int MIN_PW = 300;
    private static final int MIN_LABEL = 84;
    private static final int MIN_CTRL = 150;
    private static final int MAX_LINE = 340;   // laengere Hinweise werden umbrochen statt das Fenster zu dehnen
    private static final int RESET_W = 16;
    private static final long CLOSE_MS = 150;
    private static final int SEARCH_W = 104;
    private static final int SEARCH_H = 16;
    /** Ab so vielen Einstellungen bekommt das Fenster ein Suchfeld. */
    private static final int SEARCH_MIN_ROWS = 6;
    private static final int FADE = 0xFF140F1F;

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

    /** foot = gehoert zur festen Fusszeile (scrollt nicht mit). */
    private record Placed(AbstractWidget w, int rx, int ry, int row, int sec, boolean foot) {
    }

    private record Item(Draw d, int row, int sec, int y, boolean foot) {
    }

    protected final Screen parent;
    private final List<Placed> placed = new ArrayList<>();
    private final List<Placed> boxes = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private final List<Sec> secs = new ArrayList<>();

    // Layout (aus dem Mess-Durchgang)
    private int pw, px;
    private int labelW, ctrlW, ctrlX, resetX, choiceW;
    private int bodyH;  // Hoehe des scrollbaren Inhalts, wenn alle Abschnitte offen sind
    private int footH;  // Hoehe der festen Fusszeile (0 = keine)

    // Aufbau
    private int curRow, curSec, relY, rowY;
    private boolean foot;
    private boolean syncing;

    // Messen
    private boolean measuring;
    private int needLabel, needCtrl, needLine, needChoice, rowsSeen;

    // Ein-/Ausblenden
    private final long openedAt = System.currentTimeMillis();
    private boolean closing;
    private boolean closed;
    private long closeAt;

    // Suche in den Einstellungen
    private EditBox searchBox;
    private String filter = "";      // klein geschrieben, ohne Leerzeichen am Rand
    private boolean pendingRebuild;
    private boolean searchable;
    private boolean skip;            // aktuelle Zeile passt nicht zur Suche -> nicht anlegen
    private String pendingSec;       // Abschnitts-Kopf, der erst beim ersten Treffer erscheint
    private boolean secMatches;      // Abschnittsname selbst passt -> ganzer Abschnitt sichtbar
    private int hits;

    // Scrollen
    private float scroll;
    private float scrollTarget;
    private int maxScroll;

    protected Form(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Baut alle Zeilen mit den Methoden dieser Klasse auf. */
    protected abstract void build();

    // ---------- Aufbau ----------

    @Override
    protected void init() {
        // Eigene Menue-Groesse (gilt nur, solange ein 1912-Menue offen ist)
        GuiScale.apply();
        this.width = this.minecraft.getWindow().getGuiScaledWidth();
        this.height = this.minecraft.getWindow().getGuiScaledHeight();

        // 1. Durchgang: nur messen, wie viel Platz Beschriftungen und Bedienelemente brauchen
        measuring = true;
        needLabel = 0;
        needCtrl = MIN_CTRL;
        needLine = 0;
        needChoice = 110;
        rowsSeen = 0;
        build();
        measuring = false;
        searchable = searchable || rowsSeen >= SEARCH_MIN_ROWS;

        labelW = Math.max(MIN_LABEL, needLabel + 12);
        choiceW = needChoice;
        int ctrlNeed = Math.max(needCtrl, choiceW);
        int want = PAD + labelW + ctrlNeed + 4 + RESET_W + PAD;
        want = Math.max(want, needLine + 2 * PAD);
        want = Math.max(want, 10 + this.font.width(this.title.getString()) + 12 + (searchable ? SEARCH_W + 8 : 0));
        want = Math.max(want, MIN_PW);
        pw = Math.min(want, this.width - 12);
        // Sehr schmales Fenster: die Bedienelemente behalten ihre Breite, die Beschriftung gibt nach
        int rowNeed = PAD + labelW + ctrlNeed + 4 + RESET_W + PAD;
        if (rowNeed > pw) labelW = Math.max(40, labelW - (rowNeed - pw));
        ctrlX = PAD + labelW;
        resetX = pw - PAD - RESET_W;
        ctrlW = Math.max(60, resetX - 4 - ctrlX);
        choiceW = Math.min(choiceW, ctrlW);
        px = (this.width - pw) / 2;

        // 2. Durchgang: wirklich aufbauen
        placed.clear();
        boxes.clear();
        items.clear();
        secs.clear();
        curRow = 0;
        curSec = -1;
        rowY = 0;
        relY = TOP_PAD;
        foot = false;
        footH = 0;
        skip = false;
        pendingSec = null;
        secMatches = false;
        hits = 0;
        build();
        endSection();
        skip = false;
        if (!foot) bodyH = relY + 6;
        foot = false;

        // Suchfeld oben rechts in der Kopfzeile (nur bei Fenstern mit vielen Einstellungen)
        if (searchable) {
            if (searchBox == null) {
                searchBox = new EditBox(this.font, 0, 0, SEARCH_W, SEARCH_H, Component.literal("Suchen"));
                searchBox.setHint(Component.literal("Suchen"));
                searchBox.setMaxLength(30);
                searchBox.setResponder(s -> {
                    String f = s.trim().toLowerCase(Locale.ROOT);
                    if (f.equals(filter)) return;
                    filter = f;
                    scrollTarget = 0f;
                    pendingRebuild = true;
                });
            }
            searchBox.setX(px + pw - 8 - SEARCH_W);
            searchBox.setY(Math.max(8, (this.height - (Ui.HEADER_H + bodyH + footH)) / 2) + 3);
            addWidget(searchBox);
        }
    }

    private boolean filtering() {
        return !filter.isEmpty();
    }

    private boolean hit(String s) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(filter);
    }

    private boolean isCollapsed(String key) {
        return !filtering() && COLLAPSED.contains(key);
    }

    /** Wartenden Abschnitts-Kopf jetzt anlegen (erster Treffer darin). */
    private void flushSection() {
        if (pendingSec == null) return;
        String n = pendingSec;
        pendingSec = null;
        placeSection(n);
    }

    private <T extends AbstractWidget> T place(T w, int rx, int ry) {
        if (skip || measuring) return w;
        addWidget(w);
        placed.add(new Placed(w, rx, ry, curRow, curSec, foot));
        return w;
    }

    /** Eingabefelder werden nicht von Minecraft, sondern von uns (nach dem Panel) gezeichnet. */
    private EditBox placeBox(EditBox b, int rx, int ry) {
        if (skip || measuring) return b;
        addWidget(b);
        Placed p = new Placed(b, rx, ry, curRow, curSec, foot);
        placed.add(p);
        boxes.add(p);
        return b;
    }

    private void addDraw(Draw d) {
        if (skip || measuring) return;
        items.add(new Item(d, curRow, curSec, rowY, foot));
    }

    /** Mess-Durchgang: merkt sich den Platzbedarf einer Zeile. true = es wird gerade nur gemessen. */
    private boolean measureRow(String label, int ctrl) {
        if (!measuring) return false;
        rowsSeen++;
        needLabel = Math.max(needLabel, this.font.width(label));
        needCtrl = Math.max(needCtrl, ctrl);
        return true;
    }

    private void measureLine(int width) {
        needLine = Math.max(needLine, Math.min(MAX_LINE, width));
    }

    private int beginRow(String name) {
        curRow++;
        skip = filtering() && !secMatches && !hit(name);
        if (skip) return relY;
        flushSection();
        hits++;
        int y = relY;
        rowY = y;
        relY += ROW_H;
        // dezente Hervorhebung der Zeile unter der Maus
        Object key = new Object();
        addDraw((g, ox, oy, mx, my) -> {
            boolean over = mx >= ox + 4 && mx < ox + pw - 4 && my >= oy + y - 2 && my < oy + y + ROW_H - 2;
            float hv = Ui.ease(key, 0, over ? 1f : 0f, 0.3f);
            if (hv > 0.02f) Ui.roundRect(g, ox + 4, oy + y - 2, pw - 8, ROW_H, 3, Ui.withA(0xFFFFFF, Math.round(0x0D * hv)));
        });
        return y;
    }

    private void endSection() {
        pendingSec = null;
        secMatches = false;
        if (curSec >= 0) {
            Sec s = secs.get(curSec);
            s.contentH = relY - s.contentY;
            curSec = -1;
        }
    }

    /** Beschriftung links. Der Platz ist auf die laengste Beschriftung des Fensters ausgelegt. */
    private void label(GuiGraphicsExtractor g, int ox, int oy, int y, String text) {
        Ui.fitText(g, this.font, text, ox + PAD, oy + y + 6, labelW - 8, Ui.TEXT, true);
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
        if (measuring) {
            for (String line : text.split("\n")) measureLine(this.font.width(line));
            return;
        }
        endSection();
        curRow++;
        skip = filtering();
        if (skip) return;
        for (String line : Ui.wrap(this.font, text, pw - 2 * PAD)) {
            int y = relY;
            rowY = y;
            addDraw((g, ox, oy, mx, my) -> Ui.text(g, this.font, line, ox + PAD, oy + y, 0xFFC4BCD8, true));
            relY += 11;
        }
        relY += 7;
    }

    protected void section(String name) {
        if (measuring) {
            measureLine(this.font.width(name) + 40);
            return;
        }
        endSection();
        curRow++;
        skip = false;
        if (filtering()) {
            secMatches = hit(name);
            if (!secMatches) {
                pendingSec = name; // erscheint erst, wenn eine Zeile darin passt
                return;
            }
        }
        placeSection(name);
    }

    private void placeSection(String name) {
        String key = getClass().getSimpleName() + ":" + name;
        relY += 2;
        int y = relY;
        rowY = y;
        Button b = place(Button.builder(Component.empty(), x -> {
            if (filtering()) return;
            if (!COLLAPSED.remove(key)) COLLAPSED.add(key);
        }).bounds(0, 0, pw - 2 * PAD, SEC_H).build(), PAD, y);
        addDraw((g, ox, oy, mx, my) -> {
            boolean over = b.visible && mx >= b.getX() && mx < b.getX() + b.getWidth()
                    && my >= b.getY() && my < b.getY() + SEC_H;
            float hv = Ui.ease(b, 0, over ? 1f : 0f, 0.3f);
            float t = Ui.ease(key, 0, isCollapsed(key) ? 1f : 0f, 0.25f);
            int x1 = ox + PAD;
            int x2 = ox + pw - PAD;
            int lw = this.font.width(name);
            Ui.text(g, this.font, name, x1, oy + y + 6, Ui.lerp(Theme.accent(), 0xFFFFFFFF, hv * 0.5f), true);
            Ui.fill(g, x1 + lw + 8, oy + y + 10, x2 - 16, oy + y + 11, Ui.HAIR);
            // Plus (zugeklappt) / Minus (offen), weich ueberblendet
            Ui.plusMinusIcon(g, x2 - 6, oy + y + 10, t, Ui.lerp(Ui.MUTED, 0xFFFFFFFF, hv));
        });
        relY += SEC_H + 2;
        Sec sec = new Sec(key);
        sec.contentY = relY;
        secs.add(sec);
        curSec = secs.size() - 1;
    }

    /** Abschnitt, der beim allerersten Oeffnen zugeklappt ist (z.B. fuer selten gebrauchte Einstellungen). */
    protected void sectionClosed(String name) {
        String key = getClass().getSimpleName() + ":" + name;
        if (!measuring && SEEN.add(key)) COLLAPSED.add(key);
        section(name);
    }

    /** Nutzbare Breite innerhalb des Fensters (fuer eigene Zeilen). */
    protected int contentWidth() {
        return pw - 2 * PAD;
    }

    /** Linker Rand des Inhalts (fuer eigene Zeilen). */
    protected int contentX() {
        return PAD;
    }

    /**
     * Auswahl als Reihe kleiner Farbfelder statt Durchklicken: ein Klick waehlt direkt.
     * colors liefert je Moeglichkeit {links, rechts} fuer einen kleinen Farbverlauf.
     */
    protected void swatches(String name, String[] names, IntFunction<int[]> colors, IntSupplier get, IntConsumer set,
            int def) {
        int n = names.length;
        int sw = 18;
        int gap = 4;
        int widest = 0;
        for (String nm : names) widest = Math.max(widest, this.font.width(nm));
        if (measureRow(name, n * (sw + gap) - gap + 10 + widest)) return;
        int y = beginRow(name);
        List<Button> bs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            bs.add(place(Button.builder(Component.empty(), x -> {
                set.accept(idx);
                TestConfig.save();
                this.rebuildWidgets();
            }).bounds(0, 0, sw, 20).build(), ctrlX + i * (sw + gap), y));
        }
        addDraw((g, ox, oy, mx, my) -> {
            label(g, ox, oy, y, name);
            int cur = Math.floorMod(get.getAsInt(), n);
            int hovered = -1;
            for (int i = 0; i < bs.size(); i++) {
                Button b = bs.get(i);
                int bx = b.getX();
                int by = b.getY();
                boolean over = b.visible && mx >= bx && mx < bx + sw && my >= by && my < by + 20;
                if (over) hovered = i;
                float hv = Ui.ease(b, 0, over ? 1f : 0f, 0.3f);
                float sel = Ui.ease(b, 1, i == cur ? 1f : 0f, 0.3f);
                int[] c = colors.apply(i);
                if (sel > 0.02f) {
                    Ui.roundRect(g, bx - 1, by + 1, sw + 2, 18, 4, Ui.withA(0xFFFFFF, Math.round(255 * sel)));
                }
                int grow = Math.round(hv);
                Ui.roundGradientH(g, bx + 1 - grow, by + 3 - grow, sw - 2 + 2 * grow, 14 + 2 * grow, 3,
                        c[0], c[1], true, true);
            }
            int shownIdx = hovered >= 0 ? hovered : cur;
            int tx = ctrlX + n * (sw + gap) - gap + 10;
            Ui.text(g, this.font, names[shownIdx], ox + tx, oy + y + 6, hovered >= 0 ? Ui.TEXT : Ui.MUTED, true);
        });
        resetButton(y, () -> set.accept(def), () -> get.getAsInt() != def);
    }

    /** Hinweiszeile; zu lange Texte werden umbrochen. Aendert sich der Text, passt sich die Hoehe an. */
    protected void note(Supplier<String> text) {
        if (measuring) {
            measureLine(this.font.width(text.get()));
            return;
        }
        curRow++;
        skip = filtering() && !secMatches;
        if (skip) return;
        flushSection();
        int y = relY;
        rowY = y;
        int max = pw - 2 * PAD;
        int reserved = Ui.wrap(this.font, text.get(), max).size();
        addDraw((g, ox, oy, mx, my) -> {
            List<String> lines = Ui.wrap(this.font, text.get(), max);
            if (lines.size() != reserved) pendingRebuild = true;
            for (int i = 0; i < lines.size(); i++) {
                Ui.fitText(g, this.font, lines.get(i), ox + PAD, oy + y + 2 + i * 10, max, Ui.MUTED, true);
            }
        });
        relY += 4 + reserved * 10;
    }

    protected void note(String text) {
        note(() -> text);
    }

    /** Eigene Zeile mit fester Hoehe, die selbst zeichnet (y = Zeilenposition im Panel). */
    protected void customRow(int height, IntFunction<Draw> drawAt) {
        if (measuring) return;
        curRow++;
        skip = filtering() && !secMatches;
        if (skip) return;
        flushSection();
        int y = relY;
        rowY = y;
        addDraw(drawAt.apply(y));
        relY += height;
    }

    /** Zuruecksetzen-Symbol rechts: erscheint nur, wenn der Wert vom Standard abweicht. */
    private void resetButton(int y, Runnable reset, BooleanSupplier changed) {
        Button b = place(Button.builder(Component.empty(), x -> {
            if (!changed.getAsBoolean()) return;
            reset.run();
            TestConfig.save();
            this.rebuildWidgets();
        }).bounds(0, 0, RESET_W, 20).build(), resetX, y);
        addDraw((g, ox, oy, mx, my) -> {
            float show = Ui.ease(b, 1, changed.getAsBoolean() ? 1f : 0f, 0.3f);
            Ui.resetIcon(g, b, b.getX(), b.getY(), mx, my, show);
        });
    }

    protected void toggle(String name, BooleanSupplier get, Consumer<Boolean> set, boolean def) {
        if (measureRow(name, 20)) return;
        int y = beginRow(name);
        Button b = place(Button.builder(Component.empty(), x -> {
            set.accept(!get.getAsBoolean());
            TestConfig.save();
        }).bounds(0, 0, 20, 20).build(), ctrlX, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, ox, oy, y, name);
            Ui.checkbox(g, b, b.getX(), b.getY(), get.getAsBoolean(), mx, my);
        });
        resetButton(y, () -> set.accept(def), () -> get.getAsBoolean() != def);
    }

    protected void choice(String name, String[] names, IntSupplier get, IntConsumer set, int def) {
        int dots = Math.min(names.length, 8);
        int dotsW = dots * 4 - 2;
        if (measuring) {
            int widest = 0;
            for (String n : names) widest = Math.max(widest, this.font.width(n));
            needChoice = Math.max(needChoice, 8 + widest + 10 + dotsW + 7);
            measureRow(name, 0);
            return;
        }
        int y = beginRow(name);
        int bw = choiceW;
        Button b = place(Button.builder(Component.empty(), x -> {
            set.accept((get.getAsInt() + 1) % names.length);
            TestConfig.save();
            this.rebuildWidgets(); // z.B. Hex-Felder nach Farbschema-Wechsel neu fuellen
        }).bounds(0, 0, bw, 20).build(), ctrlX, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, ox, oy, y, name);
            int bx = b.getX();
            int by = b.getY();
            Ui.box20(g, b, bx, by, bw, mx, my, true);
            int cur = Math.floorMod(get.getAsInt(), names.length);
            // kleine Punkte zeigen, welche der Moeglichkeiten gerade gewaehlt ist
            int dx0 = bx + bw - 7 - dotsW;
            for (int i = 0; i < dots; i++) {
                boolean sel = names.length <= 8 ? i == cur
                        : i == Math.round(cur * (dots - 1) / (float) (names.length - 1));
                Ui.fill(g, dx0 + i * 4, by + 9, dx0 + i * 4 + 2, by + 11, sel ? Theme.accent() : 0x60FFFFFF);
            }
            Ui.fitText(g, this.font, names[cur], bx + 8, by + 6, dx0 - 8 - (bx + 8), Ui.TEXT, true);
        });
        resetButton(y, () -> set.accept(def), () -> get.getAsInt() != def);
    }

    protected void slider(String name, int min, int max, IntSupplier get, IntConsumer set, int def) {
        if (measureRow(name, 40 + 6 + 104)) return;
        int y = beginRow(name);
        int bw = 40;
        int sx = ctrlX + bw + 6;
        int sw = Math.max(40, ctrlX + ctrlW - sx);
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
        placeBox(box, ctrlX, y);
        place(slide, sx, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, ox, oy, y, name);
            int x1 = slide.getX() + 5;
            int x2 = slide.getX() + slide.getWidth() - 5;
            int ty = slide.getY() + 9;
            int hx = x1 + Math.round((float) slide.shownFraction() * (x2 - x1));
            boolean over = slide.visible && mx >= slide.getX() && mx < slide.getX() + slide.getWidth()
                    && my >= slide.getY() && my < slide.getY() + 20;
            float hv = Ui.ease(slide, 0, over ? 1f : 0f, 0.3f);
            // Spur: links gefuellt (weicher Verlauf), rechts ruhig
            Ui.roundRect(g, x1 - 1, ty, x2 - x1 + 2, 3, 1, Ui.LINE);
            if (hx > x1) {
                Ui.gradientH(g, x1 - 1, ty, hx, ty + 3, Ui.lerp(Theme.accent(), 0xFF000000, 0.25f), Theme.accent());
            }
            // Griff: wird unter der Maus etwas groesser
            int grow = Math.round(hv);
            Ui.roundRect(g, hx - 4 - grow, ty - 3 - grow, 8 + 2 * grow, 9 + 2 * grow, 4 + grow,
                    Ui.lerp(Theme.accent(), 0xFFFFFFFF, 0.25f + 0.35f * hv));
        });
        resetButton(y, () -> set.accept(def), () -> get.getAsInt() != def);
    }

    protected void number(String name, IntSupplier get, IntConsumer set, int def) {
        if (measureRow(name, 90)) return;
        int y = beginRow(name);
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
        placeBox(box, ctrlX, y);
        addDraw((g, ox, oy, mx, my) -> label(g, ox, oy, y, name));
        resetButton(y, () -> set.accept(def), () -> get.getAsInt() != def);
    }

    /** Freies Textfeld. */
    protected void text(String name, Supplier<String> get, Consumer<String> set, String def) {
        if (measureRow(name, MIN_CTRL)) return;
        int y = beginRow(name);
        EditBox box = new EditBox(this.font, 0, 0, ctrlW, 20, Component.literal(name));
        box.setMaxLength(32);
        box.setValue(get.get());
        box.setResponder(s -> {
            if (!syncing) set.accept(s);
        });
        placeBox(box, ctrlX, y);
        addDraw((g, ox, oy, mx, my) -> label(g, ox, oy, y, name));
        resetButton(y, () -> set.accept(def), () -> !String.valueOf(get.get()).equals(def));
    }

    /** Hex-Farbe RRGGBB; leer bedeutet -1 (Standard). Daneben eine kleine Vorschau der Farbe. */
    protected void hex(String name, IntSupplier get, IntConsumer set) {
        if (measureRow(name, 70 + 6 + 20)) return;
        int y = beginRow(name);
        EditBox box = new EditBox(this.font, 0, 0, 70, 20, Component.literal(name));
        box.setMaxLength(6);
        int cur = get.getAsInt();
        box.setValue(cur >= 0 ? String.format("%06X", cur) : "");
        box.setHint(Component.literal("Standard"));
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
        placeBox(box, ctrlX, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, ox, oy, y, name);
            int c = get.getAsInt();
            if (c >= 0) {
                int sx = ox + ctrlX + 76;
                Ui.roundRect(g, sx, oy + y, 20, 20, 3, Ui.LINE);
                Ui.roundRect(g, sx + 1, oy + y + 1, 18, 18, 2, 0xFF000000 | c);
            }
        });
        resetButton(y, () -> set.accept(-1), () -> get.getAsInt() >= 0);
    }

    /** Drei Zahlenfelder X/Y/Z mit "Hier"-Knopf (aktuelle Spielerposition). */
    protected void coords(String name, Supplier<int[]> get, Consumer<int[]> set) {
        int hereW = Math.max(34, Ui.buttonWidth(this.font, "Hier"));
        if (measureRow(name, 3 * 44 + hereW - 4 - RESET_W)) return;
        int y = beginRow(name);
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
            placeBox(b, ctrlX + i * 44, y);
        }
        Button here = place(Button.builder(Component.literal("Hier"), x -> {
            if (this.minecraft == null || this.minecraft.player == null) return;
            var p = this.minecraft.player.blockPosition();
            fs[0].setValue(Integer.toString(p.getX()));
            fs[1].setValue(Integer.toString(p.getY()));
            fs[2].setValue(Integer.toString(p.getZ()));
            TestConfig.save();
        }).bounds(0, 0, hereW, 20).build(), ctrlX + 3 * 44, y);
        addDraw((g, ox, oy, mx, my) -> {
            label(g, ox, oy, y, name);
            Ui.button(g, this.font, here, mx, my, Ui.STYLE_NORMAL);
        });
    }

    /** Breiter Knopf ueber die ganze Panelbreite (beendet den aktuellen Abschnitt). */
    protected Button wideButton(String text, boolean primary, Runnable action) {
        if (measuring) {
            rowsSeen++;
            needLine = Math.max(needLine, Ui.buttonWidth(this.font, text));
            return Button.builder(Component.literal(text), x -> {
            }).bounds(0, 0, 20, 20).build();
        }
        endSection();
        int y = beginRow(text);
        Button b = place(Button.builder(Component.literal(text), x -> action.run())
                .bounds(0, 0, pw - 2 * PAD, 20).build(), PAD, y);
        addDraw((g, ox, oy, mx, my) ->
                Ui.button(g, this.font, b, mx, my, primary ? Ui.STYLE_PRIMARY : Ui.STYLE_NORMAL));
        return b;
    }

    /** Feste Fusszeile: optional der An/Aus-Schalter des Moduls links, immer "Zurueck" rechts. */
    protected void footer(BooleanSupplier active, Consumer<Boolean> setActive) {
        String backText = CommonComponents.GUI_BACK.getString();
        int backW = Math.max(72, Ui.buttonWidth(this.font, backText));
        int activeW = 16 + 6 + this.font.width("Aktiv") + 4;
        if (measuring) {
            needLine = Math.max(needLine, (active != null ? activeW + 12 : 0) + backW);
            return;
        }
        endSection();
        skip = false;
        if (filtering() && hits == 0) {
            curRow++;
            int ny = relY;
            rowY = ny;
            String msg = "Nichts gefunden für „" + filter + "“";
            addDraw((g, ox, oy, mx, my) ->
                    Ui.fitText(g, this.font, msg, ox + PAD, oy + ny + 4, pw - 2 * PAD, Ui.MUTED, true));
            relY += 18;
        }
        // Ab hier: feste Fusszeile (eigene Koordinaten, scrollt nicht mit)
        bodyH = relY + 4;
        foot = true;
        curRow++;
        relY = 0;
        rowY = 0;
        addDraw((g, ox, oy, mx, my) -> Ui.fill(g, ox + PAD, oy, ox + pw - PAD, oy + 1, Ui.HAIR));
        int y = 7;
        if (active != null) {
            Button b = place(Button.builder(Component.empty(), x -> {
                setActive.accept(!active.getAsBoolean());
                TestConfig.save();
            }).bounds(0, 0, activeW, 20).build(), PAD, y);
            addDraw((g, ox, oy, mx, my) -> {
                boolean on = active.getAsBoolean();
                boolean over = mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY() && my < b.getY() + 20;
                float hv = Ui.ease(b, 0, over ? 1f : 0f, 0.3f);
                float t = Ui.ease(b, 1, on ? 1f : 0f, 0.35f);
                Ui.switchPill(g, b.getX(), b.getY() + 6, t);
                Ui.text(g, this.font, "Aktiv", b.getX() + 22, b.getY() + 6,
                        Ui.lerp(Ui.lerp(Ui.MUTED, Ui.TEXT_ON, t), 0xFFFFFFFF, hv * 0.4f), true);
            });
        }
        Button back = place(Button.builder(CommonComponents.GUI_BACK, x -> onClose())
                .bounds(0, 0, backW, 20).build(), pw - PAD - backW, y);
        addDraw((g, ox, oy, mx, my) -> Ui.button(g, this.font, back, mx, my, Ui.STYLE_NORMAL));
        footH = y + 20 + 8;
    }

    // ---------- Animation / Zeichnen ----------

    private float rowAlpha(long now, int row) {
        if (!Theme.animations) return 1f;
        float r = (now - openedAt - Math.min(row * 22L, 260L)) / 150f;
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

    /** Mausrad: scrollt den Inhalt, wenn er hoeher ist als der Bildschirm. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll <= 0) return false;
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget - (float) scrollY * 30f));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (pendingRebuild) {
            pendingRebuild = false;
            boolean focused = searchBox != null && searchBox.isFocused();
            this.rebuildWidgets();
            if (focused && searchBox != null) this.setFocused(searchBox);
        }
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

        // Aufklapp-Anteil je Abschnitt (1 = offen) und daraus die aktuelle Hoehe des Inhalts
        float[] open = new float[secs.size()];
        float contentF = bodyH;
        for (int j = 0; j < secs.size(); j++) {
            Sec s = secs.get(j);
            open[j] = Ui.ease(s.key, 1, isCollapsed(s.key) ? 0f : 1f, 0.25f);
            contentF -= (1f - open[j]) * s.contentH;
        }
        int content = Math.round(contentF);

        // Sichtbarer Ausschnitt: passt der Inhalt nicht auf den Bildschirm, wird gescrollt
        int headH = Ui.HEADER_H;
        int viewH = Math.min(content, Math.max(48, this.height - 16 - headH - footH));
        int curPh = headH + viewH + footH;
        int curPy = Math.max(8, (this.height - curPh) / 2) + dy;
        maxScroll = Math.max(0, content - viewH);
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * Ui.step(0.35f);
        if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;
        int sc = Math.round(scroll);
        int bodyTop = curPy + headH;
        int bodyBottom = bodyTop + viewH;

        // Widgets verschieben; versteckte oder weggescrollte Zeilen sind nicht bedienbar
        for (Placed p : placed) {
            p.w().setX(px + p.rx());
            if (p.foot()) {
                p.w().setY(bodyBottom + p.ry());
                continue;
            }
            float v = p.sec() >= 0 ? open[p.sec()] : 1f;
            int wy = bodyTop + p.ry() + Math.round(shift(p.ry(), p.sec(), open)) - sc;
            p.w().setY(wy);
            boolean show = v > 0.03f && wy >= bodyTop - 2 && wy + p.w().getHeight() <= bodyBottom + 2;
            p.w().visible = show;
            p.w().active = show;
        }
        boolean search = searchable && searchBox != null;
        if (search) {
            searchBox.setX(px + pw - 8 - SEARCH_W);
            searchBox.setY(curPy + 3);
            searchBox.setAlpha(ga);
        }
        // Minecraft zeichnet hier nichts mehr selbst: alle Felder kommen unten, ueber dem Panel
        super.extractRenderState(g, mouseX, mouseY, delta);

        Ui.alpha = 1f;
        Fx.backdrop(g, this.width, this.height, mouseX, mouseY);

        Ui.alpha = ga;
        Fx.panelGlow(g, px, curPy, pw, curPh, eased);
        Ui.panel(g, this.font, px, curPy, pw, curPh, this.title.getString(), search ? SEARCH_W + 10 : 0);
        Fx.openFlash(g, px, curPy, pw, curPh, eased);

        // Inhalt, auf den sichtbaren Ausschnitt begrenzt
        boolean inView = mouseY >= bodyTop && mouseY < bodyBottom;
        int bmx = inView ? mouseX : -9999;
        int bmy = inView ? mouseY : -9999;
        g.enableScissor(px, bodyTop, px + pw, bodyBottom);
        for (Item it : items) {
            if (it.foot()) continue;
            float v = it.sec() >= 0 ? open[it.sec()] : 1f;
            if (v < 0.03f) continue;
            int oy = bodyTop + Math.round(shift(it.y(), it.sec(), open)) - sc;
            if (oy + it.y() > bodyBottom + 4 || oy + it.y() + 48 < bodyTop) continue;
            Ui.alpha = ga * rowAlpha(now, it.row()) * v;
            it.d().draw(g, px, oy, bmx, bmy);
        }
        for (Placed p : boxes) {
            if (p.foot() || !p.w().visible) continue;
            float a = ga * rowAlpha(now, p.row()) * (p.sec() >= 0 ? open[p.sec()] : 1f);
            EditBox eb = (EditBox) p.w();
            eb.setAlpha(a);
            Ui.alpha = a;
            eb.extractRenderState(g, bmx, bmy, delta);
            Ui.field(g, eb);
        }
        g.disableScissor();
        Ui.alpha = ga;

        // Weiche Kanten und Scrollbalken zeigen, dass es weitergeht
        if (maxScroll > 0) {
            float up = Ui.ease(this, 0, sc > 0 ? 1f : 0f, 0.25f);
            float down = Ui.ease(this, 1, sc < maxScroll ? 1f : 0f, 0.25f);
            int fade = Ui.withA(FADE, 0xF0);
            Ui.gradientV(g, px, bodyTop, px + pw, bodyTop + 12, Ui.withA(fade, Math.round(0xF0 * up)), Ui.withA(fade, 0));
            Ui.gradientV(g, px, bodyBottom - 12, px + pw, bodyBottom, Ui.withA(fade, 0),
                    Ui.withA(fade, Math.round(0xF0 * down)));
            int trackY = bodyTop + 4;
            int trackH = Math.max(12, viewH - 8);
            int thumbH = Math.max(14, trackH * viewH / Math.max(1, content));
            int thumbY = trackY + Math.round((trackH - thumbH) * (sc / (float) maxScroll));
            boolean overBar = mouseX >= px + pw - 8 && mouseX < px + pw && inView;
            Ui.scrollbar(g, px + pw - 5, trackY, trackH, thumbY, thumbH, Ui.ease(this, 2, overBar ? 1f : 0f, 0.3f));
        }

        // Feste Fusszeile
        for (Item it : items) {
            if (!it.foot()) continue;
            Ui.alpha = ga;
            it.d().draw(g, px, bodyBottom, mouseX, mouseY);
        }

        // Suchfeld in der Kopfzeile
        if (search) {
            Ui.alpha = ga;
            searchBox.extractRenderState(g, mouseX, mouseY, delta);
            Ui.field(g, searchBox);
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
        GuiScale.restore();
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
