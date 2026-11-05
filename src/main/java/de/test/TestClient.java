package de.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
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
    private static int stuckTicks = 0;
    private static int mineTicks = 0;
    private static int noProgressTicks = 0;
    private static double bestDist = Double.MAX_VALUE;
    private static BlockPos digPos = null;
    private static int digTicks = 0;
    private static boolean retried = false;
    private static double[] reachPoint = null;
    private static double lastX = 0.0;
    private static double lastZ = 0.0;
    // nicht erreichbar (nur fuer die Wegsuche gesperrt, werden aber mitgenommen, wenn sie spaeter in Reichweite sind)
    private static final Set<Long> skipped = new HashSet<>();
    // lassen sich nicht abbauen (ueberall gesperrt)
    private static final Set<Long> hopeless = new HashSet<>();
    // Hindernisse auf dem Weg, die sich nicht abbauen lassen
    private static final Set<Long> badObstacles = new HashSet<>();

    private static final float LOGOUT_HEALTH = 4.0f;
    // Verhindert Logout-Schleife: erst wieder aktiv, wenn die Gesundheit
    // nach einem Auto-Logout ueber der Schwelle war.
    private static boolean logoutArmed = true;

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

        TestConfig.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TestConfig.save());
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
        TestConfig.save();
        skipped.clear();
        hopeless.clear();
        badObstacles.clear();
        retried = false;
        setTarget(null);
        walking = false;
        phase = Phase.WALK;
        applied = true;
        areaActive = true;
        return null;
    }

    public static void stopArea(Minecraft mc, String message) {
        areaActive = false;
        setTarget(null);
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
        if (health > LOGOUT_HEALTH) {
            logoutArmed = true;
        }
        if (autoLogout && logoutArmed && health > 0.0f && health <= LOGOUT_HEALTH) {
            logoutArmed = false;
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

    private static final double REACH = 4.3;
    private static final double REACH_SQ = REACH * REACH;
    // Block-Mittelpunkt hoechstens so weit weg, damit irgendein Punkt des Blocks noch in Reichweite sein kann
    private static final double CULL_SQ = (REACH + 0.9) * (REACH + 0.9);
    private static final double ARRIVE_DIST = 0.6;
    private static final int MAX_MINE_TICKS = 600;
    private static final int MAX_DIG_TICKS = 300;
    private static final int MAX_STUCK_TICKS = 40;
    private static final int MAX_NO_PROGRESS_TICKS = 120;
    private static final int MAX_WALK_TICKS = 2400;

    private static final int[][] DIRS = {
            {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final double[][] FACE_POINTS = {
            {0.5, 0.95, 0.5}, {0.5, 0.05, 0.5}, {0.95, 0.5, 0.5},
            {0.05, 0.5, 0.5}, {0.5, 0.5, 0.95}, {0.5, 0.5, 0.05}};

    private static double eyeDist2(Entity p, BlockPos b) {
        double dx = b.getX() + 0.5 - p.getX();
        double dy = b.getY() + 0.5 - p.getEyeY();
        double dz = b.getZ() + 0.5 - p.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void setTarget(BlockPos pos) {
        areaTarget = pos;
        walkTicks = 0;
        stuckTicks = 0;
        mineTicks = 0;
        noProgressTicks = 0;
        bestDist = Double.MAX_VALUE;
        digPos = null;
        digTicks = 0;
    }

    private static void skipTarget(Minecraft mc) {
        if (areaTarget != null) skipped.add(areaTarget.asLong());
        setTarget(null);
        releaseAll(mc);
    }

    private static String finishMessage(Level level) {
        Set<Long> left = new HashSet<>();
        for (long l : skipped) {
            if (isMineable(level, BlockPos.of(l))) left.add(l);
        }
        for (long l : hopeless) {
            if (isMineable(level, BlockPos.of(l))) left.add(l);
        }
        if (left.isEmpty()) return "Bereich fertig";
        return "Bereich fertig (" + left.size() + " Bl\u00f6cke nicht erreichbar)";
    }

    private static void runArea(Minecraft mc, Level level, Entity player) {
        applied = true;
        if (mc.gui.screen() != null) {
            releaseAll(mc);
            return;
        }

        // Bewegung seit dem letzten Tick (fuer die Haengen-Erkennung)
        double moved = Math.abs(player.getX() - lastX) + Math.abs(player.getZ() - lastZ);
        lastX = player.getX();
        lastZ = player.getZ();

        if (areaTarget != null && !isMineable(level, areaTarget)) {
            setTarget(null);
        }

        // 1. Ist das aktuelle Ziel in Reichweite? Sonst den naechsten erreichbaren Block suchen.
        double[] point = areaTarget != null ? visiblePoint(level, player, areaTarget) : null;
        if (point == null) {
            BlockPos near = nearestReachable(level, player);
            if (near != null) {
                if (!near.equals(areaTarget)) setTarget(near);
                point = reachPoint;
            }
        }

        // 2. Abbauen
        if (point != null) {
            stopMove(mc);
            digPos = null;
            boolean aimed = aimAt(player, point[0], point[1], point[2]);
            mc.options.keyAttack.setDown(aimed);
            mineTicks++;
            if (mineTicks > MAX_MINE_TICKS) {
                // laesst sich nicht abbauen - nicht noch einmal versuchen
                hopeless.add(areaTarget.asLong());
                skipTarget(mc);
            }
            return;
        }

        // 3. Nichts in Reichweite: naechsten Block im Bereich als Laufziel waehlen.
        if (areaTarget == null) {
            BlockPos t = nearestOverall(level, player);
            if (t == null && !skipped.isEmpty() && !retried) {
                // zweiter Durchgang: vieles ist jetzt abgebaut und dadurch erreichbar
                retried = true;
                skipped.clear();
                t = nearestOverall(level, player);
            }
            if (t == null) {
                stopArea(mc, finishMessage(level));
                return;
            }
            setTarget(t);
        }

        // 4. Hinlaufen
        walkTicks++;
        if (walkTicks > MAX_WALK_TICKS) {
            skipTarget(mc);
            return;
        }

        // Kommt der Block ueberhaupt naeher? Sonst (Kreislaufen, Haengen) ueberspringen.
        double dist = Math.sqrt(eyeDist2(player, areaTarget));
        if (dist < bestDist - 0.25) {
            bestDist = dist;
            noProgressTicks = 0;
        } else if (++noProgressTicks > MAX_NO_PROGRESS_TICKS) {
            skipTarget(mc);
            return;
        }

        double dx = areaTarget.getX() + 0.5 - player.getX();
        double dz = areaTarget.getZ() + 0.5 - player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        // 4a. Direkt ueber/unter dem Ziel angekommen, aber noch nicht in Reichweite:
        //     Nicht weiter drehen und laufen (das war das Im-Kreis-Drehen), sondern senkrecht handeln.
        if (horiz < ARRIVE_DIST) {
            if (areaTarget.getY() < player.blockPosition().getY()) {
                // Ziel liegt tiefer: Block unter den Fuessen abbauen und nachrutschen
                BlockPos under = player.blockPosition().below();
                if (isMineable(level, under) && !badObstacles.contains(under.asLong())) {
                    if (!safeToDigDown(level, under) || !digBlock(mc, player, under)) {
                        skipTarget(mc);
                    }
                } else {
                    // Luft unter den Fuessen: wir fallen gerade
                    releaseAll(mc);
                }
            } else {
                // Ziel liegt darueber/ist verdeckt und von hier nicht erreichbar
                skipTarget(mc);
            }
            return;
        }

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        // 4b. Hindernisse auf dem Weg abbauen. Die Suche richtet sich nach der Laufrichtung
        //     (nicht nach der aktuellen Blickrichtung), sonst arbeiten Zielen und Drehen gegeneinander.
        BlockPos obstacle = findObstacle(level, player, targetYaw);
        if (obstacle != null) {
            digBlock(mc, player, obstacle);
            return;
        }
        digPos = null;
        mc.options.keyAttack.setDown(false);

        // 4c. In Laufrichtung drehen (steht dabei still) und loslaufen
        if (!rotateTo(player, targetYaw, 10.0f, 15.0f)) {
            stopMove(mc);
            return;
        }

        if (moved < 0.02) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks > MAX_STUCK_TICKS) {
            skipTarget(mc);
            return;
        }
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(stuckTicks > 8);
        mc.options.keySprint.setDown(false);
    }

    /** Baut einen Block auf dem Weg ab. Gibt false zurueck, wenn er sich nicht abbauen laesst. */
    private static boolean digBlock(Minecraft mc, Entity player, BlockPos pos) {
        stopMove(mc);
        if (pos.equals(digPos)) {
            digTicks++;
        } else {
            digPos = pos;
            digTicks = 0;
        }
        if (digTicks > MAX_DIG_TICKS) {
            badObstacles.add(pos.asLong());
            digPos = null;
            mc.options.keyAttack.setDown(false);
            return false;
        }
        mc.options.keyAttack.setDown(aimAt(player, pos));
        stuckTicks = 0;
        noProgressTicks = 0;
        return true;
    }

    /** Nicht nach unten graben, wenn darunter Fluessigkeit ist oder man tiefer als 3 Bloecke faellt. */
    private static boolean safeToDigDown(Level level, BlockPos under) {
        for (int i = 1; i <= 3; i++) {
            BlockPos p = under.below(i);
            var bs = level.getBlockState(p);
            if (!bs.getFluidState().isEmpty()) return false;
            if (!bs.getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }

    /** Naechster Block in Reichweite, den man wirklich sehen kann (setzt reachPoint). */
    private static BlockPos nearestReachable(Level level, Entity p) {
        reachPoint = null;
        BlockPos c = p.blockPosition();
        int r = 5;
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = Math.max(minX, c.getX() - r); x <= Math.min(maxX, c.getX() + r); x++) {
            for (int y = Math.max(minY, c.getY() - 4); y <= Math.min(maxY, c.getY() + 6); y++) {
                for (int z = Math.max(minZ, c.getZ() - r); z <= Math.min(maxZ, c.getZ() + r); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (hopeless.contains(pos.asLong())) continue;
                    if (!isMineable(level, pos)) continue;
                    if (eyeDist2(p, pos) > CULL_SQ) continue;
                    candidates.add(pos);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> eyeDist2(p, pos)));
        for (BlockPos pos : candidates) {
            double[] pt = visiblePoint(level, p, pos);
            if (pt != null) {
                reachPoint = pt;
                return pos;
            }
        }
        return null;
    }

    /** Naechster abbaubarer Block im ganzen Bereich (zum Hinlaufen). */
    private static BlockPos nearestOverall(Level level, Entity p) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    long key = pos.asLong();
                    if (skipped.contains(key) || hopeless.contains(key)) continue;
                    if (!isMineable(level, pos)) continue;
                    double score = eyeDist2(p, pos);
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Sucht einen Punkt auf dem Block, der in Reichweite liegt und auf den man direkt sehen kann.
     * Es wird nicht nur die Mitte geprueft, sondern auch die freiliegenden Seitenflaechen -
     * so sind auch Bloecke erreichbar, die nur teilweise sichtbar sind.
     */
    private static double[] visiblePoint(Level level, Entity p, BlockPos b) {
        boolean[] open = new boolean[6];
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            BlockPos n = b.offset(DIRS[i][0], DIRS[i][1], DIRS[i][2]);
            open[i] = level.getBlockState(n).getCollisionShape(level, n).isEmpty();
            any |= open[i];
        }
        if (!any) return null; // ringsum zu, also nicht sichtbar

        double ex = p.getX();
        double ey = p.getEyeY();
        double ez = p.getZ();
        for (int i = -1; i < 6; i++) {
            if (i >= 0 && !open[i]) continue;
            double px = b.getX() + (i < 0 ? 0.5 : FACE_POINTS[i][0]);
            double py = b.getY() + (i < 0 ? 0.5 : FACE_POINTS[i][1]);
            double pz = b.getZ() + (i < 0 ? 0.5 : FACE_POINTS[i][2]);
            double dx = px - ex;
            double dy = py - ey;
            double dz = pz - ez;
            if (dx * dx + dy * dy + dz * dz > REACH_SQ) continue;
            if (clipHits(level, p, px, py, pz, b)) {
                return new double[] {px, py, pz};
            }
        }
        return null;
    }

    private static boolean clipHits(Level level, Entity p, double x, double y, double z, BlockPos target) {
        var from = p.getEyePosition();
        var to = new Vec3(x, y, z);
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

    private static final double[][] OBSTACLE_PROBES = {
            {0.9, 0.0}, {0.6, 0.0}, {0.9, 0.25}, {0.9, -0.25}};

    /** Block, der in Laufrichtung (Kopf- oder Fusshoehe, auch an den Koerperkanten) im Weg ist. */
    private static BlockPos findObstacle(Level level, Entity p, float yaw) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double lx = Math.cos(rad);
        double lz = Math.sin(rad);
        for (double[] probe : OBSTACLE_PROBES) {
            double x = p.getX() + fx * probe[0] + lx * probe[1];
            double z = p.getZ() + fz * probe[0] + lz * probe[1];
            BlockPos head = BlockPos.containing(x, p.getY() + 1.1, z);
            BlockPos feet = BlockPos.containing(x, p.getY() + 0.1, z);
            if (isObstacle(level, head)) return head;
            if (isObstacle(level, feet)) return feet;
        }
        return null;
    }

    private static boolean isObstacle(Level level, BlockPos pos) {
        return isMineable(level, pos) && !badObstacles.contains(pos.asLong());
    }

    private static boolean isMineable(Level level, BlockPos pos) {
        var bs = level.getBlockState(pos);
        if (bs.isAir()) return false;
        if (bs.getCollisionShape(level, pos).isEmpty()) return false;
        return bs.getDestroySpeed(level, pos) >= 0.0f;
    }

    private static boolean aimAt(Entity p, BlockPos target) {
        return aimAt(p, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
    }

    private static boolean aimAt(Entity p, double x, double y, double z) {
        double dx = x - p.getX();
        double dy = y - p.getEyeY();
        double dz = z - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        // Blickt man fast senkrecht, ist die Drehrichtung egal - sonst dreht man sich im Kreis
        float targetYaw = horiz < 0.1
                ? p.getYRot()
                : (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
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
