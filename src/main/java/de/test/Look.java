package de.test;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Gemeinsame Helfer der Bots (Walker, Debris Miner, Bewegung): Blick drehen, Tasten, Abstaende. */
final class Look {
    private Look() {
    }

    /** Blickrichtung (Yaw) fuer die Weltrichtung (dx, dz). */
    static float yawOf(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    /** Weich auf einen Punkt zielen. true = innerhalb der Toleranz. */
    static boolean aim(LocalPlayer p, double x, double y, double z, float tol) {
        double dx = x - p.getX();
        double dy = y - p.getEyeY();
        double dz = z - p.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        // fast senkrecht: Drehrichtung egal, sonst dreht er sich im Kreis
        float yaw = h < 0.25 ? p.getYRot() : yawOf(dx, dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return turn(p, yaw, pitch, 30.0f, 25.0f, tol);
    }

    /**
     * Menschlich drehen (setzt der Debris Miner, solange er laeuft): etwas langsamer, weich auslaufend,
     * mit wechselndem Tempo und einem kleinen Zittern wie bei einer Hand an der Maus.
     */
    static boolean human = false;
    private static final java.util.Random RNG = new java.util.Random();
    private static float noiseYaw;
    private static float noisePitch;
    private static float driftYaw;
    private static float driftPitch;
    private static float pace = 1.0f;
    private static int paceTicks;
    private static int steady;

    /**
     * Beim Abbauen haelt ein Spieler die Maus ruhig: fuer die naechsten zwei Ticks klingt das Zittern ab.
     * (Sonst rutscht das Fadenkreuz bei schmalen, schraeg sichtbaren Seiten vom Block und der Abbau beginnt
     * jedes Mal von vorne.)
     */
    static void holdSteady() {
        steady = 2;
    }

    /** Blick weich drehen (schnell, aber ohne Ruck). true = innerhalb der Toleranz. */
    static boolean turn(LocalPlayer p, float yaw, float pitch, float maxYaw, float maxPitch, float tol) {
        float cy = p.getYRot();
        float cp = p.getXRot();
        float k = 0.6f;
        if (human) {
            if (--paceTicks <= 0) { // mal etwas schneller, mal gemuetlicher
                paceTicks = 30 + RNG.nextInt(60);
                pace = 0.75f + RNG.nextFloat() * 0.4f;
            }
            if (steady > 0) {
                steady--;
                driftYaw = 0f;
                driftPitch = 0f;
                noiseYaw *= 0.6f;
                noisePitch *= 0.6f;
            } else {
                // langsam wandernde Abweichung von hoechstens etwa einem halben Grad
                driftYaw = driftYaw * 0.85f + (RNG.nextFloat() - 0.5f) * 0.12f;
                driftPitch = driftPitch * 0.85f + (RNG.nextFloat() - 0.5f) * 0.08f;
                noiseYaw = Mth.clamp(noiseYaw + driftYaw, -0.7f, 0.7f);
                noisePitch = Mth.clamp(noisePitch + driftPitch, -0.45f, 0.45f);
            }
            yaw += noiseYaw;
            pitch += noisePitch;
            maxYaw *= 0.6f * pace;
            maxPitch *= 0.6f * pace;
            k = 0.33f + RNG.nextFloat() * 0.12f;
            tol += 1.0f; // das Zittern gehoert dazu
        }
        float dy = Mth.wrapDegrees(yaw - cy);
        float dp = pitch - cp;
        float near = human ? 0.5f : 1.0f; // letzte Grade: gleich hin bzw. (menschlich) halb hin
        float sy = Math.abs(dy) < 1.0f ? dy * near : Mth.clamp(dy * k, -maxYaw, maxYaw);
        float sp = Math.abs(dp) < 1.0f ? dp * near : Mth.clamp(dp * k, -maxPitch, maxPitch);
        p.setYRot(cy + sy);
        p.setXRot(Mth.clamp(cp + sp, -90.0f, 90.0f));
        return Math.abs(dy - sy) < tol && Math.abs(dp - sp) < tol;
    }

    // ---------- menschlich: Ducken und Blick beim Gehen ----------

    private static int sneakHold;
    private static float wanderYaw;
    private static float wanderPitch;
    private static float wanderVelYaw;
    private static float wanderVelPitch;

    /**
     * Ducken setzen. Menschlich: einmal geduckt bleibt er ein Weilchen geduckt (0,4 bis 0,8 s), statt fuer
     * einen einzigen Tick zu zucken - so sieht das wie ein Spieler aus, der sich an der Kante duckt.
     */
    static void setSneak(KeyMapping k, boolean want) {
        if (human) {
            if (want) {
                sneakHold = Math.max(sneakHold, 8 + RNG.nextInt(9));
            } else if (sneakHold > 0) {
                sneakHold--;
                want = true;
            }
        } else {
            sneakHold = 0;
        }
        setKey(k, want);
    }

    /** Sofort aufstehen (z.B. wenn er absichtlich hinunterfallen soll). */
    static void releaseSneak() {
        sneakHold = 0;
    }

    /**
     * Beim Gehen schaut ein Spieler nicht stur geradeaus: der Blick wandert langsam ein paar Grad hin und her.
     * Liefert {Yaw-Abweichung, Pitch-Abweichung}; ohne "menschlich" {0, 0}. Einmal pro Tick aufrufen.
     */
    static float[] wander() {
        if (!human) return new float[] {0f, 0f};
        // gedaempfte Zufallsbewegung, die immer wieder zur Mitte zurueckfindet
        wanderVelYaw = wanderVelYaw * 0.92f + (RNG.nextFloat() - 0.5f) * 0.35f - wanderYaw * 0.006f;
        wanderVelPitch = wanderVelPitch * 0.92f + (RNG.nextFloat() - 0.5f) * 0.3f - wanderPitch * 0.008f;
        wanderYaw = Mth.clamp(wanderYaw + wanderVelYaw, -6f, 6f);
        wanderPitch = Mth.clamp(wanderPitch + wanderVelPitch, -9f, 9f);
        return new float[] {wanderYaw, wanderPitch};
    }

    static java.util.Random rng() {
        return RNG;
    }

    /** Quadrat des Abstands vom Auge zur Blockmitte. */
    static double eyeDist2(Entity p, BlockPos b) {
        double dx = b.getX() + 0.5 - p.getX();
        double dy = b.getY() + 0.5 - p.getEyeY();
        double dz = b.getZ() + 0.5 - p.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Taste setzen (manche Tasten schalten nur um - dann nochmal). */
    static void setKey(KeyMapping k, boolean want) {
        if (k.isDown() == want) return;
        k.setDown(want);
        if (k.isDown() != want) k.setDown(!want);
    }

    /** Laufrichtung (x, z) aus den gedrueckten Tasten; null = steht. */
    static double[] moveDir(LocalPlayer p, boolean fwd, boolean back, boolean left, boolean right) {
        double f = (fwd ? 1 : 0) - (back ? 1 : 0);
        double s = (left ? 1 : 0) - (right ? 1 : 0);
        double len = Math.sqrt(f * f + s * s);
        if (len < 1e-6) return null;
        f /= len;
        s /= len;
        double yaw = Math.toRadians(p.getYRot());
        return new double[] {-Math.sin(yaw) * f + Math.cos(yaw) * s, Math.cos(yaw) * f + Math.sin(yaw) * s};
    }
}
