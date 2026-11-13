package de.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * Hauptmenue im ClickGUI-Stil: Kategorien als Spalten, darunter die Module; oben eine Suche.
 * Jede Spalte ist so breit wie ihr laengster Eintrag - kein Name wird gekuerzt.
 */
public class TestScreen extends Screen {
    private static final int GAP = 6;
    private static final int HEADER_H = Ui.HEADER_H;
    private static final int ENTRY_H = 18;
    private static final int DOTS_W = 9;     // Klickbereich rechts fuer die Einstellungen (drei Punkte)
    private static final int SCROLL_W = 5;   // Platz fuer den schmalen Scrollbalken
    private static final int SEARCH_H = 16;
    private static final int FOOTER_H = 14;
    private static final int R = Ui.RADIUS;  // Eckenradius

    private static final int PANEL = Ui.PANEL;
    private static final int TEXT = Ui.TEXT;
    private static final int TEXT_ON = Ui.TEXT_ON;
    private static final int MUTED = Ui.MUTED;

    private static final class Entry {
        final String name;
        final BooleanSupplier state; // null = reiner Button ohne An/Aus
        final Runnable action;
        Supplier<Screen> settings; // null = keine Einstellungen
        int x, y;
        boolean last;
        float hover = 0f;
        float anim = 0f;

        Entry(String name, BooleanSupplier state, Runnable action) {
            this.name = name;
            this.state = state;
            this.action = action;
        }
    }

    private static final class Column {
        final String title;
        final List<Entry> entries = new ArrayList<>();
        final List<Entry> matches = new ArrayList<>(); // passend zur Suche
        final List<Entry> visible = new ArrayList<>(); // gerade sichtbare Zeilen
        int index;
        int x, y, w;
        float t0, t1;    // Ausschnitt des Farbverlaufs fuer die Kopfzeile
        boolean scrollable;
        int scroll;
        float open = 1f; // 0 = eingeklappt, 1 = ausgeklappt (animiert)
        float headHover = 0f;
        float laneHover = 0f;

        Column(String title) {
            this.title = title;
        }
    }

    /** Scroll-Position je Spalte (bleibt beim Schliessen erhalten). */
    private static final java.util.Map<String, Integer> SCROLL = new java.util.HashMap<>();

    private final Screen parent;
    private final List<Column> columns = new ArrayList<>();
    private final List<Column> shown = new ArrayList<>();
    private int fitRows = 10;
    private final long openedAt = System.currentTimeMillis();
    private int logoScale = 2;
    private int logoY = 0;
    private int gridY = 0;
    private int searchY = 0;

    private EditBox searchBox;
    private String query = "";
    private boolean pendingRebuild;

    // Tasten zuweisen
    private static final String BIND_TEXT = "Tasten zuweisen";
    private boolean bindMode;
    private String waiting; // Name des Moduls, das gerade auf eine Taste wartet
    private java.util.Set<Integer> waitHeld = new java.util.HashSet<>();
    private Button bindBtn;

    public TestScreen(Screen parent) {
        super(Component.literal("1912"));
        this.parent = parent;
    }

    private static Entry toggle(String name, BooleanSupplier state, Runnable flip) {
        return new Entry(name, state, () -> {
            flip.run();
            TestConfig.save();
        });
    }

    /** Modul mit An/Aus-Schalter und eigenem Einstellungs-Menue (drei Punkte rechts). */
    private static Entry module(String name, BooleanSupplier state, Runnable flip, Supplier<Screen> settings) {
        Entry e = toggle(name, state, flip);
        e.settings = settings;
        return e;
    }

    private Entry open(String name, Supplier<Screen> screen) {
        return new Entry(name, null, () -> this.minecraft.gui.setScreen(screen.get()));
    }

    @Override
    protected void init() {
        buildColumns();
        initLayout();
    }

    /** Fuer Tasten ohne offenes Menue: baut die Eintraege einmal auf und merkt sich, was sie tun. */
    static void registerActions() {
        new TestScreen(null).buildColumns();
    }

    private void buildColumns() {
        columns.clear();

        Column move = new Column("Bewegung");
        move.entries.add(module("Air Jump", () -> Movement2.airJump,
                () -> Movement2.airJump = !Movement2.airJump, () -> Movement2.airJumpScreen(this)));
        move.entries.add(module("Anchor", () -> Movement2.anchor,
                () -> Movement2.anchor = !Movement2.anchor, () -> Movement2.anchorScreen(this)));
        move.entries.add(module("Anti Void", () -> Movement2.antiVoid,
                () -> Movement2.antiVoid = !Movement2.antiVoid, () -> Movement2.antiVoidScreen(this)));
        move.entries.add(module("Auto Jump", () -> Movement2.autoJump,
                () -> Movement2.autoJump = !Movement2.autoJump, () -> Movement2.autoJumpScreen(this)));
        move.entries.add(module("Auto Walk", () -> TestClient.walking,
                () -> TestClient.walking = !TestClient.walking, () -> Movement.autoWalkScreen(this)));
        move.entries.add(module("Auto Wasp", () -> Movement2.autoWasp,
                () -> Movement2.autoWasp = !Movement2.autoWasp, () -> Movement2.autoWaspScreen(this)));
        move.entries.add(module("Blink", () -> Movement2.blink,
                () -> Movement2.blink = !Movement2.blink, () -> Movement2.blinkScreen(this)));
        move.entries.add(module("Click Tp", () -> Movement2.clickTp,
                () -> Movement2.clickTp = !Movement2.clickTp, () -> Movement2.clickTpScreen(this)));
        move.entries.add(module("Elytra Boost", () -> Movement2.elytraBoost,
                () -> Movement2.elytraBoost = !Movement2.elytraBoost, () -> Movement2.elytraBoostScreen(this)));
        move.entries.add(module("Elytra Fly", () -> Movement2.elytraFly,
                () -> Movement2.elytraFly = !Movement2.elytraFly, () -> Movement2.elytraFlyScreen(this)));
        move.entries.add(module("Entity Control", () -> Movement2.entityControl,
                () -> Movement2.entityControl = !Movement2.entityControl, () -> Movement2.entityControlScreen(this)));
        move.entries.add(module("Fast Climb", () -> Movement2.fastClimb,
                () -> Movement2.fastClimb = !Movement2.fastClimb, () -> Movement2.fastClimbScreen(this)));
        move.entries.add(module("Flight", () -> Movement2.flight,
                () -> Movement2.flight = !Movement2.flight, () -> Movement2.flightScreen(this)));
        move.entries.add(module("Gui Move", () -> Movement2.guiMove,
                () -> Movement2.guiMove = !Movement2.guiMove, () -> Movement2.guiMoveScreen(this)));
        move.entries.add(module("High Jump", () -> Movement.highJump,
                () -> Movement.highJump = !Movement.highJump, () -> Movement.highJumpScreen(this)));
        move.entries.add(module("Jesus", () -> Movement.jesus,
                () -> Movement.jesus = !Movement.jesus, () -> Movement.jesusScreen(this)));
        move.entries.add(module("Long Jump", () -> Movement.longJump,
                () -> Movement.longJump = !Movement.longJump, () -> Movement.longJumpScreen(this)));
        move.entries.add(module("No Fall", () -> Movement.noFall,
                () -> Movement.noFall = !Movement.noFall, () -> Movement.noFallScreen(this)));
        move.entries.add(module("No Slow", () -> Movement.noSlow,
                () -> Movement.noSlow = !Movement.noSlow, () -> Movement.noSlowScreen(this)));
        move.entries.add(module("Parkour", () -> Movement.parkour,
                () -> Movement.parkour = !Movement.parkour, () -> Movement.parkourScreen(this)));
        move.entries.add(module("Reverse Step", () -> Movement.reverseStep,
                () -> Movement.reverseStep = !Movement.reverseStep, () -> Movement.reverseStepScreen(this)));
        move.entries.add(module("Safe Walk", () -> Movement.safeWalk,
                () -> Movement.safeWalk = !Movement.safeWalk, () -> Movement.safeWalkScreen(this)));
        move.entries.add(module("Scaffold", () -> Movement.scaffold,
                () -> Movement.scaffold = !Movement.scaffold, () -> Movement.scaffoldScreen(this)));
        move.entries.add(module("Slippy", () -> Movement.slippy,
                () -> Movement.slippy = !Movement.slippy, () -> Movement.slippyScreen(this)));
        move.entries.add(module("Sneak", () -> Movement.sneak,
                () -> Movement.sneak = !Movement.sneak, () -> Movement.sneakScreen(this)));
        move.entries.add(module("Speed", () -> Movement.speed,
                () -> Movement.speed = !Movement.speed, () -> Movement.speedScreen(this)));
        move.entries.add(module("Spider", () -> Movement.spider,
                () -> Movement.spider = !Movement.spider, () -> Movement.spiderScreen(this)));
        move.entries.add(module("Sprint", () -> Movement.autoSprint,
                () -> Movement.autoSprint = !Movement.autoSprint, () -> Movement.sprintScreen(this)));
        move.entries.add(module("Step", () -> Movement.step,
                () -> Movement.step = !Movement.step, () -> Movement.stepScreen(this)));
        move.entries.add(module("Trident Boost", () -> Movement.trident,
                () -> Movement.trident = !Movement.trident, () -> Movement.tridentScreen(this)));
        move.entries.add(module("Velocity", () -> Movement.velocity,
                () -> Movement.velocity = !Movement.velocity, () -> Movement.velocityScreen(this)));

        Column player = new Column("Spieler");
        player.entries.add(module("Air Place", () -> PlayerMods.airPlace,
                () -> PlayerMods.airPlace = !PlayerMods.airPlace, () -> PlayerMods.screen("Air Place", this)));
        player.entries.add(module("Anti Afk", () -> PlayerMods.antiAfk,
                () -> PlayerMods.antiAfk = !PlayerMods.antiAfk, () -> PlayerMods.screen("Anti Afk", this)));
        player.entries.add(module("Anti Hunger", () -> PlayerMods.antiHunger,
                () -> PlayerMods.antiHunger = !PlayerMods.antiHunger, () -> PlayerMods.screen("Anti Hunger", this)));
        player.entries.add(module("Auto Clicker", () -> TestClient.autoClicker,
                () -> TestClient.autoClicker = !TestClient.autoClicker, () -> new ClickerScreen(this)));
        player.entries.add(module("Auto Eat", () -> PlayerMods.autoEat,
                () -> PlayerMods.autoEat = !PlayerMods.autoEat, () -> PlayerMods.screen("Auto Eat", this)));
        player.entries.add(module("Auto Fish", () -> PlayerMods.autoFish,
                () -> PlayerMods.autoFish = !PlayerMods.autoFish, () -> PlayerMods.screen("Auto Fish", this)));
        player.entries.add(module("Auto Gap", () -> PlayerMods.autoGap,
                () -> PlayerMods.autoGap = !PlayerMods.autoGap, () -> PlayerMods.screen("Auto Gap", this)));
        player.entries.add(module("Auto Mend", () -> PlayerMods.autoMend,
                () -> PlayerMods.autoMend = !PlayerMods.autoMend, () -> PlayerMods.screen("Auto Mend", this)));
        player.entries.add(module("Auto Replenish", () -> PlayerMods.autoReplenish,
                () -> PlayerMods.autoReplenish = !PlayerMods.autoReplenish, () -> PlayerMods.screen("Auto Replenish", this)));
        player.entries.add(module("Auto Respawn", () -> PlayerMods.autoRespawn,
                () -> PlayerMods.autoRespawn = !PlayerMods.autoRespawn, () -> PlayerMods.screen("Auto Respawn", this)));
        player.entries.add(module("Auto Tool", () -> PlayerMods.autoTool,
                () -> PlayerMods.autoTool = !PlayerMods.autoTool, () -> PlayerMods.screen("Auto Tool", this)));
        player.entries.add(module("Break Delay", () -> PlayerMods.breakDelay,
                () -> PlayerMods.breakDelay = !PlayerMods.breakDelay, () -> PlayerMods.screen("Break Delay", this)));
        player.entries.add(module("Chest Swap", () -> PlayerMods.chestSwap,
                () -> PlayerMods.chestSwap = !PlayerMods.chestSwap, () -> PlayerMods.screen("Chest Swap", this)));
        player.entries.add(module("Exp Thrower", () -> PlayerMods.expThrower,
                () -> PlayerMods.expThrower = !PlayerMods.expThrower, () -> PlayerMods.screen("Exp Thrower", this)));
        player.entries.add(module("Fake Player", () -> PlayerMods.fakePlayer,
                () -> PlayerMods.fakePlayer = !PlayerMods.fakePlayer, () -> PlayerMods.screen("Fake Player", this)));
        player.entries.add(module("Fast Use", () -> PlayerMods.fastUse,
                () -> PlayerMods.fastUse = !PlayerMods.fastUse, () -> PlayerMods.screen("Fast Use", this)));
        player.entries.add(module("Ghost Hand", () -> PlayerMods.ghostHand,
                () -> PlayerMods.ghostHand = !PlayerMods.ghostHand, () -> PlayerMods.screen("Ghost Hand", this)));
        player.entries.add(module("Instant Rebreak", () -> PlayerMods.instantRebreak,
                () -> PlayerMods.instantRebreak = !PlayerMods.instantRebreak, () -> PlayerMods.screen("Instant Rebreak", this)));
        player.entries.add(module("Liquid Interact", () -> PlayerMods.liquidInteract,
                () -> PlayerMods.liquidInteract = !PlayerMods.liquidInteract, () -> PlayerMods.screen("Liquid Interact", this)));
        player.entries.add(module("Middle Click Extra", () -> PlayerMods.middleClickExtra,
                () -> PlayerMods.middleClickExtra = !PlayerMods.middleClickExtra, () -> PlayerMods.screen("Middle Click Extra", this)));
        player.entries.add(module("Multitask", () -> PlayerMods.multitask,
                () -> PlayerMods.multitask = !PlayerMods.multitask, () -> PlayerMods.screen("Multitask", this)));
        player.entries.add(module("Name Protect", () -> PlayerMods.nameProtect,
                () -> PlayerMods.nameProtect = !PlayerMods.nameProtect, () -> PlayerMods.screen("Name Protect", this)));
        player.entries.add(module("No Interact", () -> PlayerMods.noInteract,
                () -> PlayerMods.noInteract = !PlayerMods.noInteract, () -> PlayerMods.screen("No Interact", this)));
        player.entries.add(module("No Mining Trace", () -> PlayerMods.noMiningTrace,
                () -> PlayerMods.noMiningTrace = !PlayerMods.noMiningTrace, () -> PlayerMods.screen("No Mining Trace", this)));
        player.entries.add(module("No Rotate", () -> PlayerMods.noRotate,
                () -> PlayerMods.noRotate = !PlayerMods.noRotate, () -> PlayerMods.screen("No Rotate", this)));
        player.entries.add(module("No Status Effects", () -> PlayerMods.noStatusEffects,
                () -> PlayerMods.noStatusEffects = !PlayerMods.noStatusEffects, () -> PlayerMods.screen("No Status Effects", this)));
        player.entries.add(module("Portals", () -> PlayerMods.portals,
                () -> PlayerMods.portals = !PlayerMods.portals, () -> PlayerMods.screen("Portals", this)));
        player.entries.add(module("Potion Saver", () -> PlayerMods.potionSaver,
                () -> PlayerMods.potionSaver = !PlayerMods.potionSaver, () -> PlayerMods.screen("Potion Saver", this)));
        player.entries.add(module("Reach", () -> PlayerMods.reach,
                () -> PlayerMods.reach = !PlayerMods.reach, () -> PlayerMods.screen("Reach", this)));
        player.entries.add(module("Rotation", () -> PlayerMods.rotation,
                () -> PlayerMods.rotation = !PlayerMods.rotation, () -> PlayerMods.screen("Rotation", this)));
        player.entries.add(module("Speed Mine", () -> PlayerMods.speedMine,
                () -> PlayerMods.speedMine = !PlayerMods.speedMine, () -> PlayerMods.screen("Speed Mine", this)));

        Column render = new Column("Render");
        render.entries.add(module("Better Tab", () -> RenderMods.betterTab,
                () -> RenderMods.betterTab = !RenderMods.betterTab, () -> RenderMods.screen("Better Tab", this)));
        render.entries.add(module("Better Tooltips", () -> RenderMods.betterTooltips,
                () -> RenderMods.betterTooltips = !RenderMods.betterTooltips, () -> RenderMods.screen("Better Tooltips", this)));
        render.entries.add(module("Block Esp", () -> RenderMods.blockEsp,
                () -> RenderMods.blockEsp = !RenderMods.blockEsp, () -> RenderMods.screen("Block Esp", this)));
        render.entries.add(module("Block Selection", () -> RenderMods.blockSelection,
                () -> RenderMods.blockSelection = !RenderMods.blockSelection, () -> RenderMods.screen("Block Selection", this)));
        render.entries.add(module("Blur", () -> RenderMods.blur,
                () -> RenderMods.blur = !RenderMods.blur, () -> RenderMods.screen("Blur", this)));
        render.entries.add(module("Boss Stack", () -> RenderMods.bossStack,
                () -> RenderMods.bossStack = !RenderMods.bossStack, () -> RenderMods.screen("Boss Stack", this)));
        render.entries.add(module("Breadcrumbs", () -> RenderMods.breadcrumbs,
                () -> RenderMods.breadcrumbs = !RenderMods.breadcrumbs, () -> RenderMods.screen("Breadcrumbs", this)));
        render.entries.add(module("Break Indicators", () -> RenderMods.breakIndicators,
                () -> RenderMods.breakIndicators = !RenderMods.breakIndicators, () -> RenderMods.screen("Break Indicators", this)));
        render.entries.add(module("Camera Tweaks", () -> RenderMods.cameraTweaks,
                () -> RenderMods.cameraTweaks = !RenderMods.cameraTweaks, () -> RenderMods.screen("Camera Tweaks", this)));
        render.entries.add(module("Chams", () -> RenderMods.chams,
                () -> RenderMods.chams = !RenderMods.chams, () -> RenderMods.screen("Chams", this)));
        render.entries.add(module("City Esp", () -> RenderMods.cityEsp,
                () -> RenderMods.cityEsp = !RenderMods.cityEsp, () -> RenderMods.screen("City Esp", this)));
        render.entries.add(module("Entity Owner", () -> RenderMods.entityOwner,
                () -> RenderMods.entityOwner = !RenderMods.entityOwner, () -> RenderMods.screen("Entity Owner", this)));
        render.entries.add(module("Free Look", () -> RenderMods.freeLook,
                () -> RenderMods.freeLook = !RenderMods.freeLook, () -> RenderMods.screen("Free Look", this)));
        render.entries.add(module("Freecam", () -> RenderMods.freecam,
                () -> RenderMods.freecam = !RenderMods.freecam, () -> RenderMods.screen("Freecam", this)));
        render.entries.add(module("Fullbright", () -> RenderMods.fullbright,
                () -> RenderMods.fullbright = !RenderMods.fullbright, () -> RenderMods.screen("Fullbright", this)));
        render.entries.add(module("Hand View", () -> RenderMods.handView,
                () -> RenderMods.handView = !RenderMods.handView, () -> RenderMods.screen("Hand View", this)));
        render.entries.add(module("Hole Esp", () -> RenderMods.holeEsp,
                () -> RenderMods.holeEsp = !RenderMods.holeEsp, () -> RenderMods.screen("Hole Esp", this)));
        render.entries.add(module("Item Highlight", () -> RenderMods.itemHighlight,
                () -> RenderMods.itemHighlight = !RenderMods.itemHighlight, () -> RenderMods.screen("Item Highlight", this)));
        render.entries.add(module("Item Physics", () -> RenderMods.itemPhysics,
                () -> RenderMods.itemPhysics = !RenderMods.itemPhysics, () -> RenderMods.screen("Item Physics", this)));
        render.entries.add(module("Light Overlay", () -> RenderMods.lightOverlay,
                () -> RenderMods.lightOverlay = !RenderMods.lightOverlay, () -> RenderMods.screen("Light Overlay", this)));
        render.entries.add(module("Logout Spots", () -> RenderMods.logoutSpots,
                () -> RenderMods.logoutSpots = !RenderMods.logoutSpots, () -> RenderMods.screen("Logout Spots", this)));
        render.entries.add(module("Marker", () -> RenderMods.marker,
                () -> RenderMods.marker = !RenderMods.marker, () -> RenderMods.screen("Marker", this)));
        render.entries.add(module("Nametags", () -> RenderMods.nametags,
                () -> RenderMods.nametags = !RenderMods.nametags, () -> RenderMods.screen("Nametags", this)));
        render.entries.add(module("No Render", () -> RenderMods.noRender,
                () -> RenderMods.noRender = !RenderMods.noRender, () -> RenderMods.screen("No Render", this)));
        render.entries.add(module("Pop Chams", () -> RenderMods.popChams,
                () -> RenderMods.popChams = !RenderMods.popChams, () -> RenderMods.screen("Pop Chams", this)));
        render.entries.add(module("Storage Esp", () -> RenderMods.storageEsp,
                () -> RenderMods.storageEsp = !RenderMods.storageEsp, () -> RenderMods.screen("Storage Esp", this)));
        render.entries.add(module("Time Changer", () -> RenderMods.timeChanger,
                () -> RenderMods.timeChanger = !RenderMods.timeChanger, () -> RenderMods.screen("Time Changer", this)));
        render.entries.add(module("Tracers", () -> RenderMods.tracers,
                () -> RenderMods.tracers = !RenderMods.tracers, () -> RenderMods.screen("Tracers", this)));
        render.entries.add(module("Trail", () -> RenderMods.trail,
                () -> RenderMods.trail = !RenderMods.trail, () -> RenderMods.screen("Trail", this)));
        render.entries.add(module("Trajectories", () -> RenderMods.trajectories,
                () -> RenderMods.trajectories = !RenderMods.trajectories, () -> RenderMods.screen("Trajectories", this)));
        render.entries.add(module("Tunnel Esp", () -> RenderMods.tunnelEsp,
                () -> RenderMods.tunnelEsp = !RenderMods.tunnelEsp, () -> RenderMods.screen("Tunnel Esp", this)));
        render.entries.add(module("Void Esp", () -> RenderMods.voidEsp,
                () -> RenderMods.voidEsp = !RenderMods.voidEsp, () -> RenderMods.screen("Void Esp", this)));
        render.entries.add(module("Wall Hack", () -> RenderMods.wallHack,
                () -> RenderMods.wallHack = !RenderMods.wallHack, () -> RenderMods.screen("Wall Hack", this)));
        render.entries.add(module("Waypoints", () -> RenderMods.waypoints,
                () -> RenderMods.waypoints = !RenderMods.waypoints, () -> RenderMods.screen("Waypoints", this)));

        Column world = new Column("Welt");
        world.entries.add(toggle("Bl\u00f6cke abbauen", () -> TestClient.autoMine,
                () -> TestClient.autoMine = !TestClient.autoMine));
        world.entries.add(open("Bereich", () -> new AreaScreen(this)));
        world.entries.add(open("Block", () -> new BlockScreen(this)));
        world.entries.add(open("Koordinaten", () -> new GotoScreen(this)));
        world.entries.add(toggle("Auto-Logout", () -> TestClient.autoLogout,
                () -> TestClient.autoLogout = !TestClient.autoLogout));
        world.entries.add(module("ESP", () -> Esp.enabled, () -> {
            Esp.enabled = !Esp.enabled;
            Esp.rescan();
        }, () -> new EspScreen(this)));

        Column inv = new Column("Inventar");
        inv.entries.add(module("AutoDrop", () -> TestClient.autoDrop,
                () -> TestClient.autoDrop = !TestClient.autoDrop, () -> new DropScreen(this)));

        Column misc2 = new Column("Misc");
        misc2.entries.add(module("Anti Packet Kick", () -> MiscMods.antiPacketKick,
                () -> MiscMods.antiPacketKick = !MiscMods.antiPacketKick, () -> MiscMods.screen("Anti Packet Kick", this)));
        misc2.entries.add(module("Auto Reconnect", () -> MiscMods.autoReconnect,
                () -> MiscMods.autoReconnect = !MiscMods.autoReconnect, () -> MiscMods.screen("Auto Reconnect", this)));
        misc2.entries.add(module("Better Beacons", () -> MiscMods.betterBeacons,
                () -> MiscMods.betterBeacons = !MiscMods.betterBeacons, () -> MiscMods.screen("Better Beacons", this)));
        misc2.entries.add(module("Better Chat", () -> MiscMods.betterChat,
                () -> MiscMods.betterChat = !MiscMods.betterChat, () -> MiscMods.screen("Better Chat", this)));
        misc2.entries.add(module("Book Bot", () -> MiscMods.bookBot,
                () -> MiscMods.bookBot = !MiscMods.bookBot, () -> MiscMods.screen("Book Bot", this)));
        misc2.entries.add(module("Discord Presence", () -> MiscMods.discordPresence,
                () -> MiscMods.discordPresence = !MiscMods.discordPresence, () -> MiscMods.screen("Discord Presence", this)));
        misc2.entries.add(module("Inventory Tweaks", () -> MiscMods.inventoryTweaks,
                () -> MiscMods.inventoryTweaks = !MiscMods.inventoryTweaks, () -> MiscMods.screen("Inventory Tweaks", this)));
        misc2.entries.add(module("Message Aura", () -> MiscMods.messageAura,
                () -> MiscMods.messageAura = !MiscMods.messageAura, () -> MiscMods.screen("Message Aura", this)));
        misc2.entries.add(module("Notebot", () -> MiscMods.notebot,
                () -> MiscMods.notebot = !MiscMods.notebot, () -> MiscMods.screen("Notebot", this)));
        misc2.entries.add(module("Notifier", () -> MiscMods.notifier,
                () -> MiscMods.notifier = !MiscMods.notifier, () -> MiscMods.screen("Notifier", this)));
        misc2.entries.add(module("Packet Canceller", () -> MiscMods.packetCanceller,
                () -> MiscMods.packetCanceller = !MiscMods.packetCanceller, () -> MiscMods.screen("Packet Canceller", this)));
        misc2.entries.add(module("Server Spoof", () -> MiscMods.serverSpoof,
                () -> MiscMods.serverSpoof = !MiscMods.serverSpoof, () -> MiscMods.screen("Server Spoof", this)));
        misc2.entries.add(module("Sound Blocker", () -> MiscMods.soundBlocker,
                () -> MiscMods.soundBlocker = !MiscMods.soundBlocker, () -> MiscMods.screen("Sound Blocker", this)));
        misc2.entries.add(module("Spam", () -> MiscMods.spam,
                () -> MiscMods.spam = !MiscMods.spam, () -> MiscMods.screen("Spam", this)));
        misc2.entries.add(module("Swarm", () -> MiscMods.swarm,
                () -> MiscMods.swarm = !MiscMods.swarm, () -> MiscMods.screen("Swarm", this)));

        Column misc = new Column("Men\u00fc");
        misc.entries.add(open("Design anpassen", () -> new DesignScreen(this)));
        misc.entries.add(open("Tasten \u00e4ndern",
                () -> new KeyBindsScreen(this, this.minecraft.options)));
        misc.entries.add(new Entry("Alle Module aus / an", null, Keybinds::panic));
        misc.entries.add(new Entry("Schlie\u00dfen", null, this::onClose));

        columns.add(move);
        columns.add(player);
        columns.add(render);
        columns.add(world);
        columns.add(inv);
        columns.add(misc2);
        columns.add(misc);
        for (int i = 0; i < columns.size(); i++) columns.get(i).index = i;

        // Was jede Taste ausloesen darf (alles ausser der Menue-Spalte)
        Keybinds.ACTIONS.clear();
        for (Column col : columns) {
            if (col == misc) continue;
            for (Entry e : col.entries) Keybinds.ACTIONS.put(e.name, new Keybinds.Action(e.state, e.action));
        }
    }

    /** Die einfachen Module, die der Standardmodus zeigt (Welt, Inventar und Menue sind immer komplett). */
    private static final java.util.Set<String> SIMPLE = java.util.Set.of(
            "Auto Jump", "Auto Walk", "Flight", "High Jump", "No Fall", "Safe Walk", "Sneak", "Speed", "Sprint", "Step",
            "Anti Afk", "Auto Clicker", "Auto Eat", "Auto Fish", "Auto Respawn", "Auto Tool", "Fast Use", "Reach",
            "Speed Mine",
            "Better Tooltips", "Chams", "Freecam", "Fullbright", "Item Highlight", "Nametags", "Time Changer",
            "Tracers", "Waypoints",
            "Auto Reconnect", "Better Chat", "Discord Presence", "Inventory Tweaks", "Notifier");

    /**
     * Ob ein Eintrag im aktuellen Modus zu sehen ist. Im Standardmodus bleiben ausserdem alle Module
     * sichtbar, die gerade an sind oder eine Taste haben - sonst waeren sie an, ohne dass man es sieht.
     */
    private boolean inMode(Column col, Entry e) {
        if (Theme.menuMode == 1 || col.index >= 3 && col.index != 5) return true;
        if (SIMPLE.contains(e.name) || Keybinds.get(e.name) != null) return true;
        return e.state != null && e.state.getAsBoolean();
    }

    private boolean bindable(Column col) {
        return col.index != columns.size() - 1; // die Menue-Spalte bekommt keine Tasten
    }

    private void initLayout() {

        // Jede Spalte so breit wie ihr laengster Eintrag: kein Name muss gekuerzt werden
        // total = Breite ohne Tasten-Schilder: nur danach richtet sich die Menue-Groesse, damit das Menue
        // beim Zuweisen von Tasten nicht ploetzlich kleiner wird (dann ruecken die Spalten in zwei Reihen)
        int total = 0;
        for (Column col : columns) {
            int head = 8 + this.font.width(col.title) + 12 + this.font.width("00/00") + 8 + 7 + 9;
            int need = head;
            int plain = head;
            for (Entry e : col.entries) {
                need = Math.max(need, needWidth(e));
                plain = Math.max(plain, needWidth(e) - badgeW(e));
            }
            col.w = Math.max(Theme.columnWidth(), need);
            total += Math.max(Theme.columnWidth(), plain);
        }
        total += (columns.size() - 1) * GAP;

        // Eigene Menue-Groesse (gilt nur, solange ein 1912-Menue offen ist): "Auto" waehlt sie so,
        // dass alle Spalten nebeneinander passen
        GuiScale.need(total + 24, 330);
        GuiScale.apply();
        this.width = this.minecraft.getWindow().getGuiScaledWidth();
        this.height = this.minecraft.getWindow().getGuiScaledHeight();

        layout();
    }

    private String q() {
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private boolean searching() {
        return !q().isEmpty();
    }

    private boolean collapsed(Column col) {
        return !searching() && Theme.isCollapsed(col.index);
    }

    /** Platz, den ein Eintrag braucht, damit sein Name ganz zu lesen ist. */
    private int needWidth(Entry e) {
        int right = e.state != null ? 4 + 16 + 2 + DOTS_W : (e.settings != null ? 4 + DOTS_W : 4);
        return 8 + this.font.width(e.name) + 2 + badgeW(e) + right + SCROLL_W;
    }

    /** Platz fuer das Tasten-Schild eines Eintrags. */
    private int badgeW(Entry e) {
        Integer k = Keybinds.get(e.name);
        if (k != null) return this.font.width(Keybinds.keyName(k)) + 8 + 4;
        return bindMode ? 16 : 0; // freie Plaetze brauchen nur im Zuweisen-Modus Platz
    }

    /** Verteilt Spalten auf moeglichst wenige, gleichmaessig gefuellte Reihen. */
    private List<List<Column>> flow(List<Column> cols, int maxW) {
        // erst zaehlen, wie viele Reihen noetig sind
        int rows = 1;
        int cur = 0;
        for (Column col : cols) {
            int add = col.w + (cur > 0 ? GAP : 0);
            if (cur > 0 && cur + add > maxW) {
                rows++;
                cur = col.w;
            } else {
                cur += add;
            }
        }
        int perRow = (cols.size() + rows - 1) / rows;
        List<List<Column>> out = new ArrayList<>();
        List<Column> line = new ArrayList<>();
        cur = 0;
        for (Column col : cols) {
            int add = col.w + (line.isEmpty() ? 0 : GAP);
            if (!line.isEmpty() && (cur + add > maxW || line.size() >= perRow)) {
                out.add(line);
                line = new ArrayList<>();
                cur = 0;
                add = col.w;
            }
            line.add(col);
            cur += add;
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private void layout() {
        String q = q();
        shown.clear();
        for (Column col : columns) {
            col.matches.clear();
            boolean titleHit = !q.isEmpty() && col.title.toLowerCase(Locale.ROOT).contains(q);
            for (Entry e : col.entries) {
                // die Suche findet immer alle Module, auch die im Standardmodus ausgeblendeten
                boolean hit = q.isEmpty() ? inMode(col, e) : titleHit || e.name.toLowerCase(Locale.ROOT).contains(q);
                if (hit) col.matches.add(e);
            }
            if (!col.matches.isEmpty()) shown.add(col);
        }

        int maxW = this.width - 16;
        logoScale = Theme.logoSize > 0 ? Theme.logoSize : (this.height >= 300 ? 2 : 1);
        int logoH = Theme.showLogo ? Logo.H * logoScale : 10;
        int topY = 8 + logoH + 8 + SEARCH_H + 10;
        int rowGap = 12;
        int availV = this.height - FOOTER_H - 6 - topY;

        // Hoehe und Lage des Rasters immer wie ohne Suche -> Suchfeld und Logo springen nicht
        int gridRowsAll = flow(columns, maxW).size();
        fitRows = Math.max(4, ((availV - (gridRowsAll - 1) * rowGap) / gridRowsAll - HEADER_H) / ENTRY_H);
        int maxRows = 1;
        for (Column col : columns) {
            int n = 0;
            for (Entry e : col.entries) if (inMode(col, e)) n++;
            maxRows = Math.max(maxRows, Math.min(fitRows, n));
        }
        int rowH = HEADER_H + maxRows * ENTRY_H;
        int gridH = gridRowsAll * rowH + (gridRowsAll - 1) * rowGap;
        gridY = topY + Math.max(0, (availV - gridH) / 3);
        searchY = gridY - 10 - SEARCH_H;
        logoY = searchY - 8 - logoH;

        // Suchfeld (bleibt dasselbe Objekt, damit Text und Cursor beim Neuaufbau erhalten bleiben)
        int sw = Math.min(200, this.width - 40);
        if (searchBox == null) {
            searchBox = new EditBox(this.font, 0, 0, sw, SEARCH_H, Component.literal("Suchen"));
            searchBox.setHint(Component.literal("Module suchen"));
            searchBox.setMaxLength(40);
            searchBox.setValue(query);
            searchBox.setResponder(s -> {
                if (s.equals(query)) return;
                query = s;
                SCROLL.keySet().removeIf(k -> k.endsWith("?"));
                pendingRebuild = true;
            });
        }
        int bw = this.font.width(BIND_TEXT) + 18;
        int sx0 = (this.width - (sw + 6 + bw)) / 2;
        searchBox.setWidth(sw);
        searchBox.setX(sx0);
        searchBox.setY(searchY);
        addWidget(searchBox); // gezeichnet wird es von uns selbst (nach dem Hintergrund)
        bindBtn = addWidget(Button.builder(Component.literal(BIND_TEXT), b -> {
            bindMode = !bindMode;
            waiting = null;
            pendingRebuild = true; // Spalten brauchen im Zuweisen-Modus etwas mehr Platz
        }).bounds(sx0 + sw + 6, searchY, bw, SEARCH_H).build());

        // Spalten (nur die mit Treffern) in Reihen anordnen
        List<List<Column>> lines = flow(shown, maxW);
        for (int gr = 0; gr < lines.size(); gr++) {
            List<Column> line = lines.get(gr);
            int rowW = (line.size() - 1) * GAP;
            for (Column col : line) rowW += col.w;
            int x = (this.width - rowW) / 2;
            int rowX0 = x;
            for (Column col : line) {
                col.x = x;
                col.y = gridY + gr * (rowH + rowGap);
                col.t0 = (x - rowX0) / (float) Math.max(1, rowW);
                col.t1 = (x + col.w - rowX0) / (float) Math.max(1, rowW);
                x += col.w + GAP;
                place(col);
            }
        }
    }

    /** Legt die unsichtbaren Klick-Bereiche einer Spalte an. */
    private void place(Column col) {
        col.open = collapsed(col) ? 0f : 1f;
        // Klick auf die Kopfzeile klappt die Spalte ein bzw. aus (nicht waehrend der Suche)
        addWidget(Button.builder(Component.empty(), b -> {
            if (searching()) return;
            Theme.toggleCollapsed(col.index);
            TestConfig.save();
        }).bounds(col.x, col.y, col.w, HEADER_H).build());
        buildVisible(col);
        for (int e = 0; e < col.visible.size(); e++) {
            Entry en = col.visible.get(e);
            en.x = col.x;
            en.y = col.y + HEADER_H + e * ENTRY_H;
            en.last = e == col.visible.size() - 1;
            en.anim = en.state != null && en.state.getAsBoolean() ? 1f : 0f;
            // links umschalten, rechts (Punkte) Einstellungen oeffnen
            int dotsX = col.x + col.w - SCROLL_W - DOTS_W;
            int toggleW = en.settings != null ? dotsX - en.x : col.w - SCROLL_W;
            boolean canBind = bindable(col);
            addWidget(Button.builder(Component.empty(), b -> {
                if (collapsed(col)) return;
                if (bindMode && canBind) {
                    if (!en.name.equals(waiting)) {
                        waiting = en.name;
                        waitHeld = Keybinds.pressedNow();
                    }
                    return;
                }
                en.action.run();
            }).bounds(en.x, en.y, toggleW, ENTRY_H).build());
            if (en.settings != null) {
                Supplier<Screen> s = en.settings;
                addWidget(Button.builder(Component.empty(), b -> {
                    if (!collapsed(col)) this.minecraft.gui.setScreen(s.get());
                }).bounds(dotsX, en.y, DOTS_W, ENTRY_H).build());
            }
        }
        if (col.scrollable) {
            // Klick in den Scrollbalken ober-/unterhalb des Griffs blaettert eine Seite
            int[] th = thumb(col);
            int lx = col.x + col.w - SCROLL_W;
            int page = Math.max(1, fitRows - 1);
            if (th[2] > th[0]) {
                addWidget(Button.builder(Component.empty(), b -> scroll(col, -page))
                        .bounds(lx, th[0], SCROLL_W, th[2] - th[0]).build());
            }
            int below = th[2] + th[3];
            if (th[0] + th[1] > below) {
                addWidget(Button.builder(Component.empty(), b -> scroll(col, page))
                        .bounds(lx, below, SCROLL_W, th[0] + th[1] - below).build());
            }
        }
    }

    private String scrollKey(Column col) {
        return col.title + (searching() ? "?" : "");
    }

    /** Waehlt die sichtbaren Zeilen; laengere Spalten bekommen einen Scrollbalken. */
    private void buildVisible(Column col) {
        col.visible.clear();
        int total = col.matches.size();
        col.scrollable = total > fitRows;
        if (!col.scrollable) {
            col.scroll = 0;
            col.visible.addAll(col.matches);
            return;
        }
        int maxScroll = total - fitRows;
        int scroll = Math.max(0, Math.min(maxScroll, SCROLL.getOrDefault(scrollKey(col), 0)));
        SCROLL.put(scrollKey(col), scroll);
        col.scroll = scroll;
        for (int i = scroll; i < scroll + fitRows; i++) col.visible.add(col.matches.get(i));
    }

    /** Scrollbalken: {Spur oben, Spur-Hoehe, Griff oben, Griff-Hoehe}. */
    private int[] thumb(Column col) {
        int trackY = col.y + HEADER_H + 4;
        int trackH = Math.max(8, col.visible.size() * ENTRY_H - 8 - 2);
        int total = Math.max(1, col.matches.size());
        int maxScroll = Math.max(1, total - fitRows);
        int thumbH = Math.max(12, trackH * fitRows / total);
        int thumbY = trackY + Math.round((trackH - thumbH) * (col.scroll / (float) maxScroll));
        return new int[] {trackY, trackH, thumbY, thumbH};
    }

    private void scroll(Column col, int delta) {
        SCROLL.put(scrollKey(col), SCROLL.getOrDefault(scrollKey(col), 0) + delta);
        rebuildKeepFocus();
    }

    private void rebuildKeepFocus() {
        boolean focused = searchBox != null && searchBox.isFocused();
        this.rebuildWidgets();
        if (focused && searchBox != null) this.setFocused(searchBox);
    }

    /** Mausrad ueber einer Spalte scrollt sie. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (Column col : shown) {
            int h = HEADER_H + col.visible.size() * ENTRY_H;
            boolean over = mouseX >= col.x && mouseX < col.x + col.w && mouseY >= col.y && mouseY < col.y + h;
            if (over && col.scrollable) {
                scroll(col, scrollY > 0 ? -2 : 2);
                return true;
            }
        }
        return false;
    }

    // ---------- Zeichnen ----------

    /** Schreibt einen Namen; ein Suchtreffer wird darin unterstrichen. Der Platz reicht immer aus. */
    private void name(GuiGraphicsExtractor g, String s, int x, int y, int max, int color) {
        Ui.fitText(g, this.font, s, x, y, max, color, true);
        String q = q();
        if (q.isEmpty()) return;
        int i = s.toLowerCase(Locale.ROOT).indexOf(q);
        if (i < 0) return;
        int end = Math.min(s.length(), i + q.length());
        int w = this.font.width(s);
        float sc = max > 0 && w > max ? max / (float) w : 1f;
        int ux = x + Math.round(this.font.width(s.substring(0, i)) * sc);
        int uw = Math.round(this.font.width(s.substring(i, end)) * sc);
        Ui.fill(g, ux, y + 9, ux + uw, y + 10, Theme.accent());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (waiting != null) pollBind();
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuildKeepFocus();
        }
        Ui.beginFrame();
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        float ease = Theme.animations ? 1f - (1f - t) * (1f - t) : 1f;
        float ga = Math.max(0.06f, ease);  // globale Transparenz (Einblenden)
        int dy = Math.round((1f - ease) * 10f); // globaler Versatz (Hereingleiten)
        if (searchBox != null) {
            searchBox.setAlpha(ga);
            searchBox.setY(searchY + dy);
        }
        if (bindBtn != null) bindBtn.setY(searchY + dy);
        // Minecraft zeichnet hier nichts selbst: das Suchfeld kommt unten, ueber dem Hintergrund
        super.extractRenderState(g, mouseX, mouseY, delta);

        Ui.alpha = 1f;
        Fx.backdrop(g, this.width, this.height, mouseX, mouseY);
        Ui.alpha = ga;

        // Logo (oder nur Schriftzug)
        int cx = this.width / 2;
        if (Theme.showLogo) {
            int logoW = Logo.W * logoScale;
            Logo.draw3d(g, cx - logoW / 2, logoY, logoScale, ga, dy, Fx.mouseX(), Fx.mouseY());
        } else {
            String title = "1 9 1 2";
            Ui.text(g, this.font, title, cx - this.font.width(title) / 2, logoY + dy, 0xFFFFFFFF, true);
        }

        // Suchfeld: weiches Leuchten, solange es aktiv ist
        if (searchBox != null) {
            float f = Ui.ease("menuSearch", 0, searchBox.isFocused() ? 1f : 0f, 0.25f);
            Fx.soft(g, searchBox.getX(), searchBox.getY(), searchBox.getWidth(), SEARCH_H, 7f,
                    Fx.withAlpha(Theme.accent(), Math.round(0x18 + 0x48 * f)));
            searchBox.extractRenderState(g, mouseX, mouseY, delta);
            Ui.field(g, searchBox);
        }
        if (bindBtn != null) {
            Ui.button(g, this.font, bindBtn, mouseX, mouseY, bindMode ? Ui.STYLE_PRIMARY : Ui.STYLE_NORMAL);
        }

        if (shown.isEmpty()) {
            String msg = "Keine Module gefunden für „" + query.trim() + "“";
            Ui.fitCentered(g, this.font, msg, cx, gridY + 12 + dy, this.width - 24, MUTED, true);
        }

        for (Column col : shown) drawColumn(g, col, mouseX, mouseY, dy, ease);

        // Fusszeile: weicher Uebergang nach unten, darin eine kurze Bedienhilfe
        Ui.alpha = ga;
        Ui.gradientV(g, 0, this.height - 26, this.width, this.height, 0x00000000, 0x96000000);
        String keys = TestClient.toggleKey.getTranslatedKeyMessage().getString()
                + " / " + TestClient.guiKey.getTranslatedKeyMessage().getString();
        String sep = "   ·   ";
        String[] hints = {
            "Klick: an/aus" + sep + "Punkte: Einstellungen" + sep + "Mausrad: scrollen" + sep + "Tasten: " + keys,
            "Klick: an/aus" + sep + "Punkte: Einstellungen" + sep + "Tasten: " + keys,
            "Klick: an/aus" + sep + "Punkte: Einstellungen",
            "Klick: an/aus"
        };
        if (Theme.menuMode == 0 && !bindMode) {
            // Standardmodus: zeigen, wo die restlichen Module zu finden sind
            hints = new String[] {
                hints[1] + sep + "Alle Module: Design anpassen, Modus Experte",
                "Klick: an/aus" + sep + "Alle Module: Design anpassen, Modus Experte",
                hints[2], hints[3]
            };
        }
        if (bindMode) {
            hints = waiting != null
                    ? new String[] {"Dr\u00fccke jetzt eine Taste f\u00fcr " + waiting + sep
                            + "R\u00fccktaste oder Entf: Taste entfernen", "Dr\u00fccke jetzt eine Taste", "Taste dr\u00fccken"}
                    : new String[] {"Klick auf ein Modul, dann die gew\u00fcnschte Taste dr\u00fccken" + sep
                            + "Die Tasten wirken im Spiel", "Klick auf ein Modul, dann Taste dr\u00fccken", "Modul anklicken"};
        }
        for (String hint : hints) {
            // die ausfuehrlichste Hilfe, die ganz in die Breite passt
            if (this.font.width(hint) <= this.width - 16) {
                Ui.text(g, this.font, hint, cx - this.font.width(hint) / 2, this.height - 11, MUTED, true);
                break;
            }
        }
        Ui.alpha = 1f;
    }

    private void drawColumn(GuiGraphicsExtractor g, Column col, int mouseX, int mouseY, int dy, float ease) {
        float openTarget = collapsed(col) ? 0f : 1f;
        col.open += (openTarget - col.open) * Ui.step(0.3f);
        if (Math.abs(openTarget - col.open) < 0.01f) col.open = openTarget;
        int fullH = col.visible.size() * ENTRY_H;
        int bodyH = Math.round(fullH * col.open);
        boolean bodyShown = bodyH >= R + 1;
        int x = col.x;
        int y = col.y + dy;
        int w = col.w;
        int h = HEADER_H + (bodyShown ? bodyH : 0);

        // Schatten, dezentes Leuchten, Rand, Koerper
        Fx.shadow(g, x, y, w, h);
        Fx.panelGlow(g, x, y, w, h, ease);
        Ui.roundRect(g, x - 1, y - 1, w + 2, h + 2, R + 1, Theme.border());
        if (bodyShown) Ui.roundRect(g, x, y + HEADER_H, w, bodyH, R, PANEL, false, true);

        // Kopfzeile: Ausschnitt des durchgehenden Farbverlaufs
        boolean overHead = mouseX >= x && mouseX < x + w && mouseY >= col.y && mouseY < col.y + HEADER_H;
        col.headHover += ((overHead ? 1f : 0f) - col.headHover) * Ui.step(0.3f);
        Ui.header(g, x, y, w, col.t0, col.t1, !bodyShown);
        if (col.headHover > 0.02f) {
            Ui.roundRect(g, x, y, w, HEADER_H, R, Ui.withA(0xFFFFFF, Math.round(0x16 * col.headHover)), true, !bodyShown);
        }

        int active = 0;
        int toggles = 0;
        for (Entry en : col.matches) {
            if (en.state != null) {
                toggles++;
                if (en.state.getAsBoolean()) active++;
            }
        }
        // Plus/Minus: blendet weich um, wenn die Spalte ein-/ausklappt
        int pmX = x + w - 12;
        Ui.plusMinusIcon(g, pmX, y + 10, 1f - col.open, 0xFFFFFFFF);
        String count = toggles > 0 ? active + "/" + toggles : "";
        int countW = this.font.width(count);
        if (!count.isEmpty()) Ui.text(g, this.font, count, pmX - 8 - countW, y + 7, 0xC8FFFFFF, true);
        name(g, col.title, x + 8, y + 7, pmX - 8 - countW - 6 - (x + 8), 0xFFFFFFFF);

        int accent = Theme.accent();
        int right = x + w - SCROLL_W;           // rechter Rand des Zeileninhalts
        int dotsX = right - DOTS_W;
        for (Entry en : col.visible) {
            // nur Zeilen zeichnen, die beim Ein-/Ausklappen schon komplett sichtbar sind
            if (!bodyShown || en.y + ENTRY_H > col.y + HEADER_H + bodyH) continue;
            int ey = en.y + dy;
            boolean on = en.state != null && en.state.getAsBoolean();
            boolean over = mouseX >= x && mouseX < right && mouseY >= en.y && mouseY < en.y + ENTRY_H;
            en.hover += ((over ? 1f : 0f) - en.hover) * Ui.step(0.3f);
            en.anim += ((on ? 1f : 0f) - en.anim) * Ui.step(0.25f);
            if (Math.abs((on ? 1f : 0f) - en.anim) < 0.01f) en.anim = on ? 1f : 0f;

            // Eingeschaltet: weicher Farbverlauf von links, der sanft ein- und ausblendet
            if (en.anim > 0.02f) {
                Ui.roundGradientH(g, x, ey, w, ENTRY_H, R, Ui.withA(accent, Math.round(0x4A * en.anim)),
                        Ui.withA(accent, Math.round(0x0A * en.anim)), false, en.last);
                int barH = ENTRY_H - 6 - (en.last ? 2 : 0);
                Ui.roundRect(g, x + 2, ey + 3, 2, barH, 1, Ui.withA(accent, Math.round(255 * en.anim)));
            }
            if (en.hover > 0.02f) {
                Ui.roundRect(g, x, ey, w, ENTRY_H, R, Ui.withA(0xFFFFFF, Math.round(0x12 * en.hover)), false, en.last);
            }

            int textColor = Ui.lerp(Ui.lerp(TEXT, TEXT_ON, en.anim), 0xFFFFFFFF, en.hover * 0.6f);
            int nameX = x + 8 + Math.round(2f * en.anim);
            int nameEnd = en.state != null ? dotsX - 2 - 16 - 4 : (en.settings != null ? dotsX - 4 : right - 4);
            // Tasten-Schild: zeigt die zugewiesene Taste (im Zuweisen-Modus auch freie Plaetze)
            Integer key = Keybinds.get(en.name);
            boolean wait = en.name.equals(waiting);
            String badge = wait ? "?" : key != null ? Keybinds.keyName(key) : (bindMode && bindable(col) ? "+" : null);
            if (badge != null) {
                int bw = this.font.width(badge) + 8;
                int bx = nameEnd - bw;
                float pulse = wait ? 0.5f + 0.5f * (float) Math.sin(Fx.time() * 6f) : 0f;
                int bg = wait ? Ui.withA(accent, Math.round(0x50 + 0x50 * pulse))
                        : key != null ? Ui.withA(0xFFFFFF, 0x1A) : Ui.withA(0xFFFFFF, 0x0C);
                Ui.roundRect(g, bx, ey + 3, bw, 12, 3, bg);
                int tc = wait ? 0xFFFFFFFF : key != null ? Ui.lerp(MUTED, TEXT, 0.5f) : Ui.withA(MUTED, 0x90);
                Ui.text(g, this.font, badge, bx + 4, ey + 5, tc, false);
                nameEnd = bx - 4;
            }
            name(g, en.name, nameX, ey + 5, nameEnd - nameX, textColor);

            if (en.settings != null) {
                // Drei Punkte fuer die Einstellungen; leuchten, wenn die Maus darueber ist
                boolean overDots = over && mouseX >= dotsX;
                int dc = overDots ? accent : Ui.lerp(Ui.withA(MUTED, 0xB0), TEXT, en.hover * 0.6f);
                int dx = dotsX + (DOTS_W - 2) / 2;
                for (int i = 0; i < 3; i++) {
                    Ui.fill(g, dx, ey + 4 + i * 4, dx + 2, ey + 6 + i * 4, dc);
                }
            }
            if (en.state != null) Ui.switchPill(g, dotsX - 2 - 16, ey + (ENTRY_H - 8) / 2, en.anim);
        }

        // Scrollbalken fuer lange Spalten
        if (col.scrollable && bodyShown && col.open > 0.98f) {
            int[] th = thumb(col);
            boolean overLane = mouseX >= right && mouseX < x + w && mouseY >= th[0] && mouseY < th[0] + th[1];
            col.laneHover += ((overLane ? 1f : 0f) - col.laneHover) * Ui.step(0.3f);
            Ui.scrollbar(g, right + 1, th[0] + dy, th[1], th[2] + dy, th[3], col.laneHover);
        }
    }

    /** Wartet auf die Taste fuer das gewaehlte Modul (Ruecktaste/Entf entfernt sie). */
    private void pollBind() {
        java.util.Set<Integer> now = Keybinds.pressedNow();
        waitHeld.retainAll(now); // losgelassene Tasten duerfen wieder zaehlen
        for (int code : now) {
            if (waitHeld.contains(code)) continue;
            if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE || code == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
                Keybinds.clear(waiting);
            } else if (code != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                Keybinds.set(waiting, code);
            } else {
                continue;
            }
            waiting = null;
            pendingRebuild = true; // Spaltenbreite an das neue Schild anpassen
            return;
        }
    }

    @Override
    public void onClose() {
        TestConfig.save();
        this.minecraft.gui.setScreen(parent);
    }

    public void removed() {
        GuiScale.restore();
    }
}
