package de.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Block waehlen und automatisch abbauen lassen. */
public class BlockScreen extends Screen {
    private final Screen parent;
    private final List<Ui.Btn> btns = new ArrayList<>();
    private Button selectButton;
    private Button startButton;
    private int px, py, pw, ph;

    public BlockScreen(Screen parent) {
        super(Component.literal("Block abbauen"));
        this.parent = parent;
    }

    private Component selectLabel() {
        return Component.literal("Block: " + TestClient.selectedBlockName());
    }

    private Component startLabel() {
        return Component.literal(TestClient.areaActive ? "Abbau STOPPEN" : "Abbau STARTEN");
    }

    @Override
    protected void init() {
        btns.clear();
        int x = this.width / 2 - 110;
        int y = this.height / 2 - 40;
        px = x - 12;
        py = y - 34;
        pw = 244;
        ph = 34 + 76 + 26;

        selectButton = addWidget(Button.builder(selectLabel(), b -> {
            // Der Block, auf den man vor dem Oeffnen des Menues geschaut hat
            if (TestClient.selectLookedAt(this.minecraft)) {
                b.setMessage(selectLabel());
            } else {
                b.setMessage(Component.literal("Erst einen Block anvisieren"));
            }
        }).bounds(x, y, 220, 20).build());
        btns.add(new Ui.Btn(selectButton, () -> Ui.STYLE_ON));

        startButton = addWidget(Button.builder(startLabel(), b -> startOrStop())
                .bounds(x, y + 28, 220, 20).build());
        btns.add(new Ui.Btn(startButton, () -> Ui.STYLE_PRIMARY));

        Button back = addWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(x, y + 56, 220, 20).build());
        btns.add(new Ui.Btn(back, () -> Ui.STYLE_NORMAL));
    }

    private void startOrStop() {
        if (TestClient.areaActive) {
            TestClient.stopArea(this.minecraft, "Abbau gestoppt");
            startButton.setMessage(startLabel());
            return;
        }
        String error = TestClient.startBlock(this.minecraft);
        if (error != null) {
            startButton.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Ui.panel(g, this.font, px, py, pw, ph, this.title.getString(), new ArrayList<>());
        for (Ui.Btn b : btns) {
            Ui.button(g, this.font, b.button, mouseX, mouseY, b.style.getAsInt());
        }
        g.text(this.font, "Block anvisieren, dann oben klicken", px + 8, py + ph - 16, Ui.MUTED, true);
    }

    @Override
    public void onClose() {
        TestConfig.save();
        this.minecraft.gui.setScreen(parent);
    }
}
