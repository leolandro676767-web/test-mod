package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Block waehlen und automatisch abbauen lassen. */
public class BlockScreen extends Form {
    private Button select;
    private Button start;

    public BlockScreen(Screen parent) {
        super(Component.literal("Block abbauen"), parent);
    }

    private static String selectLabel() {
        return "Block aus Liste w\u00e4hlen (" + TestClient.selectedBlockName() + ")";
    }

    private static String startLabel() {
        return TestClient.areaActive ? "Abbau STOPPEN" : "Abbau STARTEN";
    }

    @Override
    protected void build() {
        description("W\u00e4hle einen Block aus der Liste aller Bl\u00f6cke\noder \u00fcbernimm den anvisierten.");
        select = wideButton(selectLabel(), false, () -> this.minecraft.gui.setScreen(new BlockPickScreen(this)));
        wideButton("Anvisierten Block \u00fcbernehmen", false, () -> {
            // Der Block, auf den man vor dem Oeffnen des Menues geschaut hat
            if (TestClient.selectLookedAt(this.minecraft)) {
                select.setMessage(Component.literal(selectLabel()));
            } else {
                select.setMessage(Component.literal("Erst einen Block anvisieren"));
            }
        });
        section("Suche");
        slider("Radius (Bl\u00f6cke)", 4, 40, () -> TestClient.searchRadius, v -> TestClient.searchRadius = v, 24);
        slider("H\u00f6he (Bl\u00f6cke)", 2, 20, () -> TestClient.searchHeight, v -> TestClient.searchHeight = v, 12);
        start = wideButton(startLabel(), true, this::startOrStop);
        footer(null, null);
    }

    private void startOrStop() {
        if (TestClient.areaActive) {
            TestClient.stopArea(this.minecraft, "Abbau gestoppt");
            start.setMessage(Component.literal(startLabel()));
            return;
        }
        String error = TestClient.startBlock(this.minecraft);
        if (error != null) {
            start.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }
}
