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
        toggle("Nach Umwegen zurück auf die Achse", () -> DebrisMiner.returnAxis, v -> DebrisMiner.returnAxis = v,
                true);
        toggle("Netherrack und Gestein wegwerfen", () -> DebrisMiner.dropJunk, v -> DebrisMiner.dropJunk = v, true);
        slider("Netherrack behalten (Stapel)", 0, 9, () -> DebrisMiner.keepNetherrack,
                v -> DebrisMiner.keepNetherrack = v, 1);
        note("Ohne „Erst auf die Ziel-Höhe gehen“ gräbt er auf der Höhe, auf der du gerade stehst. Ohne "
                + "„Löcher im Boden zubauen“ weicht er Löchern aus. Weggeworfen wird nach hinten, damit er es nicht "
                + "wieder aufsammelt.");

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
        slider("Radius (0 = ganze Sichtweite)", 0, 128, () -> DebrisMiner.playerRadius,
                v -> DebrisMiner.playerRadius = v, 0);
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

        section("Verteidigung");
        toggle("Mobs abwehren", () -> BotGuard.forDebris, v -> BotGuard.forDebris = v, true);
        wideButton("Einstellungen der Bot-Verteidigung", false,
                () -> this.minecraft.gui.setScreen(new BotGuardScreen(this)), true);

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
