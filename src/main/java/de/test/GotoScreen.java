package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Koordinaten waehlen und dorthin laufen. */
public class GotoScreen extends Form {
    private Button start;

    public GotoScreen(Screen parent) {
        super(Component.literal("Zu Koordinaten gehen"), parent);
    }

    private static String startLabel() {
        return TestClient.areaActive ? "Gehen STOPPEN" : "Losgehen";
    }

    @Override
    protected void build() {
        description("L\u00e4uft zu einem Ziel und baut bei Bedarf\nBr\u00fccken, S\u00e4ulen und Hindernisse ab.");
        section("Ziel");
        coords("Koordinaten",
                () -> new int[] {TestClient.gx, TestClient.gy, TestClient.gz},
                v -> {
                    TestClient.gx = v[0];
                    TestClient.gy = v[1];
                    TestClient.gz = v[2];
                });
        start = wideButton(startLabel(), true, this::startOrStop);
        footer(null, null);
    }

    private void startOrStop() {
        if (TestClient.areaActive) {
            TestClient.stopArea(this.minecraft, "Gehen abgebrochen");
            start.setMessage(Component.literal(startLabel()));
            return;
        }
        String error = TestClient.startGoto(this.minecraft);
        if (error != null) {
            start.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }
}
