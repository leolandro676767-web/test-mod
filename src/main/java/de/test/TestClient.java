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
    public static int logoutHearts = 2;          // Auto-Logout bei so vielen Herzen (oder weniger)

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

    /** Aktuelles Abbau-/Laufziel (fuer die Anzeige), sonst null. */
    public static BlockPos miningTarget() {
        if (!areaActive) return null;
        return gotoMode ? new BlockPos(gx, gy, gz) : areaTarget;
    }

    /** Grenzen des aktiven Bereichs {minX, minY, minZ, maxX, maxY, maxZ}, sonst null. */
    public static int[] miningBounds() {
        if (!areaActive || gotoMode) return null;
        return new int[] {minX, minY, minZ, maxX, maxY, maxZ};
    }
    public static int gx = 0, gy = 64, gz = 0;

    // Block-Modus: einen Blocktyp waehlen, in der Naehe suchen, hinlaufen und abbauen
    public static boolean blockMode = false;
    public static Block selectedBlock = null;
    public static int searchChunks = 6;   // Block-Modus: Suchradius waagerecht (in Chunks)
    public static int searchHeight = 12;  // Block-Modus: Suchradius vertikal
    public static int ax1 = 0, ay1 = 0, az1 = 0, ax2 = 0, ay2 = 0, az2 = 0;
    private static final long MAX_AREA_VOLUME = 50000L;
    private static int minX, minY, minZ, maxX, maxY, maxZ;
    private static BlockPos areaTarget = null;
    /** Ziel, zu dem der Walker gerade laeuft (Bereich/Block-Modus). */
    private static BlockPos walkTarget = null;
    private static int mineTicks = 0;
    private static boolean retried = false;
    // selbst platzierte Bloecke (Bruecken/Saeulen): werden nicht wieder abgebaut
    private static final Set<Long> placed = new HashSet<>();

    /** Wurde der Block (BlockPos.asLong) von uns selbst gesetzt? */
    static boolean isPlaced(long pos) {
        return placed.contains(pos);
    }

    /** Eigene gesetzte Bloecke vergessen: die Wegsuche darf sie dann wieder abbauen (Debris Miner). */
    static void forgetPlaced() {
        placed.clear();
    }
    private static boolean noBlocksWarned = false;
    private static double[] reachPoint = null;
    // nicht erreichbar (nur fuer die Wegsuche gesperrt, werden aber mitgenommen, wenn sie spaeter in Reichweite sind)
    private static final Set<Long> skipped = new HashSet<>();
    // lassen sich nicht abbauen (ueberall gesperrt)
    private static final Set<Long> hopeless = new HashSet<>();
    // Hindernisse auf dem Weg, die sich nicht abbauen lassen
    private static final Set<Long> badObstacles = new HashSet<>();

    // Verhindert Logout-Schleife: erst wieder aktiv, wenn die Gesundheit
    // nach einem Auto-Logout ueber der Schwelle war.
    private static boolean logoutArmed = true;

    /** Die einzige Taste in den Minecraft-Steuerungen: oeffnet das 1912-Menue. Alles andere wird im Menue zugewiesen. */
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

        // Nur das Menue steht in den Minecraft-Steuerungen. Laufen/Stopp (G), Block waehlen (K),
        // Alle Module aus/an (J) und alle Module bekommen ihre Tasten im 1912-Menue ("Tasten zuweisen").
        guiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.test.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        Esp.register();
        KillAura.register();
        InfoHud.register();
        Keybinds.register();

        TestConfig.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TestConfig.save());
        PlayerMods.register();
        RenderMods.register();
        MiscMods.register();
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            Safe.run("Bewegung", () -> Movement2.startTick(mc));
            Safe.run("Bewegung", () -> Movement.startTick(mc));
            Safe.run("Spieler", () -> PlayerMods.startTick(mc));
            Safe.run("Freecam", () -> CameraMods.startTick(mc));
            Safe.run("Auto Totem", () -> Survival.totemTick(mc)); // vor dem Bewegungs-Paket, wie ein Tastendruck
        });
        ClientTickEvents.END_CLIENT_TICK.register(TestClient::onTick);
        // Free Look erst ganz zum Schluss: alle Module (auch das Laufen) haben die Laufrichtung gesetzt
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("Freecam", () -> CameraMods.afterTick(mc)));
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
        if (Friends.protect(target)) return; // Freunde nie angreifen

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
                    mc.player.sendSystemMessage(Lang.literal(
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
        DebrisMiner.stop(mc, null);
        TunnelMiner.stop(mc, null);
        TestConfig.save();
        Walker.reset();
        walkTarget = null;
        blockMode = false;
        gotoMode = false;
        skipped.clear();
        hopeless.clear();
        badObstacles.clear();
        placed.clear();
        noBlocksWarned = false;
        retried = false;
        setTarget(null);
        walking = false;
        phase = Phase.WALK;
        applied = true;
        areaActive = true;
        return null;
    }

    /** Startet den Koordinaten-Modus. Gibt eine Fehlermeldung zurueck oder null bei Erfolg. */
    public static String startGoto(Minecraft mc) {
        if (mc.player == null || mc.level == null) return "Nicht im Spiel";
        DebrisMiner.stop(mc, null);
        TunnelMiner.stop(mc, null);
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
        walking = false;
        phase = Phase.WALK;
        applied = true;
        areaActive = true;
        Walker.start(gx, gy, gz);
        return null;
    }

    /** Taste "Stopp / Auto Walk" (Standard G): laufendes Gehen/Abbauen stoppen, sonst Auto Walk an/aus. */
    static void stopOrWalk(Minecraft mc) {
        if (DebrisMiner.isRunning()) {
            DebrisMiner.stop(mc, "Debris Miner aus");
            return;
        }
        if (TunnelMiner.isRunning()) {
            TunnelMiner.stop(mc, "Tunnel Miner aus");
            return;
        }
        if (areaActive) {
            stopArea(mc, gotoMode ? "Gehen abgebrochen" : blockMode ? "Abbau gestoppt" : "Bereich abgebrochen");
            return;
        }
        walking = !walking;
        if (mc.player != null) {
            mc.player.sendSystemMessage(Lang.literal("1912: Auto Walk " + Lang.t(walking ? "AN" : "AUS")));
        }
    }

    /** Waehlt den Block, auf den man gerade schaut. */
    public static boolean selectLookedAt(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            mc.player.sendSystemMessage(Lang.literal("1912: Kein Block anvisiert"));
            return false;
        }
        selectedBlock = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        TestConfig.save();
        mc.player.sendSystemMessage(Lang.literal("1912: Block gew\u00e4hlt: ")
                .append(selectedBlock.getName()));
        return true;
    }

    public static String selectedBlockName() {
        return selectedBlock == null ? Lang.t("keiner") : selectedBlock.getName().getString();
    }

    /** Startet den Block-Modus. Gibt eine Fehlermeldung zurueck oder null bei Erfolg. */
    public static String startBlock(Minecraft mc) {
        if (selectedBlock == null) return "Erst einen Block w\u00e4hlen";
        if (mc.player == null || mc.level == null) return "Nicht im Spiel";
        DebrisMiner.stop(mc, null);
        TunnelMiner.stop(mc, null);
        Walker.reset();
        walkTarget = null;
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
            mc.player.sendSystemMessage(Lang.literal("1912: " + msg));
            return msg;
        }
        phase = Phase.WALK;
        applied = true;
        areaActive = true;
        return null;
    }

    /** Suchbereich folgt dem Spieler. */
    private static void updateBounds(Entity p) {
        BlockPos c = p.blockPosition();
        int searchRadius = searchChunks * 16;
        minX = c.getX() - searchRadius;
        maxX = c.getX() + searchRadius;
        minY = c.getY() - searchHeight;
        maxY = c.getY() + searchHeight;
        minZ = c.getZ() - searchRadius;
        maxZ = c.getZ() + searchRadius;
    }

    /** Abbaubar und (im Block-Modus) vom gewaehlten Typ. */
    private static boolean isWanted(Level level, BlockPos pos) {
        if (placed.contains(pos.asLong())) return false;
        if (!isMineable(level, pos)) return false;
        return !blockMode || level.getBlockState(pos).is(selectedBlock);
    }

    public static void stopArea(Minecraft mc, String message) {
        Walker.stop(mc);
        areaActive = false;
        blockMode = false;
        gotoMode = false;
        setTarget(null);
        releaseAll(mc);
        applied = false;
        if (mc.player != null && message != null) {
            mc.player.sendSystemMessage(Lang.literal("1912: " + Lang.t(message)));
        }
    }

    // ---------- Tick ----------

    private static void onTick(Minecraft mc) {
        Safe.run("Men\u00fc-Gr\u00f6\u00dfe", () -> GuiScale.check(mc));
        Safe.run("Tasten", () -> Keybinds.tick(mc));
        Safe.run("ESP", () -> Esp.tick(mc));
        Safe.run("Freunde", () -> Friends.tick(mc));
        while (guiKey.consumeClick()) {
            mc.gui.setScreen(new TestScreen(mc.gui.screen()));
        }

        Entity player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            DebrisMiner.reset();
            TunnelMiner.reset();
            BotGuard.reset();
            KillAura.reset();
            Safe.run("Misc", () -> MiscMods.tickOffline(mc));
            Walker.reset();
            walking = false;
            areaActive = false;
            blockMode = false;
            gotoMode = false;
            phase = Phase.WALK;
            applied = false;
            return;
        }

        float health = mc.player.getHealth();
        float logoutHealth = Math.max(1, Math.min(10, logoutHearts)) * 2.0f;
        if (health > logoutHealth) {
            logoutArmed = true;
        }
        if (autoLogout && logoutArmed && health > 0.0f && health <= logoutHealth) {
            TunnelMiner.stop(mc, null);
            DebrisMiner.stop(mc, null);
            logoutArmed = false;
            Walker.stop(mc);
            walking = false;
            areaActive = false;
            blockMode = false;
            gotoMode = false;
            phase = Phase.WALK;
            applied = false;
            releaseAll(mc);
            var connection = mc.getConnection();
            if (connection != null) {
                MiscMods.holdReconnect = true; // nicht gleich wieder verbinden
                connection.getConnection().disconnect(
                        Lang.literal("1912: " + Lang.t("Auto-Logout bei ") + Math.max(1, Math.min(10, logoutHearts))
                                + Lang.t(" Herzen")));
            }
            return;
        }

        runAutoDrop(mc);
        runAutoClicker(mc);
        Safe.run("Bot-Verteidigung", () -> BotGuard.tick(mc));
        Safe.run("Auto Armor", () -> Survival.tick(mc));
        Safe.run("Info-Anzeige", () -> InfoHud.tick(mc));
        Safe.run("Kill Aura", () -> KillAura.tick(mc));
        Safe.run("Bewegung", () -> Movement.endTick(mc));
        Safe.run("Bewegung", () -> Movement2.endTick(mc));
        Safe.run("Spieler", () -> PlayerMods.endTick(mc));
        Safe.run("Render", () -> RenderMods.tick(mc));
        Safe.run("Misc", () -> MiscMods.tick(mc));
        Safe.run("Inventar", () -> InvLayout.tick(mc));
        Safe.run("Freecam", () -> CameraMods.endTick(mc));

        Safe.run("Debris Miner", () -> DebrisMiner.tickCommands(mc));
        if (DebrisMiner.isRunning()) {
            Safe.run("Debris Miner", () -> DebrisMiner.tick(mc));
            return;
        }
        if (TunnelMiner.isRunning()) {
            Safe.run("Tunnel Miner", () -> TunnelMiner.tick(mc));
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
        if (BotGuard.fighting()) {
            releaseAll(mc); // die Bot-Verteidigung kaempft: kurz stehen bleiben
            return;
        }

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
    private static final int MAX_MINE_TICKS = 600;

    private static final int[][] DIRS = {
            {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final double[][] FACE_POINTS = {
            {0.5, 0.95, 0.5}, {0.5, 0.05, 0.5}, {0.95, 0.5, 0.5},
            {0.05, 0.5, 0.5}, {0.5, 0.5, 0.95}, {0.5, 0.5, 0.05}};
    private static void setTarget(BlockPos pos) {
        areaTarget = pos;
        mineTicks = 0;
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

    /** Koordinaten-Modus: Wegsuche und Ablaufen uebernimmt {@link Walker} (wie Baritone). */
    private static void runGoto(Minecraft mc, Level level, Entity player) {
        applied = true;
        if (mc.gui.screen() != null || mc.player == null || BotGuard.fighting()) {
            releaseAll(mc); // Menue offen oder die Bot-Verteidigung kaempft: stehen bleiben
            Walker.release(mc);
            return;
        }
        String done;
        try {
            done = Walker.tick(mc, level, mc.player);
        } catch (RuntimeException | LinkageError e) {
            Safe.report("Gehen", e);
            done = "Gehen abgebrochen (Fehler)";
        }
        if (done != null) stopArea(mc, done);
    }

    /**
     * Bereich abbauen / Bloecke suchen: was in Reichweite und sichtbar ist, wird abgebaut. Sonst laeuft
     * der Spieler mit der Wegsuche ({@link Walker}, wie bei "Zu Koordinaten gehen") zum naechsten Block -
     * springt, schwimmt, baut Bruecken und graebt sich nur durch, wo es noetig ist.
     */
    private static void runArea(Minecraft mc, Level level, Entity player) {
        if (gotoMode) {
            runGoto(mc, level, player);
            return;
        }
        applied = true;
        if (mc.gui.screen() != null || mc.player == null || BotGuard.fighting()) {
            releaseAll(mc); // Menue offen oder die Bot-Verteidigung kaempft: stehen bleiben
            Walker.release(mc);
            return;
        }
        if (blockMode) updateBounds(player);

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

        // Mitten im Springen, Hochbauen oder Brueckenbauen nicht unterbrechen
        if (point != null && Walker.isActive() && Walker.inCriticalMove(mc.player)) {
            walkOn(mc, level);
            return;
        }

        // 2. Abbauen
        if (point != null) {
            if (Walker.isActive()) {
                Walker.stop(mc);
                walkTarget = null;
            }
            stopMove(mc);
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
        mc.options.keyAttack.setDown(false);

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

        // 4. Hinlaufen mit der Wegsuche
        if (!Walker.isActive() || !areaTarget.equals(walkTarget)) {
            Walker.start(areaTarget.getX(), areaTarget.getY(), areaTarget.getZ(), true);
            walkTarget = areaTarget;
        }
        walkOn(mc, level);
    }

    /** Einen Tick mit dem Walker laufen; klappt es nicht (kein Weg, angekommen und nichts zu sehen): naechster Block. */
    private static void walkOn(Minecraft mc, Level level) {
        String done;
        try {
            done = Walker.tick(mc, level, mc.player);
        } catch (RuntimeException | LinkageError e) {
            Safe.report("Laufen", e);
            done = "Fehler";
        }
        if (done != null) {
            Walker.stop(mc);
            walkTarget = null;
            // angekommen und der Block ist zu sehen: im naechsten Tick wird er abgebaut
            boolean arrived = Walker.ARRIVED.equals(done) && areaTarget != null
                    && visiblePoint(level, mc.player, areaTarget) != null;
            if (!arrived) skipTarget(mc); // wird spaeter noch einmal versucht, wenn ringsum abgebaut ist
        }
    }

    // ---------- Bauen (Bruecken und Saeulen) ----------

    /** Nur guenstige Bloecke zum Bauen verwenden, keine wertvollen. */
    static boolean isBuildBlock(Block b) {
        return b == Blocks.COBBLESTONE || b == Blocks.DIRT || b == Blocks.NETHERRACK
                || b == Blocks.COBBLED_DEEPSLATE || b == Blocks.STONE || b == Blocks.ANDESITE
                || b == Blocks.DIORITE || b == Blocks.GRANITE || b == Blocks.TUFF
                || b == Blocks.DEEPSLATE || b == Blocks.BLACKSTONE;
    }

    /** Hotbar-Slot mit einem Baublock oder -1. */
    static int findBuildSlot(Minecraft mc) {
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
    static boolean placeBlock(Minecraft mc, BlockPos against, Direction face, Vec3 hitVec, BlockPos placePos) {
        if (mc.player == null || mc.gameMode == null) return false;
        int slot = findBuildSlot(mc);
        if (slot < 0) {
            if (!noBlocksWarned) {
                noBlocksWarned = true;
                mc.player.sendSystemMessage(Lang.literal(
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
                    if (Look.eyeDist2(p, pos) > CULL_SQ) continue;
                    candidates.add(pos);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> Look.eyeDist2(p, pos)));
        for (BlockPos pos : candidates) {
            double[] pt = visiblePoint(level, p, pos);
            if (pt != null) {
                reachPoint = pt;
                return pos;
            }
        }
        return null;
    }

    /**
     * Naechster abbaubarer Block im ganzen Bereich (zum Hinlaufen). Gesucht wird in Ringen von innen nach
     * aussen; sobald ein Ring weiter weg ist als der beste Fund, ist Schluss - so bleibt das auch bei einem
     * Radius von mehreren Chunks schnell.
     */
    private static BlockPos nearestOverall(Level level, Entity p) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos c = p.blockPosition();
        int cx = Math.max(minX, Math.min(maxX, c.getX()));
        int cz = Math.max(minZ, Math.min(maxZ, c.getZ()));
        int off = Math.max(Math.abs(c.getX() - cx), Math.abs(c.getZ() - cz)); // Spieler ausserhalb des Bereichs
        int maxD = Math.max(Math.max(cx - minX, maxX - cx), Math.max(cz - minZ, maxZ - cz));
        for (int d = 0; d <= maxD; d++) {
            // alles in diesem Ring ist mindestens (d - off - 0,5) Bloecke weit weg
            if (best != null && d - off - 0.5 > bestScore) break;
            for (int x = cx - d; x <= cx + d; x++) {
                if (x < minX || x > maxX) continue;
                boolean edge = x == cx - d || x == cx + d;
                int step = edge ? 1 : Math.max(1, 2 * d);
                for (int z = cz - d; z <= cz + d; z += step) {
                    if (z < minZ || z > maxZ) continue;
                    for (int y = minY; y <= maxY; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        double score = Math.sqrt(Look.eyeDist2(p, pos));
                        if (score >= bestScore) continue;
                        long key = pos.asLong();
                        if (skipped.contains(key) || hopeless.contains(key)) continue;
                        if (!isWanted(level, pos)) continue;
                        // freiliegende Bloecke bevorzugen: verschuettete sind erst nach den Nachbarn dran
                        if (!exposed(level, pos)) score += 3.0;
                        if (score < bestScore) {
                            bestScore = score;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Hat der Block mindestens eine freie Seite? */
    private static boolean exposed(Level level, BlockPos b) {
        for (int[] d : DIRS) {
            BlockPos n = b.offset(d[0], d[1], d[2]);
            if (level.getBlockState(n).getCollisionShape(level, n).isEmpty()) return true;
        }
        return false;
    }

    /**
     * Sucht einen Punkt auf dem Block, der in Reichweite liegt und auf den man direkt sehen kann.
     * Es wird nicht nur die Mitte geprueft, sondern auch die freiliegenden Seitenflaechen -
     * so sind auch Bloecke erreichbar, die nur teilweise sichtbar sind.
     */
    static double[] visiblePoint(Level level, Entity p, BlockPos b) {
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
        return rotateTo(p, targetYaw, targetPitch, tolerance, 12.0f, 8.0f, 0.3f);
    }

    private static boolean rotateTo(Entity p, float targetYaw, float targetPitch, float tolerance,
                                    float maxYawStep, float maxPitchStep, float factor) {
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        float dy = Mth.wrapDegrees(targetYaw - yaw);
        float dp = targetPitch - pitch;
        yaw += Math.abs(dy) < 1.0f ? dy : Mth.clamp(dy * factor, -maxYawStep, maxYawStep);
        pitch += Math.abs(dp) < 1.0f ? dp : Mth.clamp(dp * factor, -maxPitchStep, maxPitchStep);
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(pitch, -90.0f, 90.0f));
        return Math.abs(dy) < tolerance && Math.abs(dp) < tolerance;
    }
}
