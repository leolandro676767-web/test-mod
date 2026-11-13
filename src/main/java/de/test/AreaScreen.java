package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Quader zwischen zwei Ecken abbauen. */
public class AreaScreen extends Form {
    private Button start;

    public AreaScreen(Screen parent) {
        super(Component.literal("Bereich abbauen"), parent);
    }

    private static String startLabel() {
        return TestClient.areaActive ? "Abbau STOPPEN" : "Abbau STARTEN";
    }

    @Override
    protected void build() {
        description("Baut alle Bl\u00f6cke zwischen zwei Ecken ab.");
        section("Bereich");
        coords("Ecke 1",
                () -> new int[] {TestClient.ax1, TestClient.ay1, TestClient.az1},
                v -> {
                    TestClient.ax1 = v[0];
                    TestClient.ay1 = v[1];
                    TestClient.az1 = v[2];
                });
        coords("Ecke 2",
                () -> new int[] {TestClient.ax2, TestClient.ay2, TestClient.az2},
                v -> {
                    TestClient.ax2 = v[0];
                    TestClient.ay2 = v[1];
                    TestClient.az2 = v[2];
                });
        note(() -> {
            long dx = Math.abs((long) TestClient.ax2 - TestClient.ax1) + 1;
            long dy = Math.abs((long) TestClient.ay2 - TestClient.ay1) + 1;
            long dz = Math.abs((long) TestClient.az2 - TestClient.az1) + 1;
            return "Gr\u00f6\u00dfe: " + dx + " x " + dy + " x " + dz + " = " + (dx * dy * dz)
                    + " Bl\u00f6cke (max. 50000)";
        });
        start = wideButton(startLabel(), true, this::startOrStop);
        section("Anzeige");
        toggle("Ziele hervorheben", () -> RenderMods.miningHighlight, v -> RenderMods.miningHighlight = v, true);
        toggle("Bereichsrahmen", () -> RenderMods.miningShowArea, v -> RenderMods.miningShowArea = v, true);
        choice("Umriss-Stil", Render3D.STYLES, () -> RenderMods.outlineStyle, v -> RenderMods.outlineStyle = v, 2);
        footer(null, null);
    }

    private void startOrStop() {
        if (TestClient.areaActive) {
            TestClient.stopArea(this.minecraft, "Bereich abgebrochen");
            start.setMessage(Component.literal(startLabel()));
            return;
        }
        String error = TestClient.startArea(this.minecraft);
        if (error != null) {
            start.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }
}
