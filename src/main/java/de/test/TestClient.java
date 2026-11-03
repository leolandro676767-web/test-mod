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
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("test", "main"));

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.test.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
        guiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.test.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(TestClient::onTick);
    }

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            walking = !walking;
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

    private static void stopMove(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    private static void releaseAll(Minecraft mc) {
        stopMove(mc);
        mc.options.keyAttack.setDown(false);
    }

    private static BlockPos findObstacle(Level level, Entity p, float yaw) {
        double rad = Math.toRadians(yaw);
        double x = p.getX() + (-Math.sin(rad)) * 0.8;
        double z = p.getZ() + Math.cos(rad) * 0.8;
        BlockPos head = BlockPos.containing(x, p.getY() + 1.1, z);
        BlockPos feet = BlockPos.containing(x, p.getY() + 0.1, z);
        if (isMineable(level, head)) return head;
        if (isMineable(level, feet)) return feet;
        return null;
    }

    private static boolean isMineable(Level level, BlockPos pos) {
        var bs = level.getBlockState(pos);
        if (bs.isAir()) return false;
        if (bs.getCollisionShape(level, pos).isEmpty()) return false;
        return bs.getDestroySpeed(level, pos) >= 0.0f;
    }

    private static boolean aimAt(Entity p, BlockPos target) {
        double dx = target.getX() + 0.5 - p.getX();
        double dy = target.getY() + 0.5 - p.getEyeY();
        double dz = target.getZ() + 0.5 - p.getZ();
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        return rotateTo(p, targetYaw, targetPitch, 4.0f);
    }

    private static boolean rotateTo(Entity p, float targetYaw, float targetPitch, float tolerance) {
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        float dy = Mth.wrapDegrees(targetYaw - yaw);
        float dp = targetPitch - pitch;
        yaw += Math.abs(dy) < 1.0f ? dy : Mth.clamp(dy * 0.3f, -12.0f, 12.0f);
        pitch += Math.abs(dp) < 1.0f ? dp : Mth.clamp(dp * 0.3f, -8.0f, 8.0f);
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(pitch, -90.0f, 90.0f));
        return Math.abs(dy) < tolerance && Math.abs(dp) < tolerance;
    }
}
