package de.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.resources.Identifier;
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
 * Kill Aura: greift Wesen in Reichweite automatisch an.
 * Freunde (Freundesliste), Fake Player, Spieler im Kreativ-/Zuschauermodus, Dorfbewohner und Golems
 * werden nie angegriffen. Alles Weitere ist einzeln einstellbar.
 */
public final class KillAura {
    private KillAura() {
    }

    // ---------- Einstellungen (gespeichert ueber TestConfig, Praefix "ka.") ----------

    public static boolean enabled = false;

    // Ziele
    public static boolean players = true;
    public static boolean hostile = true;
    public static boolean passive = false;
    public static boolean neutralOnlyAngry = true;  // Zombie-Piglins, Endermen, Piglins nur, wenn sie angreifen
    public static boolean skipPets = true;          // gezaehmte und benannte Tiere auslassen
    public static int range = 30;                   // Zehntel-Bloecke (30 = 3,0)
    public static boolean throughWalls = false;
    public static int fov = 360;                    // Blickfeld in Grad (360 = rundherum)
    public static int priority = 0;                 // 0 naechstes, 1 wenigste Leben, 2 kleinster Winkel
    public static int targetMode = 0;               // 0 bleibt am Ziel, 1 wechselt bei jedem Schlag

    // Takt
    public static boolean cooldown = true;          // auf die Abklingzeit warten
    public static int cooldownPercent = 100;
    public static int cpsMin = 8;                   // ohne Abklingzeit: Schlaege pro Sekunde
    public static int cpsMax = 12;
    public static boolean crits = false;            // in der Luft erst beim Fallen schlagen (kritische Treffer)
    public static boolean pauseOnUse = true;        // nicht beim Essen, Blocken, Bogen spannen
    public static boolean onlyHolding = false;      // nur solange die Angriffstaste gedrueckt ist

    // Waffe
    public static boolean onlyWeapon = false;       // nur mit Schwert, Axt, Speer, Streitkolben oder Dreizack
    public static boolean autoWeapon = false;       // beste Waffe aus der Hotbar nehmen
    public static boolean swing = true;             // Arm sichtbar schwingen

    // Drehung
    public static int rotation = 1;                 // 0 keine, 1 nur zum Server, 2 Kopf drehen
    public static int rotSpeed = 40;                // Grad pro Tick beim Kopf drehen

    // Anzeige
    public static boolean highlight = true;
    public static boolean targetInfo = true;
    public static int color = -1;                   // 0xRRGGBB, -1 = Standard

    static final String[] PRIORITIES = {"Nächstes", "Wenigste Leben", "Kleinster Winkel"};
    static final String[] TARGET_MODES = {"Am Ziel bleiben", "Bei jedem Schlag wechseln"};
    static final String[] ROTATIONS = {"Keine", "Nur zum Server", "Kopf drehen"};
    static final int DEFAULT_COLOR = 0xFFFF4D6A;

    // ---------- Zustand ----------

    private static LivingEntity target;
    private static int switchIndex;
    private static double cpsBudget;
    private static double cpsNow = 10;
    private static final Random RNG = new Random();

    public static void register() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("test", "kill_aura"),
                Safe.hud("Kill Aura", KillAura::render));
    }

    /** Welt verlassen: altes Ziel vergessen. */
    static void reset() {
        target = null;
        switchIndex = 0;
        cpsBudget = 0;
    }

    public static void toggle() {
        enabled = !enabled;
        if (!enabled) target = null;
    }

    static LivingEntity target() {
        return target;
    }

    // ---------- Tick ----------

    /** Am Ende jedes Ticks (wie der Auto Clicker). */
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!enabled || p == null || mc.level == null || mc.gameMode == null) {
            target = null;
            return;
        }
        if (!p.isAlive() || p.isSpectator() || mc.gui.screen() != null
                || onlyHolding && !mc.options.keyAttack.isDown()) {
            target = null;
            return;
        }

        if (BotGuard.fighting()) {
            target = null; // die Bot-Verteidigung kaempft gerade - nicht doppelt schlagen
            return;
        }
        target = pick(mc, p);
        if (target == null) {
            cpsBudget = 0;
            return;
        }
        if (pauseOnUse && p.isUsingItem()) return;

        boolean botBusy = otherBotActive();
        if (autoWeapon && !botBusy) Combat.selectWeapon(p);
        if (onlyWeapon && !Combat.isWeapon(p.getMainHandItem())) return;

        float[] rot = Combat.rotationTo(p, Combat.aimPoint(p, target));
        boolean silent = rotation == 1 || rotation == 2 && botBusy; // Bots drehen selbst - dann nur zum Server
        if (rotation == 2 && !botBusy) {
            Combat.turn(p, rot, Math.max(1, rotSpeed));
            if (!Combat.looksAt(p, target, range / 10.0)) return; // erst schlagen, wenn das Ziel im Fadenkreuz ist
        }

        if (!ready(p)) return;
        if (crits && !critReady(p)) return;

        if (silent) Combat.sendRotation(mc, p, rot);
        mc.gameMode.attack(p, target);
        if (swing) {
            p.swing(InteractionHand.MAIN_HAND);
        } else {
            var conn = mc.getConnection();
            if (conn != null) conn.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
        if (targetMode == 1) switchIndex++;
    }

    /** Laeuft gerade ein Bot, der selbst den Blick steuert? */
    private static boolean otherBotActive() {
        return DebrisMiner.isRunning() || Walker.isActive() || TestClient.areaActive;
    }

    // ---------- Ziele ----------

    private static LivingEntity pick(Minecraft mc, LocalPlayer p) {
        double r = range / 10.0;
        Vec3 eye = p.getEyePosition();
        List<LivingEntity> list = new ArrayList<>();
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(r + 2.0))) {
            if (!(e instanceof LivingEntity le) || !valid(p, le)) continue;
            if (Combat.distance(eye, le.getBoundingBox()) > r) continue;
            if (!throughWalls && !p.hasLineOfSight(le)) continue;
            if (fov < 360 && angle(p, le) > fov / 2.0) continue;
            list.add(le);
        }
        if (list.isEmpty()) return null;
        if (targetMode == 0 && target != null && list.contains(target)) return target; // am Ziel bleiben

        Comparator<LivingEntity> order = switch (priority) {
            case 1 -> Comparator.comparingDouble(le -> le.getHealth() + le.getAbsorptionAmount());
            case 2 -> Comparator.comparingDouble(le -> angle(p, le));
            default -> Comparator.comparingDouble(le -> Combat.distance(eye, le.getBoundingBox()));
        };
        list.sort(order);
        if (targetMode == 1) return list.get(Math.floorMod(switchIndex, list.size()));
        return list.get(0);
    }

    /** Darf dieses Wesen ueberhaupt angegriffen werden? */
    static boolean valid(LocalPlayer self, LivingEntity le) {
        if (le == self || !le.isAlive() || le.getHealth() <= 0) return false;
        if (le instanceof Player pl) {
            if (!players || pl.isSpectator() || pl.isCreative()) return false;
            if (PlayerMods.isFake(pl)) return false;
            return !Friends.protect(pl); // Freunde nie
        }
        MobCategory cat = le.getType().getCategory();
        if (cat == MobCategory.MONSTER) {
            if (!hostile) return false;
            if (neutralOnlyAngry && isNeutral(le) && !(le instanceof Mob m && m.isAggressive())) return false;
            return true;
        }
        if (cat == MobCategory.MISC) return false; // Dorfbewohner, Golems, Ruestungsstaender
        if (!passive) return false;
        if (skipPets && (le.hasCustomName() || le instanceof TamableAnimal ta && ta.isTame())) return false;
        return true;
    }

    /** Monster, die erst angreifen, wenn man sie reizt (und dann oft in Gruppen). */
    private static boolean isNeutral(LivingEntity le) {
        return Combat.isNeutralMonster(le);
    }

    /** Winkel zwischen Blickrichtung und Ziel (Grad). */
    private static double angle(LocalPlayer p, LivingEntity le) {
        float[] rot = Combat.rotationTo(p, le.getBoundingBox().getCenter());
        float dy = Math.abs(Mth.wrapDegrees(rot[0] - p.getYRot()));
        float dp = Math.abs(rot[1] - p.getXRot());
        return Math.sqrt(dy * dy + dp * dp);
    }

    // ---------- Takt ----------

    private static boolean ready(LocalPlayer p) {
        if (cooldown) return p.getAttackStrengthScale(0.5f) >= Math.min(100, cooldownPercent) / 100f;
        cpsBudget += cpsNow / 20.0;
        if (cpsBudget < 1.0) return false;
        cpsBudget = Math.min(cpsBudget - 1.0, 1.0);
        int lo = Math.max(1, Math.min(cpsMin, cpsMax));
        int hi = Math.max(lo, Math.max(cpsMin, cpsMax));
        cpsNow = lo + RNG.nextDouble() * (hi - lo);
        return true;
    }

    /** In der Luft erst beim Fallen schlagen; wo kein kritischer Treffer moeglich ist, sofort. */
    private static boolean critReady(LocalPlayer p) {
        if (p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()
                || p.getAbilities().flying || p.isFallFlying()) {
            return true;
        }
        return p.fallDistance > 0 && p.getDeltaMovement().y < 0;
    }

    // ---------- Anzeige ----------

    static int color() {
        return color >= 0 ? 0xFF000000 | color : DEFAULT_COLOR;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        boolean guard = BotGuard.fighting() && BotGuard.highlight;
        LivingEntity t = guard ? BotGuard.target() : enabled ? target : null;
        if (t == null || mc.player == null || !t.isAlive()) return;
        if (mc.gui.screen() != null) return;
        float oldAlpha = Ui.alpha;
        Ui.alpha = 1f;
        try {
            if ((guard || highlight) && Render3D.begin(mc, delta.getGameTimeDeltaPartialTick(true))) {
                Render3D.wire(g, Render3D.smoothBox(t), guard ? BotGuard.COLOR : color());
            }
            if (guard || targetInfo) drawInfo(g, mc, t);
        } finally {
            Ui.alpha = oldAlpha;
        }
    }

    /** Kleines Feld unter dem Fadenkreuz: Name und Leben des Ziels. */
    private static void drawInfo(GuiGraphicsExtractor g, Minecraft mc, LivingEntity t) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        String name = t.getName().getString();
        float hp = t.getHealth();
        float abs = t.getAbsorptionAmount();
        float max = Math.max(1f, t.getMaxHealth());
        String hpText = String.format(java.util.Locale.ROOT, "%.1f", (hp + abs) / 2f).replace(".0", "") + " ❤";
        int w = Math.max(90, Math.max(mc.font.width(name), mc.font.width(hpText)) + 16);
        int x = sw / 2 - w / 2;
        int y = sh / 2 + 14;
        Ui.roundRect(g, x, y, w, 26, 4, 0xC0101018);
        Ui.fill(g, x + 2, y, x + w - 2, y + 1, color());
        // Namen nie uebersetzen
        g.text(mc.font, name, x + 8, y + 4, Ui.mul(Friends.is(t) ? Friends.color() : 0xFFFFFFFF), true);
        g.text(mc.font, hpText, x + w - 8 - mc.font.width(hpText), y + 4, Ui.mul(0xFFFF6B81), true);
        int bx = x + 8;
        int bw = w - 16;
        Ui.fill(g, bx, y + 17, bx + bw, y + 20, 0x60FFFFFF);
        int fill = Math.round(bw * Mth.clamp(hp / max, 0f, 1f));
        int c = hp / max > 0.5f ? 0xFF5CFF7A : hp / max > 0.25f ? 0xFFFFC94D : 0xFFFF4D4D;
        if (fill > 0) Ui.fill(g, bx, y + 17, bx + fill, y + 20, c);
        if (abs > 0) {
            int af = Math.round(bw * Mth.clamp(abs / max, 0f, 1f));
            Ui.fill(g, bx, y + 20, bx + af, y + 21, 0xFFFFD84D);
        }
    }
}
