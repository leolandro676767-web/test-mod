package de.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Allgemeiner Auswahl-Bildschirm fuer jedes Modul: links alle Items bzw. alle Bloecke des Spiels
 * (mit Suche und Vorschau), rechts die aktuelle Auswahl. Die Auswahl selbst liegt in einer {@link Selection}.
 */
public class PickerScreen extends Screen {
    /** Was zur Auswahl steht: jedes Item oder jeder Block aus den Spiel-Registern. */
    public enum Kind { ITEM, BLOCK }

    /** Speicherort der Auswahl eines Moduls (Mehrfach- oder Einzelauswahl). */
    public interface Selection {
        boolean contains(Object value);

        void add(Object value);

        void remove(Object value);

        List<Object> values();
    }

    /** Mehrfachauswahl in einem Set, z.B. die Drop-Items. */
    @SuppressWarnings("unchecked")
    public static <T> Selection ofSet(Set<T> set) {
        return new Selection() {
            @Override
            public boolean contains(Object value) {
                return set.contains(value);
            }

            @Override
            public void add(Object value) {
                set.add((T) value);
            }

            @Override
            public void remove(Object value) {
                set.remove(value);
            }

            @Override
            public List<Object> values() {
                return new ArrayList<Object>(set);
            }
        };
    }

    /** Ein Eintrag der Liste; iconItem ist das Item fuer die Vorschau (bei Bloecken ohne Item: Luft). */
    private record Option(Object value, String name, String id, Item iconItem) {
    }

    private static final int ROW = 22;
    private static final int PAD = 10;

    private static final Map<Kind, List<Option>> OPTIONS = new EnumMap<>(Kind.class);
    private static final Map<Kind, Map<Object, Option>> LOOKUP = new EnumMap<>(Kind.class);
    private static final Map<Item, ItemStack> ICONS = new HashMap<>();
    private static final Set<Item> BAD_ICONS = new HashSet<>();

    private final Screen parent;
    private final Kind kind;
    private final Selection selection;
    private final BooleanSupplier active;
    private final Consumer<Boolean> setActive;
    private final List<Option> ordered = new ArrayList<>();
    private final List<Option> filtered = new ArrayList<>();
    private final List<Button> addBtns = new ArrayList<>();
    private final List<Button> delBtns = new ArrayList<>();
    private final Map<AbstractWidget, Integer> baseY = new LinkedHashMap<>();
    private final long openedAt = System.currentTimeMillis();
    private boolean closing;
    private boolean closed;
    private long closeAt;
    private String search = "";
    private int scrollL = 0;
    private int scrollR = 0;
    private int rows;
    private int px, py, pw, ph, lx, rx, leftW, rightW, listY, navY, footY;
    private EditBox searchBox;
    private Button prevL, nextL, prevR, nextR, activeBtn, backBtn;

    /**
     * @param active    optionaler "Aktiv"-Schalter des Moduls (null = keiner)
     * @param setActive zugehoeriger Setter
     */
    public PickerScreen(Screen parent, String title, Kind kind, Selection selection,
                        BooleanSupplier active, Consumer<Boolean> setActive) {
        super(Component.literal(title));
        this.parent = parent;
        this.kind = kind;
        this.selection = selection;
        this.active = active;
        this.setActive = setActive;
    }

    // ---------- Daten ----------

    private static String shortId(String full) {
        int i = full.indexOf(':');
        return (i >= 0 ? full.substring(i + 1) : full).toLowerCase(Locale.ROOT);
    }

    /** Alle Eintraege einer Art, einmal aufgebaut und nach Name sortiert. */
    private static List<Option> options(Kind kind) {
        List<Option> cached = OPTIONS.get(kind);
        if (cached != null) return cached;
        List<Option> list = new ArrayList<>();
        if (kind == Kind.ITEM) {
            for (Item it : BuiltInRegistries.ITEM) {
                if (it == Items.AIR) continue;
                list.add(new Option(it, Component.translatable(it.getDescriptionId()).getString(),
                        shortId(BuiltInRegistries.ITEM.getKey(it).toString()), it));
            }
        } else {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) continue;
                list.add(new Option(b, b.getName().getString(),
                        shortId(BuiltInRegistries.BLOCK.getKey(b).toString()), b.asItem()));
            }
        }
        list.sort(Comparator.<Option, String>comparing(Option::name, String.CASE_INSENSITIVE_ORDER));
        Map<Object, Option> lookup = new HashMap<>();
        for (Option o : list) lookup.put(o.value(), o);
        OPTIONS.put(kind, list);
        LOOKUP.put(kind, lookup);
        return list;
    }

    /** Eintraege, deren Item im Inventar liegt, zuerst; danach der Rest. */
    private void buildOrdered() {
        Set<Item> inv = new LinkedHashSet<>();
        if (this.minecraft != null && this.minecraft.player != null) {
            var inventory = this.minecraft.player.getInventory();
            for (int i = 0; i < 41; i++) {
                ItemStack st = inventory.getItem(i);
                if (!st.isEmpty()) inv.add(st.getItem());
            }
        }
        List<Option> first = new ArrayList<>();
        List<Option> rest = new ArrayList<>();
        for (Option o : options(kind)) {
            (inv.contains(o.iconItem()) ? first : rest).add(o);
        }
        ordered.clear();
        ordered.addAll(first);
        ordered.addAll(rest);
    }

    private void refilter() {
        String q = search.toLowerCase(Locale.ROOT).trim();
        String qId = q.replace(' ', '_');
        filtered.clear();
        for (Option o : ordered) {
            if (q.isEmpty() || o.name().toLowerCase(Locale.ROOT).contains(q) || o.id().contains(qId)) {
                filtered.add(o);
            }
        }
        clampScroll();
    }

    private List<Option> selected() {
        Map<Object, Option> lookup = LOOKUP.get(kind);
        List<Option> out = new ArrayList<>();
        for (Object v : selection.values()) {
            Option o = lookup.get(v);
            if (o != null) out.add(o);
        }
        out.sort(Comparator.<Option, String>comparing(Option::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private void clampScroll() {
        scrollL = Math.max(0, Math.min(scrollL, Math.max(0, filtered.size() - rows)));
        scrollR = Math.max(0, Math.min(scrollR, Math.max(0, selection.values().size() - rows)));
    }

    private Option leftOption(int r) {
        int idx = scrollL + r;
        return idx >= 0 && idx < filtered.size() ? filtered.get(idx) : null;
    }

    private Option rightOption(int r) {
        List<Option> sel = selected();
        int idx = scrollR + r;
        return idx >= 0 && idx < sel.size() ? sel.get(idx) : null;
    }

    private String fit(String s, int max) {
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "...") > max) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    // ---------- Vorschau ----------

    private static ItemStack icon(Item it) {
        return ICONS.computeIfAbsent(it, Item::getDefaultInstance);
    }

    /** Zeichnet die Vorschau; ohne Item (z.B. Portal-Block) oder bei einem Fehler kommt ein Ersatzkaestchen mit Anfangsbuchstabe. */
    private void drawIcon(GuiGraphicsExtractor g, Option o, int x, int y) {
        Item it = o.iconItem();
        if (it != Items.AIR && !BAD_ICONS.contains(it)) {
            try {
                g.fakeItem(icon(it), x, y);
                return;
            } catch (RuntimeException e) {
                BAD_ICONS.add(it);
            }
        }
        String ch = o.name().isEmpty() ? "?" : o.name().substring(0, 1).toUpperCase(Locale.ROOT);
        Ui.fill(g, x, y, x + 16, y + 16, 0xFF3A3450);
        Ui.text(g, this.font, ch, x + (16 - this.font.width(ch)) / 2, y + 4, Ui.MUTED, true);
    }

    // ---------- Aufbau ----------

    @Override
    protected void init() {
        options(kind);
        ICONS.clear();
        BAD_ICONS.clear();
        buildOrdered();
        addBtns.clear();
        delBtns.clear();

        rows = Math.max(4, Math.min(10, (this.height - 190) / ROW));
        pw = Math.min(380, this.width - 12);
        int inner = pw - 2 * PAD;
        leftW = inner * 3 / 5;
        rightW = inner - leftW - 9;
        int searchY = Ui.HEADER_H + 8;
        int listRel = searchY + 20 + 6;
        int navRel = listRel + rows * ROW + 6;
        int footRel = navRel + 20 + 4 + 1 + 6;
        ph = footRel + 24 + 6;
        px = (this.width - pw) / 2;
        py = Math.max(4, (this.height - ph) / 2);
        lx = px + PAD;
        rx = lx + leftW + 9;
        listY = py + listRel;
        navY = py + navRel;
        footY = py + footRel;

        searchBox = new EditBox(this.font, lx, py + searchY, inner, 20, Component.literal("Suchen"));
        searchBox.setHint(Component.literal(kind == Kind.ITEM ? "Alle Items suchen..." : "Alle Bl\u00f6cke suchen..."));
        searchBox.setMaxLength(40);
        searchBox.setValue(search);
        searchBox.setResponder(s -> {
            search = s;
            scrollL = 0;
            refilter();
        });
        addRenderableWidget(searchBox);
        refilter();

        for (int i = 0; i < rows; i++) {
            final int r = i;
            int y = listY + i * ROW + 1;
            addBtns.add(addWidget(Button.builder(Component.empty(), b -> {
                Option o = leftOption(r);
                if (o != null) {
                    selection.add(o.value());
                    TestConfig.save();
                }
            }).bounds(lx + leftW - 22, y, 20, 20).build()));
            delBtns.add(addWidget(Button.builder(Component.empty(), b -> {
                Option o = rightOption(r);
                if (o != null) {
                    selection.remove(o.value());
                    TestConfig.save();
                    clampScroll();
                }
            }).bounds(rx + rightW - 22, y, 20, 20).build()));
        }

        prevL = addWidget(Button.builder(Component.literal("<"), b -> {
            scrollL -= rows;
            clampScroll();
        }).bounds(lx, navY, 28, 20).build());
        nextL = addWidget(Button.builder(Component.literal(">"), b -> {
            scrollL += rows;
            clampScroll();
        }).bounds(lx + 32, navY, 28, 20).build());
        prevR = addWidget(Button.builder(Component.literal("<"), b -> {
            scrollR -= rows;
            clampScroll();
        }).bounds(rx, navY, 28, 20).build());
        nextR = addWidget(Button.builder(Component.literal(">"), b -> {
            scrollR += rows;
            clampScroll();
        }).bounds(rx + 32, navY, 28, 20).build());

        activeBtn = null;
        if (active != null) {
            activeBtn = addWidget(Button.builder(Component.empty(), b -> {
                setActive.accept(!active.getAsBoolean());
                TestConfig.save();
            }).bounds(lx + 46, footY, 20, 20).build());
        }
        backBtn = addWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(px + pw - PAD - 90, footY, 90, 20).build());

        // Ausgangspositionen merken, damit die Einblend-Animation alles mitverschieben kann
        baseY.clear();
        for (var c : this.children()) {
            if (c instanceof AbstractWidget w) baseY.put(w, w.getY());
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int d = scrollY > 0 ? -3 : 3;
        if (mouseX < rx - 4) {
            scrollL += d;
        } else {
            scrollR += d;
        }
        clampScroll();
        return true;
    }

    // ---------- Zeichnen ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Ui.beginFrame();
        long nowMs = System.currentTimeMillis();
        float closeProgress = 0f;
        float t;
        if (closing) {
            closeProgress = Theme.animations ? Math.min(1f, (nowMs - closeAt) / 150f) : 1f;
            t = 1f - closeProgress;
        } else {
            t = Theme.animations ? Math.min(1f, (nowMs - openedAt) / 180f) : 1f;
        }
        float ease = 1f - (1f - t) * (1f - t);
        int d = Math.round((1f - ease) * 10f);
        float ga = Math.max(0.06f, ease);
        for (var e : baseY.entrySet()) {
            e.getKey().setY(e.getValue() + d);
        }
        searchBox.setAlpha(ga);
        super.extractRenderState(g, mouseX, mouseY, delta);

        List<int[]> holes = new ArrayList<>();
        holes.add(Ui.hole(searchBox));
        boolean icons = ga >= 0.85f;
        Ui.alpha = ga;
        Ui.panel(g, this.font, px, py + d, pw, ph, this.title.getString(), holes);
        Ui.frame(g, searchBox);

        // Trennlinie zwischen den Listen
        Ui.fill(g, lx + leftW + 4, listY + d, lx + leftW + 5, listY + d + rows * ROW, Ui.TEXT);

        List<Option> sel = selected();
        for (int r = 0; r < rows; r++) {
            int y = listY + d + r * ROW;

            Option a = leftOption(r);
            Button ab = addBtns.get(r);
            boolean chosen = a != null && selection.contains(a.value());
            ab.active = a != null && !chosen;
            boolean overL = a != null && mouseX >= lx && mouseX < lx + leftW && mouseY >= y && mouseY < y + ROW;
            float hl = Ui.ease(ab, 2, overL ? 1f : 0f, 0.3f);
            if (hl > 0.01f) {
                Ui.fill(g, lx, y, lx + leftW - 1, y + ROW - 2, Ui.lerp(0x00000000, Theme.hoverBg(), hl));
            }
            if (a != null) {
                if (icons) drawIcon(g, a, lx + 4, y + 3);
                Ui.text(g, this.font, fit(a.name(), leftW - 56), lx + 24, y + 7,
                        chosen ? Theme.accent() : Ui.TEXT, true);
                Ui.plusMinus(g, ab, ab.getX(), ab.getY(), true, ab.active, mouseX, mouseY);
            }

            int idx = scrollR + r;
            Option s = idx < sel.size() ? sel.get(idx) : null;
            Button db = delBtns.get(r);
            db.active = s != null;
            boolean overR = s != null && mouseX >= rx && mouseX < rx + rightW && mouseY >= y && mouseY < y + ROW;
            float hr = Ui.ease(db, 2, overR ? 1f : 0f, 0.3f);
            if (hr > 0.01f) {
                Ui.fill(g, rx, y, rx + rightW - 1, y + ROW - 2, Ui.lerp(0x00000000, Theme.hoverBg(), hr));
            }
            if (s != null) {
                if (icons) drawIcon(g, s, rx + 4, y + 3);
                Ui.text(g, this.font, fit(s.name(), rightW - 56), rx + 24, y + 7, Ui.TEXT, true);
                Ui.plusMinus(g, db, db.getX(), db.getY(), false, true, mouseX, mouseY);
            }
        }

        // Navigation + Zaehler
        prevL.active = scrollL > 0;
        nextL.active = scrollL + rows < filtered.size();
        prevR.active = scrollR > 0;
        nextR.active = scrollR + rows < sel.size();
        for (Button b : new Button[] {prevL, nextL, prevR, nextR}) {
            Ui.button(g, this.font, b, mouseX, mouseY, Ui.STYLE_NORMAL);
        }
        Ui.text(g, this.font, filtered.size() + " Treffer", lx + 66, navY + d + 6, Ui.MUTED, true);
        Ui.text(g, this.font, "Gew\u00e4hlt: " + sel.size(), rx + 66, navY + d + 6, Ui.MUTED, true);

        // Fusszeile
        Ui.fill(g, px, footY + d - 5, px + pw, footY + d - 4, Ui.TEXT);
        if (activeBtn != null) {
            Ui.text(g, this.font, "Aktiv:", lx, footY + d + 6, Ui.TEXT, true);
            Ui.checkbox(g, activeBtn, activeBtn.getX(), activeBtn.getY(), active.getAsBoolean(), mouseX, mouseY);
        }
        Ui.button(g, this.font, backBtn, mouseX, mouseY, Ui.STYLE_NORMAL);
        Ui.alpha = 1f;

        if (closing && closeProgress >= 1f) finishClose();
    }

    private void finishClose() {
        if (closed) return;
        closed = true;
        this.minecraft.gui.setScreen(parent);
    }

    public void tick() {
        if (closing && System.currentTimeMillis() - closeAt >= 150) finishClose();
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
}
