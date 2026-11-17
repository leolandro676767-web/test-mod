package de.test;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.screens.Screen;

import net.minecraft.world.entity.Entity;

import net.minecraft.world.item.Items;

import net.minecraft.world.level.block.Block;

import static de.test.RenderMods.*;

/** Einstellungs-Fenster der Render-Module (Logik und Schalter stehen in RenderMods). */
final class RenderScreens {
    private RenderScreens() {
    }

    public static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Block Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Hebt gew\u00e4hlte Bl\u00f6cke durch W\u00e4nde hervor.");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 12, () -> blockEspChunks, v -> {
                    blockEspChunks = v;
                    rescan();
                }, 6);
                f.wideButton("Bl\u00f6cke w\u00e4hlen (" + blockEspBlocks.size() + ")", false,
                        () -> Minecraft.getInstance().gui.setScreen(new PickerScreen(f, "Block Esp",
                                PickerScreen.Kind.BLOCK, PickerScreen.ofSet(blockEspBlocks), null, null)));
                f.section("Umriss");
                f.choice("Stil", Render3D.STYLES, () -> outlineStyle, v -> outlineStyle = v, 2);
                f.slider("Scan-Tempo", 1, 10, () -> outlineScanSpeed, v -> outlineScanSpeed = v, 4);
                f.toggle("Welle", () -> outlineWave, v -> outlineWave = v, true);
                f.hex("Farbe (RRGGBB)", () -> Esp.colBlock, v -> Esp.colBlock = v);
                f.footer(() -> blockEsp, v -> {
                    blockEsp = v;
                    rescan();
                });
            });
            case "Storage Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Hebt Kisten, Fässer, Öfen usw. hervor.");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 12, () -> storageEspChunks, v -> storageEspChunks = v, 6);
                f.footer(() -> storageEsp, v -> storageEsp = v);
            });
            case "Hole Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt sichere 1x1-Löcher zum Hineinstellen.");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 12, () -> holeEspChunks, v -> holeEspChunks = v, 6);
                f.footer(() -> holeEsp, v -> holeEsp = v);
            });
            case "Void Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt L\u00f6cher, durch die man in die Leere f\u00e4llt.");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 12, () -> voidEspChunks, v -> voidEspChunks = v, 6);
                f.footer(() -> voidEsp, v -> voidEsp = v);
            });
            case "City Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Hebt Blöcke rund um Gegner hervor (PvP).");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 32, () -> cityEspChunks, v -> cityEspChunks = v, 6);
                f.footer(() -> cityEsp, v -> cityEsp = v);
            });
            case "Tunnel Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Hebt gegrabene 1x2-G\u00e4nge hervor.");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 12, () -> tunnelEspChunks, v -> tunnelEspChunks = v, 6);
                f.footer(() -> tunnelEsp, v -> tunnelEsp = v);
            });
            case "Tracers" -> new ModuleScreen(parent, module, f -> {
                f.description("Linien von der Bildschirmmitte zu Zielen.");
                f.section("Ziele");
                f.toggle("Spieler", () -> tracersPlayers, v -> tracersPlayers = v, true);
                f.toggle("Mobs", () -> tracersMobs, v -> tracersMobs = v, false);
                f.toggle("Items", () -> tracersItems, v -> tracersItems = v, false);
                f.slider("Reichweite (Chunks)", 1, 32, () -> tracersChunks, v -> tracersChunks = v, 6);
                f.footer(() -> tracers, v -> tracers = v);
            });
            case "Nametags" -> new ModuleScreen(parent, module, f -> {
                f.description("Namen über Spielern und Mobs, durch Wände.");
                f.section("Allgemein");
                f.toggle("Leben zeigen", () -> nametagsHealth, v -> nametagsHealth = v, true);
                f.slider("Reichweite (Chunks)", 1, 32, () -> nametagsChunks, v -> nametagsChunks = v, 6);
                f.footer(() -> nametags, v -> nametags = v);
            });
            case "Entity Owner" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt den Besitzer von Tieren und Geschossen.");
                f.section("Allgemein");
                f.toggle("Zahme Tiere", () -> ownerAnimals, v -> ownerAnimals = v, true);
                f.toggle("Geschosse", () -> ownerProjectiles, v -> ownerProjectiles = v, true);
                f.slider("Reichweite (Chunks)", 1, 32, () -> ownerChunks, v -> ownerChunks = v, 6);
                f.footer(() -> entityOwner, v -> entityOwner = v);
            });
            case "Item Highlight" -> new ModuleScreen(parent, module, f -> {
                f.description("Rahmt am Boden liegende Items ein.");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 32, () -> itemHighlightChunks, v -> itemHighlightChunks = v, 6);
                f.footer(() -> itemHighlight, v -> itemHighlight = v);
            });
            case "Marker" -> pointScreen(parent, module, false, () -> marker, v -> marker = v);
            case "Waypoints" -> new ModuleScreen(parent, module, f -> {
                f.description("Gespeicherte Orte im Spiel anzeigen.");
                f.section("Allgemein");
                f.toggle("Lichtstrahl", () -> waypointsBeam, v -> waypointsBeam = v, true);
                f.text("Neuer Name", () -> "", v -> {
                    if (!v.isBlank()) addPoint(true, v);
                }, "");
                f.note(() -> "Gespeichert: " + pointCount(waypointList));
                f.wideButton("Alle löschen", false, () -> clearPoints(true));
                f.footer(() -> waypoints, v -> waypoints = v);
            });
            case "Logout Spots" -> new ModuleScreen(parent, module, f -> {
                f.description("Markiert, wo Spieler sich ausgeloggt haben.");
                f.section("Allgemein");
                f.toggle("Kasten zeigen", () -> logoutBox, v -> logoutBox = v, true);
                f.toggle("Entfernung zeigen", () -> logoutDistance, v -> logoutDistance = v, true);
                f.note(() -> "Gemerkt: " + logoutCount());
                f.wideButton("Alle l\u00f6schen", false, RenderMods::clearLogouts);
                f.footer(() -> logoutSpots, v -> logoutSpots = v);
            });
            case "Trail" -> new ModuleScreen(parent, module, f -> {
                f.description("Zieht eine Linie hinter dir her.");
                f.section("Allgemein");
                f.slider("Länge", 20, 300, () -> trailLength, v -> trailLength = v, 80);
                f.footer(() -> trail, v -> trail = v);
            });
            case "Breadcrumbs" -> new ModuleScreen(parent, module, f -> {
                f.description("Punkte entlang deines Wegs.");
                f.section("Allgemein");
                f.slider("Länge", 40, 500, () -> breadcrumbsLength, v -> breadcrumbsLength = v, 200);
                f.footer(() -> breadcrumbs, v -> breadcrumbs = v);
            });
            case "Trajectories" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt die Flugbahn von B\u00f6gen und Wurfobjekten.");
                f.section("Allgemein");
                f.slider("L\u00e4nge (Schritte)", 20, 200, () -> trajSteps, v -> trajSteps = v, 80);
                f.toggle("Aufprall markieren", () -> trajLanding, v -> trajLanding = v, true);
                f.footer(() -> trajectories, v -> trajectories = v);
            });
            case "Light Overlay" -> new ModuleScreen(parent, module, f -> {
                f.description("Markiert dunkle Blöcke, auf denen Mobs spawnen.");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 12, () -> lightOverlayChunks, v -> lightOverlayChunks = v, 6);
                f.footer(() -> lightOverlay, v -> lightOverlay = v);
            });
            case "Break Indicators" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt den Abbaufortschritt an Bl\u00f6cken\n(rot = gerade begonnen, gr\u00fcn = gleich weg).");
                f.section("Allgemein");
                f.toggle("Prozent anzeigen", () -> breakPercent, v -> breakPercent = v, true);
                f.toggle("Auch andere Spieler", () -> breakOthers, v -> breakOthers = v, true);
                f.footer(() -> breakIndicators, v -> breakIndicators = v);
            });
            case "Fullbright" -> new ModuleScreen(parent, module, f -> {
                f.description("Macht alles hell.");
                f.section("Allgemein");
                f.choice("Modus", FULLBRIGHT_MODES, () -> fullbrightMode, v -> fullbrightMode = v, 0);
                f.slider("Gamma (%) [nur Gamma-Modus]", 100, 1600, () -> fullbrightGamma, v -> fullbrightGamma = v, 1000);
                f.footer(() -> fullbright, v -> fullbright = v);
            });
            case "Time Changer" -> new ModuleScreen(parent, module, f -> {
                f.description("Ändert die Tageszeit (nur bei dir).\nServer setzt sie oft zurück.");
                f.section("Allgemein");
                f.slider("Zeit", 0, 23000, () -> timeChangerValue, v -> timeChangerValue = v, 6000);
                f.footer(() -> timeChanger, v -> timeChanger = v);
            });
            case "Camera Tweaks" -> new ModuleScreen(parent, module, f -> {
                f.description("Kamera in der 3rd-Person-Ansicht.");
                f.section("Allgemein");
                f.slider("Abstand", 2, 16, () -> cameraDistance, v -> cameraDistance = v, 4);
                f.toggle("Durch Blöcke", () -> cameraNoClip, v -> cameraNoClip = v, true);
                f.footer(() -> cameraTweaks, v -> cameraTweaks = v);
            });
            case "Free Look" -> new ModuleScreen(parent, module, f -> {
                f.description("Schaut dir schr\u00e4g von der Seite zu, w\u00e4hrend du l\u00e4ufst\n"
                        + "und baust, z.B. bei \u201eZu Koordinaten gehen\u201c. Mit der Maus\n"
                        + "drehst du die Kamera um dich herum, die Laufrichtung bleibt.");
                f.section("Kamera");
                f.toggle("3rd Person (schr\u00e4g von der Seite)", () -> freeLookThird, v -> freeLookThird = v, true);
                f.slider("Winkel zur Seite", 0, 180, () -> freeLookAngle, v -> freeLookAngle = v, 50);
                f.slider("Neigung", 0, 80, () -> freeLookPitch, v -> freeLookPitch = v, 25);
                f.toggle("Schwenkt mit der Laufrichtung", () -> freeLookFollow, v -> freeLookFollow = v, true);
                f.note("0\u00b0 = genau hinter dir, 90\u00b0 = von der Seite. Den Abstand stellst du bei Camera Tweaks ein.");
                f.footer(() -> freeLook, v -> {
                    freeLook = v;
                    if (v) freecam = false;
                });
            });
            case "Freecam" -> new ModuleScreen(parent, module, f -> {
                f.description("Kamera frei bewegen, dein K\u00f6rper bleibt stehen.\nLaufen-Tasten, Springen = hoch, Schleichen = runter,\nSprinten = doppelt so schnell.");
                f.section("Allgemein");
                f.slider("Tempo (Bl\u00f6cke/s)", 2, 40, () -> freecamSpeed, v -> freecamSpeed = v, 10);
                f.toggle("Klicks sperren", () -> freecamBlockClicks, v -> freecamBlockClicks = v, true);
                f.toggle("Erst am Boden starten", () -> freecamWaitGround, v -> freecamWaitGround = v, true);
                f.note("F\u00fcr den Server stehst du nur still: es wird keine Bewegung gesendet. "
                        + "In der Luft gestartet w\u00fcrde dein K\u00f6rper dort h\u00e4ngen bleiben, das kann wie Fliegen aussehen.");
                f.footer(() -> freecam, v -> {
                    freecam = v;
                    if (v) freeLook = false;
                });
            });
            case "No Render" -> new ModuleScreen(parent, module, f -> {
                f.description("Blendet störende Effekte aus.");
                f.section("Aus");
                f.toggle("Feuer-Overlay", () -> noFire, v -> noFire = v, true);
                f.toggle("Wasser-Overlay", () -> noWaterOverlay, v -> noWaterOverlay = v, true);
                f.toggle("Übelkeit", () -> noNausea, v -> noNausea = v, true);
                f.toggle("Kürbis-Overlay", () -> noPumpkin, v -> noPumpkin = v, true);
                f.toggle("Nebel", () -> noFog, v -> noFog = v, false);
                f.toggle("Trefferwackeln", () -> noHurtCam, v -> noHurtCam = v, false);
                f.footer(() -> noRender, v -> noRender = v);
            });
            case "Block Selection" -> new ModuleScreen(parent, module, f -> {
                f.description("Ändert den Auswahlrahmen von Blöcken.");
                f.section("Allgemein");
                f.choice("Modus", BLOCK_SEL_MODES, () -> blockSelMode, v -> blockSelMode = v, 0);
                f.footer(() -> blockSelection, v -> blockSelection = v);
            });
            case "Hand View" -> new ModuleScreen(parent, module, f -> {
                f.description("Verschiebt und skaliert deine Hand (Ego-Sicht).");
                f.section("Position");
                f.slider("Seitlich", -100, 100, () -> handX, v -> handX = v, 0);
                f.slider("H\u00f6he", -100, 100, () -> handY, v -> handY = v, 0);
                f.slider("Tiefe", -100, 100, () -> handZ, v -> handZ = v, 0);
                f.section("Gr\u00f6\u00dfe");
                f.slider("Gr\u00f6\u00dfe (%)", 30, 200, () -> handScale, v -> handScale = v, 100);
                f.footer(() -> handView, v -> handView = v);
            });
            case "Chams" -> new ModuleScreen(parent, module, f -> {
                f.description("F\u00e4rbt Spieler und Mobs als farbige Fl\u00e4che ein -\nauch durch W\u00e4nde sichtbar. Farben wie beim ESP.");
                f.section("Ziele");
                f.toggle("Spieler", () -> chamsPlayers, v -> chamsPlayers = v, true);
                f.toggle("Mobs", () -> chamsMobs, v -> chamsMobs = v, true);
                f.toggle("Nur hinter W\u00e4nden", () -> chamsHiddenOnly, v -> chamsHiddenOnly = v, false);
                f.section("Darstellung");
                f.slider("Reichweite (Chunks)", 1, 32, () -> chamsChunks, v -> chamsChunks = v, 6);
                f.slider("Deckkraft (%)", 5, 100, () -> chamsAlpha, v -> chamsAlpha = v, 45);
                f.footer(() -> chams, v -> chams = v);
            });
            case "Wall Hack" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt Erze durch W\u00e4nde: leuchtende K\u00e4sten in der Farbe des Erzes.");
                f.section("Erze");
                f.toggle("Diamant", () -> wallDiamond, v -> wallDiamond = v, true);
                f.toggle("Antiker Schrott", () -> wallDebris, v -> wallDebris = v, true);
                f.toggle("Gold", () -> wallGold, v -> wallGold = v, true);
                f.toggle("Smaragd", () -> wallEmerald, v -> wallEmerald = v, true);
                f.toggle("Eisen", () -> wallIron, v -> wallIron = v, false);
                f.toggle("Redstone", () -> wallRedstone, v -> wallRedstone = v, false);
                f.toggle("Lapislazuli", () -> wallLapis, v -> wallLapis = v, false);
                f.toggle("Kupfer", () -> wallCopper, v -> wallCopper = v, false);
                f.toggle("Kohle", () -> wallCoal, v -> wallCoal = v, false);
                f.toggle("Netherquarz", () -> wallQuartz, v -> wallQuartz = v, false);
                f.section("Suche");
                f.slider("Reichweite (Chunks)", 1, 12, () -> wallHackChunks, v -> wallHackChunks = v, 6);
                f.footer(() -> wallHack, v -> wallHack = v);
            });
            case "Item Physics" -> flagScreen(parent, module, "Items am Boden liegen flach, statt sich zu drehen und zu h\u00fcpfen.",
                    () -> itemPhysics, v -> itemPhysics = v);
            case "Blur" -> new ModuleScreen(parent, module, f -> {
                f.description("Dunkler Hintergrund bei offenem Men\u00fc.\n(Vereinfacht, kein echter Weichzeichner.)");
                f.section("Allgemein");
                f.slider("St\u00e4rke", 0, 200, () -> blurStrength, v -> blurStrength = v, 100);
                f.footer(() -> blur, v -> blur = v);
            });
            case "Boss Stack" -> flagScreen(parent, module, "Fasst gleiche Bossleisten zu einer zusammen (mit Anzahl).\nGilt f\u00fcr Leisten, die nach dem Einschalten erscheinen.",
                    () -> bossStack, v -> bossStack = v);
            case "Better Tab" -> flagScreen(parent, module, "Eigene Spielerliste beim Halten von Tab:\nalle Namen mit Ping in Millisekunden.",
                    () -> betterTab, v -> betterTab = v);
            case "Pop Chams" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt einen verblassenden Geist, wenn\njemand ein Totem verbraucht.");
                f.section("Allgemein");
                f.slider("Dauer (s)", 1, 10, () -> popDuration, v -> popDuration = v, 3);
                f.toggle("Meldung im Chat", () -> popChat, v -> popChat = v, true);
                f.toggle("Auch eigene", () -> popSelf, v -> popSelf = v, false);
                f.footer(() -> popChams, v -> popChams = v);
            });
            case "Better Tooltips" -> new ModuleScreen(parent, module, f -> {
                f.description("Zus\u00e4tzliche Infos im Item-Tooltip.");
                f.section("Anzeigen");
                f.toggle("Shulker-Inhalt", () -> tipShulker, v -> tipShulker = v, true);
                f.slider("Shulker-Zeilen", 1, 27, () -> tipShulkerLines, v -> tipShulkerLines = v, 5);
                f.toggle("Haltbarkeit", () -> tipDurability, v -> tipDurability = v, true);
                f.toggle("N\u00e4hrwert", () -> tipFood, v -> tipFood = v, true);
                f.footer(() -> betterTooltips, v -> betterTooltips = v);
            });
            default -> null;
        };
    }

    private static Screen pointScreen(Screen parent, String module, boolean waypoint,
                                      java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        return new ModuleScreen(parent, module, f -> {
            f.description("Eigene Markierungen an deiner Position.");
            f.section("Allgemein");
            f.text("Neuer Name", () -> "", v -> {
                if (!v.isBlank()) addPoint(waypoint, v);
            }, "");
            f.note(() -> "Gesetzt: " + pointCount(markerList));
            f.wideButton("Alle löschen", false, () -> clearPoints(waypoint));
            f.footer(get, set);
        });
    }

    private static Screen flagScreen(Screen parent, String module, String desc,
                                     java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        return new ModuleScreen(parent, module, f -> {
            f.description(desc);
            f.footer(get, set);
        });
    }
}
