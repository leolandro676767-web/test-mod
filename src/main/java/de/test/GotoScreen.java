package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Koordinaten waehlen und dorthin laufen (Wegsuche wie Baritone). */
public class GotoScreen extends Form {
    private Button start;

    public GotoScreen(Screen parent) {
        super(Component.literal("Zu Koordinaten gehen"), parent);
    }

    private static String startLabel() {
        return TestClient.areaActive && TestClient.gotoMode ? "Gehen STOPPEN" : "Losgehen";
    }

    @Override
    protected void build() {
        description("Sucht den besten Weg zum Ziel: läuft gerade Strecken am Stück,\n"
                + "springt auf Stufen und über Lücken, schwimmt und baut nur ab,\n"
                + "wenn es nicht anders geht.");
        section("Ziel");
        coords("Koordinaten",
                () -> new int[] {TestClient.gx, TestClient.gy, TestClient.gz},
                v -> {
                    TestClient.gx = v[0];
                    TestClient.gy = v[1];
                    TestClient.gz = v[2];
                });
        start = wideButton(startLabel(), true, this::startOrStop);
        note(() -> TestClient.gotoMode ? Walker.status(this.minecraft) : "Bereit");

        section("Weg");
        toggle("Blöcke abbauen", () -> TestClient.autoMine, v -> TestClient.autoMine = v, true);
        toggle("Blöcke setzen", () -> Walker.allowPlace, v -> Walker.allowPlace = v, true);
        toggle("Über Lücken springen", () -> Walker.allowParkour, v -> Walker.allowParkour = v, true);
        toggle("Sprinten", () -> Walker.sprint, v -> Walker.sprint = v, true);
        slider("Max. Fallhöhe", 1, 10, () -> Walker.maxFall, v -> Walker.maxFall = v, 3);

        section("Anzeige");
        toggle("Weg anzeigen", () -> Walker.showPath, v -> Walker.showPath = v, true);
        toggle("Von der Seite zuschauen (Free Look)", () -> RenderMods.freeLook, v -> {
            RenderMods.freeLook = v;
            if (v) RenderMods.freecam = false;
        }, false);
        toggle("Ziel hervorheben", () -> RenderMods.miningHighlight, v -> RenderMods.miningHighlight = v, true);
        choice("Umriss-Stil", Render3D.STYLES, () -> RenderMods.outlineStyle, v -> RenderMods.outlineStyle = v, 2);
        section("Verteidigung");
        toggle("Mobs abwehren", () -> BotGuard.forGoto, v -> BotGuard.forGoto = v, true);
        wideButton("Einstellungen der Bot-Verteidigung", false,
                () -> this.minecraft.gui.setScreen(new BotGuardScreen(this)), true);
        footer(null, null);
    }

    private void startOrStop() {
        if (TestClient.areaActive && TestClient.gotoMode) {
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
