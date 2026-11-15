package de.test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen der Bot-Verteidigung. */
public class BotGuardScreen extends Form {
    public BotGuardScreen(Screen parent) {
        super(Component.literal("Bot-Verteidigung"), parent);
    }

    @Override
    protected void build() {
        description("Während ein Bot selbst läuft oder abbaut, wehrt er Monster in Reichweite ab. Tiere und "
                + "neutrale Mobs nur, wenn sie dich gerade angreifen. Solange er kämpft, hält der Bot an und macht "
                + "danach mit seinem Werkzeug weiter.");

        section("Bots");
        toggle("Debris Miner", () -> BotGuard.forDebris, v -> BotGuard.forDebris = v, true);
        toggle("Bereich abbauen", () -> BotGuard.forArea, v -> BotGuard.forArea = v, true);
        toggle("Block abbauen", () -> BotGuard.forBlock, v -> BotGuard.forBlock = v, true);
        toggle("Zu Koordinaten gehen", () -> BotGuard.forGoto, v -> BotGuard.forGoto = v, true);
        toggle("Auto Walk", () -> BotGuard.forWalk, v -> BotGuard.forWalk = v, true);

        section("Ziele");
        toggle("Monster", () -> BotGuard.hostile, v -> BotGuard.hostile = v, true);
        toggle("Tiere und neutrale Mobs, wenn sie angreifen", () -> BotGuard.aggressive,
                v -> BotGuard.aggressive = v, true);
        toggle("Creeper sofort schlagen", () -> BotGuard.creeperFirst, v -> BotGuard.creeperFirst = v, true);
        sliderTenths("Reichweite (Blöcke)", 10, 60, () -> BotGuard.range, v -> BotGuard.range = v, 30);
        toggle("Durch Wände", () -> BotGuard.throughWalls, v -> BotGuard.throughWalls = v, false);
        note("Spieler greift die Bot-Verteidigung nie an, gezähmte Tiere auch nicht. Zombie-Piglins, Endermen und "
                + "Piglins zählen als neutral. Creeper werden zuerst und ohne volle Abklingzeit geschlagen, damit "
                + "sie zurückfliegen, bevor sie explodieren.");

        section("Kampf");
        toggle("Beste Waffe nehmen, danach zurück zum Werkzeug", () -> BotGuard.useWeapon,
                v -> BotGuard.useWeapon = v, true);
        toggle("Auf die Abklingzeit warten", () -> BotGuard.waitCooldown, v -> BotGuard.waitCooldown = v, true);
        choice("Blick", BotGuard.ROTATIONS, () -> BotGuard.rotation, v -> BotGuard.rotation = v, 0);
        slider("Ziel aufgeben nach (s)", 5, 120, () -> BotGuard.giveUpSeconds, v -> BotGuard.giveUpSeconds = v, 30);
        note("Ein Ziel, das in dieser Zeit nicht stirbt (z.B. weil die Schläge nicht treffen), lässt er "
                + "30 Sekunden in Ruhe.");

        section("Anzeige");
        toggle("Ziel hervorheben", () -> BotGuard.highlight, v -> BotGuard.highlight = v, true);
        toggle("Meldung im Chat", () -> BotGuard.chat, v -> BotGuard.chat = v, false);

        footer(() -> BotGuard.enabled, v -> BotGuard.enabled = v);
    }
}
