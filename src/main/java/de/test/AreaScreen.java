package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AreaScreen extends Screen {
    private final Screen parent;
    private final EditBox[] fields = new EditBox[6];
    private Button startButton;

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
        int x = this.width / 2 - 110;
        int y = this.height / 2 - 60;

        int[] values = {TestClient.ax1, TestClient.ay1, TestClient.az1,
                TestClient.ax2, TestClient.ay2, TestClient.az2};
        String[] hints = {"X1", "Y1", "Z1", "X2", "Y2", "Z2"};

        for (int i = 0; i < 3; i++) {
            fields[i] = box(x + i * 52, y, 48, hints[i], values[i]);
        }
        addRenderableWidget(Button.builder(Component.literal("Hier"), b -> fillHere(0))
                .bounds(x + 160, y, 60, 20).build());

        for (int i = 3; i < 6; i++) {
            fields[i] = box(x + (i - 3) * 52, y + 26, 48, hints[i], values[i]);
        }
        addRenderableWidget(Button.builder(Component.literal("Hier"), b -> fillHere(3))
                .bounds(x + 160, y + 26, 60, 20).build());

        startButton = addRenderableWidget(Button.builder(startLabel(), b -> startOrStop())
                .bounds(x, y + 60, 220, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(x, y + 88, 220, 20).build());
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
    public void onClose() {
        if (applyFields()) {
            TestConfig.save();
        }
        this.minecraft.gui.setScreen(parent);
    }
}
