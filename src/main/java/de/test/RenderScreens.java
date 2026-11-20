package de.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import static de.test.PlayerMods.*;
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
                f.note(RenderMods::blockEspStatus);
                f.section("Umriss");
                f.choice("Stil", Render3D.STYLES, () -> outlineStyle, v -> outlineStyle = v, 2);
                f.slider("Scan-Tempo", 1, 10, () -> outlineScanSpeed, v -> outlineScanSpeed = v, 4);
                f.toggle("Welle", () -> outlineWave, v -> outlineWave = v, true);
                f.toggle("Leistungs-Modus (für schwache PCs)", () -> perfMode, v -> perfMode = v, false);
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
                f.toggle("Schon geöffnete grau", () -> storGrayOpened, v -> storGrayOpened = v, false);
                f.section("Was");
                f.toggle("Truhen und Fässer", () -> storChests, v -> storChests = v, true);
                f.toggle("Shulker-Kisten", () -> storShulkers, v -> storShulkers = v, true);
                f.toggle("Endertruhen", () -> storEnder, v -> storEnder = v, true);
                f.toggle("Öfen, Schmelzöfen und Räucheröfen", () -> storFurnaces, v -> storFurnaces = v, true);
                f.toggle("Trichter, Werfer und Spender", () -> storHoppers, v -> storHoppers = v, true);
                f.toggle("Braustände", () -> storBrewing, v -> storBrewing = v, true);
                f.toggle("Spawner", () -> storSpawners, v -> storSpawners = v, false);
                f.note("Spawner sind rot. Welche du schon geöffnet hast, merkt er sich, bis du die Welt verlässt.");
                f.footer(() -> storageEsp, v -> storageEsp = v);
            });
            case "Hole Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt sichere 1x1-Löcher zum Hineinstellen.");
                f.section("Allgemein");
                f.slider("Reichweite (Chunks)", 1, 12, () -> holeEspChunks, v -> holeEspChunks = v, 6);
                f.toggle("Nur Löcher aus Obsidian und Grundgestein", () -> holeOnlySafe, v -> holeOnlySafe = v, false);
                f.note("Grün = ganz aus Grundgestein (am sichersten), gelb = Obsidian oder ähnlich fest, blau = andere "
                        + "Blöcke, die Explosionen nicht standhalten.");
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
                f.section("Aussehen");
                f.toggle("Farbe nach Entfernung (rot = nah)", () -> tracersByDistance, v -> tracersByDistance = v, false);
                f.toggle("Freunde ausblenden", () -> tracersNoFriends, v -> tracersNoFriends = v, false);
                f.footer(() -> tracers, v -> tracers = v);
            });
            case "Nametags" -> new ModuleScreen(parent, module, f -> {
                f.description("Namen über Spielern und Mobs, durch Wände.");
                f.section("Allgemein");
                f.toggle("Leben zeigen", () -> nametagsHealth, v -> nametagsHealth = v, true);
                f.toggle("Rüstung und Items in den Händen", () -> nametagsArmor, v -> nametagsArmor = v, true);
                f.toggle("Entfernung zeigen", () -> nametagsDistance, v -> nametagsDistance = v, false);
                f.toggle("Ping zeigen", () -> nametagsPing, v -> nametagsPing = v, false);
                f.slider("Größe (%)", 50, 200, () -> nametagsSize, v -> nametagsSize = v, 100);
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
                f.toggle("Nur wertvolle Items", () -> itemValuable, v -> itemValuable = v, false);
                f.toggle("Name und Anzahl zeigen", () -> itemNames, v -> itemNames = v, true);
                f.note("Wertvoll: alles Verzauberte, Diamant- und Netherit-Sachen, Antiker Schrott, Totems, Elytren, "
                        + "Goldäpfel, Shulker-Kisten, Smaragde, Endkristalle und Ähnliches.");
                f.footer(() -> itemHighlight, v -> itemHighlight = v);
            });
            case "Marker" -> pointScreen(parent, module, false, () -> marker, v -> marker = v);
            case "Waypoints" -> new ModuleScreen(parent, module, f -> {
                f.description("Gespeicherte Orte im Spiel anzeigen.");
                f.section("Allgemein");
                f.toggle("Lichtstrahl", () -> waypointsBeam, v -> waypointsBeam = v, true);
                f.toggle("Entfernung zeigen", () -> waypointsDistance, v -> waypointsDistance = v, true);
                f.toggle("Nur Wegpunkte dieser Dimension", () -> waypointsDimOnly, v -> waypointsDimOnly = v, true);
                f.note("Wegpunkte von früher kennen ihre Dimension nicht - die zeigt er überall.");
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
                f.toggle("Wetter immer klar", () -> timeClearWeather, v -> timeClearWeather = v, false);
                f.note("Regen und Gewitter siehst nur du nicht mehr - auf dem Server regnet es trotzdem.");
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
                f.note(RenderMods::wallHackStatus);
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
                f.toggle("Leistungs-Modus (für schwache PCs)", () -> perfMode, v -> perfMode = v, false);
                f.note("Leistungs-Modus: Erze, die zusammenliegen, werden ein großer Kasten - viel weniger zu "
                        + "zeichnen. Er gilt für alle Block-Anzeigen (auch unter Menü → Design anpassen).");
                f.footer(() -> wallHack, v -> wallHack = v);
            });
            case "X-Ray" -> new ModuleScreen(parent, module, f -> {
                f.description("Klassisches X-Ray: Alles außer den gewählten Blöcken wird unsichtbar - du schaust durch "
                        + "Stein und Erde direkt auf die Erze.");
                f.wideButton("Blöcke wählen (" + xrayBlocks.size() + ")", false,
                        () -> Minecraft.getInstance().gui.setScreen(new PickerScreen(f, "X-Ray",
                                PickerScreen.Kind.BLOCK, PickerScreen.ofSet(xrayBlocks), null, null)));
                f.wideButton("Nur Erze (Standard)", false, () -> {
                    xrayBlocks.clear();
                    xrayDefaults();
                    f.refresh();
                });
                f.toggle("Hell machen (Nachtsicht)", () -> xrayBright, v -> xrayBright = v, true);
                f.toggle("Wasser ausblenden", () -> xrayHideWater, v -> xrayHideWater = v, true);
                f.note("Standard: alle Erze, Antiker Schrott und Spawner. Truhen, Shulker-Kisten und Lava siehst du "
                        + "immer. Durch die unsichtbaren Blöcke laufen kannst du nicht. Auf Servern mit Anti-Xray "
                        + "tauchen Erze oft erst auf, wenn du nah dran bist.");
                f.footer(() -> xray, v -> xray = v);
            });
            case "Item Physics" -> flagScreen(parent, module, "Items am Boden liegen flach, statt sich zu drehen und zu h\u00fcpfen.",
                    () -> itemPhysics, v -> itemPhysics = v);
            case "Blur" -> new ModuleScreen(parent, module, f -> {
                f.description("Dunkler Hintergrund bei offenem Men\u00fc.\n(Vereinfacht, kein echter Weichzeichner.)");
                f.section("Allgemein");
                f.slider("St\u00e4rke", 0, 200, () -> blurStrength, v -> blurStrength = v, 100);
                f.footer(() -> blur, v -> blur = v);
            });
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
                f.slider("Auch essen bei Leben ≤ (0 = aus)", 0, 19, () -> autoEatHealth, v -> autoEatHealth = v, 0);
                f.toggle("Nicht im Kampf essen", () -> autoEatNoCombat, v -> autoEatNoCombat = v, false);
                f.note("Mit fast vollem Hunger heilt man schneller - deshalb kann er auch bei wenig Leben essen. "
                        + "Kampf heißt: du hast in den letzten 5 Sekunden geschlagen oder Schaden bekommen. "
                        + "Schlechtes Essen wie verrottetes Fleisch lässt er immer liegen.");
                f.footer(() -> autoEat, v -> autoEat = v);
            });
            case "Auto Fish" -> new ModuleScreen(parent, module, f -> {
                f.description("Holt die Angel bei einem Biss ein\nund wirft sie neu aus.");
                f.section("Allgemein");
                f.slider("Neu auswerfen nach (Ticks)", 1, 40, () -> autoFishDelay, v -> autoFishDelay = v, 10);
                f.toggle("Angel wechseln, bevor sie kaputtgeht", () -> autoFishSwap, v -> autoFishSwap = v, true);
                f.note("Bei nur noch 5 Einsätzen nimmt er eine andere Angel aus dem Inventar. Gibt es keine, hört er "
                        + "auf, statt sie kaputt zu machen.");
                f.footer(() -> autoFish, v -> autoFish = v);
            });
            case "Auto Gap" -> new ModuleScreen(parent, module, f -> {
                f.description("Isst einen Goldapfel, wenn das Leben\nniedrig ist.");
                f.section("Allgemein");
                f.slider("Leben ≤ (2 = 1 Herz)", 2, 19, () -> autoGapHealth, v -> autoGapHealth = v, 10);
                f.choice("Reihenfolge", GAP_ORDER, () -> autoGapOrder, v -> autoGapOrder = v, 0);
                f.toggle("Beim Brennen verzauberten Goldapfel essen", () -> autoGapFire, v -> autoGapFire = v, false);
                f.note("Nur verzauberte Goldäpfel geben Feuerschutz - deshalb isst er beim Brennen nur die, und nur, "
                        + "wenn du noch keinen Feuerschutz hast.");
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
                f.toggle("Auch die zweite Hand", () -> autoReplenishOffhand, v -> autoReplenishOffhand = v, false);
                f.footer(() -> autoReplenish, v -> autoReplenish = v);
            });
            case "Auto Respawn" -> new ModuleScreen(parent, module, f -> {
                f.description("Klickt nach dem Tod automatisch auf\n„Wiederbeleben“.");
                f.section("Allgemein");
                f.slider("Verzögerung (Ticks)", 0, 100, () -> autoRespawnDelay, v -> autoRespawnDelay = v, 10);
                f.text("Danach senden", () -> autoRespawnCommand, v -> autoRespawnCommand = v, "", 120, () -> "/home");
                f.note("Zum Beispiel /home oder /kit. Er sendet es 2 Sekunden nach dem Wiederbeleben. Leer = nichts.");
                f.footer(() -> autoRespawn, v -> autoRespawn = v);
            });
            case "Auto Tool" -> new ModuleScreen(parent, module, f -> {
                f.description("Nimmt beim Abbauen das schnellste\nWerkzeug aus der Hotbar.");
                f.section("Allgemein");
                f.toggle("Zurückwechseln", () -> autoToolSwitchBack, v -> autoToolSwitchBack = v, true);
                f.toggle("Fast kaputte schonen", () -> autoToolSave, v -> autoToolSave = v, true);
                f.choice("Behutsamkeit für", TOOL_SILK, () -> autoToolSilk, v -> autoToolSilk = v, 0);
                f.toggle("Spinnweben mit dem Schwert", () -> autoToolWebSword, v -> autoToolWebSword = v, false);
                f.note("Behutsamkeit: Glas und Eis gehen dann nicht kaputt, Erze bleiben ganz (ohne Glück-Bonus). "
                        + "Spinnweben gibt das Schwert als Faden, die Schere als Netz.");
                f.footer(() -> autoTool, v -> autoTool = v);
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
