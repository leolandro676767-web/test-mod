package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen fuer den Debris Miner: jeder Teil hat einen eigenen Abschnitt mit eigenem Schalter. */
public class DebrisScreen extends Form {
    private Button start;

    public DebrisScreen(Screen parent) {
        super(Component.literal("Debris Miner"), parent);
    }

    private static String startLabel() {
        return DebrisMiner.isRunning() ? "Debris Miner STOPPEN" : "Debris Miner starten";
    }

    @Override
    protected void build() {
        description("Gräbt im Nether einen Tunnel auf der eingestellten Höhe und baut Antiken Schrott ab, "
                + "sobald er am Tunnel sichtbar wird - auch ganze Adern. Danach geht es auf der alten "
                + "Tunnelachse weiter. Für Server mit Anti-Xray: Es wird nur die direkte Umgebung geprüft.");
        start = wideButton(startLabel(), true, this::startOrStop);
        note(DebrisMiner::status);

        section("Tunnel");
        slider("Ziel-Höhe (Y)", 5, 118, () -> DebrisMiner.targetY, v -> DebrisMiner.targetY = v, 15);
        toggle("Erst auf die Ziel-Höhe gehen", () -> DebrisMiner.descendFirst, v -> DebrisMiner.descendFirst = v, true);
        choice("Tunnel-Muster", DebrisMiner.PATTERNS, () -> DebrisMiner.pattern, v -> DebrisMiner.pattern = v, 0);
        choice("Richtung", DebrisMiner.DIRECTIONS, () -> DebrisMiner.direction, v -> DebrisMiner.direction = v, 0);
        number("Max. Länge (0 = unbegrenzt)", () -> DebrisMiner.maxLength,
                v -> DebrisMiner.maxLength = Math.max(0, v), 0);
        toggle("Löcher im Boden zubauen", () -> DebrisMiner.fillFloor, v -> DebrisMiner.fillFloor = v, true);
        toggle("An einer Chunk-Grenze entlang graben", () -> DebrisMiner.chunkBorder,
                v -> DebrisMiner.chunkBorder = v, true);
        toggle("Nach Umwegen zurück auf die Achse", () -> DebrisMiner.returnAxis, v -> DebrisMiner.returnAxis = v,
                true);
        toggle("Netherrack und Gestein wegwerfen", () -> DebrisMiner.dropJunk, v -> DebrisMiner.dropJunk = v, true);
        slider("Netherrack behalten (Stapel)", 0, 9, () -> DebrisMiner.keepNetherrack,
                v -> DebrisMiner.keepNetherrack = v, 1);
        note("Ohne „Erst auf die Ziel-Höhe gehen“ gräbt er auf der Höhe, auf der du gerade stehst. Ohne "
                + "„Löcher im Boden zubauen“ weicht er Löchern aus. Weggeworfen wird nach hinten, damit er es nicht "
                + "wieder aufsammelt.");

        note("Mit Chunk-Grenze gräbt er von Anfang an an der nächsten Chunk-Grenze entlang (höchstens 8 Blöcke "
                + "daneben): Vor dem Abstieg auf die Ziel-Höhe geht er erst seitlich auf die Grenze und gräbt dort "
                + "senkrecht hinunter; ein 2x2-Tunnel liegt genau auf beiden Seiten. Nach Umwegen kommt er immer auf die Grenze zurück.");

        section("Bewegung");
        toggle("Menschlich bewegen", () -> DebrisMiner.humanize, v -> DebrisMiner.humanize = v, true);
        toggle("Ab und zu umschauen", () -> DebrisMiner.lookAround, v -> DebrisMiner.lookAround = v, true);
        toggle("Netherrack durchsprinten", () -> DebrisMiner.sprintMine, v -> DebrisMiner.sprintMine = v, true);
        note("Dreht sich weicher und nicht immer gleich schnell, mit einem leichten Zittern wie an der Maus. Beim "
                + "Gehen wandert der Blick ein paar Grad, und nach jedem Block wartet er unterschiedlich lange. "
                + "Ducken nur noch an Kanten und dann eine Weile am Stück, nicht mehr für einzelne Ticks; in die "
                + "Blockmitte tippt er mit kurzen Schritten statt zu schleichen.");
        note("„Ab und zu umschauen“ (nur mit „Menschlich bewegen“): Alle 45 Sekunden bis 2 Minuten bleibt er im "
                + "Tunnel kurz stehen, schaut zur Seite und macht dann weiter – nur, wenn keine Lava in der Nähe "
                + "ist.");
        note("„Netherrack durchsprinten“: Baut dein Werkzeug Netherrack sofort ab (z.B. Effizienz V), hält er die "
                + "Maustaste gedrückt und sprintet durch, mit festem Blick 19° nach unten. Bei 18° bis 20° trifft "
                + "das Fadenkreuz beim Laufen erst den Fußblock weiter vorne und dann den Kopfblock, aber nie den "
                + "Boden. Nur beim 1x2-Tunnel; vor Lava, Debris, Kies oder härterem Gestein gräbt er wieder Block "
                + "für Block.");

        section("Abbau");
        slider("Scan-Verzögerung (Ticks)", 0, 20, () -> DebrisMiner.scanDelay, v -> DebrisMiner.scanDelay = v, 3);
        slider("Scan-Radius (Blöcke)", 1, 3, () -> DebrisMiner.scanRadius, v -> DebrisMiner.scanRadius = v, 2);
        toggle("Nur echte Erze (gegen Anti-Xray-Tarnung)", () -> DebrisMiner.onlyExposed,
                v -> DebrisMiner.onlyExposed = v, true);
        toggle("Ganze Ader abbauen", () -> DebrisMiner.veinMine, v -> DebrisMiner.veinMine = v, true);
        toggle("Zu verdecktem Debris graben", () -> DebrisMiner.digToHidden, v -> DebrisMiner.digToHidden = v, true);
        toggle("Debris unter dem Boden abbauen", () -> DebrisMiner.mineFloor, v -> DebrisMiner.mineFloor = v, true);
        toggle("Debris über der Decke abbauen", () -> DebrisMiner.mineCeiling, v -> DebrisMiner.mineCeiling = v,
                true);
        toggle("Fund im Chat melden", () -> DebrisMiner.chatFound, v -> DebrisMiner.chatFound = v, true);
        toggle("Ton bei Fund", () -> DebrisMiner.foundSound, v -> DebrisMiner.foundSound = v, false);
        note("Scan-Verzögerung: so lange wartet er nach jedem Block, bis der Server die Nachbarn zeigt. "
                + "Scan-Radius: so weit um den Tunnel wird geschaut (der Server deckt meist 2 Blöcke auf). "
                + "„Nur echte Erze“ zählt nur Debris, das an Luft grenzt oder nahe an einem gerade "
                + "abgebauten Block liegt - getarnte Erze im Gestein werden ignoriert. Ohne „Zu verdecktem Debris "
                + "graben“ baut er nur ab, was er vom Tunnel aus sehen kann.");

        section("Einsammeln");
        toggle("Gedropptes Debris einsammeln", () -> DebrisMiner.collect, v -> DebrisMiner.collect = v, true);
        slider("Suchradius (Blöcke)", 2, 16, () -> DebrisMiner.collectRadius, v -> DebrisMiner.collectRadius = v, 8);
        slider("Max. Wartezeit (s)", 2, 60, () -> DebrisMiner.collectTimeout,
                v -> DebrisMiner.collectTimeout = v, 10);
        toggle("Hoch liegende Items herunterholen", () -> DebrisMiner.collectHigh, v -> DebrisMiner.collectHigh = v,
                true);
        toggle("Für Items in Gruben hinuntersteigen", () -> DebrisMiner.collectPits,
                v -> DebrisMiner.collectPits = v, true);
        toggle("Auch andere Funde einsammeln (z.B. Gold, Quarz)", () -> DebrisMiner.collectOther,
                v -> DebrisMiner.collectOther = v, true);
        note("Andere Funde: nur Items aus Blöcken, die er selbst abgebaut hat – kein Netherrack oder anderes "
                + "Gestein, das er sowieso wegwirft, und nichts von anderen Spielern. Was voraus im Tunnel liegt, "
                + "nimmt er beim Weitergehen mit; bleibt etwas neben oder hinter ihm liegen, holt er es kurz.");

        section("Lava");
        toggle("Lava-Schutz", () -> DebrisMiner.lavaAvoid, v -> DebrisMiner.lavaAvoid = v, true);
        slider("Sicherheitsabstand (Blöcke)", 1, 6, () -> DebrisMiner.lavaDistance,
                v -> DebrisMiner.lavaDistance = v, 3);
        choice("Verhalten", DebrisMiner.LAVA_MODES, () -> DebrisMiner.lavaMode, v -> DebrisMiner.lavaMode = v, 0);
        toggle("Zurücklaufen, wenn es seitlich nicht weitergeht", () -> DebrisMiner.lavaBacktrack,
                v -> DebrisMiner.lavaBacktrack = v, true);
        note("Blöcke direkt neben Lava baut er nie ab. Umgehen: seitlich ausweichen, an der Lava vorbei und "
                + "danach zurück auf die alte Tunnelachse. Zubauen: sichtbare Lava und Lava-Böden mit Netherrack "
                + "zuschütten und darüber weiter (Lava hinter Gestein wird trotzdem umgangen).");

        section("Nachschub");
        toggle("Nachschub holen", () -> DebrisMiner.refill, v -> DebrisMiner.refill = v, true);
        toggle("Spitzhacken in die Hotbar holen", () -> DebrisMiner.refillPickaxe,
                v -> DebrisMiner.refillPickaxe = v, true);
        toggle("Essen in die Hotbar holen", () -> DebrisMiner.refillFood, v -> DebrisMiner.refillFood = v, true);
        toggle("Baublöcke in die Hotbar holen", () -> DebrisMiner.refillBlocks, v -> DebrisMiner.refillBlocks = v,
                true);
        toggle("Bei Hunger essen", () -> DebrisMiner.autoEat, v -> DebrisMiner.autoEat = v, true);
        slider("Essen bei Hunger ≤", 1, 19, () -> DebrisMiner.eatHunger, v -> DebrisMiner.eatHunger = v, 14);
        toggle("Goldäpfel nicht essen", () -> DebrisMiner.keepGapples, v -> DebrisMiner.keepGapples = v, true);
        toggle("Aus Shulker-Kisten", () -> DebrisMiner.refillShulker, v -> DebrisMiner.refillShulker = v, false);
        toggle("Aus der Endertruhe", () -> DebrisMiner.refillEnderChest, v -> DebrisMiner.refillEnderChest = v,
                false);
        toggle("Kiste danach wieder abbauen", () -> DebrisMiner.boxPickup, v -> DebrisMiner.boxPickup = v, true);
        note("Holt Spitzhacken und Essen aus dem Inventar in die Hotbar. Ist nichts mehr da, stellt er eine "
                + "Shulker-Kiste oder Endertruhe im Tunnel auf und nimmt Nachschub heraus (die Endertruhe baut er nur "
                + "mit einer Spitzhacke mit Behutsamkeit wieder ab). „Bei Hunger essen“ geht auch ohne Nachschub.");

        section("Werkzeugschutz");
        toggle("Werkzeugschutz", () -> DebrisMiner.toolGuard, v -> DebrisMiner.toolGuard = v, true);
        choice("Grenze in", DebrisMiner.TOOL_UNITS, () -> DebrisMiner.toolUnit, v -> DebrisMiner.toolUnit = v, 0);
        slider("Grenze (%)", 1, 50, () -> DebrisMiner.toolPercent, v -> DebrisMiner.toolPercent = v, 5);
        slider("Grenze (Punkte)", 1, 300, () -> DebrisMiner.toolPoints, v -> DebrisMiner.toolPoints = v, 60);
        toggle("Automatisch wechseln", () -> DebrisMiner.toolSwitch, v -> DebrisMiner.toolSwitch = v, true);
        toggle("Warnton", () -> DebrisMiner.toolSound, v -> DebrisMiner.toolSound = v, true);
        toggle("Warnung im Bild", () -> DebrisMiner.toolToast, v -> DebrisMiner.toolToast = v, true);
        toggle("Warnung im Chat", () -> DebrisMiner.toolChat, v -> DebrisMiner.toolChat = v, true);
        toggle("Anhalten, wenn keine Spitzhacke mehr da ist", () -> DebrisMiner.toolStop,
                v -> DebrisMiner.toolStop = v, true);
        note("Erreicht eine Spitzhacke die Grenze, wird gewarnt und auf eine andere gewechselt. Ohne „Automatisch "
                + "wechseln“ gräbt er mit ihr weiter - oder hält an, wenn „Anhalten“ an ist. Gezählt werden nur "
                + "Spitzhacken, die Antiken Schrott abbauen können (Diamant oder Netherit).");

        section("Spieler in der Nähe");
        toggle("Verbindung trennen (Auto-Log)", () -> DebrisMiner.playerLog, v -> DebrisMiner.playerLog = v, true);
        slider("Reichweite in Chunks (0 = ganze Sichtweite)", 0, 32, () -> DebrisMiner.playerChunks,
                v -> DebrisMiner.playerChunks = v, 6);
        toggle("Freundesliste des Clients mitbenutzen", () -> DebrisMiner.useFriends,
                v -> DebrisMiner.useFriends = v, true);
        text("Weitere Freunde (mit Komma getrennt)", () -> DebrisMiner.whitelist, v -> DebrisMiner.whitelist = v, "",
                256, null);
        toggle("Nicht starten, wenn schon jemand da ist", () -> DebrisMiner.playerCheckStart,
                v -> DebrisMiner.playerCheckStart = v, true);

        section("Totem");
        toggle("Nach Totem trennen (Auto-Log)", () -> DebrisMiner.totemLog, v -> DebrisMiner.totemLog = v, true);
        slider("Verzögerung (Ticks, 0 = sofort)", 0, 100, () -> DebrisMiner.totemDelay,
                v -> DebrisMiner.totemDelay = v, 0);
        toggle("Ignorieren, solange noch ein Totem da ist", () -> DebrisMiner.totemIgnoreSpare,
                v -> DebrisMiner.totemIgnoreSpare = v, false);

        section("Rückzug (Auto-Home)");
        toggle("Rückzug-Befehl senden", () -> DebrisMiner.home, v -> DebrisMiner.home = v, false);
        text("Befehl", () -> DebrisMiner.homeCommand, v -> DebrisMiner.homeCommand = v, "/home", 256,
                () -> "/home");
        presets("Schnellwahl", DebrisMiner.HOME_PRESETS, () -> DebrisMiner.homeCommand,
                v -> DebrisMiner.homeCommand = v);
        slider("Pause zwischen mehreren Befehlen (Ticks)", 0, 200, () -> DebrisMiner.homeCmdDelay,
                v -> DebrisMiner.homeCmdDelay = v, 20);
        note("Der Befehl ist frei wählbar, z.B. /home, /home farm, /spawn oder /warp shop - der / am Anfang ist "
                + "egal. Mehrere Befehle mit ; trennen (z.B. /spawn; /afk), dazwischen wartet er die Pause "
                + "(20 Ticks = 1 Sekunde). Danach hält der Debris Miner an.");
        toggle("Bei wenig Leben", () -> DebrisMiner.homeLowHp, v -> DebrisMiner.homeLowHp = v, true);
        slider("Leben ≤ (Herzen)", 1, 10, () -> DebrisMiner.homeHearts, v -> DebrisMiner.homeHearts = v, 4);
        triggerCommand("Befehl bei wenig Leben", () -> DebrisMiner.homeCmdLowHp, v -> DebrisMiner.homeCmdLowHp = v);
        toggle("Bei vollem Inventar", () -> DebrisMiner.homeFull, v -> DebrisMiner.homeFull = v, true);
        triggerCommand("Befehl bei vollem Inventar", () -> DebrisMiner.homeCmdFull, v -> DebrisMiner.homeCmdFull = v);
        toggle("Ohne Spitzhacke", () -> DebrisMiner.homeNoTool, v -> DebrisMiner.homeNoTool = v, true);
        triggerCommand("Befehl ohne Spitzhacke", () -> DebrisMiner.homeCmdNoTool,
                v -> DebrisMiner.homeCmdNoTool = v);
        toggle("Bei Spieler in der Nähe", () -> DebrisMiner.homePlayer, v -> DebrisMiner.homePlayer = v, false);
        triggerCommand("Befehl bei Spieler in der Nähe", () -> DebrisMiner.homeCmdPlayer,
                v -> DebrisMiner.homeCmdPlayer = v);
        toggle("Nach Totem", () -> DebrisMiner.homeTotem, v -> DebrisMiner.homeTotem = v, false);
        triggerCommand("Befehl nach Totem", () -> DebrisMiner.homeCmdTotem, v -> DebrisMiner.homeCmdTotem = v);
        toggle("Wenn er feststeckt", () -> DebrisMiner.homeStuck, v -> DebrisMiner.homeStuck = v, false);
        triggerCommand("Befehl, wenn er feststeckt", () -> DebrisMiner.homeCmdStuck,
                v -> DebrisMiner.homeCmdStuck = v);
        note("Jeder Auslöser kann einen eigenen Befehl haben (z.B. bei wenig Leben /spawn, bei vollem Inventar "
                + "/home lager). Leer = der Befehl von oben. „Bei Spieler in der Nähe“ nutzt Radius und Freunde von "
                + "oben; „Bei Spieler in der Nähe“ und „Nach Totem“ greifen nur, wenn das Trennen dort aus ist. "
                + "„Wenn er feststeckt“: Lava ringsum, Weg blockiert oder kein Rückweg.");


        section("Anzeige");
        toggle("Zustand im Bild anzeigen", () -> DebrisMiner.showStatus, v -> DebrisMiner.showStatus = v, true);
        toggle("Debris und nächsten Abschnitt hervorheben", () -> DebrisMiner.highlight,
                v -> DebrisMiner.highlight = v, true);
        toggle("Weg bei Umwegen anzeigen", () -> DebrisMiner.showPath, v -> DebrisMiner.showPath = v, true);
        toggle("Von der Seite zuschauen (Free Look)", () -> RenderMods.freeLook, v -> {
            RenderMods.freeLook = v;
            if (v) RenderMods.freecam = false;
        }, false);
        footer(null, null);
    }

    private void startOrStop() {
        if (DebrisMiner.isRunning()) {
            DebrisMiner.stop(this.minecraft, "Debris Miner aus");
            start.setMessage(Component.literal(startLabel()));
            return;
        }
        TestConfig.save();
        String error = DebrisMiner.start(this.minecraft);
        if (error != null) {
            start.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }

    /** Eigener Befehl fuer einen Ausloeser; leer zeigt grau, welcher Befehl stattdessen gilt. */
    private void triggerCommand(String rawName, java.util.function.Supplier<String> get,
            java.util.function.Consumer<String> set) {
        text(rawName, get, set, "", 256, () -> {
            var cmds = DebrisMiner.commands(DebrisMiner.homeCommand);
            return "wie oben: " + (cmds.isEmpty() ? "/home" : String.join("; ", cmds));
        });
    }
}

/** Einstellungen fuer den Tunnel Miner (gerade graben, z.B. Strip Mining in der Overworld). */
class TunnelScreen extends Form {
    private Button start;

    public TunnelScreen(Screen parent) {
        super(Component.literal("Tunnel Miner"), parent);
    }

    private static String startLabel() {
        return TunnelMiner.isRunning() ? "Tunnel Miner STOPPEN" : "Tunnel Miner starten";
    }

    @Override
    protected void build() {
        description("Gräbt einfach geradeaus, auf der Höhe, auf der du stehst – gedacht für die Overworld (Strip "
                + "Mining), geht aber überall. Er hält die Maustaste gedrückt und behält einen festen Blickwinkel, "
                + "statt jeden Block einzeln anzuschauen.");
        start = wideButton(startLabel(), true, this::startOrStop);
        note(TunnelMiner::status);

        section("Tunnel");
        choice("Größe", TunnelMiner.SIZES, () -> TunnelMiner.size, v -> TunnelMiner.size = v, 0);
        choice("Richtung", DebrisMiner.DIRECTIONS, () -> TunnelMiner.direction, v -> TunnelMiner.direction = v, 0);
        number("Max. Länge (0 = unbegrenzt)", () -> TunnelMiner.maxLength,
                v -> TunnelMiner.maxLength = Math.max(0, v), 0);
        choice("Abbauen", TunnelMiner.METHODS, () -> TunnelMiner.method, v -> TunnelMiner.method = v, 0);
        note("„Fester Blickwinkel“: Er hält die Taste gedrückt und bewegt die Maus nicht zu jedem Block. Beim "
                + "1x2-Tunnel schaut er 19° nach unten: Das Fadenkreuz trifft erst den Kopfblock, dann durch das Loch "
                + "den Fußblock und die Fußblöcke weiter vorne – aber nie den Boden. Er geht nach, sobald vorne Platz "
                + "ist. „Block für Block“ schaut jeden Block einzeln an (langsamer).");
        toggle("3x3: Reste einzeln abbauen", () -> TunnelMiner.cleanup, v -> TunnelMiner.cleanup = v, true);
        note("„3x3 (3x3-Spitzhacke)“: Nimm die 3x3-Spitzhacke beim Start in die Hand. Er schaut waagerecht auf den "
                + "mittleren Block jedes Abschnitts, die Spitzhacke nimmt die acht drumherum mit. Was sie nicht "
                + "mitnimmt (z.B. Erze), baut er mit einer zweiten, normalen Spitzhacke aus der Hotbar einzeln ab – "
                + "ohne die bleiben solche Reste stehen (außer sie sind im Weg).");
        toggle("Löcher im Boden zubauen", () -> TunnelMiner.fillFloor, v -> TunnelMiner.fillFloor = v, true);
        choice("Bei Lava oder Wasser", TunnelMiner.FLUID_MODES, () -> TunnelMiner.fluidMode,
                v -> TunnelMiner.fluidMode = v, 0);
        toggle("Nach dem Ausweichen zurück auf die Linie", () -> TunnelMiner.returnAxis,
                v -> TunnelMiner.returnAxis = v, true);
        note("Lava und Wasser erkennt er, bevor er den Block daneben aufmacht. „Ausweichen“: ein paar Blöcke zur "
                + "Seite und parallel weiter; danach zurück auf die alte Linie, sobald es sicher ist.");

        section("Bewegung");
        toggle("Menschlich bewegen", () -> TunnelMiner.humanize, v -> TunnelMiner.humanize = v, true);
        note("Dreht sich weicher, mit leichtem Zittern wie an der Maus; beim Abbauen hält er die Maus ruhig, und "
                + "er bleibt nicht immer genau gleich weit vor der Wand stehen.");

        section("Erze");
        toggle("Erze an Wand und Decke mitnehmen", () -> TunnelMiner.ores, v -> TunnelMiner.ores = v, true);
        choice("Welche Erze", TunnelMiner.ORE_SETS, () -> TunnelMiner.oreSet, v -> TunnelMiner.oreSet = v, 0);
        note("Nur Erze, die man vom Tunnel aus sieht (kein Röntgenblick). „Wertvolle“: Diamant, Smaragd, Gold, "
                + "Eisen, Lapis, Redstone. Erze im Boden lässt er liegen, damit kein Loch entsteht. Mit der "
                + "3x3-Spitzhacke nimmt er für Erze lieber eine normale Spitzhacke.");

        section("Einsammeln");
        toggle("Liegengebliebene Items einsammeln", () -> TunnelMiner.collect, v -> TunnelMiner.collect = v, true);
        slider("Umkreis (Blöcke)", 2, 16, () -> TunnelMiner.collectRadius, v -> TunnelMiner.collectRadius = v, 8);
        note("Was beim Abbauen vor ihm herausfällt, nimmt er beim Weitergehen mit. Bleibt etwas neben oder hinter "
                + "ihm liegen (z.B. Erz aus der Wand), geht er kurz hin und dann zurück in den Tunnel. Nur eigene "
                + "Items, und kein Gestein, das er sowieso wegwirft.");

        section("Inventar");
        toggle("Gestein wegwerfen", () -> TunnelMiner.dropJunk, v -> TunnelMiner.dropJunk = v, true);
        slider("Baublöcke behalten (Stapel)", 0, 9, () -> TunnelMiner.keepBlocks, v -> TunnelMiner.keepBlocks = v, 1);
        toggle("Automatisch essen", () -> TunnelMiner.autoEat, v -> TunnelMiner.autoEat = v, true);
        slider("Essen bei Hunger", 1, 19, () -> TunnelMiner.eatHunger, v -> TunnelMiner.eatHunger = v, 14);
        toggle("Goldäpfel nicht essen", () -> TunnelMiner.keepGapples, v -> TunnelMiner.keepGapples = v, true);
        note("Weggeworfen werden nur volle Stapel, nach hinten. Ein paar Baublöcke (z.B. Bruchstein) behält er zum "
                + "Zubauen von Löchern. Ist das Inventar voll, hält er an.");

        section("Werkzeug");
        toggle("Spitzhacke schonen", () -> TunnelMiner.toolGuard, v -> TunnelMiner.toolGuard = v, true);
        slider("Grenze (% Haltbarkeit)", 1, 50, () -> TunnelMiner.toolPercent, v -> TunnelMiner.toolPercent = v, 5);
        toggle("Anhalten, wenn keine Spitzhacke mehr da ist", () -> TunnelMiner.toolStop,
                v -> TunnelMiner.toolStop = v, true);

        section("Auto-Log");
        toggle("Bei Spieler in der Nähe trennen", () -> TunnelMiner.playerLog, v -> TunnelMiner.playerLog = v, true);
        slider("Reichweite in Chunks (0 = ganze Sichtweite)", 0, 32, () -> TunnelMiner.playerChunks,
                v -> TunnelMiner.playerChunks = v, 6);
        toggle("Freundesliste des Clients mitbenutzen", () -> TunnelMiner.useFriends,
                v -> TunnelMiner.useFriends = v, true);
        text("Weitere Freunde (mit Komma getrennt)", () -> TunnelMiner.whitelist, v -> TunnelMiner.whitelist = v, "",
                256, null);
        toggle("Nicht starten, wenn schon jemand da ist", () -> TunnelMiner.playerCheckStart,
                v -> TunnelMiner.playerCheckStart = v, true);
        toggle("Bei wenig Leben trennen", () -> TunnelMiner.healthLog, v -> TunnelMiner.healthLog = v, true);
        slider("Bei Herzen (oder weniger)", 1, 10, () -> TunnelMiner.logHearts, v -> TunnelMiner.logHearts = v, 4);
        toggle("Nach einem Totem trennen", () -> TunnelMiner.totemLog, v -> TunnelMiner.totemLog = v, true);
        note("Gilt, solange der Tunnel Miner läuft. Danach verbindet Auto Reconnect nicht von selbst neu. Unabhängig "
                + "davon gibt es im Menü unter „Welt“ das allgemeine Auto-Logout (immer aktiv, eigene Herzen).");


        section("Anzeige");
        toggle("Zustand im Bild anzeigen", () -> TunnelMiner.showStatus, v -> TunnelMiner.showStatus = v, true);
        toggle("Nächsten Abschnitt hervorheben", () -> TunnelMiner.highlight, v -> TunnelMiner.highlight = v, true);
        footer(null, null);
    }

    private void startOrStop() {
        if (TunnelMiner.isRunning()) {
            TunnelMiner.stop(this.minecraft, "Tunnel Miner aus");
            start.setMessage(Component.literal(startLabel()));
            return;
        }
        TestConfig.save();
        String error = TunnelMiner.start(this.minecraft);
        if (error != null) {
            start.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }
}

/** Koordinaten waehlen und dorthin laufen (Wegsuche wie Baritone). */
class GotoScreen extends Form {
    private Button start;

    public GotoScreen(Screen parent) {
        super(Component.literal("Zu Koordinaten gehen"), parent);
    }

    private static String startLabel() {
        return TestClient.areaActive && TestClient.gotoMode ? "Gehen STOPPEN" : "Losgehen";
    }

    @Override
    protected void build() {
        description("Sucht den besten Weg zum Ziel: läuft gerade Strecken am Stück,\n"
                + "springt auf Stufen und über Lücken, schwimmt und baut nur ab,\n"
                + "wenn es nicht anders geht.");
        section("Ziel");
        coords("Koordinaten",
                () -> new int[] {TestClient.gx, TestClient.gy, TestClient.gz},
                v -> {
                    TestClient.gx = v[0];
                    TestClient.gy = v[1];
                    TestClient.gz = v[2];
                });
        start = wideButton(startLabel(), true, this::startOrStop);
        note(() -> TestClient.gotoMode ? Walker.status(this.minecraft) : "Bereit");

        section("Weg");
        toggle("Blöcke abbauen", () -> TestClient.autoMine, v -> TestClient.autoMine = v, true);
        toggle("Blöcke setzen", () -> Walker.allowPlace, v -> Walker.allowPlace = v, true);
        toggle("Über Lücken springen", () -> Walker.allowParkour, v -> Walker.allowParkour = v, true);
        toggle("Sprinten", () -> Walker.sprint, v -> Walker.sprint = v, true);
        slider("Max. Fallhöhe", 1, 10, () -> Walker.maxFall, v -> Walker.maxFall = v, 3);

        section("Anzeige");
        toggle("Weg anzeigen", () -> Walker.showPath, v -> Walker.showPath = v, true);
        toggle("Von der Seite zuschauen (Free Look)", () -> RenderMods.freeLook, v -> {
            RenderMods.freeLook = v;
            if (v) RenderMods.freecam = false;
        }, false);
        toggle("Ziel hervorheben", () -> RenderMods.miningHighlight, v -> RenderMods.miningHighlight = v, true);
        choice("Umriss-Stil", Render3D.STYLES, () -> RenderMods.outlineStyle, v -> RenderMods.outlineStyle = v, 2);
        footer(null, null);
    }

    private void startOrStop() {
        if (TestClient.areaActive && TestClient.gotoMode) {
            TestClient.stopArea(this.minecraft, "Gehen abgebrochen");
            start.setMessage(Component.literal(startLabel()));
            return;
        }
        String error = TestClient.startGoto(this.minecraft);
        if (error != null) {
            start.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }
}

/** Block waehlen und automatisch abbauen lassen. */
class BlockScreen extends Form {
    private Button select;
    private Button start;

    public BlockScreen(Screen parent) {
        super(Component.literal("Block abbauen"), parent);
    }

    private static String selectLabel() {
        return "Block aus Liste w\u00e4hlen (" + TestClient.selectedBlockName() + ")";
    }

    private static String startLabel() {
        return TestClient.areaActive ? "Abbau STOPPEN" : "Abbau STARTEN";
    }

    @Override
    protected void build() {
        description("W\u00e4hle einen Block aus der Liste aller Bl\u00f6cke\noder \u00fcbernimm den anvisierten.");
        select = wideButton(selectLabel(), false, () -> this.minecraft.gui.setScreen(new BlockPickScreen(this)));
        wideButton("Anvisierten Block \u00fcbernehmen", false, () -> {
            // Der Block, auf den man vor dem Oeffnen des Menues geschaut hat
            if (TestClient.selectLookedAt(this.minecraft)) {
                select.setMessage(Component.literal(selectLabel()));
            } else {
                select.setMessage(Component.literal("Erst einen Block anvisieren"));
            }
        });
        section("Suche");
        slider("Reichweite (Chunks)", 1, 12, () -> TestClient.searchChunks, v -> TestClient.searchChunks = v, 6);
        slider("H\u00f6he (Bl\u00f6cke)", 2, 20, () -> TestClient.searchHeight, v -> TestClient.searchHeight = v, 12);
        start = wideButton(startLabel(), true, this::startOrStop);
        section("Einsammeln");
        toggle("Items einsammeln", () -> TestClient.blockCollect, v -> TestClient.blockCollect = v, true);
        toggle("Auch Items vom Weg dorthin", () -> TestClient.blockCollectAll, v -> TestClient.blockCollectAll = v,
                false);
        slider("Umkreis (Blöcke)", 2, 16, () -> TestClient.blockCollectRadius,
                v -> TestClient.blockCollectRadius = v, 8);
        note("Sobald nichts mehr in Reichweite ist, holt er die Items, die beim Abbauen herausgefallen sind – nur "
                + "seine eigenen, nichts von anderen Spielern. Ohne „Auch Items vom Weg dorthin“ nur die vom "
                + "gesuchten Block, nicht z.B. Steine, die er auf dem Weg abbaut.");
        section("Anzeige");
        toggle("Ziele hervorheben", () -> RenderMods.miningHighlight, v -> RenderMods.miningHighlight = v, true);
        choice("Umriss-Stil", Render3D.STYLES, () -> RenderMods.outlineStyle, v -> RenderMods.outlineStyle = v, 2);
        footer(null, null);
    }

    private void startOrStop() {
        if (TestClient.areaActive) {
            TestClient.stopArea(this.minecraft, "Abbau gestoppt");
            start.setMessage(Component.literal(startLabel()));
            return;
        }
        String error = TestClient.startBlock(this.minecraft);
        if (error != null) {
            start.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }
}

/** Quader zwischen zwei Ecken abbauen. */
class AreaScreen extends Form {
    private Button start;

    public AreaScreen(Screen parent) {
        super(Component.literal("Bereich abbauen"), parent);
    }

    private static String startLabel() {
        return TestClient.areaActive ? "Abbau STOPPEN" : "Abbau STARTEN";
    }

    @Override
    protected void build() {
        description("Baut alle Bl\u00f6cke zwischen zwei Ecken ab.");
        section("Bereich");
        coords("Ecke 1",
                () -> new int[] {TestClient.ax1, TestClient.ay1, TestClient.az1},
                v -> {
                    TestClient.ax1 = v[0];
                    TestClient.ay1 = v[1];
                    TestClient.az1 = v[2];
                });
        coords("Ecke 2",
                () -> new int[] {TestClient.ax2, TestClient.ay2, TestClient.az2},
                v -> {
                    TestClient.ax2 = v[0];
                    TestClient.ay2 = v[1];
                    TestClient.az2 = v[2];
                });
        note(() -> {
            long dx = Math.abs((long) TestClient.ax2 - TestClient.ax1) + 1;
            long dy = Math.abs((long) TestClient.ay2 - TestClient.ay1) + 1;
            long dz = Math.abs((long) TestClient.az2 - TestClient.az1) + 1;
            return "Gr\u00f6\u00dfe: " + dx + " x " + dy + " x " + dz + " = " + (dx * dy * dz)
                    + " Bl\u00f6cke (max. 50000)";
        });
        start = wideButton(startLabel(), true, this::startOrStop);
        section("Einsammeln");
        toggle("Items einsammeln", () -> TestClient.areaCollect, v -> TestClient.areaCollect = v, true);
        slider("Umkreis (Blöcke)", 2, 16, () -> TestClient.areaCollectRadius,
                v -> TestClient.areaCollectRadius = v, 8);
        note("Sobald nichts mehr in Reichweite ist, holt er die Items, die beim Abbauen herausgefallen sind – nur "
                + "seine eigenen, nichts von anderen Spielern. Ist das Inventar voll, lässt er sie liegen.");
        section("Anzeige");
        toggle("Ziele hervorheben", () -> RenderMods.miningHighlight, v -> RenderMods.miningHighlight = v, true);
        toggle("Bereichsrahmen", () -> RenderMods.miningShowArea, v -> RenderMods.miningShowArea = v, true);
        choice("Umriss-Stil", Render3D.STYLES, () -> RenderMods.outlineStyle, v -> RenderMods.outlineStyle = v, 2);
        footer(null, null);
    }

    private void startOrStop() {
        if (TestClient.areaActive) {
            TestClient.stopArea(this.minecraft, "Bereich abgebrochen");
            start.setMessage(Component.literal(startLabel()));
            return;
        }
        String error = TestClient.startArea(this.minecraft);
        if (error != null) {
            start.setMessage(Component.literal(error));
            return;
        }
        this.minecraft.gui.setScreen(null);
    }
}
