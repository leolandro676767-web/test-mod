package de.test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schutz gegen Abstuerze: Jeder Teil der Mod (Tick eines Moduls, HUD-Zeichnung) laeuft hier
 * durch. Wirft er einen Fehler, wird der Fehler einmal ins Log geschrieben und kurz im Chat
 * gemeldet - das Spiel laeuft weiter, statt komplett abzustuerzen.
 */
public final class Safe {
    private Safe() {
    }

    private static final Logger LOG = LoggerFactory.getLogger("1912");
    // auch Netzwerk-Threads melden hier (ConnectionMixin) - deshalb threadsicher
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** Fuehrt einen Teil aus; Fehler werden abgefangen und (einmal je Teil) gemeldet. */
    public static void run(String part, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException | LinkageError e) {
            report(part, e);
        }
    }

    /** Fuer Mixins: Ja/Nein-Frage an die Mod; bei einem Fehler gilt "nein" (Minecraft macht normal weiter). */
    public static boolean test(String part, BooleanSupplier s) {
        try {
            return s.getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            report(part, e);
            return false;
        }
    }

    /** Fuer Mixins: Wert berechnen; bei einem Fehler bleibt der Ausgangswert. */
    public static <T> T map(String part, T fallback, Supplier<T> s) {
        try {
            T v = s.get();
            return v != null ? v : fallback;
        } catch (RuntimeException | LinkageError e) {
            report(part, e);
            return fallback;
        }
    }

    /** Zeichen-Methode eines HUD-Teils (wie Esp::render). */
    @FunctionalInterface
    public interface Draw {
        void draw(GuiGraphicsExtractor g, DeltaTracker delta);
    }

    /** HUD-Element, das bei einem Fehler nicht das ganze Spiel mitreisst. */
    public static HudElement hud(String part, Draw inner) {
        return (g, delta) -> {
            var pose = g.pose();
            pose.pushMatrix();
            try {
                inner.draw(g, delta);
            } catch (RuntimeException | LinkageError e) {
                report(part, e);
            } finally {
                // auch nach einem Fehler mitten im Zeichnen: Transparenz und Verschiebung zuruecksetzen
                Ui.alpha = 1f;
                pose.popMatrix();
            }
        };
    }

    static void report(String part, Throwable e) {
        if (!REPORTED.add(part)) return;
        LOG.error("1912: Fehler in {} (wird abgefangen, das Spiel laeuft weiter)", part, e);
        try {
            // Chat nur im Spiel-Thread anfassen (Fehler koennen auch aus dem Netzwerk-Thread kommen)
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                var p = mc.player;
                if (p != null) {
                    p.sendSystemMessage(Lang.literal("§c1912: Fehler in „" + part
                            + "“ abgefangen. Details stehen in logs/latest.log."));
                }
            });
        } catch (RuntimeException ignored) {
            // Meldung ist nur ein Zusatz
        }
    }
}
