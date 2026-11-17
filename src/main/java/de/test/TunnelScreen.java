package de.test;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen fuer den Tunnel Miner (gerade graben, z.B. Strip Mining in der Overworld). */
public class TunnelScreen extends Form {
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

        section("Verteidigung");
        toggle("Mobs abwehren", () -> BotGuard.forTunnel, v -> BotGuard.forTunnel = v, true);
        wideButton("Einstellungen der Bot-Verteidigung", false,
                () -> this.minecraft.gui.setScreen(new BotGuardScreen(this)), true);

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
