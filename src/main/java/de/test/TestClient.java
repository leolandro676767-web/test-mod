package de.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

public class TestClient implements ClientModInitializer {
    public static ModConfig CONFIG;

    public static boolean walking = false;
    public static boolean sprint = false;
    public static boolean sprintJump = false;
    public static boolean autoMine = true;
    public static boolean autoLogout = true;

    private static final float LOGOUT_HEALTH = 4.0f;

    public static KeyMapping toggleKey;
    public static KeyMapping guiKey;

    private enum Phase { WALK, MINE, RETURN }

    private static Phase phase = Phase.WALK;
    private static float savedYaw = 0.0f;
    private static float savedPitch = 0.0f;
    private static boolean applied = false;

    @Override
    public void onInitializeClient() {
        // Konfiguration beim Start laden und Werte zuweisen
        CONFIG = ModConfig.load();
        walking = CONFIG.walking;
        sprint = CONFIG.sprint;
        sprintJump = CONFIG.sprintJump;
        autoMine = CONFIG.autoMine;
        autoLogout = CONFIG.autoLogout;

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("test", "main"));

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.test.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
        guiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.test.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(TestClient::onTick);
    }

    public static void saveConfig() {
        if (CONFIG == null) {
            CONFIG = new ModConfig();
        }
        CONFIG.walking = walking;
        CONFIG.sprint = sprint;
        CONFIG.sprintJump = sprintJump;
        CONFIG.autoMine = autoMine;
        CONFIG.autoLogout = autoLogout;
        CONFIG.save();
    }

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            walking = !walking;
            saveConfig(); // Speichert direkt, wenn man die Taste (G) drückt
            if (mc.player != null) {
                mc.player.sendSystemMessage(
                        Component.literal("Test: " + (walking ? "AN" : "AUS")));
            }
        }
        while (guiKey.consumeClick()) {
            mc.gui.setScreen(new TestScreen(mc.gui.screen()));
        }

        Entity player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            walking = false;
            phase = Phase.WALK;
            applied = false;
            return;
        }

        float health = mc.player.getHealth();
        if (autoLogout && health > 0.0f && health <= LOGOUT_HEALTH) {
            walking = false;
            phase = Phase.WALK;
            applied = false;
            releaseAll(mc);
            var connection = mc.getConnection();
            if (connection != null) {
                connection.getConnection().disconnect(
                        Component.literal("Test: Auto-Logout bei 2 Herzen"));
            }
            return;
        }

        boolean active = walking && mc.gui.screen() == null;
        if (!active) {
            if (applied) {
                releaseAll(mc);
                applied = false;
            }
            phase = Phase.WALK;
            return;
        }
        applied = true;

        switch (phase) {
            case WALK -> {
                BlockPos obstacle = autoMine
                        ? findObstacle(level, player, player.getYRot()) : null;
                if (obstacle != null) {
                    savedYaw = player.getYRot();
                    savedPitch = player.getXRot();
                    phase = Phase.MINE;
                    stopMove(mc);
                } else {
                    pressMove(mc);
                }
            }
            case MINE -> {
                stopMove(mc);
                BlockPos target = autoMine ? findObstacle(level, player, savedYaw) : null;
                if (target == null) {
                    mc.options.keyAttack.setDown(false);
                    phase = Phase.RETURN;
                } else {
                    boolean aimed = aimAt(player, target);
                    mc.options.keyAttack.setDown(aimed);
                }
            }
            case RETURN -> {
                stopMove(mc);
                mc.options.keyAttack.setDown(false);
                if (rotateTo(player, savedYaw, savedPitch, 1.5f)) {
                    phase = Phase.WALK;
                }
            }
        }
    }

    private static void pressMove(Minecraft mc) {
        mc.options.keyUp.setDown(true);
        mc.options.keySprint.setDown(sprint || sprintJump);
        mc.options.keyJump.setDown(sprintJump);
        if (autoMine) mc.options.keyAttack.setDown(false);
    }

    private static void stopMove(mc) { // (Hinweis: falls Minecraft-Version hier ein Minecraft-Objekt erwartet)
        // Bleibt im Originalcode exakt so wie bei dir:
    }
    // ... restliche Methoden bleiben unverändert ...
