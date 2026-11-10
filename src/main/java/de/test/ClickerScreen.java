package de.test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen fuer den AutoClicker. */
public class ClickerScreen extends Form {
    public ClickerScreen(Screen parent) {
        super(Component.literal("AutoClicker"), parent);
    }

    @Override
    protected void build() {
        description("Schl\u00e4gt im Takt des Tool-Cooldowns.");
        section("Allgemein");
        toggle("Nur beim Halten", () -> TestClient.clickerHold, v -> TestClient.clickerHold = v, true);
        toggle("Auch fr\u00fcher schlagen", () -> TestClient.clickerEarly, v -> {
            TestClient.clickerEarly = v;
            TestClient.rollClickerFactor();
        }, false);
        slider("Zufall (%)", 0, 100, () -> TestClient.clickerRandom, v -> {
            TestClient.clickerRandom = v;
            TestClient.rollClickerFactor();
        }, 20);
        footer(() -> TestClient.autoClicker, v -> TestClient.autoClicker = v);
    }
}
