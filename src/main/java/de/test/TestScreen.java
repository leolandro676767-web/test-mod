package de.test;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class TestScreen extends Screen {
    private final Screen parent;

    public TestScreen(Screen parent) {
        super(Component.literal("Test"));
        this.parent = parent;
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "AN" : "AUS"));
    }

    private void addToggle(int x, int y, String name, BooleanSupplier value, Runnable flip) {
        addRenderableWidget(Button.builder(label(name, value.getAsBoolean()), b -> {
            flip.run();
            b.setMessage(label(name, value.getAsBoolean()));
            // Speichert die Konfiguration sofort bei Änderung
            TestClient.saveConfig();
        }).bounds(x, y, 200, 20).build());
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 92;

        addToggle(x, y, "AutoWalk", () -> TestClient.walking,
                () -> TestClient.walking = !TestClient.walking);
        addToggle(x, y + 24, "Sprinten", () -> TestClient.sprint,
                () -> TestClient.sprint = !TestClient.sprint);
        addToggle(x, y + 48, "Sprint-Jump", () -> TestClient.sprintJump,
                () -> TestClient.sprintJump = !TestClient.sprintJump);
        addToggle(x, y + 72, "Bl\u00f6cke abbauen", () -> TestClient.autoMine,
                () -> TestClient.autoMine = !TestClient.autoMine);
        addToggle(x, y + 96, "Auto-Logout (2 Herzen)", () -> TestClient.autoLogout,
                () -> TestClient.autoLogout = !TestClient.autoLogout);

        String keys = TestClient.toggleKey.getTranslatedKeyMessage().getString()
                + " / " + TestClient.guiKey.getTranslatedKeyMessage().getString();
        addRenderableWidget(Button.builder(
                Component.literal("Tasten \u00e4ndern (" + keys + ")"),
                b -> this.minecraft.gui.setScreen(new KeyBindsScreen(this, this.minecraft.options)))
                .bounds(x, y + 124, 200, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, y + 152, 200, 20).build());
    }

    @Override
    public void onClose() {
        // Speichert auch beim Schließen des Menüs
        TestClient.saveConfig();
        this.minecraft.gui.setScreen(parent);
    }
}
