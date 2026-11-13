package de.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Notebot: spielt ein Lied auf Notenbloecken in Reichweite. Ablauf: Notenbloecke suchen, jedem
 * eine benoetigte Tonhoehe zuweisen, per Rechtsklick stimmen, dann im Takt anschlagen (Linksklick).
 * <p>Lied-Format (eingebaut oder Datei config/1912-song.txt): Schritte mit Leerzeichen getrennt.
 * Ein Schritt ist eine Note (c d e f g a h, mit # einen Halbton hoeher, mit 2 eine Oktave hoeher,
 * oder eine Zahl 0-24 = Klicks am Notenblock), ein Akkord (c+e+g) oder eine Pause (-).
 */
public final class Notebot {
    private Notebot() {
    }

    static final String[] SONG_NAMES = {"Alle meine Entchen", "Freude schöner Götterfunken", "Tonleiter",
        "Eigene Datei"};
    private static final String[] SONGS = {
        "c d e f g - g - a a a a g - - - a a a a g - - - f f f f e - e - g g g g c - - -",
        "e e f g g f e d c c d e e - d d - - e e f g g f e d c c d e d - c c - -",
        "c d e f g a h c2 - c2 h a g f e d c - -",
        null
    };
    static final String FILE_NAME = "1912-song.txt";

    private static final int IDLE = 0;
    private static final int TUNE = 1;
    private static final int PLAY = 2;

    private static int phase = IDLE;
    private static boolean wasOn;
    private static List<int[]> steps = new ArrayList<>();          // je Schritt die Tonhoehen (leer = Pause)
    private static final Map<Integer, BlockPos> blocks = new HashMap<>(); // Tonhoehe -> Notenblock
    private static int stepIndex;
    private static int wait;
    private static int tuneTimeout;

    private static void say(LocalPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    private static void stop(String reason, LocalPlayer p) {
        phase = IDLE;
        MiscMods.notebot = false;
        wasOn = false;
        if (p != null && reason != null) say(p, reason);
    }

    // ---------- Lied lesen ----------

    /** Eine Note als Zahl der Klicks am Notenblock (0-24), -1 = ungueltig. */
    private static int parseNote(String t) {
        t = t.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty()) return -1;
        if (Character.isDigit(t.charAt(0))) {
            try {
                int n = Integer.parseInt(t);
                return n >= 0 && n <= 24 ? n : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        int base;
        switch (t.charAt(0)) {
            case 'c' -> base = 6;
            case 'd' -> base = 8;
            case 'e' -> base = 10;
            case 'f' -> base = 11;
            case 'g' -> base = 13;
            case 'a' -> base = 15;
            case 'h', 'b' -> base = 17;
            default -> {
                return -1;
            }
        }
        for (int i = 1; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '#') base += 1;
            else if (ch == '2' || ch == '\'') base += 12;
            else if (ch == '0') base -= 12;
            else return -1;
        }
        return base >= 0 && base <= 24 ? base : -1;
    }

    private static List<int[]> parse(String text) {
        List<int[]> out = new ArrayList<>();
        for (String tok : text.trim().split("\\s+")) {
            if (tok.isEmpty() || tok.startsWith("//")) continue;
            if (tok.equals("-") || tok.equals(".") || tok.equals("|")) {
                if (!tok.equals("|")) out.add(new int[0]);
                continue;
            }
            String[] parts = tok.split("\\+");
            List<Integer> notes = new ArrayList<>();
            for (String part : parts) {
                int n = parseNote(part);
                if (n >= 0) notes.add(n);
            }
            int[] arr = new int[notes.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = notes.get(i);
            out.add(arr);
        }
        return out;
    }

    private static String loadSong(LocalPlayer p) {
        int idx = Math.max(0, Math.min(SONGS.length - 1, MiscMods.notebotSong));
        if (SONGS[idx] != null) return SONGS[idx];
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            if (!Files.exists(file)) {
                // Beispiel anlegen, damit man sieht, wie das Format aussieht
                Files.writeString(file, SONGS[0] + "\n", StandardCharsets.UTF_8);
                say(p, "§e1912: Notebot - Beispiel angelegt: config/" + FILE_NAME);
            }
            return Files.readString(file, StandardCharsets.UTF_8).replace('\n', ' ').replace('\r', ' ');
        } catch (java.io.IOException e) {
            return null;
        }
    }

    // ---------- Start ----------

    private static void start(Minecraft mc, LocalPlayer p) {
        if (p.isCreative()) {
            stop("§c1912: Notebot - nicht im Kreativmodus (Linksklick würde die Blöcke abbauen)", p);
            return;
        }
        String text = loadSong(p);
        steps = text == null ? new ArrayList<>() : parse(text);
        TreeSet<Integer> needed = new TreeSet<>();
        for (int[] s : steps) for (int n : s) needed.add(n);
        if (needed.isEmpty()) {
            stop("§c1912: Notebot - das Lied enthält keine Noten", p);
            return;
        }

        // Notenbloecke in Reichweite sammeln (ueber ihnen muss Luft sein, sonst klingen sie nicht)
        List<BlockPos> found = new ArrayList<>();
        BlockPos c = p.blockPosition();
        Vec3 eye = p.getEyePosition();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -4; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos pos = c.offset(dx, dy, dz);
                    if (!(mc.level.getBlockState(pos).getBlock() instanceof NoteBlock)) continue;
                    if (!mc.level.getBlockState(pos.above()).isAir()) continue;
                    if (eye.distanceTo(Vec3.atCenterOf(pos)) > 4.3) continue;
                    found.add(pos.immutable());
                }
            }
        }
        if (found.size() < needed.size()) {
            stop("§c1912: Notebot - das Lied braucht " + needed.size() + " Notenblöcke in Reichweite, gefunden: "
                    + found.size(), p);
            return;
        }

        // Zuweisen: zuerst Bloecke nehmen, die schon richtig gestimmt sind, dann die naechstliegenden
        blocks.clear();
        List<BlockPos> free = new ArrayList<>(found);
        for (int note : needed) {
            for (BlockPos pos : free) {
                if (mc.level.getBlockState(pos).getValue(NoteBlock.NOTE) == note) {
                    blocks.put(note, pos);
                    free.remove(pos);
                    break;
                }
            }
        }
        for (int note : needed) {
            if (blocks.containsKey(note)) continue;
            BlockPos best = null;
            int bestClicks = 99;
            for (BlockPos pos : free) {
                int clicks = Math.floorMod(note - mc.level.getBlockState(pos).getValue(NoteBlock.NOTE), 25);
                if (clicks < bestClicks) {
                    bestClicks = clicks;
                    best = pos;
                }
            }
            blocks.put(note, best);
            free.remove(best);
        }
        phase = TUNE;
        wait = 0;
        tuneTimeout = 20 * 60;
        say(p, "§a1912: Notebot - stimme " + needed.size() + " Notenblöcke");
    }

    // ---------- Tick ----------

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
            phase = IDLE;
            wasOn = false;
            MiscMods.notebot = false;
            return;
        }
        if (!MiscMods.notebot) {
            phase = IDLE;
            wasOn = false;
            return;
        }
        if (!wasOn) {
            wasOn = true;
            start(mc, p);
            return;
        }
        if (mc.gui.screen() != null) return; // im Menue pausieren
        if (phase == TUNE) tune(mc, p);
        else if (phase == PLAY) play(mc, p);
    }

    /** Stimmen: pro Durchgang ein Rechtsklick auf einen noch falschen Block. */
    private static void tune(Minecraft mc, LocalPlayer p) {
        if (--tuneTimeout <= 0) {
            stop("§c1912: Notebot - Stimmen dauert zu lange (Blöcke geschützt?)", p);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        for (var e : blocks.entrySet()) {
            BlockPos pos = e.getValue();
            BlockState st = mc.level.getBlockState(pos);
            if (!(st.getBlock() instanceof NoteBlock)) {
                stop("§c1912: Notebot - ein Notenblock fehlt plötzlich", p);
                return;
            }
            if (st.getValue(NoteBlock.NOTE) == e.getKey()) continue;
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            p.swing(InteractionHand.MAIN_HAND);
            wait = Math.max(1, MiscMods.notebotTuneDelay);
            return;
        }
        // alles gestimmt
        phase = PLAY;
        stepIndex = 0;
        wait = 10;
        say(p, "§a1912: Notebot - spiele „" + SONG_NAMES[Math.max(0,
                Math.min(SONG_NAMES.length - 1, MiscMods.notebotSong))] + "“");
    }

    private static void play(Minecraft mc, LocalPlayer p) {
        if (wait > 0) {
            wait--;
            return;
        }
        if (stepIndex >= steps.size()) {
            if (MiscMods.notebotLoop) {
                stepIndex = 0;
                wait = 20;
                return;
            }
            stop("§a1912: Notebot - fertig", p);
            return;
        }
        int[] notes = steps.get(stepIndex++);
        for (int note : notes) {
            BlockPos pos = blocks.get(note);
            if (pos == null) continue;
            // Linksklick = anschlagen; sofort wieder abbrechen, damit nichts abgebaut wird
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.UP));
        }
        if (notes.length > 0) p.swing(InteractionHand.MAIN_HAND);
        wait = Math.max(1, MiscMods.notebotTempo) - 1;
    }
}
