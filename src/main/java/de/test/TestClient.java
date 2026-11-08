package de.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
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

    // AutoClicker: schlaegt im Takt des Tool-Cooldowns, mit zufaelligem Abstand
    public static boolean autoClicker = false;
    public static int clickerRandom = 20;        // Prozent des Cooldowns
    public static boolean clickerHold = true;    // nur bei gedrueckter Angriffstaste
    public static boolean clickerEarly = false;  // auch vor vollem Cooldown schlagen
    private static double clickerFactor = 0.0;   // gewuerfelter Faktor fuer den naechsten Schlag
    private static int clickerReadyTicks = 0;
    private static final java.util.Random CLICK_RNG = new java.util.Random();

    // AutoDrop: gewaehlte Items werden automatisch aus dem Inventar gedroppt
    public static boolean autoDrop = false;
    public static final Set<Item> dropItems = new LinkedHashSet<>();
    private static int dropCooldown = 0;
    private static boolean dropFailedWarned = false;
    private static Method clickMethod = null;
    private static Object throwConstant = null;
    private static boolean clickLookupDone = false;

    // Bereichs-Modus
    public static boolean areaActive = false;

    // Koordinaten-Modus: zu einem festen Punkt laufen (nutzt areaActive als "aktiv"-Flag)
    public static boolean gotoMode = false;
    public static int gx = 0, gy = 64, gz = 0;
    private static final int MAX_GOTO_TICKS = 12000;
    private static final int MAX_GOTO_NO_PROGRESS = 200;

    // Block-Modus: einen Blocktyp waehlen, in der Naehe suchen, hinlaufen und abbauen
    public static boolean blockMode = false;
    public static Block selectedBlock = null;
    private static final int SEARCH_RADIUS = 24;
    private static final int SEARCH_HEIGHT = 12;
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
    // selbst platzierte Bloecke (Bruecken/Saeulen): werden nicht wieder abgebaut
    private static final Set<Long> placed = new HashSet<>();
    private static final int NO_PILLAR = Integer.MIN_VALUE;
    private static final int MAX_PILLAR_TICKS = 200;
    private static final int MAX_STUCK_PILLARS = 3;
    private static int pillarGoalY = NO_PILLAR;
    private static int pillarTicks = 0;
    private static int stuckPillars = 0;
    private static BlockPos pillarBase = null;
    private static boolean noBlocksWarned = false;
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
    public static KeyMapping selectKey;

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

        selectKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.test.select", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        TestConfig.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TestConfig.save());
        ClientTickEvents.END_CLIENT_TICK.register(TestClient::onTick);
    }

    // ---------- AutoClicker ----------

    public static void rollClickerFactor() {
        double p = Math.max(0, Math.min(100, clickerRandom)) / 100.0;
        // spaeter: 0..+p, mit "frueher": -p..+p
        clickerFactor = clickerEarly ? (CLICK_RNG.nextDouble() * 2.0 - 1.0) * p : CLICK_RNG.nextDouble() * p;
    }

    private static void runAutoClicker(Minecraft mc) {
        if (!autoClicker || areaActive) return;
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        if (mc.gui.screen() != null || mc.player.isSpectator() || mc.player.isUsingItem()) return;
        if (clickerHold && !mc.options.keyAttack.isDown()) return;

        // Nur Lebewesen im Fadenkreuz (keine Rahmen, Boote usw.)
        if (!(mc.hitResult instanceof EntityHitResult ehr) || ehr.getType() != HitResult.Type.ENTITY) return;
        Entity target = ehr.getEntity();
        if (!(target instanceof LivingEntity) || !target.isAlive()) return;

        float scale = mc.player.getAttackStrengthScale(0.0f);
        if (clickerFactor < 0) {
            // frueher schlagen: schon bei z.B. 85 % Cooldown
            if (scale < 1.0f + clickerFactor) return;
        } else {
            // voller Cooldown, danach noch eine zufaellige Wartezeit (Anteil der Cooldown-Dauer)
            if (scale < 1.0f) {
                clickerReadyTicks = 0;
                return;
            }
            float delay = mc.player.getCurrentItemAttackStrengthDelay();
            if (clickerReadyTicks++ < delay * clickerFactor) return;
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        clickerReadyTicks = 0;
        rollClickerFactor();
    }

    // ---------- AutoDrop ----------

    /**
     * Sucht die Methode zum Inventar-Klick per Signatur (int, int, int, Enum, Player) und die
     * Konstante THROW. Per Reflection, weil Mojang die Namen zwischen Versionen aendert
     * (ClickType -> ContainerInput).
     */
    private static boolean initClick() {
        if (clickLookupDone) return clickMethod != null;
        clickLookupDone = true;
        for (Method m : MultiPlayerGameMode.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 5 || p[0] != int.class || p[1] != int.class || p[2] != int.class) continue;
            if (!p[3].isEnum() || !p[4].isAssignableFrom(net.minecraft.client.player.LocalPlayer.class)) continue;
            for (Object c : p[3].getEnumConstants()) {
                if (((Enum<?>) c).name().equals("THROW")) {
                    clickMethod = m;
                    throwConstant = c;
                    return true;
                }
            }
        }
        return false;
    }

    /** Wirft den ganzen Stapel im angegebenen Slot des Spieler-Inventars (Menue-Slot-Nummer) aus. */
    private static boolean dropMenuSlot(Minecraft mc, int menuSlot) {
        if (!initClick()) return false;
        try {
            clickMethod.invoke(mc.gameMode, 0, menuSlot, 1, throwConstant, mc.player);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.println("[1912] AutoDrop fehlgeschlagen: " + e);
            clickMethod = null;
            return false;
        }
    }

    private static void runAutoDrop(Minecraft mc) {
        if (!autoDrop || dropItems.isEmpty()) return;
        if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (dropCooldown > 0) {
            dropCooldown--;
            return;
        }
        var inv = mc.player.getInventory();
        // Inventar-Index 0-8 = Hotbar (Menue-Slot 36-44), 9-35 = Hauptinventar (Menue-Slot 9-35)
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !dropItems.contains(st.getItem())) continue;
            if (dropMenuSlot(mc, i < 9 ? 36 + i : i)) {
                dropCooldown = 2;
            } else {
                autoDrop = false;
                if (!dropFailedWarned) {
                    dropFailedWarned = true;
                    mc.player.sendSystemMessage(Component.literal(
                            "1912: AutoDrop konnte nicht ausgef\u00fchrt werden und wurde ausgeschaltet"));
                }
            }
            return;
        }
    }

    // ---------- Bereichs-Modus: Start / Stop ----------

    public static String startArea(Minecraft mc) {
        if (mc.player == null || mc.level == null) return "Nicht im Spiel";
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
        blockMode = false;
        gotoMode = false;
        skipped.clear();
        hopeless.clear();
        badObstacles.clear();
        placed.clear();
        noBlocksWarned = false;
        retried = false;
        setTarget(null);
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        walking = false;
        phase = Phase.WALK;
        applied = true;
        areaActive = true;
        return null;
    }

    /** Startet den Koordinaten-Modus. Gibt eine Fehlermeldung zurueck oder null bei Erfolg. */
    public static String startGoto(Minecraft mc) {
        if (mc.player == null || mc.level == null) return "Nicht im Spiel";
        TestConfig.save();
        blockMode = false;
        gotoMode = true;
        skipped.clear();
        hopeless.clear();
        badObstacles.clear();
        placed.clear();
        noBlocksWarned = false;
        retried = false;
        setTarget(new BlockPos(gx, gy, gz));
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        walking = false;
        phase = Phase.WALK;
        applied = true;
        areaActive = true;
        return null;
    }

    /** Waehlt den Block, auf den man gerade schaut. */
    public static boolean selectLookedAt(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            mc.player.sendSystemMessage(Component.literal("1912: Kein Block anvisiert"));
            return false;
        }
        selectedBlock = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        TestConfig.save();
        mc.player.sendSystemMessage(Component.literal("1912: Block gew\u00e4hlt: ")
                .append(selectedBlock.getName()));
        return true;
    }

    public static String selectedBlockName() {
        return selectedBlock == null ? "keiner" : selectedBlock.getName().getString();
    }

    /** Startet den Block-Modus. Gibt eine Fehlermeldung zurueck oder null bei Erfolg. */
    public static String startBlock(Minecraft mc) {
        if (selectedBlock == null) return "Erst einen Block w\u00e4hlen";
        if (mc.player == null || mc.level == null) return "Nicht im Spiel";
        skipped.clear();
        hopeless.clear();
        badObstacles.clear();
        placed.clear();
        noBlocksWarned = false;
        retried = false;
        setTarget(null);
        walking = false;
        gotoMode = false;
        blockMode = true;
        updateBounds(mc.player);
        if (nearestOverall(mc.level, mc.player) == null) {
            blockMode = false;
            String msg = selectedBlockName() + " nicht in der N\u00e4he gefunden";
            mc.player.sendSystemMessage(Component.literal("1912: " + msg));
            return msg;
        }
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        phase = Phase.WALK;
        applied = true;
        areaActive = true;
        return null;
    }

    /** Suchbereich folgt dem Spieler. */
    private static void updateBounds(Entity p) {
        BlockPos c = p.blockPosition();
        minX = c.getX() - SEARCH_RADIUS;
        maxX = c.getX() + SEARCH_RADIUS;
        minY = c.getY() - SEARCH_HEIGHT;
        maxY = c.getY() + SEARCH_HEIGHT;
        minZ = c.getZ() - SEARCH_RADIUS;
        maxZ = c.getZ() + SEARCH_RADIUS;
    }

    /** Abbaubar und (im Block-Modus) vom gewaehlten Typ. */
    private static boolean isWanted(Level level, BlockPos pos) {
        if (placed.contains(pos.asLong())) return false;
        if (!isMineable(level, pos)) return false;
        return !blockMode || level.getBlockState(pos).is(selectedBlock);
    }

    public static void stopArea(Minecraft mc, String message) {
        areaActive = false;
        blockMode = false;
        gotoMode = false;
        setTarget(null);
        releaseAll(mc);
        applied = false;
        if (mc.player != null && message != null) {
            mc.player.sendSystemMessage(Component.literal("1912: " + message));
        }
    }

    // ---------- Tick ----------

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            if (areaActive) {
                stopArea(mc, gotoMode ? "Gehen abgebrochen" : blockMode ? "Abbau gestoppt" : "Bereich abgebrochen");
            } else {
                walking = !walking;
                if (mc.player != null) {
                    mc.player.sendSystemMessage(
                            Component.literal("1912: " + (walking ? "AN" : "AUS")));
                }
            }
        }
        while (selectKey.consumeClick()) {
            selectLookedAt(mc);
        }
        while (guiKey.consumeClick()) {
            mc.gui.setScreen(new TestScreen(mc.gui.screen()));
        }

        Entity player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            walking = false;
            areaActive = false;
            blockMode = false;
            gotoMode = false;
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
            blockMode = false;
            gotoMode = false;
            phase = Phase.WALK;
            applied = false;
            releaseAll(mc);
            var connection = mc.getConnection();
            if (connection != null) {
                connection.getConnection().disconnect(
                        Component.literal("1912: Auto-Logout bei 2 Herzen"));
            }
            return;
        }

        runAutoDrop(mc);
        runAutoClicker(mc);

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
        pillarGoalY = NO_PILLAR;
        pillarTicks = 0;
        stuckPillars = 0;
        pillarBase = null;
    }

    private static void skipTarget(Minecraft mc) {
        if (areaTarget != null) skipped.add(areaTarget.asLong());
        setTarget(null);
        releaseAll(mc);
    }

    private static String finishMessage(Level level) {
        Set<Long> left = new HashSet<>();
        for (long l : skipped) {
            if (isWanted(level, BlockPos.of(l))) left.add(l);
        }
        for (long l : hopeless) {
            if (isWanted(level, BlockPos.of(l))) left.add(l);
        }
        if (blockMode) {
            String name = selectedBlockName();
            if (left.isEmpty()) return "Kein " + name + " mehr in der N\u00e4he";
            return "Kein erreichbarer " + name + " mehr in der N\u00e4he (" + left.size() + " nicht erreichbar)";
        }
        if (left.isEmpty()) return "Bereich fertig";
        return "Bereich fertig (" + left.size() + " Bl\u00f6cke nicht erreichbar)";
    }

    private static void runGoto(Minecraft mc, Level level, Entity player) {
        applied = true;
        if (mc.gui.screen() != null) {
            releaseAll(mc);
            return;
        }
        double moved = Math.abs(player.getX() - lastX) + Math.abs(player.getZ() - lastZ);
        lastX = player.getX();
        lastZ = player.getZ();

        double dx = gx + 0.5 - player.getX();
        double dz = gz + 0.5 - player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        int footY = player.blockPosition().getY();

        if (horiz < 1.2 && Math.abs(footY - gy) <= 1) {
            stopArea(mc, "Ziel erreicht");
            return;
        }
        if (++walkTicks > MAX_GOTO_TICKS) {
            stopArea(mc, "Ziel nicht erreichbar (Zeit abgelaufen)");
            return;
        }

        // Gerade dabei, nach oben zu bauen
        if (pillarGoalY != NO_PILLAR) {
            if (!pillarStep(mc, level, player)) {
                stopArea(mc, "Ziel nicht erreichbar (Hochbauen fehlgeschlagen)");
            }
            return;
        }

        // Direkt ueber/unter dem Ziel: senkrecht handeln
        if (horiz < ARRIVE_DIST) {
            // digBlock/pillarStep setzen den Zaehler zurueck; nur Leerlauf (z.B. auf Bedrock) zaehlt hoch
            if (++noProgressTicks > MAX_GOTO_NO_PROGRESS) {
                stopArea(mc, "Ziel nicht erreichbar (kein Fortschritt)");
                return;
            }
            if (gy < footY) {
                BlockPos under = player.blockPosition().below();
                if (isMineable(level, under) && !badObstacles.contains(under.asLong())) {
                    if (!safeToDigDown(level, under) || !digBlock(mc, player, under)) {
                        stopArea(mc, "Ziel nicht erreichbar (nach unten graben nicht m\u00f6glich)");
                    }
                } else {
                    releaseAll(mc);
                }
            } else if (findBuildSlot(mc) >= 0) {
                pillarGoalY = gy;
                pillarTicks = 0;
            } else {
                stopArea(mc, "Ziel liegt h\u00f6her, aber keine Baubl\u00f6cke in der Hotbar");
            }
            return;
        }

        // Kommt das Ziel naeher? Sonst aufgeben.
        if (horiz < bestDist - 0.25) {
            bestDist = horiz;
            noProgressTicks = 0;
            stuckPillars = 0;
        } else if (++noProgressTicks > MAX_GOTO_NO_PROGRESS) {
            stopArea(mc, "Ziel nicht erreichbar (kein Fortschritt)");
            return;
        }

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        BlockPos obstacle = autoMine ? findObstacle(level, player, targetYaw) : null;
        boolean stepUp = false;
        if (obstacle != null && canStepUp(level, player, targetYaw)) {
            obstacle = null;
            stepUp = true;
        }
        if (obstacle != null) {
            digBlock(mc, player, obstacle);
            return;
        }
        digPos = null;
        mc.options.keyAttack.setDown(false);

        if (!rotateTo(player, targetYaw, 10.0f, 15.0f)) {
            stopMove(mc);
            return;
        }

        if (bridgeAhead(mc, level, player, targetYaw)) {
            return;
        }

        if (moved < 0.02) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks > MAX_STUCK_TICKS) {
            if (stuckPillars < MAX_STUCK_PILLARS && gy >= footY - 1 && findBuildSlot(mc) >= 0) {
                stuckPillars++;
                pillarGoalY = footY + 2;
                pillarTicks = 0;
                stuckTicks = 0;
                return;
            }
            stopArea(mc, "Ziel nicht erreichbar (h\u00e4ngt fest)");
            return;
        }
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(stuckTicks > 8 || stepUp);
        mc.options.keySprint.setDown(sprint && stuckTicks == 0);
    }

    private static void runArea(Minecraft mc, Level level, Entity player) {
        if (gotoMode) {
            runGoto(mc, level, player);
            return;
        }
        applied = true;
        if (mc.gui.screen() != null) {
            releaseAll(mc);
            return;
        }
        if (blockMode) updateBounds(player);

        // Bewegung seit dem letzten Tick (fuer die Haengen-Erkennung)
        double moved = Math.abs(player.getX() - lastX) + Math.abs(player.getZ() - lastZ);
        lastX = player.getX();
        lastZ = player.getZ();

        if (areaTarget != null && !isWanted(level, areaTarget)) {
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

        // Gerade dabei, nach oben zu bauen
        if (pillarGoalY != NO_PILLAR) {
            if (!pillarStep(mc, level, player)) {
                skipTarget(mc);
            }
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
                // Ziel liegt darueber: hochbauen, wenn Baubloecke da sind
                if (areaTarget.getY() > player.blockPosition().getY() && findBuildSlot(mc) >= 0) {
                    pillarGoalY = areaTarget.getY();
                    pillarTicks = 0;
                } else {
                    skipTarget(mc);
                }
            }
            return;
        }

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        // 4b. Hindernisse auf dem Weg abbauen. Die Suche richtet sich nach der Laufrichtung
        //     (nicht nach der aktuellen Blickrichtung), sonst arbeiten Zielen und Drehen gegeneinander.
        BlockPos obstacle = findObstacle(level, player, targetYaw);
        boolean stepUp = false;
        if (obstacle != null && canStepUp(level, player, targetYaw)) {
            // Stufe von einem Block Hoehe: einfach hochspringen statt abbauen
            obstacle = null;
            stepUp = true;
        }
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

        // Luecke vor uns: Bruecke bauen
        if (bridgeAhead(mc, level, player, targetYaw)) {
            return;
        }

        if (moved < 0.02) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks > MAX_STUCK_TICKS) {
            // Haengt an einer Wand: zwei Bloecke hochbauen und weiter
            if (stuckPillars < MAX_STUCK_PILLARS
                    && areaTarget.getY() >= player.blockPosition().getY() - 1
                    && findBuildSlot(mc) >= 0) {
                stuckPillars++;
                pillarGoalY = player.blockPosition().getY() + 2;
                pillarTicks = 0;
                stuckTicks = 0;
                return;
            }
            skipTarget(mc);
            return;
        }
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(stuckTicks > 8 || stepUp);
        mc.options.keySprint.setDown(false);
    }

    // ---------- Bauen (Bruecken und Saeulen) ----------

    private static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Nur guenstige Bloecke zum Bauen verwenden, keine wertvollen. */
    private static boolean isBuildBlock(Block b) {
        return b == Blocks.COBBLESTONE || b == Blocks.DIRT || b == Blocks.NETHERRACK
                || b == Blocks.COBBLED_DEEPSLATE || b == Blocks.STONE || b == Blocks.ANDESITE
                || b == Blocks.DIORITE || b == Blocks.GRANITE || b == Blocks.TUFF
                || b == Blocks.DEEPSLATE || b == Blocks.BLACKSTONE;
    }

    /** Hotbar-Slot mit einem Baublock oder -1. */
    private static int findBuildSlot(Minecraft mc) {
        if (mc.player == null) return -1;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.getItem() instanceof BlockItem bi && isBuildBlock(bi.getBlock())) {
                return i;
            }
        }
        return -1;
    }

    /** Platziert einen Baublock an placePos, indem gegen 'against' geklickt wird. */
    private static boolean placeBlock(Minecraft mc, BlockPos against, Direction face, Vec3 hitVec, BlockPos placePos) {
        if (mc.player == null || mc.gameMode == null) return false;
        int slot = findBuildSlot(mc);
        if (slot < 0) {
            if (!noBlocksWarned) {
                noBlocksWarned = true;
                mc.player.sendSystemMessage(Component.literal(
                        "1912: Keine Baubl\u00f6cke (z.B. Bruchstein) in der Hotbar"));
            }
            return false;
        }
        var inv = mc.player.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        BlockHitResult hit = new BlockHitResult(hitVec, face, against, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        inv.setSelectedSlot(prev);
        placed.add(placePos.asLong());
        return true;
    }

    /** Ein Block hohe Stufe vor uns, darueber frei: springen reicht. */
    private static boolean canStepUp(Level level, Entity p, float yaw) {
        if (!p.onGround()) return false;
        double rad = Math.toRadians(yaw);
        double x = p.getX() - Math.sin(rad) * 0.9;
        double z = p.getZ() + Math.cos(rad) * 0.9;
        return solid(level, BlockPos.containing(x, p.getY() + 0.1, z))
                && !solid(level, BlockPos.containing(x, p.getY() + 1.1, z))
                && !solid(level, BlockPos.containing(x, p.getY() + 2.1, z));
    }

    /** Tiefe Luecke direkt vor uns (und das Ziel nicht tiefer): Block davor platzieren. */
    private static boolean bridgeAhead(Minecraft mc, Level level, Entity p, float yaw) {
        if (!p.onGround() || areaTarget == null) return false;
        if (areaTarget.getY() < p.blockPosition().getY() - 1) return false;
        BlockPos below = p.blockPosition().below();
        if (!solid(level, below)) return false;
        Direction d = Direction.fromYRot(yaw);
        BlockPos ahead = below.relative(d);
        if (solid(level, ahead) || solid(level, ahead.below()) || solid(level, ahead.below(2))) {
            return false;
        }
        stopMove(mc);
        stuckTicks = 0;
        noProgressTicks = 0;
        Vec3 face = Vec3.atCenterOf(below).add(d.getStepX() * 0.5, 0.0, d.getStepZ() * 0.5);
        return placeBlock(mc, below, d, face, ahead);
    }

    /** Ein Schritt beim Hochbauen: springen und im Sprung den Block darunter setzen. */
    private static boolean pillarStep(Minecraft mc, Level level, Entity p) {
        if (p.onGround() && p.blockPosition().getY() >= pillarGoalY) {
            pillarGoalY = NO_PILLAR;
            pillarBase = null;
            mc.options.keyJump.setDown(false);
            return true;
        }
        if (++pillarTicks > MAX_PILLAR_TICKS) return false;
        stopMove(mc);
        mc.options.keyAttack.setDown(false);
        stuckTicks = 0;
        noProgressTicks = 0;

        if (p.onGround()) {
            BlockPos ceiling = p.blockPosition().above(2);
            if (isMineable(level, ceiling) && !placed.contains(ceiling.asLong())) {
                // Decke im Weg: erst abbauen
                return digBlock(mc, p, ceiling);
            }
            pillarBase = p.blockPosition().below();
            if (!solid(level, pillarBase)) {
                pillarBase = null;
                return true;
            }
            mc.options.keyJump.setDown(true);
            return true;
        }

        // in der Luft: sobald hoch genug, den Block auf den Untergrund setzen
        if (pillarBase != null && p.getY() >= pillarBase.getY() + 2.05) {
            BlockPos placePos = pillarBase.above();
            if (level.getBlockState(placePos).isAir()) {
                Vec3 face = Vec3.atCenterOf(pillarBase).add(0.0, 0.5, 0.0);
                if (!placeBlock(mc, pillarBase, Direction.UP, face, placePos)) {
                    return false;
                }
            }
            pillarBase = null;
            mc.options.keyJump.setDown(false);
        }
        return true;
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
                    if (!isWanted(level, pos)) continue;
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
                    if (!isWanted(level, pos)) continue;
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
        return isMineable(level, pos) && !badObstacles.contains(pos.asLong())
                && !placed.contains(pos.asLong());
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
