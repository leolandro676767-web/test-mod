package de.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class TestClient implements ClientModInitializer {
    public static boolean walking = false;
    public static boolean sprint = false;

    private static KeyMapping toggleKey;
    private static KeyMapping guiKey;
    private static boolean appliedLastTick = false;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("test", "main"));

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.test.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.test.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(TestClient::onTick);
    }

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            walking = !walking;
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("Test: " + (walking ? "AN" : "AUS")), true);
            }
        }
        while (guiKey.consumeClick()) {
            mc.setScreen(new TestScreen(mc.screen));
        }

        if (mc.player == null) {
            walking = false;
            appliedLastTick = false;
            return;
        }

        if (walking && mc.screen == null) {
            mc.options.keyUp.setDown(true);
            if (sprint) mc.options.keySprint.setDown(true);
            appliedLastTick = true;
        } else if (appliedLastTick) {
            mc.options.keyUp.setDown(false);
            if (sprint) mc.options.keySprint.setDown(false);
            appliedLastTick = false;
        }
    }
}
