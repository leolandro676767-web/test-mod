package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Block waehlen und automatisch abbauen lassen. */
public class BlockScreen extends Screen {
    private final Screen parent;
    private Button selectButton;
    private Button startButton;

    public BlockScreen(Screen parent) {
        super(Component.literal("Block abbauen"));
        this.parent = parent;
    }

    private Component selectLabel() {
        return Component.literal("Block: " + TestClient.selectedBlockName() + " (anvisieren zum \u00c4ndern)");
    }

    private Component startLabel() {
        return Component.literal(TestClient.areaActive ? "Abbau STOPPEN" : "Abbau STARTEN");
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 110;
        int y = this.height / 2 - 40;

        selectButton = addRenderableWidget(Button.builder(selectLabel(), b -> {
            // Der Block, auf den man vor dem Oeffnen des Menues geschaut hat
            if (TestClient.selectLookedAt(this.minecraft)) {
                b.setMessage(selectLabel());
            } else {
                b.setMessage(Component.literal("Erst einen Block anvisieren"));
            }
        }).bounds(x, y, 220, 20).build());

        startButton = addRenderableWidget(Button.builder(startLabel(), b -> startOrStop())
                .bounds(x, y + 28, 220, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(x, y + 56, 220, 20).build());
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
    public void onClose() {
        TestConfig.save();
        this.minecraft.gui.setScreen(parent);
    }
}
