package de.test;

import net.minecraft.client.gui.screens.Screen;

/** AutoDrop: jedes Item des Spiels waehlbar, das automatisch aus dem Inventar gedroppt wird. */
public class DropScreen extends PickerScreen {
    public DropScreen(Screen parent) {
        super(parent, "AutoDrop", Kind.ITEM, ofSet(TestClient.dropItems),
                () -> TestClient.autoDrop, v -> TestClient.autoDrop = v);
    }
}
