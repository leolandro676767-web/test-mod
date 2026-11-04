package de.test;

import java.util.HashSet;
import java.util.Set;

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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class TestClient implements ClientModInitializer {
    public static boolean walking = false;
    public static boolean sprint = false;
    public static boolean sprintJump = false;
    public static boolean autoMine = true;
    public static boolean autoLogout = true;

    // Bereichs-Modus
    public static boolean areaActive = false;
    public static int ax1 = 0, ay1 = 0, az1 = 0, ax2 = 0, ay2 = 0, az2 = 0;
    private static final long MAX_AREA_VOLUME = 50000L;
    private static int minX, minY, minZ, maxX, maxY, maxZ;
    private static BlockPos areaTarget = null;
    private static int walkTicks = 0;
    private static final Set<Long> skipped = new HashSet<>();

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

    // ---------- Bereichs-Modus: Start / Stop ----------

    public static String startArea(Minecraft mc) {
        minX = Math.min(ax1, ax2);
        maxX = Math.max(ax1, ax2);
        minY = Math.min(ay1, ay2);
        maxY = Math.max(ay1, ay2);
        minZ = Math.min(az1, az2);
        maxZ = Math.max(az1, az2);
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_AREA_VOLUME) {
            return "Zu gro\u00df (max. 50000 Bl\u00f6cke)";
        }
        skipped.clear();
        areaTarget = null;
        walkTicks = 0;
        walking = false;
        phase = Phase.WALK;
        applied = true;
        areaActive = true;
        return null;
    }

    public static void stopArea(Minecraft mc, String message) {
        areaActive = false;
        areaTarget = null;
        releaseAll(mc);
        applied = false;
        if (mc.player != null && message != null) {
            mc.player.sendSystemMessage(Component.literal("Test: " + message));
        }
    }

    // ---------- Tick ----------

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            if (areaActive) {
                stopArea(mc, "Bereich abgebrochen");
            } else {
                walking = !walking;
                if (mc.player != null) {
                    mc.player.sendSystemMessage(
                            Component.literal("Test: " + (walking ? "AN" : "AUS")));
                }
            }
        }
        while (guiKey.consumeClick()) {
            mc.gui.setScreen(new TestScreen(mc.gui.screen()));
        }

        Entity player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            walking = false;
            areaActive = false;
            phase = Phase.WALK;
            applied = false;
            return;
        }

        float health = mc.player.getHealth();
        if (autoLogout && health > 0.0f && health <= LOGOUT_HEALTH) {
            walking = false;
            areaActive = false;
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

        if (areaActive) {
            runArea(mc, level, player);
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

    // ---------- Bereichs-Modus: Ablauf ----------

    private static void runArea(Minecraft mc, Level level, Entity player) {
        applied = true;
        if (mc.gui.screen() != null) {
            releaseAll(mc);
            return;
        }

        if (areaTarget != null && !isMineable(level, areaTarget)) {
            areaTarget = null;
        }
        if (areaTarget == null) {
            areaTarget = nextTarget(level, player);
            walkTicks = 0;
            if (areaTarget == null) {
                stopArea(mc, "Bereich fertig");
                return;
            }
        }

        double dx = areaTarget.getX() + 0.5 - player.getX();
        double dy = areaTarget.getY() + 0.5 - player.getEyeY();
        double dz = areaTarget.getZ() + 0.5 - player.getZ();
        boolean inReach = dx * dx + dy * dy + dz * dz <= 16.0
                && canSee(level, player, areaTarget);

        if (inReach) {
            stopMove(mc);
            walkTicks = 0;
            boolean aimed = aimAt(player, areaTarget);
            mc.options.keyAttack.setDown(aimed);
        } else {
            mc.options.keyAttack.setDown(false);
            walkTicks++;
            if (walkTicks > 400) {
                skipped.add(areaTarget.asLong());
                areaTarget = null;
                walkTicks = 0;
                stopMove(mc);
                return;
            }
            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            boolean aligned = rotateTo(player, targetYaw, 10.0f, 15.0f);
            mc.options.keyUp.setDown(aligned);
            mc.options.keyJump.setDown(aligned);
            mc.options.keySprint.setDown(false);
        }
    }

    private static BlockPos nextTarget(Level level, Entity p) {
        BlockPos bestExposed = null;
        double bestExposedScore = Double.MAX_VALUE;
        BlockPos bestAny = null;
        double bestAnyScore = Double.MAX_VALUE;
        double px = p.getX();
        double py = p.getEyeY();
        double pz = p.getZ();

        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (skipped.contains(pos.asLong())) continue;
                    if (!isMineable(level, pos)) continue;
                    double dx = x + 0.5 - px;
                    double dy = y + 0.5 - py;
                    double dz = z + 0.5 - pz;
                    double score = (maxY - y) * 10000.0 + dx * dx + dy * dy + dz * dz;
                    if (score < bestAnyScore) {
                        bestAnyScore = score;
                        bestAny = pos;
                    }
                    if (score < bestExposedScore && isExposed(level, pos)) {
                        bestExposedScore = score;
                        bestExposed = pos;
                    }
                }
            }
        }
        return bestExposed != null ? bestExposed : bestAny;
    }

    private static boolean isOpen(Level level, BlockPos n) {
        return level.getBlockState(n).getCollisionShape(level, n).isEmpty();
    }

    private static boolean isExposed(Level level, BlockPos p) {
        return isOpen(level, p.above()) || isOpen(level, p.below())
                || isOpen(level, p.north()) || isOpen(level, p.south())
                || isOpen(level, p.east()) || isOpen(level, p.west());
    }

    private static boolean canSee(Level level, Entity p, BlockPos target) {
        var from = p.getEyePosition();
        var to = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        var hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getBlockPos().equals(target);
    }

    // ---------- Bewegung und Zielen ----------

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
