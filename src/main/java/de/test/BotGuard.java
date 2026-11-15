package de.test;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Bot-Verteidigung: Solange ein Bot selbst laeuft oder abbaut (Debris Miner, Bereich, Block, Koordinaten,
 * Auto Walk), wehrt sie Monster in Reichweite ab. Tiere und neutrale Mobs nur, wenn sie gerade angreifen.
 * Waehrend des Kampfes haelt der Bot an; danach bekommt er sein Werkzeug zurueck und macht weiter.
 */
public final class BotGuard {
    private BotGuard() {
    }

    // ---------- Einstellungen (gespeichert ueber TestConfig, Praefix "bg.") ----------

    public static boolean enabled = true;

    // fuer welche Bots
    public static boolean forDebris = true;
    public static boolean forArea = true;
    public static boolean forBlock = true;
    public static boolean forGoto = true;
    public static boolean forWalk = true;

    // Ziele
    public static boolean hostile = true;          // Monster
    public static boolean aggressive = true;       // Tiere und neutrale Mobs, wenn sie dich angreifen
    public static boolean creeperFirst = true;     // Creeper sofort schlagen, ohne volle Abklingzeit
    public static int range = 30;                  // Zehntel-Bloecke
    public static boolean throughWalls = false;

    // Kampf
    public static boolean useWeapon = true;        // beste Waffe nehmen, danach zurueck zum Werkzeug
    public static boolean waitCooldown = true;
    public static int rotation = 0;                // 0 Kopf drehen, 1 nur zum Server
    public static int giveUpSeconds = 30;          // ein Ziel, das nicht stirbt, nach so vielen Sekunden aufgeben

    // Anzeige
    public static boolean highlight = true;
    public static boolean chat = false;

    static final String[] ROTATIONS = {"Kopf drehen", "Nur zum Server"};
    static final int COLOR = 0xFFFF9A3D;
    private static final int CALM_TICKS = 8;       // so lange nach dem letzten Gegner noch stehen bleiben

    // ---------- Zustand ----------

    private static boolean fighting;
    private static LivingEntity target;
    private static int savedSlot = -1;
    private static int calm;
    private static int targetTicks;
    private static long ticks;
    private static final Map<Integer, Long> ignored = new HashMap<>();

    /** Kaempft gerade - der Bot soll stehen bleiben. */
    public static boolean fighting() {
        return fighting;
    }

    static LivingEntity target() {
        return target;
    }

    /** Laeuft gerade ein Bot, fuer den die Verteidigung eingeschaltet ist? */
    static boolean botWantsDefense() {
        if (DebrisMiner.isRunning()) return forDebris;
        if (TestClient.areaActive) {
            if (TestClient.gotoMode) return forGoto;
            return TestClient.blockMode ? forBlock : forArea;
        }
        return TestClient.walking && forWalk;
    }

    // ---------- Tick (vor den Bots) ----------

    static void tick(Minecraft mc) {
        ticks++;
        LocalPlayer p = mc.player;
        boolean active = enabled && p != null && mc.level != null && mc.gameMode != null && p.isAlive()
                && !p.isSpectator() && mc.gui.screen() == null && botWantsDefense();
        if (!active) {
            end(p);
            return;
        }
        if (ticks % 200 == 0) ignored.values().removeIf(until -> until < ticks);

        LivingEntity t = pick(mc, p);
        if (t == null) {
            target = null;
            if (fighting && ++calm >= CALM_TICKS) end(p);
            return;
        }
        calm = 0;
        if (!fighting) {
            fighting = true;
            savedSlot = p.getInventory().getSelectedSlot();
            if (chat) p.sendSystemMessage(Lang.literal("1912: Bot wehrt sich: " + t.getName().getString()));
        }
        if (t != target) {
            target = t;
            targetTicks = 0;
        }
        if (++targetTicks > Math.max(5, giveUpSeconds) * 20) {
            // trifft nicht (z.B. hinter einer Kante) - eine Weile in Ruhe lassen
            ignored.put(t.getId(), ticks + 600);
            target = null;
            return;
        }

        if (useWeapon) Combat.selectWeapon(p);
        float[] rot = Combat.rotationTo(p, Combat.aimPoint(p, t));
        if (rotation == 0) {
            Combat.turn(p, rot, 45f);
            if (!Combat.looksAt(p, t, range / 10.0)) return;
        }
        if (!ready(p, t)) return;
        if (rotation == 1) Combat.sendRotation(mc, p, rot);
        mc.gameMode.attack(p, t);
        p.swing(InteractionHand.MAIN_HAND);
    }

    /** Kampf vorbei: Werkzeug zurueck in die Hand, der Bot macht weiter. */
    private static void end(LocalPlayer p) {
        if (fighting && useWeapon && savedSlot >= 0 && p != null) p.getInventory().setSelectedSlot(savedSlot);
        fighting = false;
        target = null;
        savedSlot = -1;
        calm = 0;
        targetTicks = 0;
    }

    /** Bot gestoppt oder Welt verlassen. */
    static void reset() {
        fighting = false;
        target = null;
        savedSlot = -1;
        calm = 0;
        ignored.clear();
    }

    private static boolean ready(LocalPlayer p, LivingEntity t) {
        if (!waitCooldown) return ticks % 2 == 0; // 10 Schlaege pro Sekunde
        float need = creeperFirst && Combat.isCreeper(t) ? 0.5f : 0.95f;
        return p.getAttackStrengthScale(0.5f) >= need;
    }

    // ---------- Ziele ----------

    private static LivingEntity pick(Minecraft mc, LocalPlayer p) {
        double r = range / 10.0;
        Vec3 eye = p.getEyePosition();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(r + 2.0))) {
            if (!(e instanceof LivingEntity le) || !valid(p, le)) continue;
            double d = Combat.distance(eye, le.getBoundingBox());
            if (d > r) continue;
            if (!throughWalls && !p.hasLineOfSight(le)) continue;
            // Creeper zuerst, sonst der naechste; am alten Ziel bleiben, solange es da ist
            double score = d - (le == target ? 1.0 : 0.0) - (Combat.isCreeper(le) ? 2.0 : 0.0);
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    static boolean valid(LocalPlayer p, LivingEntity le) {
        if (le == p || le instanceof Player || !le.isAlive() || le.getHealth() <= 0) return false;
        Long until = ignored.get(le.getId());
        if (until != null && until > ticks) return false;
        boolean neutral = Combat.isNeutralMonster(le);
        if (le.getType().getCategory() == MobCategory.MONSTER && !neutral) return hostile;
        // Tiere, neutrale Monster, Golems: nur, wenn sie gerade angreifen - und zwar dich
        if (!aggressive || !(le instanceof Mob m) || !m.isAggressive()) return false;
        if (le instanceof TamableAnimal ta && ta.isTame()) return false;
        return facesPlayer(le, p);
    }

    /** Schaut der Mob zum Spieler? (Wer gerade etwas anderes angreift, schaut woandershin.) */
    private static boolean facesPlayer(LivingEntity le, LocalPlayer p) {
        double dx = p.getX() - le.getX();
        double dz = p.getZ() - le.getZ();
        if (dx * dx + dz * dz < 0.25) return true;
        float yawToPlayer = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Math.abs(Mth.wrapDegrees(yawToPlayer - le.getYHeadRot())) < 50f;
    }
}
