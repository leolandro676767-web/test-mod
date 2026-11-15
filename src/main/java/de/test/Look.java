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

    /** Blick weich drehen (schnell, aber ohne Ruck). true = innerhalb der Toleranz. */
    static boolean turn(LocalPlayer p, float yaw, float pitch, float maxYaw, float maxPitch, float tol) {
        float cy = p.getYRot();
        float cp = p.getXRot();
        float dy = Mth.wrapDegrees(yaw - cy);
        float dp = pitch - cp;
        float sy = Math.abs(dy) < 1.0f ? dy : Mth.clamp(dy * 0.6f, -maxYaw, maxYaw);
        float sp = Math.abs(dp) < 1.0f ? dp : Mth.clamp(dp * 0.6f, -maxPitch, maxPitch);
        p.setYRot(cy + sy);
        p.setXRot(Mth.clamp(cp + sp, -90.0f, 90.0f));
        return Math.abs(dy - sy) < tol && Math.abs(dp - sp) < tol;
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
