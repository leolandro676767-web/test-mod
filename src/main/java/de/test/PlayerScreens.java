package de.test;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.screens.Screen;

import net.minecraft.world.item.Item;

import net.minecraft.world.item.Items;

import net.minecraft.world.level.block.Block;

import static de.test.PlayerMods.*;

/** Einstellungs-Fenster der Spieler-Module (Logik und Schalter stehen in PlayerMods). */
final class PlayerScreens {
    private PlayerScreens() {
    }

    public static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Air Place" -> new ModuleScreen(parent, module, f -> {
                f.description("Setzt Blöcke in die Luft, wenn du\nmit Rechtsklick ins Leere zielst.");
                f.section("Allgemein");
                f.slider("Abstand (Blöcke)", 1, 5, () -> airPlaceRange, v -> airPlaceRange = v, 4);
                f.footer(() -> airPlace, v -> airPlace = v);
            });
            case "Anti Afk" -> new ModuleScreen(parent, module, f -> {
                f.description("Bewegt dich regelmäßig, damit der\nServer dich nicht wegen AFK kickt.");
                f.section("Allgemein");
                f.slider("Abstand (s)", 3, 120, () -> antiAfkInterval, v -> antiAfkInterval = v, 15);
                f.toggle("Springen", () -> antiAfkJump, v -> antiAfkJump = v, true);
                f.toggle("Schlagen", () -> antiAfkSwing, v -> antiAfkSwing = v, true);
                f.toggle("Drehen", () -> antiAfkTurn, v -> antiAfkTurn = v, true);
                f.footer(() -> antiAfk, v -> antiAfk = v);
            });
            case "Anti Hunger" -> new ModuleScreen(parent, module, f -> {
                f.description("Verringert den Hunger, indem dem Server\nweniger Bewegung gemeldet wird.");
                f.section("Allgemein");
                f.toggle("Sprinten verbergen", () -> antiHungerSprint, v -> antiHungerSprint = v, true);
                f.toggle("Boden verbergen", () -> antiHungerGround, v -> antiHungerGround = v, true);
                f.footer(() -> antiHunger, v -> antiHunger = v);
            });
            case "Auto Eat" -> new ModuleScreen(parent, module, f -> {
                f.description("Isst automatisch das beste Essen\naus der Hotbar.");
                f.section("Allgemein");
                f.slider("Essen bei Hunger ≤", 1, 19, () -> autoEatHunger, v -> autoEatHunger = v, 14);
                f.toggle("Goldäpfel erlauben", () -> autoEatGapples, v -> autoEatGapples = v, false);
                f.footer(() -> autoEat, v -> autoEat = v);
            });
            case "Auto Fish" -> new ModuleScreen(parent, module, f -> {
                f.description("Holt die Angel bei einem Biss ein\nund wirft sie neu aus.");
                f.section("Allgemein");
                f.slider("Neu auswerfen nach (Ticks)", 1, 40, () -> autoFishDelay, v -> autoFishDelay = v, 10);
                f.footer(() -> autoFish, v -> autoFish = v);
            });
            case "Auto Gap" -> new ModuleScreen(parent, module, f -> {
                f.description("Isst einen Goldapfel, wenn das Leben\nniedrig ist (verzauberte zuerst).");
                f.section("Allgemein");
                f.slider("Leben ≤ (2 = 1 Herz)", 2, 19, () -> autoGapHealth, v -> autoGapHealth = v, 10);
                f.footer(() -> autoGap, v -> autoGap = v);
            });
            case "Auto Mend" -> new ModuleScreen(parent, module, f -> {
                f.description("Legt besch\u00e4digte Items mit Mending in die\nzweite Hand, damit XP sie repariert.");
                f.section("Allgemein");
                f.slider("Nur unter Haltbarkeit (%)", 10, 100, () -> autoMendBelow, v -> autoMendBelow = v, 100);
                f.toggle("Auch aus der Hotbar", () -> autoMendHotbar, v -> autoMendHotbar = v, true);
                f.footer(() -> autoMend, v -> autoMend = v);
            });
            case "Auto Replenish" -> new ModuleScreen(parent, module, f -> {
                f.description("Füllt Hotbar-Stapel aus dem Inventar auf.");
                f.section("Allgemein");
                f.slider("Auffüllen ab (Stück)", 1, 63, () -> autoReplenishMin, v -> autoReplenishMin = v, 8);
                f.footer(() -> autoReplenish, v -> autoReplenish = v);
            });
            case "Auto Respawn" -> new ModuleScreen(parent, module, f -> {
                f.description("Klickt nach dem Tod automatisch auf\n„Wiederbeleben“.");
                f.section("Allgemein");
                f.slider("Verzögerung (Ticks)", 0, 100, () -> autoRespawnDelay, v -> autoRespawnDelay = v, 10);
                f.footer(() -> autoRespawn, v -> autoRespawn = v);
            });
            case "Auto Tool" -> new ModuleScreen(parent, module, f -> {
                f.description("Nimmt beim Abbauen das schnellste\nWerkzeug aus der Hotbar.");
                f.section("Allgemein");
                f.toggle("Zurückwechseln", () -> autoToolSwitchBack, v -> autoToolSwitchBack = v, true);
                f.toggle("Fast kaputte schonen", () -> autoToolSave, v -> autoToolSave = v, true);
                f.footer(() -> autoTool, v -> autoTool = v);
            });
            case "Break Delay" -> new ModuleScreen(parent, module, f -> {
                f.description("Wartezeit zwischen zwei abgebauten\nBlöcken (normal 5 Ticks).");
                f.section("Allgemein");
                f.slider("Wartezeit (Ticks)", 0, 5, () -> breakDelayTicks, v -> breakDelayTicks = v, 0);
                f.footer(() -> breakDelay, v -> breakDelay = v);
            });
            case "Chest Swap" -> new ModuleScreen(parent, module, f -> {
                f.description("Tauscht einmal Elytra \u2194 Brustpanzer\nund schaltet sich wieder aus.");
                f.section("Allgemein");
                f.choice("Ziel", CHEST_TARGETS, () -> chestSwapTarget, v -> chestSwapTarget = v, 0);
                f.footer(() -> chestSwap, v -> chestSwap = v);
            });
            case "Exp Thrower" -> new ModuleScreen(parent, module, f -> {
                f.description("Wirft XP-Flaschen aus der Hotbar\nauf deine Füße.");
                f.section("Allgemein");
                f.slider("Abstand (Ticks)", 1, 20, () -> expThrowerDelay, v -> expThrowerDelay = v, 2);
                f.footer(() -> expThrower, v -> expThrower = v);
            });
            case "Fake Player" -> new ModuleScreen(parent, module, f -> {
                f.description("Erzeugt eine Kopie von dir an deiner\nStelle (nur bei dir sichtbar).\n\u00c4nderungen gelten beim n\u00e4chsten Einschalten.");
                f.section("Allgemein");
                f.text("Name", () -> fakeName, v -> fakeName = v, "FakePlayer");
                f.slider("Leben", 1, 20, () -> fakeHealth, v -> fakeHealth = v, 20);
                f.toggle("Inventar kopieren", () -> fakeCopyInv, v -> fakeCopyInv = v, true);
                f.footer(() -> fakePlayer, v -> fakePlayer = v);
            });
            case "Fast Use" -> new ModuleScreen(parent, module, f -> {
                f.description("Benutzt Items und setzt Blöcke schneller\n(normal 4 Ticks Pause).");
                f.section("Allgemein");
                f.slider("Pause (Ticks)", 0, 3, () -> fastUseDelay, v -> fastUseDelay = v, 0);
                f.footer(() -> fastUse, v -> fastUse = v);
            });
            case "Ghost Hand" -> new ModuleScreen(parent, module, f -> {
                f.description("\u00d6ffnet Kisten und \u00e4hnliches durch W\u00e4nde\n(Rechtsklick ohne Block in der Hand).");
                f.section("Allgemein");
                f.slider("Reichweite (Bl\u00f6cke)", 2, 6, () -> ghostHandRange, v -> ghostHandRange = v, 4);
                f.footer(() -> ghostHand, v -> ghostHand = v);
            });
            case "Instant Rebreak" -> new ModuleScreen(parent, module, f -> {
                f.description("Baut einen gerade abgebauten Block sofort\nwieder ab, wenn er neu gesetzt wird.");
                f.section("Allgemein");
                f.slider("Pause (Ticks)", 0, 20, () -> instantRebreakDelay, v -> instantRebreakDelay = v, 0);
                f.footer(() -> instantRebreak, v -> instantRebreak = v);
            });
            case "Liquid Interact" -> new ModuleScreen(parent, module, f -> {
                f.description("Bl\u00f6cke direkt auf Wasser oder Lava\nsetzen.");
                f.section("Allgemein");
                f.toggle("Auch Lava", () -> liquidLava, v -> liquidLava = v, true);
                f.slider("Reichweite (Bl\u00f6cke)", 2, 6, () -> liquidRange, v -> liquidRange = v, 4);
                f.footer(() -> liquidInteract, v -> liquidInteract = v);
            });
            case "Middle Click Extra" -> new ModuleScreen(parent, module, f -> {
                f.description("Mittlere Maustaste benutzt ein Item aus\nder Hotbar, ohne zu wechseln.");
                f.section("Allgemein");
                f.choice("Item", MIDDLE_MODES, () -> middleClickMode, v -> middleClickMode = v, 0);
                f.footer(() -> middleClickExtra, v -> middleClickExtra = v);
            });
            case "Multitask" -> new ModuleScreen(parent, module, f -> {
                f.description("Abbauen und angreifen, w\u00e4hrend du isst\noder mit dem Schild blockst.");
                f.section("Erlauben");
                f.toggle("Abbauen", () -> multitaskMine, v -> multitaskMine = v, true);
                f.toggle("Angreifen", () -> multitaskAttack, v -> multitaskAttack = v, true);
                f.footer(() -> multitask, v -> multitask = v);
            });
            case "Name Protect" -> new ModuleScreen(parent, module, f -> {
                f.description("Ersetzt deinen Namen im Chat.");
                f.section("Allgemein");
                f.text("Ersatzname", () -> nameProtectAlias, v -> nameProtectAlias = v, "Ich");
                f.footer(() -> nameProtect, v -> nameProtect = v);
            });
            case "No Interact" -> new ModuleScreen(parent, module, f -> {
                f.description("Verhindert Rechtsklick auf gewählte\nBlöcke und Wesen.");
                f.wideButton("Blöcke wählen (" + noInteractBlocks.size() + ")", false,
                        () -> Minecraft.getInstance().gui.setScreen(new PickerScreen(f, "No Interact: Blöcke",
                                PickerScreen.Kind.BLOCK, PickerScreen.ofSet(noInteractBlocks), null, null)));
                f.wideButton("Wesen wählen (" + noInteractEntities.size() + ")", false,
                        () -> Minecraft.getInstance().gui.setScreen(new PickerScreen(f, "No Interact: Wesen",
                                PickerScreen.Kind.ENTITY, PickerScreen.ofSet(noInteractEntities), null, null)));
                f.footer(() -> noInteract, v -> noInteract = v);
            });
            case "No Mining Trace" -> new ModuleScreen(parent, module, f -> {
                f.description("Baut durch Wesen hindurch ab, die\nvor dem Block stehen.");
                f.section("Allgemein");
                f.toggle("Nur mit Werkzeug", () -> noMiningTraceTools, v -> noMiningTraceTools = v, true);
                f.footer(() -> noMiningTrace, v -> noMiningTrace = v);
            });
            case "No Rotate" -> new ModuleScreen(parent, module, f -> {
                f.description("Der Server kann deine Blickrichtung\nnicht mehr verstellen.");
                f.section("Festhalten");
                f.toggle("Drehung (links/rechts)", () -> noRotateYaw, v -> noRotateYaw = v, true);
                f.toggle("Neigung (hoch/runter)", () -> noRotatePitch, v -> noRotatePitch = v, true);
                f.footer(() -> noRotate, v -> noRotate = v);
            });
            case "No Status Effects" -> new ModuleScreen(parent, module, f -> {
                f.description("Entfernt störende Effekte bei dir.");
                f.section("Effekte");
                f.toggle("Blindheit", () -> nseBlindness, v -> nseBlindness = v, true);
                f.toggle("Dunkelheit", () -> nseDarkness, v -> nseDarkness = v, true);
                f.toggle("Übelkeit", () -> nseNausea, v -> nseNausea = v, true);
                f.toggle("Schwebeeffekt", () -> nseLevitation, v -> nseLevitation = v, false);
                f.toggle("Langsamkeit", () -> nseSlowness, v -> nseSlowness = v, false);
                f.toggle("Abbaulähmung", () -> nseMiningFatigue, v -> nseMiningFatigue = v, false);
                f.footer(() -> noStatusEffects, v -> noStatusEffects = v);
            });
            case "Portals" -> new ModuleScreen(parent, module, f -> {
                f.description("Menüs und Chat bleiben in Netherportalen\noffen.");
                f.footer(() -> portals, v -> portals = v);
            });
            case "Potion Saver" -> new ModuleScreen(parent, module, f -> {
                f.description("Hält Tränke an, solange du stillstehst.\nWirkt nur auf manchen Servern.");
                f.footer(() -> potionSaver, v -> potionSaver = v);
            });
            case "Reach" -> new ModuleScreen(parent, module, f -> {
                f.description("Größere Reichweite (normal 4,5 / 3).\nServer begrenzen das oft.");
                f.section("Allgemein");
                f.slider("Blöcke", 3, 10, () -> reachBlocks, v -> reachBlocks = v, 5);
                f.slider("Wesen", 3, 8, () -> reachEntities, v -> reachEntities = v, 4);
                f.footer(() -> reach, v -> reach = v);
            });
            case "Rotation" -> new ModuleScreen(parent, module, f -> {
                f.description("Blickrichtung festhalten oder einrasten.");
                f.section("Allgemein");
                f.choice("Drehung", YAW_MODES, () -> rotYawMode, v -> rotYawMode = v, 0);
                f.slider("Drehung (°)", -180, 180, () -> rotYaw, v -> rotYaw = v, 0);
                f.choice("Neigung", PITCH_MODES, () -> rotPitchMode, v -> rotPitchMode = v, 0);
                f.slider("Neigung (°)", -90, 90, () -> rotPitch, v -> rotPitch = v, 0);
                f.footer(() -> rotation, v -> rotation = v);
            });
            case "Speed Mine" -> new ModuleScreen(parent, module, f -> {
                f.description("Baut schneller ab. Über ~140 % lehnen\nviele Server den Abbau ab.");
                f.section("Allgemein");
                f.slider("Tempo (%)", 100, 300, () -> speedMinePercent, v -> speedMinePercent = v, 130);
                f.footer(() -> speedMine, v -> speedMine = v);
            });
            default -> null;
        };
    }
}
