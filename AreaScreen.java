package de.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AreaScreen extends Screen {
    private final Screen parent;
    private final EditBox[] fields = new EditBox[6];
    private final List<Ui.Btn> btns = new ArrayList<>();
    private Button startButton;
    private int px, py, pw, ph;

    public AreaScreen(Screen parent) {
        super(Component.literal("Bereich abbauen"));
        this.parent = parent;
    }

    private EditBox box(int x, int y, int w, String hint, int value) {
        EditBox b = new EditBox(this.font, x, y, w, 20, Component.literal(hint));
        b.setMaxLength(8);
        b.setHint(Component.literal(hint));
        b.setValue(Integer.toString(value));
        b.setResponder(s -> {
            if (startButton != null && !TestClient.areaActive) {
                startButton.setMessage(startLabel());
            }
            if (!s.matches("-?\\d*")) {
                b.setValue((s.startsWith("-") ? "-" : "") + s.replaceAll("[^0-9]", ""));
            }
        });
        addRenderableWidget(b);
        return b;
    }

    private void fillHere(int first) {
        if (this.minecraft.player == null) return;
        BlockPos p = this.minecraft.player.blockPosition();
        fields[first].setValue(Integer.toString(p.getX()));
        fields[first + 1].setValue(Integer.toString(p.getY()));
        fields[first + 2].setValue(Integer.toString(p.getZ()));
    }

    private Component startLabel() {
        return Component.literal(TestClient.areaActive ? "Abbau STOPPEN" : "Abbau STARTEN");
    }

    @Override
    protected void init() {
        btns.clear();
        int x = this.width / 2 - 110;
        int y = this.height / 2 - 60;
        px = x - 12;
        py = y - 34;
        pw = 244;
        ph = 34 + 108 + 12;

        int[] values = {TestClient.ax1, TestClient.ay1, TestClient.az1,
                TestClient.ax2, TestClient.ay2, TestClient.az2};
        String[] hints = {"X1", "Y1", "Z1", "X2", "Y2", "Z2"};

        for (int i = 0; i < 3; i++) {
            fields[i] = box(x + i * 52, y, 48, hints[i], values[i]);
        }
        Button here1 = addWidget(Button.builder(Component.literal("Hier"), b -> fillHere(0))
                .bounds(x + 160, y, 60, 20).build());
        btns.add(new Ui.Btn(here1, () -> Ui.STYLE_NORMAL));

        for (int i = 3; i < 6; i++) {
            fields[i] = box(x + (i - 3) * 52, y + 26, 48, hints[i], values[i]);
        }
        Button here2 = addWidget(Button.builder(Component.literal("Hier"), b -> fillHere(3))
                .bounds(x + 160, y + 26, 60, 20).build());
        btns.add(new Ui.Btn(here2, () -> Ui.STYLE_NORMAL));

        startButton = addWidget(Button.builder(startLabel(), b -> startOrStop())
                .bounds(x, y + 60, 220, 20).build());
        btns.add(new Ui.Btn(startButton, () -> Ui.STYLE_PRIMARY));

        Button back = addWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(x, y + 88, 220, 20).build());
        btns.add(new Ui.Btn(back, () -> Ui.STYLE_NORMAL));
    }

    /** Uebernimmt die Feldwerte nach TestClient. Gibt false zurueck, wenn ein Feld ungueltig ist. */
    private boolean applyFields() {
        try {
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) {
                v[i] = Integer.parseInt(fields[i].getValue());
            }
            TestClient.ax1 = v[0];
            TestClient.ay1 = v[1];
            TestClient.az1 = v[2];
            TestClient.ax2 = v[3];
            TestClient.ay2 = v[4];
            TestClient.az2 = v[5];
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void startOrStop() {
        if (TestClient.areaActive) {
            TestClient.stopArea(this.minecraft, "Bereich abgebrochen");
            startButton.setMessage(startLabel());
            return;
        }
        if (!applyFields()) {
            startButton.setMessage(Component.literal("Bitte alle Felder mit Zahlen f\u00fcllen"));
            return;
        }
        String error = TestClient.startArea(this.minecraft);
        if (error != null) {
            startButton.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        List<int[]> holes = new ArrayList<>();
        for (EditBox f : fields) holes.add(Ui.hole(f));
        Ui.panel(g, this.font, px, py, pw, ph, this.title.getString(), holes);
        for (EditBox f : fields) Ui.frame(g, f);
        for (Ui.Btn b : btns) {
            Ui.button(g, this.font, b.button, mouseX, mouseY, b.style.getAsInt());
        }
    }

    @Override
    public void onClose() {
        if (applyFields()) {
            TestConfig.save();
        }
        this.minecraft.gui.setScreen(parent);
    }
}
