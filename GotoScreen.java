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

/** Koordinaten waehlen und dorthin laufen. */
public class GotoScreen extends Screen {
    private final Screen parent;
    private final EditBox[] fields = new EditBox[3];
    private final List<Ui.Btn> btns = new ArrayList<>();
    private Button startButton;
    private int px, py, pw, ph;

    public GotoScreen(Screen parent) {
        super(Component.literal("Zu Koordinaten gehen"));
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

    private Component startLabel() {
        return Component.literal(TestClient.areaActive ? "Gehen STOPPEN" : "Losgehen");
    }

    @Override
    protected void init() {
        btns.clear();
        int x = this.width / 2 - 110;
        int y = this.height / 2 - 40;
        px = x - 12;
        py = y - 34;
        pw = 244;
        ph = 34 + 76 + 12;

        fields[0] = box(x, y, 48, "X", TestClient.gx);
        fields[1] = box(x + 52, y, 48, "Y", TestClient.gy);
        fields[2] = box(x + 104, y, 48, "Z", TestClient.gz);
        Button here = addWidget(Button.builder(Component.literal("Hier"), b -> {
            if (this.minecraft.player == null) return;
            BlockPos p = this.minecraft.player.blockPosition();
            fields[0].setValue(Integer.toString(p.getX()));
            fields[1].setValue(Integer.toString(p.getY()));
            fields[2].setValue(Integer.toString(p.getZ()));
        }).bounds(x + 160, y, 60, 20).build());
        btns.add(new Ui.Btn(here, () -> Ui.STYLE_NORMAL));

        startButton = addWidget(Button.builder(startLabel(), b -> startOrStop())
                .bounds(x, y + 28, 220, 20).build());
        btns.add(new Ui.Btn(startButton, () -> Ui.STYLE_PRIMARY));

        Button back = addWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(x, y + 56, 220, 20).build());
        btns.add(new Ui.Btn(back, () -> Ui.STYLE_NORMAL));
    }

    private boolean applyFields() {
        try {
            int x = Integer.parseInt(fields[0].getValue());
            int y = Integer.parseInt(fields[1].getValue());
            int z = Integer.parseInt(fields[2].getValue());
            TestClient.gx = x;
            TestClient.gy = y;
            TestClient.gz = z;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void startOrStop() {
        if (TestClient.areaActive) {
            TestClient.stopArea(this.minecraft, "Gehen abgebrochen");
            startButton.setMessage(startLabel());
            return;
        }
        if (!applyFields()) {
            startButton.setMessage(Component.literal("Bitte alle Felder mit Zahlen f\u00fcllen"));
            return;
        }
        String error = TestClient.startGoto(this.minecraft);
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
