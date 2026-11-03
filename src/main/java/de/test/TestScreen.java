package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class TestScreen extends Screen {
    private final Screen parent;

    public TestScreen(Screen parent) {
        super(Component.literal("Test"));
        this.parent = parent;
    }

    private static Component walkLabel() {
        return Component.literal("Test: " + (TestClient.walking ? "AN" : "AUS"));
    }

    private static Component sprintLabel() {
        return Component.literal("Sprinten: " + (TestClient.sprint ? "AN" : "AUS"));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 30;

        addRenderableWidget(Button.builder(walkLabel(), b -> {
            TestClient.walking = !TestClient.walking;
            b.setMessage(walkLabel());
        }).bounds(x, y, 200, 20).build());

        addRenderableWidget(Button.builder(sprintLabel(), b -> {
            TestClient.sprint = !TestClient.sprint;
            b.setMessage(sprintLabel());
        }).bounds(x, y + 24, 200, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, y + 56, 200, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
