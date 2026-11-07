package de.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Das Menue selbst anpassen: Farben, Logo, Groesse, Animationen. */
public class DesignScreen extends Screen {
    private final Screen parent;
    private final List<Ui.Btn> btns = new ArrayList<>();
    private final List<EditBox> boxes = new ArrayList<>();
    private int px, py, pw, ph;
    private int previewX, previewY;

    public DesignScreen(Screen parent) {
        super(Component.literal("Design anpassen"));
        this.parent = parent;
    }

    private static Component onOff(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "AN" : "AUS"));
    }

    private EditBox hexBox(int x, int y, int w, String hint, int current, IntConsumer set) {
        EditBox b = new EditBox(this.font, x, y, w, 20, Component.literal(hint));
        b.setMaxLength(6);
        b.setHint(Component.literal(hint));
        b.setValue(current >= 0 ? String.format("%06X", current) : "");
        b.setResponder(s -> {
            String f = s.replaceAll("[^0-9a-fA-F]", "");
            if (f.length() > 6) f = f.substring(0, 6);
            if (!f.equals(s)) {
                b.setValue(f);
                return;
            }
            set.accept(Theme.parseHex(f));
        });
        addRenderableWidget(b);
        boxes.add(b);
        return b;
    }

    private Ui.Btn add(Button button, java.util.function.IntSupplier style) {
        Ui.Btn bt = new Ui.Btn(addWidget(button), style);
        btns.add(bt);
        return bt;
    }

    @Override
    protected void init() {
        btns.clear();
        boxes.clear();
        int x = this.width / 2 - 110;
        int y = this.height / 2 - 85;
        px = x - 12;
        py = y - 34;
        pw = 244;
        ph = 34 + 144 + 12;
        previewX = x;
        previewY = y + 102;

        // Farbschema durchschalten
        add(Button.builder(Component.literal("Farbschema: " + Theme.SCHEME_NAMES[Theme.scheme]), b -> {
            Theme.scheme = (Theme.scheme + 1) % Theme.SCHEME_NAMES.length;
            Theme.headHex = -1;
            Theme.accentHex = -1;
            TestConfig.save();
            this.rebuildWidgets();
        }).bounds(x, y, 220, 20).build(), () -> Ui.STYLE_NORMAL);

        // Eigene Farben als Hex (RRGGBB)
        hexBox(x, y + 26, 106, "Kopf RRGGBB", Theme.headHex, v -> Theme.headHex = v);
        hexBox(x + 114, y + 26, 106, "Akzent RRGGBB", Theme.accentHex, v -> Theme.accentHex = v);

        add(Button.builder(onOff("Logo", Theme.showLogo), b -> {
            Theme.showLogo = !Theme.showLogo;
            TestConfig.save();
            b.setMessage(onOff("Logo", Theme.showLogo));
        }).bounds(x, y + 52, 106, 20).build(), () -> Theme.showLogo ? Ui.STYLE_ON : Ui.STYLE_NORMAL);

        add(Button.builder(onOff("Animation", Theme.animations), b -> {
            Theme.animations = !Theme.animations;
            TestConfig.save();
            b.setMessage(onOff("Animation", Theme.animations));
        }).bounds(x + 114, y + 52, 106, 20).build(), () -> Theme.animations ? Ui.STYLE_ON : Ui.STYLE_NORMAL);

        add(Button.builder(Component.literal("Logo: " + Theme.SIZE_NAMES[Theme.logoSize]), b -> {
            Theme.logoSize = (Theme.logoSize + 1) % Theme.SIZE_NAMES.length;
            TestConfig.save();
            b.setMessage(Component.literal("Logo: " + Theme.SIZE_NAMES[Theme.logoSize]));
        }).bounds(x, y + 76, 106, 20).build(), () -> Ui.STYLE_NORMAL);

        add(Button.builder(Component.literal("Spalten: " + Theme.WIDTH_NAMES[Theme.colWidth]), b -> {
            Theme.colWidth = (Theme.colWidth + 1) % Theme.WIDTH_NAMES.length;
            TestConfig.save();
            b.setMessage(Component.literal("Spalten: " + Theme.WIDTH_NAMES[Theme.colWidth]));
        }).bounds(x + 114, y + 76, 106, 20).build(), () -> Ui.STYLE_NORMAL);

        add(Button.builder(Component.literal("Zur\u00fccksetzen"), b -> {
            Theme.reset();
            TestConfig.save();
            this.rebuildWidgets();
        }).bounds(x, y + 124, 106, 20).build(), () -> Ui.STYLE_NORMAL);

        add(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x + 114, y + 124, 106, 20).build(), () -> Ui.STYLE_PRIMARY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        List<int[]> holes = new ArrayList<>();
        for (EditBox f : boxes) holes.add(Ui.hole(f));
        Ui.panel(g, this.font, px, py, pw, ph, this.title.getString(), holes);
        for (EditBox f : boxes) Ui.frame(g, f);
        for (Ui.Btn b : btns) {
            Ui.button(g, this.font, b.button, mouseX, mouseY, b.style.getAsInt());
        }

        // Vorschau: die vier Spaltenfarben + Akzentfarbe
        for (int i = 0; i < 4; i++) {
            g.fill(previewX + i * 40, previewY, previewX + i * 40 + 36, previewY + 14, Theme.head(i));
        }
        g.fill(previewX + 160, previewY, previewX + 220, previewY + 14, Theme.accent());
    }

    @Override
    public void onClose() {
        Theme.clamp();
        TestConfig.save();
        this.minecraft.gui.setScreen(parent);
    }
}
