package de.test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen der Kill Aura. */
public class KillAuraScreen extends Form {
    public KillAuraScreen(Screen parent) {
        super(Component.literal("Kill Aura"), parent);
    }

    @Override
    protected void build() {
        description("Greift Wesen in Reichweite automatisch an. Freunde aus der Freundesliste, Spieler im "
                + "Kreativ- oder Zuschauermodus, Dorfbewohner und Golems greift sie nie an.");

        section("Ziele");
        toggle("Spieler", () -> KillAura.players, v -> KillAura.players = v, true);
        toggle("Feindliche Mobs", () -> KillAura.hostile, v -> KillAura.hostile = v, true);
        toggle("Tiere", () -> KillAura.passive, v -> KillAura.passive = v, false);
        toggle("Neutrale Mobs nur, wenn sie angreifen", () -> KillAura.neutralOnlyAngry,
                v -> KillAura.neutralOnlyAngry = v, true);
        toggle("Gezähmte und benannte Tiere auslassen", () -> KillAura.skipPets, v -> KillAura.skipPets = v, true);
        sliderTenths("Reichweite (Blöcke)", 10, 60, () -> KillAura.range, v -> KillAura.range = v, 30);
        toggle("Durch Wände", () -> KillAura.throughWalls, v -> KillAura.throughWalls = v, false);
        slider("Blickfeld (°)", 30, 360, () -> KillAura.fov, v -> KillAura.fov = v, 360);
        choice("Zuerst angreifen", KillAura.PRIORITIES, () -> KillAura.priority, v -> KillAura.priority = v, 0);
        choice("Ziel", KillAura.TARGET_MODES, () -> KillAura.targetMode, v -> KillAura.targetMode = v, 0);
        note("Neutrale Mobs sind Zombie-Piglins, Endermen und Piglins - greift man einen an, kommen oft alle. "
                + "Normal sind es 3 Blöcke, mehr geht nur mit Reach und wird auf vielen Servern erkannt. "
                + "Blickfeld 360° = rundherum.");

        section("Takt");
        toggle("Auf die Abklingzeit warten", () -> KillAura.cooldown, v -> KillAura.cooldown = v, true);
        slider("Schlagen ab Abklingzeit (%)", 50, 100, () -> KillAura.cooldownPercent,
                v -> KillAura.cooldownPercent = v, 100);
        slider("Schläge pro Sekunde (min)", 1, 20, () -> KillAura.cpsMin, v -> KillAura.cpsMin = v, 8);
        slider("Schläge pro Sekunde (max)", 1, 20, () -> KillAura.cpsMax, v -> KillAura.cpsMax = v, 12);
        toggle("Kritische Treffer (in der Luft erst beim Fallen)", () -> KillAura.crits,
                v -> KillAura.crits = v, false);
        toggle("Pause beim Essen und Benutzen", () -> KillAura.pauseOnUse, v -> KillAura.pauseOnUse = v, true);
        toggle("Nur bei gedrückter Angriffstaste", () -> KillAura.onlyHolding, v -> KillAura.onlyHolding = v, false);
        note("Mit Abklingzeit schlägt sie, sobald der Schlag voll aufgeladen ist (volle Wirkung). "
                + "Ohne Abklingzeit gelten die Schläge pro Sekunde - für Server ohne Abklingzeit.");

        section("Waffe");
        toggle("Nur mit Waffe in der Hand", () -> KillAura.onlyWeapon, v -> KillAura.onlyWeapon = v, false);
        toggle("Beste Waffe aus der Hotbar nehmen", () -> KillAura.autoWeapon, v -> KillAura.autoWeapon = v, false);
        toggle("Arm sichtbar schwingen", () -> KillAura.swing, v -> KillAura.swing = v, true);
        note("Waffen sind Schwerter, Äxte, Speere, Streitkolben und Dreizacke. Schwerter werden vor Äxten gewählt.");

        section("Zielen");
        choice("Blick", KillAura.ROTATIONS, () -> KillAura.rotation, v -> KillAura.rotation = v, 1);
        slider("Drehgeschwindigkeit (°/Tick)", 5, 180, () -> KillAura.rotSpeed, v -> KillAura.rotSpeed = v, 40);
        note("„Nur zum Server“ schickt die Blickrichtung zum Ziel mit, deine Kamera bleibt still. "
                + "„Kopf drehen“ dreht den Blick sichtbar und schlägt erst, wenn das Ziel im Fadenkreuz ist. "
                + "Während Debris Miner oder Abbau-Bots laufen, dreht sie nur zum Server.");

        section("Anzeige");
        toggle("Ziel hervorheben", () -> KillAura.highlight, v -> KillAura.highlight = v, true);
        toggle("Ziel-Info unter dem Fadenkreuz", () -> KillAura.targetInfo, v -> KillAura.targetInfo = v, true);
        hex("Farbe", () -> KillAura.color, v -> KillAura.color = v);

        footer(() -> KillAura.enabled, v -> KillAura.enabled = v);
    }
}
