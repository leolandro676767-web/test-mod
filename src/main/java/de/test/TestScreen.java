package de.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/** Hauptmenue im ClickGUI-Stil: Kategorien als Spalten, darunter die Module. */
public class TestScreen extends Screen {
    private static final int GAP = 8;
    private static final int HEADER_H = 22;
    private static final int ENTRY_H = 18;
    private static final int SETTINGS_W = 13; // Klickbereich rechts fuer die Einstellungen

    // Flieder / Lila statt Rosa
    private static final int PANEL = 0xE0140F1F;
    private static final int TEXT = 0xFFE8E2F3;
    private static final int TEXT_ON = 0xFFE3D4FF;
    private static final int MUTED = 0xFF8F84A8;
    private static final int TRACK_OFF = 0xFF3A3450;

    private static final class Entry {
        final String name;
        final BooleanSupplier state; // null = reiner Button ohne An/Aus
        final Runnable action;
        Supplier<Screen> settings; // null = keine Einstellungen
        boolean plain;             // Scroll-Zeile: ohne Pfeil rechts
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
        final List<Entry> visible = new ArrayList<>(); // gerade sichtbare Zeilen (inkl. Scroll-Zeilen)
        int x, y;
        float open = 1f; // 0 = eingeklappt, 1 = ausgeklappt (animiert)

        Column(String title) {
            this.title = title;
        }
    }

    /** Scroll-Position je Spalte (bleibt beim Schliessen erhalten). */
    private static final java.util.Map<String, Integer> SCROLL = new java.util.HashMap<>();
    private static final int SCROLL_STEP = 4;

    private final Screen parent;
    private final List<Column> columns = new ArrayList<>();
    private int fitRows = 10;
    private final long openedAt = System.currentTimeMillis();
    private int colW = 104;
    private int logoScale = 2;
    private float ga = 1f; // globale Transparenz (Einblenden)
    private int dy = 0;    // globaler Versatz (Hereingleiten)

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

    /** Modul mit An/Aus-Schalter und eigenem Einstellungs-Menue (Pfeil rechts). */
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
        misc.entries.add(new Entry("Schlie\u00dfen", null, this::onClose));

        columns.add(move);
        columns.add(player);
        columns.add(render);
        columns.add(world);
        columns.add(inv);
        columns.add(misc2);
        columns.add(misc);

        int n = columns.size();
        colW = Math.max(72, Math.min(Theme.columnWidth(), (this.width - 16) / n));
        logoScale = Theme.logoSize > 0 ? Theme.logoSize : (this.height >= 220 ? 2 : 1);
        int logoH = Theme.showLogo ? Logo.H * logoScale : 12;

        // Spalten ggf. auf mehrere Zeilen umbrechen, wenn sie nicht nebeneinander passen
        int perRow = Math.max(1, Math.min(n, (this.width - 16 + GAP) / (colW + GAP)));
        int gridRows = (n + perRow - 1) / perRow;
        int topY = logoH + 12 + 2;
        int rowGap = 10;
        int availV = this.height - 16 - topY;
        // So viele Modul-Zeilen passen je Spalte; laengere Spalten werden scrollbar
        fitRows = Math.max(5, ((availV - (gridRows - 1) * rowGap) / gridRows - HEADER_H) / ENTRY_H);
        int maxRows = 1;
        for (Column col : columns) maxRows = Math.max(maxRows, Math.min(fitRows, col.entries.size()));
        int rowH = HEADER_H + maxRows * ENTRY_H;
        int gridH = gridRows * rowH + (gridRows - 1) * rowGap;
        int y0 = topY + Math.max(0, (availV - gridH) / 2);

        for (int c = 0; c < n; c++) {
            Column col = columns.get(c);
            int gr = c / perRow;
            int inRow = c % perRow;
            int colsThisRow = Math.min(perRow, n - gr * perRow);
            int rowW = colsThisRow * colW + (colsThisRow - 1) * GAP;
            int rowX0 = (this.width - rowW) / 2;
            col.x = rowX0 + inRow * (colW + GAP);
            col.y = y0 + gr * (rowH + rowGap);
            col.open = Theme.isCollapsed(c) ? 0f : 1f;
            final int ci = c;
            // Klick auf die Kopfzeile klappt die Spalte ein bzw. aus
            addWidget(Button.builder(Component.empty(), b -> {
                Theme.toggleCollapsed(ci);
                TestConfig.save();
            }).bounds(col.x, col.y, colW, HEADER_H).build());
            buildVisible(col);
            for (int e = 0; e < col.visible.size(); e++) {
                Entry en = col.visible.get(e);
                en.x = col.x;
                en.y = col.y + HEADER_H + e * ENTRY_H;
                en.last = e == col.visible.size() - 1;
                en.anim = en.state != null && en.state.getAsBoolean() ? 1f : 0f;
                Runnable action = () -> {
                    if (!Theme.isCollapsed(ci)) en.action.run();
                };
                // Unsichtbare Klick-Bereiche (werden nicht gezeichnet, nur fuer Mausklicks):
                // links umschalten, rechts (Pfeil) Einstellungen oeffnen
                int toggleW = en.settings != null ? colW - SETTINGS_W : colW;
                addWidget(Button.builder(Component.empty(), b -> action.run())
                        .bounds(en.x, en.y, toggleW, ENTRY_H).build());
                if (en.settings != null) {
                    Supplier<Screen> s = en.settings;
                    addWidget(Button.builder(Component.empty(), b -> {
                        if (!Theme.isCollapsed(ci)) this.minecraft.gui.setScreen(s.get());
                    }).bounds(en.x + toggleW, en.y, SETTINGS_W, ENTRY_H).build());
                }
            }
        }
    }

    /** Waehlt die sichtbaren Zeilen; bei zu vielen kommen oben/unten Scroll-Zeilen dazu. */
    private void buildVisible(Column col) {
        col.visible.clear();
        int total = col.entries.size();
        if (total <= fitRows) {
            col.visible.addAll(col.entries);
            return;
        }
        int shown = fitRows - 2;
        int maxScroll = total - shown;
        int scroll = Math.max(0, Math.min(maxScroll, SCROLL.getOrDefault(col.title, 0)));
        SCROLL.put(col.title, scroll);
        int above = scroll;
        int below = total - shown - scroll;
        Entry up = new Entry(above > 0 ? "^  " + above + " weitere" : "^", null, () -> scroll(col, -SCROLL_STEP));
        Entry down = new Entry(below > 0 ? "v  " + below + " weitere" : "v", null, () -> scroll(col, SCROLL_STEP));
        up.plain = true;
        down.plain = true;
        col.visible.add(up);
        for (int i = scroll; i < scroll + shown; i++) col.visible.add(col.entries.get(i));
        col.visible.add(down);
    }

    private void scroll(Column col, int delta) {
        SCROLL.put(col.title, SCROLL.getOrDefault(col.title, 0) + delta);
        this.rebuildWidgets();
    }

    /** Mausrad ueber einer Spalte scrollt sie. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (Column col : columns) {
            int h = HEADER_H + col.visible.size() * ENTRY_H;
            boolean over = mouseX >= col.x && mouseX < col.x + colW && mouseY >= col.y && mouseY < col.y + h;
            if (over && col.entries.size() > fitRows) {
                scroll(col, scrollY > 0 ? -1 : 1);
                return true;
            }
        }
        return false;
    }

    // ---------- Zeichen-Helfer ----------

    private int al(int argb) {
        int a = Math.round((argb >>> 24) * ga);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int ar = a >>> 24, r1 = (a >> 16) & 255, g1 = (a >> 8) & 255, b1 = a & 255;
        int br = b >>> 24, r2 = (b >> 16) & 255, g2 = (b >> 8) & 255, b2 = b & 255;
        return (Math.round(ar + (br - ar) * t) << 24)
                | (Math.round(r1 + (r2 - r1) * t) << 16)
                | (Math.round(g1 + (g2 - g1) * t) << 8)
                | Math.round(b1 + (b2 - b1) * t);
    }

    private void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1 + dy, x2, y2 + dy, al(color));
    }

    /** Rechteck, optional mit abgerundeten unteren Ecken. */
    private void fillRows(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, boolean roundBottom) {
        if (!roundBottom) {
            fill(g, x, y, x + w, y + h, color);
            return;
        }
        fill(g, x, y, x + w, y + h - 2, color);
        fill(g, x + 1, y + h - 2, x + w - 1, y + h - 1, color);
        fill(g, x + 2, y + h - 1, x + w - 2, y + h, color);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, s, x, y + dy, al(color), true);
    }

    // ---------- Rendern ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Ui.beginFrame();

        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        float ease = 1f - (1f - t) * (1f - t);
        if (!Theme.animations) {
            ease = 1f;
        }
        ga = Math.max(0.06f, ease);
        dy = Math.round((1f - ease) * 10f);

        Ui.alpha = 1f;
        Fx.backdrop(g, this.width, this.height);
        Ui.alpha = ga;

        // Logo (oder nur Schriftzug)
        int cx = this.width / 2;
        if (Theme.showLogo) {
            int logoW = Logo.W * logoScale;
            int logoY = columns.get(0).y - 12 - Logo.H * logoScale;
            Logo.draw(g, cx - logoW / 2, logoY, logoScale, ga, dy);
        } else {
            String title = "1 9 1 2";
            text(g, title, cx - this.font.width(title) / 2, columns.get(0).y - 18, 0xFFFFFFFF);
        }

        for (Column col : columns) {
            int colIndex = columns.indexOf(col);
            float openTarget = Theme.isCollapsed(colIndex) ? 0f : 1f;
            col.open += (openTarget - col.open) * Ui.step(0.3f);
            if (Math.abs(openTarget - col.open) < 0.01f) col.open = openTarget;
            int fullH = col.visible.size() * ENTRY_H;
            int bodyH = Math.round(fullH * col.open);
            boolean bodyShown = bodyH >= 3;

            // Weiches Leuchten hinter der Spalte
            Fx.panelGlow(g, col.x, col.y + dy, colW, HEADER_H + bodyH, ease);
            // Schatten + Rand
            fillRows(g, col.x + 2, col.y + 3, colW, HEADER_H + bodyH, 0x55000000, true);
            fillRows(g, col.x - 1, col.y - 1, colW + 2, HEADER_H + bodyH + 2, Theme.border(), true);

            // Koerper
            if (bodyShown) {
                fillRows(g, col.x, col.y + HEADER_H, colW, bodyH, PANEL, true);
            }

            // Kopfzeile mit Farbverlauf und abgerundeten oberen Ecken
            for (int r = 0; r < HEADER_H; r++) {
                int inset = r == 0 ? 2 : r == 1 ? 1 : 0;
                if (!bodyShown) {
                    // ohne Koerper: auch die unteren Ecken der Kopfzeile abrunden
                    inset = Math.max(inset, r == HEADER_H - 1 ? 2 : r == HEADER_H - 2 ? 1 : 0);
                }
                int base = Theme.head(columns.indexOf(col));
                int color = lerp(lerp(base, 0xFFFFFFFF, 0.22f), lerp(base, 0xFF000000, 0.18f),
                        r / (float) (HEADER_H - 1));
                fill(g, col.x + inset, col.y + r, col.x + colW - inset, col.y + r + 1, color);
            }
            fill(g, col.x, col.y + HEADER_H - 1, col.x + colW, col.y + HEADER_H, 0x40FFFFFF);
            text(g, col.title, col.x + 8, col.y + 7, 0xFFFFFFFF);

            int active = 0, toggles = 0;
            for (Entry en : col.entries) {
                if (en.state != null) {
                    toggles++;
                    if (en.state.getAsBoolean()) active++;
                }
            }
            String mark = Theme.isCollapsed(colIndex) ? "+" : "-";
            text(g, mark, col.x + colW - 8 - this.font.width(mark), col.y + 7, 0xFFFFFFFF);
            if (toggles > 0) {
                String count = active + "/" + toggles;
                text(g, count, col.x + colW - 8 - this.font.width(mark) - 6 - this.font.width(count),
                        col.y + 7, 0xCCFFFFFF);
            }

            for (Entry en : col.visible) {
                // nur Zeilen zeichnen, die beim Ein-/Ausklappen schon komplett sichtbar sind
                if (en.y + ENTRY_H > col.y + HEADER_H + bodyH) continue;
                boolean on = en.state != null && en.state.getAsBoolean();
                boolean over = mouseX >= en.x && mouseX < en.x + colW
                        && mouseY >= en.y && mouseY < en.y + ENTRY_H;
                float speedH = Ui.step(0.3f);
                float speedA = Ui.step(0.35f);
                en.hover += ((over ? 1f : 0f) - en.hover) * speedH;
                en.anim += ((on ? 1f : 0f) - en.anim) * speedA;

                int ph = ENTRY_H - (en.last ? 2 : 0);
                if (on) {
                    fillRows(g, en.x, en.y, colW, ENTRY_H, Theme.onBg(), en.last);
                    // sanfter Schimmer von links
                    for (int i = 0; i < 6; i++) {
                        int a = 0x30 - i * 7;
                        fill(g, en.x + 2 + i * 5, en.y, en.x + 7 + i * 5, en.y + ph,
                                (a << 24) | (Theme.accent() & 0xFFFFFF));
                    }
                    fill(g, en.x, en.y + 1, en.x + 2, en.y + ph - 1, Theme.accent());
                } else if (en.hover > 0.02f) {
                    fillRows(g, en.x, en.y, colW, ENTRY_H, lerp(PANEL, Theme.hoverBg(), en.hover), en.last);
                }
                if (!en.last) {
                    fill(g, en.x + 6, en.y + ENTRY_H - 1, en.x + colW - 6, en.y + ENTRY_H, 0x14FFFFFF);
                }

                int textColor = on ? TEXT_ON : lerp(TEXT, 0xFFFFFFFF, en.hover);
                int rightPad = en.settings != null ? SETTINGS_W : 8;
                int sx = en.x + colW - rightPad - 16;
                int nameX = en.x + 8 + (on ? 2 : 0);
                int nameMax = (en.state != null ? sx - 4 : en.x + colW - 16) - nameX;
                text(g, fitName(en.name, nameMax), nameX, en.y + 5, textColor);

                if (en.settings != null) {
                    // Pfeil fuer die Einstellungen; leuchtet, wenn die Maus darueber ist
                    boolean overArrow = over && mouseX >= en.x + colW - SETTINGS_W;
                    String chev = ">";
                    text(g, chev, en.x + colW - SETTINGS_W + (SETTINGS_W - this.font.width(chev)) / 2,
                            en.y + 5, overArrow ? Theme.accent() : MUTED);
                }
                if (en.state != null) {
                    // Schalter rechts: Spur + Knopf
                    int sy = en.y + (ENTRY_H - 8) / 2;
                    int track = lerp(TRACK_OFF, Theme.accent(), en.anim);
                    fill(g, sx + 1, sy, sx + 15, sy + 8, track);
                    fill(g, sx, sy + 1, sx + 16, sy + 7, track);
                    int kx = sx + 1 + Math.round(en.anim * 8f);
                    fill(g, kx, sy + 1, kx + 6, sy + 7, 0xFFFFFFFF);
                } else if (!en.plain) {
                    String chev = ">";
                    text(g, chev, en.x + colW - 8 - this.font.width(chev) + Math.round(en.hover * 2f),
                            en.y + 5, lerp(MUTED, Theme.accent(), en.hover));
                }
            }
        }

        // Fusszeile
        String keys = TestClient.toggleKey.getTranslatedKeyMessage().getString()
                + " / " + TestClient.guiKey.getTranslatedKeyMessage().getString();
        fill(g, 0, this.height - 16, this.width, this.height, 0x80000000);
        text(g, "1912 Client   |   Klick: an/aus   |   > : Einstellungen   |   Tasten: " + keys,
                6, this.height - 12, MUTED);
    }

    /** Kuerzt einen Namen mit "..", wenn die Spalte zu schmal ist. */
    private String fitName(String s, int max) {
        if (max <= 0 || this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "..") > max) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    @Override
    public void onClose() {
        TestConfig.save();
        this.minecraft.gui.setScreen(parent);
    }
}
