package de.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Einstellungen fuer den AutoClicker. */
public class ClickerScreen extends Screen {
    private static final int STEP = 5;

    private final Screen parent;
    private final List<Ui.Btn> btns = new ArrayList<>();
    private Button percentButton;
    private int px, py, pw, ph;

    public ClickerScreen(Screen parent) {
        super(Component.literal("AutoClicker"));
        this.parent = parent;
    }

    private static Component onOff(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "AN" : "AUS"));
    }

    private static Component percentLabel() {
        return Component.literal("Zufall: " + TestClient.clickerRandom + " %");
    }

    private void add(Button button, IntSupplier style) {
        btns.add(new Ui.Btn(addWidget(button), style));
    }

    private void changePercent(int delta) {
        TestClient.clickerRandom = Math.max(0, Math.min(100, TestClient.clickerRandom + delta));
        TestClient.rollClickerFactor();
        TestConfig.save();
        percentButton.setMessage(percentLabel());
    }

    @Override
    protected void init() {
        btns.clear();
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 70;
        px = x - 12;
        py = y - 34;
        pw = 224;
        ph = 34 + 124 + 40;

        add(Button.builder(onOff("AutoClicker", TestClient.autoClicker), b -> {
            TestClient.autoClicker = !TestClient.autoClicker;
            TestConfig.save();
            b.setMessage(onOff("AutoClicker", TestClient.autoClicker));
        }).bounds(x, y, 200, 20).build(), () -> TestClient.autoClicker ? Ui.STYLE_ON : Ui.STYLE_NORMAL);

        add(Button.builder(Component.literal("-"), b -> changePercent(-STEP))
                .bounds(x, y + 26, 40, 20).build(), () -> Ui.STYLE_NORMAL);
        percentButton = addWidget(Button.builder(percentLabel(), b -> { })
                .bounds(x + 44, y + 26, 112, 20).build());
        percentButton.active = false;
        btns.add(new Ui.Btn(percentButton, () -> Ui.STYLE_NORMAL));
        add(Button.builder(Component.literal("+"), b -> changePercent(STEP))
                .bounds(x + 160, y + 26, 40, 20).build(), () -> Ui.STYLE_NORMAL);

        add(Button.builder(onOff("Nur beim Halten", TestClient.clickerHold), b -> {
            TestClient.clickerHold = !TestClient.clickerHold;
            TestConfig.save();
            b.setMessage(onOff("Nur beim Halten", TestClient.clickerHold));
        }).bounds(x, y + 52, 200, 20).build(), () -> TestClient.clickerHold ? Ui.STYLE_ON : Ui.STYLE_NORMAL);

        add(Button.builder(onOff("Auch fr\u00fcher schlagen", TestClient.clickerEarly), b -> {
            TestClient.clickerEarly = !TestClient.clickerEarly;
            TestClient.rollClickerFactor();
            TestConfig.save();
            b.setMessage(onOff("Auch fr\u00fcher schlagen", TestClient.clickerEarly));
        }).bounds(x, y + 78, 200, 20).build(), () -> TestClient.clickerEarly ? Ui.STYLE_ON : Ui.STYLE_NORMAL);

        add(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(x, y + 106, 200, 20).build(), () -> Ui.STYLE_PRIMARY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Ui.panel(g, this.font, px, py, pw, ph, this.title.getString(), new ArrayList<>());
        for (Ui.Btn b : btns) {
            Ui.button(g, this.font, b.button, mouseX, mouseY, b.style.getAsInt());
        }
        int ty = py + ph - 34;
        String line1 = "Schl\u00e4gt im Takt des Tool-Cooldowns.";
        String line2 = "Abstand variiert um bis zu " + TestClient.clickerRandom + " %"
                + (TestClient.clickerEarly ? " (+/-)." : " (nur sp\u00e4ter).");
        g.text(this.font, line1, px + 8, ty, Ui.MUTED, true);
        g.text(this.font, line2, px + 8, ty + 11, Ui.MUTED, true);
    }

    @Override
    public void onClose() {
        TestConfig.save();
        this.minecraft.gui.setScreen(parent);
    }
}
