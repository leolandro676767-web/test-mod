package de.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** Einstellungs-Menue fuer ein Modul; der Inhalt kommt als Lambda (siehe Movement). */
public class ModuleScreen extends Form {
    @FunctionalInterface
    public interface Builder {
        void build(ModuleScreen form);
    }

    private final Builder builder;

    public ModuleScreen(Screen parent, String title, Builder builder) {
        super(Component.literal(title), parent);
        this.builder = builder;
    }

    @Override
    protected void build() {
        builder.build(this);
    }
}

/** AutoDrop: jedes Item des Spiels waehlbar, das automatisch aus dem Inventar gedroppt wird. */
class DropScreen extends PickerScreen {
    public DropScreen(Screen parent) {
        super(parent, "AutoDrop", Kind.ITEM, ofSet(TestClient.dropItems),
                () -> TestClient.autoDrop, v -> TestClient.autoDrop = v);
    }
}

/** Sprache des 1912-Menues waehlen (der Titel bleibt zweisprachig, damit man immer zurueckfindet). */
class LanguageScreen extends Form {
    public LanguageScreen(Screen parent) {
        super(Component.literal("Sprache / Language"), parent);
    }

    @Override
    protected void build() {
        description("Sprache des 1912-Menüs. „Automatisch“ nimmt die Sprache, die in Minecraft eingestellt ist.");
        section("Sprache");
        for (int i = 0; i < Lang.NAMES.length; i++) {
            final int idx = i;
            wideButton(Lang.NAMES[i], Lang.language == i, () -> {
                Lang.language = idx;
                TestConfig.save();
                this.rebuildWidgets();
            }, true);
        }
        footer(null, null);
    }
}

/** Modus des Menues: Anfänger, Standard oder Experte (siehe Levels). */
class LevelScreen extends Form {
    public LevelScreen(Screen parent) {
        super(Component.literal("Modus"), parent);
    }

    @Override
    protected void build() {
        description("Welche Module das Menü zeigt. Voreingestellt ist Standard.");
        choice("Modus", Levels.NAMES, () -> Levels.level, Levels::set, Levels.STANDARD);
        note(() -> Lang.t("Anfänger: ") + Levels.count(Levels.BEGINNER)
                + Lang.t(" Module – nur die einfachsten, z.B. Sprint, Fullbright, Wegpunkte und Chat."));
        note(() -> Lang.t("Standard: ") + Levels.count(Levels.STANDARD)
                + Lang.t(" Module – dazu alles, was nur die Anzeige oder den Komfort betrifft, aber keinen "
                + "unfairen Vorteil bringt."));
        note(() -> Lang.t("Experte: alle ") + Levels.count(Levels.EXPERT) + Lang.t(" Module."));
        note("Was im gewählten Modus nicht dabei ist, wird ausgeschaltet, reagiert nicht auf seine Taste und "
                + "taucht auch in der Suche nicht auf.");
        footer(null, null);
    }
}

/** Einstellungen fuer den AutoClicker. */
class ClickerScreen extends Form {
    public ClickerScreen(Screen parent) {
        super(Component.literal("AutoClicker"), parent);
    }

    @Override
    protected void build() {
        description("Schl\u00e4gt im Takt des Tool-Cooldowns.");
        section("Allgemein");
        toggle("Nur beim Halten", () -> TestClient.clickerHold, v -> TestClient.clickerHold = v, true);
        toggle("Auch fr\u00fcher schlagen", () -> TestClient.clickerEarly, v -> {
            TestClient.clickerEarly = v;
            TestClient.rollClickerFactor();
        }, false);
        slider("Zufall (%)", 0, 100, () -> TestClient.clickerRandom, v -> {
            TestClient.clickerRandom = v;
            TestClient.rollClickerFactor();
        }, 20);
        footer(() -> TestClient.autoClicker, v -> TestClient.autoClicker = v);
    }
}

/** Block-Modus: jeden Block des Spiels aus der Liste waehlen, der dann abgebaut wird. */
class BlockPickScreen extends PickerScreen {
    public BlockPickScreen(Screen parent) {
        super(parent, "Block w\u00e4hlen", Kind.BLOCK, single(), null, null);
    }

    /** Einzelauswahl: ein neuer Block ersetzt den alten. */
    private static Selection single() {
        return new Selection() {
            @Override
            public boolean contains(Object value) {
                return value == TestClient.selectedBlock;
            }

            @Override
            public void add(Object value) {
                TestClient.selectedBlock = (Block) value;
            }

            @Override
            public void remove(Object value) {
                if (value == TestClient.selectedBlock) TestClient.selectedBlock = null;
            }

            @Override
            public List<Object> values() {
                List<Object> out = new ArrayList<>();
                if (TestClient.selectedBlock != null) out.add(TestClient.selectedBlock);
                return out;
            }
        };
    }
}

/** Alle vergebenen Modul-Tasten auf einen Blick: Halten/Umschalten waehlen oder Taste entfernen. */
class BindsScreen extends Form {
    public BindsScreen(Screen parent) {
        super(Component.literal("Tasten-Übersicht"), parent);
    }

    @Override
    protected void build() {
        description("Alle Tasten, die du im Hauptmenü mit „Tasten zuweisen“ vergeben hast. "
                + "Halten = Modul ist nur an, solange die Taste gedrückt ist.");
        List<String> names = new ArrayList<>(Keybinds.BINDS.keySet());
        names.removeIf(n -> !Levels.visible(n)); // im gewaehlten Modus nicht dabei
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.isEmpty()) {
            section("Tasten");
            note("Noch keine Tasten vergeben. Im Hauptmenü auf „Tasten zuweisen“ klicken, "
                    + "dann ein Modul anklicken und eine Taste drücken.");
        } else {
            section("Halten statt Umschalten");
            for (String n : names) {
                Integer code = Keybinds.get(n);
                String label = n + " (" + (code == null ? "?" : Keybinds.keyName(code)) + ")";
                toggle(label, () -> Keybinds.HOLD.contains(n), v -> {
                    if (v) {
                        Keybinds.HOLD.add(n);
                    } else {
                        Keybinds.HOLD.remove(n);
                    }
                }, false);
            }
            sectionClosed("Tasten entfernen");
            for (String n : names) {
                wideButton("Taste von „" + n + "“ entfernen", false, () -> {
                    Keybinds.clear(n);
                    this.rebuildWidgets();
                }, true);
            }
        }
        footer(null, null);
    }
}

/** Profile: alle Einstellungen unter einem Namen speichern und spaeter wieder laden. */
class ProfileScreen extends Form {
    private static String name = "";
    private static String message = "";

    public ProfileScreen(Screen parent) {
        super(Component.literal("Profile"), parent);
    }

    @Override
    protected void build() {
        description("Speichert alle Einstellungen (Module, Design, Tasten) unter einem Namen, "
                + "z.B. PvP, Bauen oder Farmen. Laden ersetzt die aktuellen Einstellungen.");

        section("Neues Profil");
        text("Name", () -> name, v -> name = v, "");
        wideButton("Aktuelle Einstellungen speichern", true, () -> {
            String err = TestConfig.saveProfile(name);
            String n = TestConfig.cleanProfileName(name);
            message = err != null ? "§c" + err : "Gespeichert: „" + n + "“";
            if (err == null) name = "";
            this.rebuildWidgets();
        }, true);
        note(() -> message);

        List<String> all = TestConfig.profiles();
        section("Laden");
        if (all.isEmpty()) {
            note("Noch keine Profile gespeichert.");
        }
        for (String p : all) {
            wideButton("Laden: „" + p + "“", false, () -> {
                String err = TestConfig.loadProfile(p);
                message = err != null ? "§c" + err : "Geladen: „" + p + "“";
                this.rebuildWidgets();
            }, true);
        }
        if (!all.isEmpty()) {
            sectionClosed("Löschen");
            for (String p : all) {
                wideButton("Löschen: „" + p + "“", false, () -> {
                    TestConfig.deleteProfile(p);
                    message = "Gelöscht: „" + p + "“";
                    this.rebuildWidgets();
                }, true);
            }
        }
        footer(null, null);
    }
}

/** Einstellungen der Kill Aura. */
class KillAuraScreen extends Form {
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

/** Einstellungen der Bot-Verteidigung. */
class BotGuardScreen extends Form {
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
        toggle("Tunnel Miner", () -> BotGuard.forTunnel, v -> BotGuard.forTunnel = v, true);
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
