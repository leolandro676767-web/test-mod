package de.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Trigger Bot, Auto Weapon, Crystal Aura, Surround, Aim Assist, Bow Aimbot, Auto Trap, Hitboxes, Auto Pot,
 * Auto Web, Arrow Dodge und Hole Filler.
 */
public final class CombatMods {
    private CombatMods() {
    }

    // =====================================================================================
    // Einstellungen (werden mit dem Praefix "cb." gespeichert)
    // =====================================================================================

    // --- Trigger Bot ---
    public static boolean trigger = false;
    public static boolean trigPlayers = true;
    public static boolean trigHostile = true;
    public static boolean trigPassive = false;
    public static int trigCooldown = 100;      // so viel % der Abklingzeit abwarten
    public static int trigDelay = 2;           // zusaetzlich zufaellig 0..x Ticks warten (wirkt menschlicher)
    public static boolean trigCrits = false;
    public static boolean trigOnlyWeapon = false;

    // --- Auto Weapon ---
    public static boolean autoWeapon = false;
    public static int weaponPrefer = 0;        // 0 Schwert, 1 Axt
    public static boolean weaponAxeShield = true;
    public static boolean weaponBack = true;

    // --- Crystal Aura ---
    public static boolean crystal = false;
    public static int caRange = 45;            // Zehntel-Bloecke: Kristalle setzen und zerstoeren
    public static int caTargetRange = 100;     // Zehntel-Bloecke: so weit darf das Ziel weg sein
    public static int caMinDamage = 6;         // Lebenspunkte beim Ziel (2 = 1 Herz)
    public static int caMaxSelf = 8;           // hoechstens so viel Schaden bei dir selbst
    public static boolean caAntiSuicide = true;
    public static boolean caPlace = true;
    public static boolean caBreak = true;
    public static int caPlaceDelay = 2;        // Ticks
    public static int caBreakDelay = 2;
    public static boolean caSwitch = true;
    public static boolean caMonsters = false;
    public static boolean caPauseEat = true;
    public static boolean caRender = true;

    // --- Surround ---
    public static boolean surround = false;
    public static int surBlocks = 0;           // 0 Obsidian, 1 + Endertruhen, 2 + Baubloecke
    public static boolean surCenter = true;
    public static boolean surFloor = true;
    public static boolean surDisableMove = true;
    public static int surPerTick = 4;

    // --- Aim Assist ---
    public static boolean aimAssist = false;
    public static int aimSpeed = 120;          // Grad pro Sekunde
    public static int aimRange = 45;           // Zehntel-Bloecke
    public static int aimFov = 60;             // Grad
    public static boolean aimOnlyClick = false;
    public static boolean aimVertical = true;
    public static boolean aimWalls = false;
    public static boolean aimPlayers = true;
    public static boolean aimHostile = true;
    public static boolean aimPassive = false;

    // --- Bow Aimbot ---
    public static boolean bowAim = false;
    public static int bowRange = 60;           // Bloecke
    public static int bowFov = 90;
    public static int bowSpeed = 540;          // Grad pro Sekunde
    public static boolean bowPredict = true;
    public static boolean bowTrident = true;
    public static boolean bowPlayers = true;
    public static boolean bowHostile = true;
    public static boolean bowPassive = false;

    // --- Auto Trap ---
    public static boolean autoTrap = false;
    public static int trapRange = 45;          // Zehntel-Bloecke
    public static boolean trapFull = true;     // auch Kopfhoehe und Decke
    public static int trapPerTick = 3;
    public static boolean trapBuildBlocks = false;
    public static boolean trapMonsters = false;

    // --- Hitboxes ---
    public static boolean hitboxes = false;
    public static int hitboxSize = 3;          // Zehntel-Bloecke mehr rundherum
    public static boolean hbPlayers = true;
    public static boolean hbHostile = true;
    public static boolean hbPassive = false;

    // --- Auto Pot ---
    public static boolean autoPot = false;
    public static int potHealth = 10;          // Lebenspunkte (2 = 1 Herz)
    public static int potDelay = 10;           // Ticks zwischen zwei Traenken

    // --- Auto Web ---
    public static boolean autoWeb = false;
    public static int webRange = 45;           // Zehntel-Bloecke
    public static boolean webHead = false;
    public static int webDelay = 10;
    public static boolean webMonsters = false;

    // --- Arrow Dodge ---
    public static boolean arrowDodge = false;
    public static int dodgeSpeed = 6;          // Zehntel-Bloecke pro Tick zur Seite
    public static int dodgeTicks = 10;         // so frueh ausweichen
    public static boolean dodgeJump = false;

    // --- Hole Filler ---
    public static boolean holeFiller = false;
    public static int hfRange = 45;            // Zehntel-Bloecke
    public static boolean hfNearEnemy = true;
    public static int hfEnemyDist = 3;
    public static boolean hfOnlySafe = true;   // nur Loecher aus Obsidian/Grundgestein
    public static int hfPerTick = 2;
    public static boolean hfBuildBlocks = false;

    static final String[] PREFER = {"Schwert", "Axt"};
    static final String[] SUR_BLOCKS = {"Obsidian", "Obsidian und Endertruhen", "Auch Bruchstein & Co."};
    private static final int CA_COLOR = 0xFFB45CFF;

    private static final Random RNG = new Random();

    static void register() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("test", "combat_mods"),
                Safe.hud("Crystal Aura", CombatMods::render));
    }

    /** Welt verlassen. */
    static void reset() {
        caTarget = null;
        caShown = null;
        surStart = null;
        weaponPrev = -1;
        trigWait = -1;
    }

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || !p.isAlive()) {
            reset();
            return;
        }
        Safe.run("Auto Weapon", () -> weaponTick(mc, p));
        Safe.run("Surround", () -> surroundTick(mc, p));
        Safe.run("Crystal Aura", () -> crystalTick(mc, p));
        Safe.run("Trigger Bot", () -> triggerTick(mc, p));
        Safe.run("Auto Trap", () -> trapTick(mc, p));
        Safe.run("Auto Pot", () -> potTick(mc, p));
        Safe.run("Auto Web", () -> webTick(mc, p));
        Safe.run("Arrow Dodge", () -> dodgeTick(mc, p));
        Safe.run("Hole Filler", () -> holeTick(mc, p));
    }

    // =====================================================================================
    // Trigger Bot
    // =====================================================================================

    private static int trigWait = -1;

    private static void triggerTick(Minecraft mc, LocalPlayer p) {
        if (!trigger || mc.gui.screen() != null || p.isSpectator()) return;
        if (!(mc.hitResult instanceof EntityHitResult eh) || eh.getType() != HitResult.Type.ENTITY
                || !(eh.getEntity() instanceof LivingEntity le) || !trigValid(le)) {
            trigWait = -1;
            return;
        }
        if (p.isUsingItem()) return;
        if (trigOnlyWeapon && !Combat.isWeapon(p.getMainHandItem())) return;
        if (p.getAttackStrengthScale(0.5f) < Math.min(100, trigCooldown) / 100f) return;
        if (trigCrits && !critReady(p)) return;
        if (trigWait < 0) trigWait = trigDelay <= 0 ? 0 : RNG.nextInt(trigDelay + 1);
        if (trigWait > 0) {
            trigWait--;
            return;
        }
        trigWait = -1;
        mc.gameMode.attack(p, le);
        p.swing(InteractionHand.MAIN_HAND);
    }

    private static boolean trigValid(LivingEntity le) {
        return valid(le, trigPlayers, trigHostile, trigPassive);
    }

    /** Darf dieses Wesen ein Ziel sein? Freunde, Kreativ-Spieler, Dorfbewohner und Haustiere nie. */
    static boolean valid(LivingEntity le, boolean players, boolean hostile, boolean passive) {
        Minecraft mc = Minecraft.getInstance();
        if (le == mc.player || !le.isAlive() || le.getHealth() <= 0) return false;
        if (le instanceof Player pl) {
            return players && !pl.isSpectator() && !pl.isCreative() && !PlayerMods.isFake(pl) && !Friends.protect(pl);
        }
        MobCategory cat = le.getType().getCategory();
        if (cat == MobCategory.MONSTER) {
            // Zombie-Piglins, Endermen, Piglins nur, wenn sie schon angreifen (sonst kommt die ganze Gruppe)
            return hostile && !(Combat.isNeutralMonster(le) && !(le instanceof Mob m && m.isAggressive()));
        }
        if (cat == MobCategory.MISC) return false; // Dorfbewohner, Golems, Ruestungsstaender
        return passive && !le.hasCustomName() && !(le instanceof TamableAnimal ta && ta.isTame());
    }

    // =====================================================================================
    // Aim Assist und Bow Aimbot (laufen in jedem Bild, damit die Drehung weich ist)
    // =====================================================================================

    private static long aimLast;

    /** Vom MouseHandlerMixin in jedem Bild, nachdem die Maus den Blick gedreht hat. */
    public static void aimFrame() {
        long now = System.nanoTime();
        float dt = aimLast == 0 ? 0f : Math.min(0.05f, (now - aimLast) / 1.0e9f);
        aimLast = now;
        if (!aimAssist && !bowAim || dt <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null || p.isSpectator() || !p.isAlive()) return;
        if (bowAim) {
            float[] rot = bowRotation(mc, p);
            if (rot != null) {
                turnToward(p, rot, bowSpeed * dt, true);
                return;
            }
        }
        if (!aimAssist || aimOnlyClick && !mc.options.keyAttack.isDown()) return;
        LivingEntity t = aimTarget(mc, p, aimRange / 10.0, aimFov, aimWalls, aimPlayers, aimHostile, aimPassive);
        if (t == null) return;
        Vec3 point = new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ());
        turnToward(p, Combat.rotationTo(p, point), aimSpeed * dt, aimVertical);
    }

    /** Dreht den Blick hoechstens maxStep Grad weiter (wie eine Mausbewegung, ohne Ruckeln). */
    private static void turnToward(LocalPlayer p, float[] rot, float maxStep, boolean vertical) {
        float dy = Mth.clamp(Mth.wrapDegrees(rot[0] - p.getYRot()), -maxStep, maxStep);
        float dp = vertical ? Mth.clamp(rot[1] - p.getXRot(), -maxStep, maxStep) : 0f;
        if (Math.abs(dy) < 0.001f && Math.abs(dp) < 0.001f) return;
        p.turn(dy / 0.15, dp / 0.15);
    }

    /** Ziel mit dem kleinsten Winkel zum Fadenkreuz. */
    private static LivingEntity aimTarget(Minecraft mc, LocalPlayer p, double range, int fov, boolean walls,
                                          boolean players, boolean hostile, boolean passive) {
        Vec3 eye = p.getEyePosition();
        LivingEntity best = null;
        double bestAngle = fov / 2.0;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(range + 1.0))) {
            if (!(e instanceof LivingEntity le) || !valid(le, players, hostile, passive)) continue;
            if (Combat.distance(eye, le.getBoundingBox()) > range) continue;
            if (!walls && !p.hasLineOfSight(le)) continue;
            float[] rot = Combat.rotationTo(p, le.getBoundingBox().getCenter());
            float dy = Mth.wrapDegrees(rot[0] - p.getYRot());
            float dp = rot[1] - p.getXRot();
            double a = Math.sqrt(dy * dy + dp * dp);
            if (a < bestAngle) {
                bestAngle = a;
                best = le;
            }
        }
        return best;
    }

    /** Blickrichtung, mit der der Pfeil (oder Dreizack) das Ziel trifft; null = nicht am Spannen / kein Ziel. */
    private static float[] bowRotation(Minecraft mc, LocalPlayer p) {
        if (!p.isUsingItem()) return null;
        String id = BuiltInRegistries.ITEM.getKey(p.getUseItem().getItem()).getPath();
        double speed;
        if (id.equals("bow")) {
            float t = p.getTicksUsingItem() / 20.0f;
            speed = Math.min(1.0f, (t * t + t * 2.0f) / 3.0f) * 3.0;
        } else if (id.equals("trident") && bowTrident) {
            speed = 2.5;
        } else {
            return null;
        }
        speed = Math.max(0.6, speed);
        LivingEntity t = aimTarget(mc, p, bowRange, bowFov, false, bowPlayers, bowHostile, bowPassive);
        if (t == null) return null;
        Vec3 from = p.getEyePosition().subtract(0, 0.1, 0);
        Vec3 aim = new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ());
        double vx = t.getX() - t.xo;
        double vz = t.getZ() - t.zo;
        float[] sol = solveArc(from, aim, speed);
        if (sol == null) return null;
        if (bowPredict) {
            for (int i = 0; i < 2; i++) { // vorhalten: wo ist das Ziel, wenn der Pfeil ankommt?
                Vec3 ahead = aim.add(vx * sol[2], 0, vz * sol[2]);
                float[] s2 = solveArc(from, ahead, speed);
                if (s2 == null) break;
                sol = s2;
            }
        }
        return new float[] {sol[0], sol[1]};
    }

    /** {Yaw, Pitch, Flugzeit in Ticks} fuer einen flachen Bogen mit Luftwiderstand und Schwerkraft wie bei Pfeilen. */
    private static float[] solveArc(Vec3 from, Vec3 to, double v) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double dy = to.y - from.y;
        double d = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        if (d < 0.5) return new float[] {yaw, dy > 0 ? -90f : 90f, 1};
        double lo = -1.45;
        double hi = 0.75;
        double[] res = new double[2];
        for (int i = 0; i < 28; i++) {
            double mid = (lo + hi) / 2;
            if (!flight(mid, d, v, res) || res[0] < dy) lo = mid;
            else hi = mid;
        }
        if (!flight(hi, d, v, res)) return null; // zu weit weg
        return new float[] {yaw, (float) -Math.toDegrees(hi), (float) res[1]};
    }

    /** Hoehe (res[0]) und Ticks (res[1]), wenn der Pfeil die Entfernung d erreicht; false = kommt nicht so weit. */
    private static boolean flight(double angle, double d, double v, double[] res) {
        double x = 0;
        double y = 0;
        double vx = v * Math.cos(angle);
        double vy = v * Math.sin(angle);
        for (int t = 1; t <= 400; t++) {
            double nx = x + vx;
            double ny = y + vy;
            if (nx >= d) {
                double f = (d - x) / Math.max(1e-6, nx - x);
                res[0] = y + (ny - y) * f;
                res[1] = t - 1 + f;
                return true;
            }
            x = nx;
            y = ny;
            vx *= 0.99;
            vy = vy * 0.99 - 0.05;
            if (vx < 1e-3 || y < -200) return false;
        }
        return false;
    }

    // =====================================================================================
    // Auto Trap
    // =====================================================================================

    private static void trapTick(Minecraft mc, LocalPlayer p) {
        if (!autoTrap || mc.gui.screen() != null || p.isSpectator()) return;
        double r = trapRange / 10.0;
        LivingEntity t = null;
        double bestD = (r + 1) * (r + 1);
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(r + 1))) {
            if (!(e instanceof LivingEntity le) || !valid(le, true, trapMonsters, false)) continue;
            double d = p.distanceToSqr(le);
            if (d < bestD) {
                bestD = d;
                t = le;
            }
        }
        if (t == null) return;
        int slot = trapSlot(p);
        if (slot < 0) return;
        Level level = mc.level;
        BlockPos f = BlockPos.containing(t.getX(), t.getY() + 0.2, t.getZ());
        List<BlockPos> want = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) want.add(f.relative(d));
        if (trapFull) {
            for (Direction d : Direction.Plane.HORIZONTAL) want.add(f.above().relative(d));
            want.add(f.above(2));
        }
        int placed = 0;
        for (BlockPos pos : want) {
            if (placed >= Math.max(1, trapPerTick)) break;
            if (!level.getBlockState(pos).canBeReplaced() || blocked(level, pos)) continue;
            BlockPos at = pos;
            if (!hasSupport(level, pos)) {
                at = pos.below(); // erst etwas darunter, sonst gibt es nichts zum Anklicken
                if (!level.getBlockState(at).canBeReplaced() || blocked(level, at)) continue;
            }
            if (placeBlock(mc, p, at, slot)) placed++;
        }
    }

    private static int trapSlot(LocalPlayer p) {
        var inv = p.getInventory();
        int best = -1;
        int bestRank = 0;
        for (int i = 0; i < 9; i++) {
            if (!(inv.getItem(i).getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            int rank = b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN ? 2
                    : trapBuildBlocks && TestClient.isBuildBlock(b) ? 1 : 0;
            if (rank > bestRank) {
                bestRank = rank;
                best = i;
            }
        }
        return best;
    }

    // =====================================================================================
    // Auto Pot
    // =====================================================================================

    private static int potTimer;

    /** Heil-Wurftrank (Sofortheilung, auch Stufe II)? */
    private static boolean healPotion(ItemStack st) {
        if (st.isEmpty() || !BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("splash_potion")) return false;
        PotionContents pc = st.get(DataComponents.POTION_CONTENTS);
        if (pc == null) return false;
        for (MobEffectInstance e : pc.getAllEffects()) {
            if (e.getEffect().value() == MobEffects.INSTANT_HEALTH.value()) return true;
        }
        return false;
    }

    private static void potTick(Minecraft mc, LocalPlayer p) {
        if (potTimer > 0) potTimer--;
        if (!autoPot || potTimer > 0 || mc.gui.screen() != null || p.isSpectator() || p.isCreative()) return;
        if (p.getHealth() > potHealth || p.isUsingItem()) return;
        var inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (healPotion(inv.getItem(i))) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;
        // senkrecht nach unten werfen: der Trank zerplatzt direkt unter einem
        int prev = inv.getSelectedSlot();
        float pitch = p.getXRot();
        inv.setSelectedSlot(slot);
        p.setXRot(90f);
        Combat.sendRotation(mc, p, new float[] {p.getYRot(), 90f});
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        p.setXRot(pitch);
        inv.setSelectedSlot(prev);
        potTimer = Math.max(4, potDelay);
    }

    // =====================================================================================
    // Auto Web
    // =====================================================================================

    private static int webTimer;

    private static void webTick(Minecraft mc, LocalPlayer p) {
        if (webTimer > 0) webTimer--;
        if (!autoWeb || webTimer > 0 || mc.gui.screen() != null || p.isSpectator()) return;
        var inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("cobweb")) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return;
        double r = webRange / 10.0;
        Level level = mc.level;
        for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(r + 1))) {
            if (!(e instanceof LivingEntity le) || !valid(le, true, webMonsters, false)) continue;
            if (p.distanceToSqr(le) > (r + 1) * (r + 1)) continue;
            BlockPos feet = BlockPos.containing(le.getX(), le.getY() + 0.1, le.getZ());
            List<BlockPos> spots = new ArrayList<>();
            spots.add(feet);
            if (webHead) spots.add(feet.above());
            for (BlockPos pos : spots) {
                BlockState s = level.getBlockState(pos);
                if (s.is(Blocks.COBWEB) || !s.canBeReplaced()) continue;
                if (p.getBoundingBox().intersects(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0,
                        pos.getY() + 1.0, pos.getZ() + 1.0))) {
                    continue; // nicht sich selbst einspinnen
                }
                if (placeBlock(mc, p, pos, slot)) {
                    webTimer = Math.max(1, webDelay);
                    return;
                }
            }
        }
    }

    // =====================================================================================
    // Arrow Dodge
    // =====================================================================================

    private static final java.util.Map<Integer, Integer> dodged = new java.util.HashMap<>();
    private static int dodgeNow;

    private static void dodgeTick(Minecraft mc, LocalPlayer p) {
        dodgeNow++;
        if (!arrowDodge || p.isSpectator() || p.isPassenger()) return;
        if (dodgeNow % 100 == 0) dodged.values().removeIf(t -> dodgeNow - t > 100);
        AABB me = p.getBoundingBox().inflate(0.35);
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(40))) {
            if (!(e instanceof AbstractArrow a) || !a.isAlive() || a.getOwner() == p) continue;
            if (dodged.containsKey(a.getId())) continue;
            Vec3 v = a.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue; // steckt fest
            // Flugbahn ein paar Ticks voraus rechnen (wie Minecraft: bewegen, bremsen, fallen)
            Vec3 pos = a.position();
            boolean hit = false;
            for (int t = 0; t < Math.max(2, dodgeTicks); t++) {
                Vec3 next = pos.add(v);
                if (Combat.rayHit(pos, next, me) >= 0) {
                    hit = true;
                    break;
                }
                pos = next;
                v = v.scale(0.99).subtract(0, 0.05, 0);
            }
            if (!hit) continue;
            dodged.put(a.getId(), dodgeNow);
            Vec3 av = a.getDeltaMovement();
            double len = Math.sqrt(av.x * av.x + av.z * av.z);
            double sx;
            double sz;
            if (len < 1e-3) { // kommt senkrecht von oben: einfach in Blickrichtung weg
                sx = -Math.sin(Math.toRadians(p.getYRot()));
                sz = Math.cos(Math.toRadians(p.getYRot()));
            } else {
                sx = -av.z / len; // quer zur Flugrichtung
                sz = av.x / len;
                double rx = p.getX() - a.getX();
                double rz = p.getZ() - a.getZ();
                if (rx * sx + rz * sz < 0) { // auf der Seite bleiben, auf der man schon ist
                    sx = -sx;
                    sz = -sz;
                }
            }
            double speed = dodgeSpeed / 10.0;
            Vec3 cur = p.getDeltaMovement();
            double vy = dodgeJump && p.onGround() ? 0.42 : cur.y;
            p.setDeltaMovement(cur.x + sx * speed, vy, cur.z + sz * speed);
            return;
        }
    }

    // =====================================================================================
    // Hole Filler
    // =====================================================================================

    private static boolean solidBlock(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && !s.canBeReplaced();
    }

    private static boolean safeBlock(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.is(Blocks.OBSIDIAN) || s.is(Blocks.BEDROCK) || s.is(Blocks.CRYING_OBSIDIAN)
                || s.is(Blocks.RESPAWN_ANCHOR) || s.is(Blocks.ENDER_CHEST);
    }

    /** Ein 1x1-Loch: Fuss- und Kopfhoehe frei, ringsum und unten fest. */
    private static boolean isHole(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return false;
        BlockPos[] around = {pos.below(), pos.north(), pos.south(), pos.east(), pos.west()};
        for (BlockPos b : around) {
            if (hfOnlySafe ? !safeBlock(level, b) : !solidBlock(level, b)) return false;
        }
        return true;
    }

    private static void holeTick(Minecraft mc, LocalPlayer p) {
        if (!holeFiller || mc.gui.screen() != null || p.isSpectator()) return;
        int slot = -1;
        var inv = p.getInventory();
        int bestRank = 0;
        for (int i = 0; i < 9; i++) {
            if (!(inv.getItem(i).getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            int rank = b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN ? 2
                    : hfBuildBlocks && TestClient.isBuildBlock(b) ? 1 : 0;
            if (rank > bestRank) {
                bestRank = rank;
                slot = i;
            }
        }
        if (slot < 0) return;
        Level level = mc.level;
        double r = hfRange / 10.0;
        List<LivingEntity> enemies = new ArrayList<>();
        if (hfNearEnemy) {
            for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(r + 8))) {
                if (e instanceof LivingEntity le && valid(le, true, false, false)) enemies.add(le);
            }
            if (enemies.isEmpty()) return;
        }
        BlockPos me = BlockPos.containing(p.getX(), p.getY() + 0.2, p.getZ());
        BlockPos c = p.blockPosition();
        int ri = (int) Math.ceil(r);
        int placed = 0;
        for (int x = -ri; x <= ri && placed < Math.max(1, hfPerTick); x++) {
            for (int y = -ri; y <= ri && placed < Math.max(1, hfPerTick); y++) {
                for (int z = -ri; z <= ri && placed < Math.max(1, hfPerTick); z++) {
                    BlockPos pos = c.offset(x, y, z);
                    if (pos.equals(me) || !isHole(level, pos)) continue;
                    if (hfNearEnemy) {
                        boolean near = false;
                        for (LivingEntity le : enemies) {
                            double dx = le.getX() - (pos.getX() + 0.5);
                            double dz = le.getZ() - (pos.getZ() + 0.5);
                            if (dx * dx + dz * dz <= hfEnemyDist * hfEnemyDist
                                    && Math.abs(le.getY() - pos.getY()) < 4) {
                                near = true;
                                break;
                            }
                        }
                        if (!near) continue;
                    }
                    if (blocked(level, pos)) continue; // da steht schon jemand drin
                    if (placeBlock(mc, p, pos, slot)) placed++;
                }
            }
        }
    }

    // =====================================================================================
    // Hitboxes
    // =====================================================================================

    /** Vom EntityMixin: so viel groesser ist die Hitbox beim Anvisieren (nur auf dem eigenen Bildschirm). */
    public static float pickExtra(Entity e) {
        if (!hitboxes || !(e instanceof LivingEntity le) || !e.level().isClientSide()) return 0f;
        if (!valid(le, hbPlayers, hbHostile, hbPassive)) return 0f;
        return Math.max(0, Math.min(10, hitboxSize)) / 10f;
    }

    /** In der Luft erst beim Fallen schlagen; wo kein kritischer Treffer moeglich ist, sofort. */
    private static boolean critReady(LocalPlayer p) {
        if (p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()
                || p.getAbilities().flying || p.isFallFlying()) {
            return true;
        }
        return p.fallDistance > 0 && p.getDeltaMovement().y < 0;
    }

    // =====================================================================================
    // Auto Weapon
    // =====================================================================================

    private static int weaponPrev = -1;   // Platz vor dem Wechsel
    private static int weaponSet = -1;    // Platz, auf den gewechselt wurde
    private static int weaponIdle;

    /** Vom MultiPlayerGameModeMixin direkt vor jedem Schlag (auch von Kill Aura und Trigger Bot). */
    public static void beforeAttack(Player player, Entity target) {
        if (!autoWeapon) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || player != p || !(target instanceof LivingEntity le)) return;
        boolean axe = weaponPrefer == 1 || weaponAxeShield && le instanceof Player pl && pl.isBlocking();
        var inv = p.getInventory();
        int cur = inv.getSelectedSlot();
        int best = cur;
        int bestScore = weaponScore(inv.getItem(cur), axe);
        for (int i = 0; i < 9; i++) {
            int s = weaponScore(inv.getItem(i), axe);
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        weaponIdle = 0;
        if (best == cur) return;
        if (weaponPrev < 0) weaponPrev = cur;
        weaponSet = best;
        inv.setSelectedSlot(best); // attack() schickt den neuen Platz gleich danach an den Server
    }

    private static int weaponScore(ItemStack st, boolean axe) {
        int s = Combat.weaponScore(st);
        if (s < 0) return -1;
        if (axe && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("_axe")) s += 100;
        return s;
    }

    private static void weaponTick(Minecraft mc, LocalPlayer p) {
        if (weaponPrev < 0) return;
        var inv = p.getInventory();
        if (!autoWeapon || !weaponBack || inv.getSelectedSlot() != weaponSet) {
            weaponPrev = -1; // selbst umgeschaltet: so lassen
            return;
        }
        if (mc.options.keyAttack.isDown()) weaponIdle = 0;
        if (++weaponIdle < 30) return; // anderthalb Sekunden nicht mehr geschlagen
        inv.setSelectedSlot(weaponPrev);
        weaponPrev = -1;
    }

    // =====================================================================================
    // Crystal Aura
    // =====================================================================================

    private static LivingEntity caTarget;
    private static BlockPos caShown;
    private static int caPlaceTimer;
    private static int caBreakTimer;

    private static void crystalTick(Minecraft mc, LocalPlayer p) {
        if (!crystal || p.isSpectator() || mc.gui.screen() != null) {
            caTarget = null;
            caShown = null;
            return;
        }
        if (caPlaceTimer > 0) caPlaceTimer--;
        if (caBreakTimer > 0) caBreakTimer--;
        caTarget = crystalTarget(mc, p);
        if (caTarget == null) {
            caShown = null;
            return;
        }
        if (caPauseEat && p.isUsingItem()) return;
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (caBreak && caBreakTimer <= 0) {
            EndCrystal c = bestCrystal(mc, p, hp);
            if (c != null) {
                Combat.sendRotation(mc, p, Combat.rotationTo(p, c.getBoundingBox().getCenter()));
                mc.gameMode.attack(p, c);
                p.swing(InteractionHand.MAIN_HAND);
                caBreakTimer = caBreakDelay;
            }
        }
        if (caPlace && caPlaceTimer <= 0) {
            caShown = bestPlace(mc, p, hp);
            if (caShown != null && placeCrystal(mc, p, caShown)) caPlaceTimer = caPlaceDelay;
        }
    }

    private static LivingEntity crystalTarget(Minecraft mc, LocalPlayer p) {
        double r = caTargetRange / 10.0;
        LivingEntity best = null;
        double bestD = r * r;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(r))) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || le.getHealth() <= 0) continue;
            if (le instanceof Player pl) {
                if (pl.isSpectator() || pl.isCreative() || PlayerMods.isFake(pl) || Friends.protect(pl)) continue;
            } else if (!caMonsters || le.getType().getCategory() != MobCategory.MONSTER) {
                continue;
            }
            double d = p.distanceToSqr(le);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    /**
     * Schaden einer Kristall-Explosion (Staerke 6) bei diesem Wesen - wie Minecraft rechnet: Abstand,
     * wie viel von ihm zu sehen ist, dann die Ruestung. Verzauberungen, Resistenz und Schwierigkeit sind
     * nicht dabei (also eher etwas zu hoch geschaetzt).
     */
    static float crystalDamage(LivingEntity e, Vec3 center) {
        double dist = Math.sqrt(e.distanceToSqr(center)) / 12.0;
        if (dist > 1.0) return 0f;
        double seen = ServerExplosion.getSeenPercent(center, e);
        double impact = (1.0 - dist) * seen;
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
        float armor = e.getArmorValue();
        float tough = (float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float eff = Mth.clamp(armor - dmg / (2.0f + tough / 4.0f), armor * 0.2f, 20.0f);
        return Math.max(0f, dmg * (1.0f - eff / 25.0f));
    }

    private static boolean safeForMe(float self, float hp) {
        if (self > caMaxSelf) return false;
        return !caAntiSuicide || self < hp - 2f;
    }

    private static EndCrystal bestCrystal(Minecraft mc, LocalPlayer p, float hp) {
        double reach = caRange / 10.0;
        Vec3 eye = p.getEyePosition();
        EndCrystal best = null;
        float bestDmg = 0f;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(reach + 2.0))) {
            if (!(e instanceof EndCrystal c) || !c.isAlive()) continue;
            if (Combat.distance(eye, c.getBoundingBox()) > reach) continue;
            Vec3 center = c.position();
            if (!safeForMe(crystalDamage(p, center), hp)) continue;
            float dmg = crystalDamage(caTarget, center);
            if (dmg < caMinDamage && dmg < caTarget.getHealth() + caTarget.getAbsorptionAmount()) continue;
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = c;
            }
        }
        return best;
    }

    private static boolean crystalBase(BlockState s) {
        return s.is(Blocks.OBSIDIAN) || s.is(Blocks.BEDROCK);
    }

    private static BlockPos bestPlace(Minecraft mc, LocalPlayer p, float hp) {
        Level level = mc.level;
        double reach = caRange / 10.0;
        int r = (int) Math.ceil(reach);
        Vec3 eye = p.getEyePosition();
        BlockPos c = p.blockPosition();
        float targetHp = caTarget.getHealth() + caTarget.getAbsorptionAmount();
        BlockPos best = null;
        float bestDmg = 0f;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos base = c.offset(x, y, z);
                    if (!crystalBase(level.getBlockState(base))) continue;
                    BlockPos up = base.above();
                    if (!level.getBlockState(up).isAir()) continue;
                    Vec3 center = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
                    if (eye.distanceTo(center) > reach) continue;
                    if (center.distanceToSqr(caTarget.position()) > 144.0) continue;
                    AABB space = new AABB(up.getX(), up.getY(), up.getZ(), up.getX() + 1.0, up.getY() + 2.0,
                            up.getZ() + 1.0);
                    if (!level.getEntities((Entity) null, space).isEmpty()) continue; // dort kann keiner hin
                    float dmg = crystalDamage(caTarget, center);
                    if (dmg < caMinDamage && dmg < targetHp) continue;
                    if (dmg <= bestDmg) continue;
                    if (!safeForMe(crystalDamage(p, center), hp)) continue;
                    bestDmg = dmg;
                    best = base;
                }
            }
        }
        return best;
    }

    private static boolean isCrystal(ItemStack st) {
        return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("end_crystal");
    }

    private static boolean placeCrystal(Minecraft mc, LocalPlayer p, BlockPos base) {
        InteractionHand hand;
        if (isCrystal(p.getMainHandItem())) {
            hand = InteractionHand.MAIN_HAND;
        } else if (isCrystal(p.getOffhandItem())) {
            hand = InteractionHand.OFF_HAND;
        } else {
            if (!caSwitch) return false;
            var inv = p.getInventory();
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                if (isCrystal(inv.getItem(i))) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) return false;
            inv.setSelectedSlot(slot);
            hand = InteractionHand.MAIN_HAND;
        }
        Vec3 hit = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        Combat.sendRotation(mc, p, Combat.rotationTo(p, hit));
        mc.gameMode.useItemOn(p, hand, new BlockHitResult(hit, Direction.UP, base, false));
        p.swing(hand);
        return true;
    }

    // =====================================================================================
    // Surround
    // =====================================================================================

    private static BlockPos surStart;
    private static boolean surWarned;

    private static void surroundTick(Minecraft mc, LocalPlayer p) {
        if (!surround) {
            surStart = null;
            return;
        }
        Level level = mc.level;
        BlockPos feet = BlockPos.containing(p.getX(), p.getY() + 0.2, p.getZ());
        if (surStart == null) {
            surStart = feet;
            surWarned = false;
            if (surCenter && p.onGround()) {
                p.setPos(feet.getX() + 0.5, p.getY(), feet.getZ() + 0.5);
                p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
            }
        } else if (surDisableMove && !feet.equals(surStart)) {
            surround = false;
            surStart = null;
            Keybinds.message(Lang.t("Surround aus: du hast dich bewegt"), "");
            return;
        }
        int slot = surroundSlot(p);
        if (slot < 0) {
            if (!surWarned) {
                surWarned = true;
                p.sendSystemMessage(Lang.literal("§e1912: " + Lang.t("Surround: keine passenden Blöcke in der Hotbar")));
            }
            return;
        }
        // alle Bloecke, die die Hitbox auf Fusshoehe beruehrt (meist einer, sonst bis zu vier)
        AABB bb = p.getBoundingBox();
        int y = feet.getY();
        Set<BlockPos> foot = new HashSet<>();
        for (int x = Mth.floor(bb.minX + 0.001); x <= Mth.floor(bb.maxX - 0.001); x++) {
            for (int z = Mth.floor(bb.minZ + 0.001); z <= Mth.floor(bb.maxZ - 0.001); z++) {
                foot.add(new BlockPos(x, y, z));
            }
        }
        List<BlockPos> want = new ArrayList<>();
        if (surFloor) {
            for (BlockPos f : foot) want.add(f.below());
        }
        for (BlockPos f : foot) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = f.relative(d);
                if (!foot.contains(n) && !want.contains(n)) want.add(n);
            }
        }
        int placed = 0;
        for (BlockPos pos : want) {
            if (placed >= Math.max(1, surPerTick)) break;
            if (!level.getBlockState(pos).canBeReplaced() || blocked(level, pos)) continue;
            if (!hasSupport(level, pos)) {
                // nichts zum Anklicken (z.B. an einer Kante): erst darunter einen Block setzen
                BlockPos below = pos.below();
                if (level.getBlockState(below).canBeReplaced() && !blocked(level, below)
                        && placeBlock(mc, p, below, slot)) {
                    placed++;
                }
                continue;
            }
            if (placeBlock(mc, p, pos, slot)) placed++;
        }
    }

    private static int surroundSlot(LocalPlayer p) {
        var inv = p.getInventory();
        int best = -1;
        int bestRank = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!(st.getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            int rank = b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN ? 3
                    : surBlocks >= 1 && b == Blocks.ENDER_CHEST ? 2
                    : surBlocks >= 2 && TestClient.isBuildBlock(b) ? 1 : 0;
            if (rank > bestRank) {
                bestRank = rank;
                best = i;
            }
        }
        return best;
    }

    /** Steht dort ein Wesen (oder ein Kristall), kann kein Block hin. */
    private static boolean blocked(Level level, BlockPos pos) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        return !level.getEntities((Entity) null, box, e -> e.blocksBuilding && e.isAlive() && !e.isSpectator()).isEmpty();
    }

    private static boolean hasSupport(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState s = level.getBlockState(pos.relative(d));
            if (!s.isAir() && !s.canBeReplaced()) return true;
        }
        return false;
    }

    /** Setzt einen Block aus der Hotbar an pos (klickt auf einen Nachbarn); die Auswahl bleibt, wie sie war. */
    static boolean placeBlock(Minecraft mc, LocalPlayer p, BlockPos pos, int slot) {
        Level level = mc.level;
        Vec3 eye = p.getEyePosition();
        double reach = p.blockInteractionRange();
        for (Direction d : Direction.values()) {
            BlockPos against = pos.relative(d);
            BlockState s = level.getBlockState(against);
            if (s.isAir() || s.canBeReplaced()) continue;
            if (s.hasBlockEntity()) continue; // Truhen usw. wuerden sich oeffnen
            Direction face = d.getOpposite();
            Vec3 hit = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            if (eye.distanceTo(hit) > reach) continue;
            Combat.sendRotation(mc, p, Combat.rotationTo(p, hit));
            var inv = p.getInventory();
            int prev = inv.getSelectedSlot();
            inv.setSelectedSlot(slot);
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, against, false));
            p.swing(InteractionHand.MAIN_HAND);
            inv.setSelectedSlot(prev);
            return true;
        }
        return false;
    }

    // =====================================================================================
    // Anzeige
    // =====================================================================================

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!crystal || !caRender || caShown == null || mc.player == null || mc.gui.screen() != null) return;
        if (!Render3D.begin(mc, delta.getGameTimeDeltaPartialTick(true))) return;
        BlockPos b = caShown;
        Render3D.wire(g, new AABB(b.getX(), b.getY(), b.getZ(), b.getX() + 1.0, b.getY() + 1.0, b.getZ() + 1.0), CA_COLOR);
    }

    // =====================================================================================
    // Einstellungen
    // =====================================================================================

    static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Trigger Bot" -> new ModuleScreen(parent, module, f -> {
                f.description("Schlägt automatisch zu, sobald dein Fadenkreuz auf einem Gegner ist. Du zielst "
                        + "selbst - anders als Kill Aura dreht er nichts.");
                f.section("Ziele");
                f.toggle("Spieler", () -> trigPlayers, v -> trigPlayers = v, true);
                f.toggle("Monster", () -> trigHostile, v -> trigHostile = v, true);
                f.toggle("Tiere", () -> trigPassive, v -> trigPassive = v, false);
                f.section("Takt");
                f.slider("Abklingzeit abwarten (%)", 50, 100, () -> trigCooldown, v -> trigCooldown = v, 100);
                f.slider("Zufällige Verzögerung (Ticks)", 0, 6, () -> trigDelay, v -> trigDelay = v, 2);
                f.toggle("Kritische Treffer (in der Luft erst beim Fallen)", () -> trigCrits, v -> trigCrits = v, false);
                f.toggle("Nur mit Waffe in der Hand", () -> trigOnlyWeapon, v -> trigOnlyWeapon = v, false);
                f.note("Freunde, Spieler im Kreativmodus und Dorfbewohner schlägt er nie.");
                f.footer(() -> trigger, v -> trigger = v);
            });
            case "Auto Weapon" -> new ModuleScreen(parent, module, f -> {
                f.description("Nimmt bei jedem Schlag die beste Waffe aus der Hotbar - auch bei Kill Aura und "
                        + "Trigger Bot.");
                f.choice("Lieber", PREFER, () -> weaponPrefer, v -> weaponPrefer = v, 0);
                f.toggle("Axt gegen Schilde", () -> weaponAxeShield, v -> weaponAxeShield = v, true);
                f.toggle("Danach zurückwechseln", () -> weaponBack, v -> weaponBack = v, true);
                f.note("Mit „Danach zurückwechseln“ hast du nach anderthalb Sekunden ohne Schlag wieder das "
                        + "vorherige Item in der Hand. Eine Axt macht Schilde für ein paar Sekunden nutzlos.");
                f.footer(() -> autoWeapon, v -> autoWeapon = v);
            });
            case "Crystal Aura" -> new ModuleScreen(parent, module, f -> {
                f.description("Setzt End-Kristalle auf Obsidian oder Grundgestein neben dem Gegner und zündet "
                        + "sie sofort. Er rechnet vorher aus, wie viel Schaden der Gegner und du selbst bekommt.");
                f.section("Schaden");
                f.slider("Mindestens beim Gegner (Leben)", 1, 20, () -> caMinDamage, v -> caMinDamage = v, 6);
                f.slider("Höchstens bei dir (Leben)", 0, 20, () -> caMaxSelf, v -> caMaxSelf = v, 8);
                f.toggle("Nie so viel, dass du stirbst", () -> caAntiSuicide, v -> caAntiSuicide = v, true);
                f.section("Reichweite und Takt");
                f.slider("Reichweite (Zehntel-Blöcke)", 30, 60, () -> caRange, v -> caRange = v, 45);
                f.slider("Gegner bis (Zehntel-Blöcke)", 40, 160, () -> caTargetRange, v -> caTargetRange = v, 100);
                f.slider("Setzen alle (Ticks)", 0, 10, () -> caPlaceDelay, v -> caPlaceDelay = v, 2);
                f.slider("Zünden alle (Ticks)", 0, 10, () -> caBreakDelay, v -> caBreakDelay = v, 2);
                f.section("Allgemein");
                f.toggle("Kristalle setzen", () -> caPlace, v -> caPlace = v, true);
                f.toggle("Kristalle zünden", () -> caBreak, v -> caBreak = v, true);
                f.toggle("Kristalle selbst in die Hand nehmen", () -> caSwitch, v -> caSwitch = v, true);
                f.toggle("Auch Monster angreifen", () -> caMonsters, v -> caMonsters = v, false);
                f.toggle("Pause beim Essen", () -> caPauseEat, v -> caPauseEat = v, true);
                f.toggle("Platz markieren", () -> caRender, v -> caRender = v, true);
                f.note("Kristalle in die Haupt- oder Zweithand. 2 Leben = 1 Herz. Verzauberungen und Tränke "
                        + "rechnet er nicht mit - der echte Schaden ist oft etwas kleiner. Freunde greift er nie an.");
                f.footer(() -> crystal, v -> crystal = v);
            });
            case "Surround" -> new ModuleScreen(parent, module, f -> {
                f.description("Setzt Obsidian rund um deine Füße - Kristalle neben dir können dir dann kaum "
                        + "noch etwas anhaben.");
                f.choice("Blöcke", SUR_BLOCKS, () -> surBlocks, v -> surBlocks = v, 0);
                f.slider("Blöcke pro Tick", 1, 8, () -> surPerTick, v -> surPerTick = v, 4);
                f.toggle("In die Blockmitte stellen", () -> surCenter, v -> surCenter = v, true);
                f.toggle("Auch unter die Füße", () -> surFloor, v -> surFloor = v, true);
                f.toggle("Ausschalten, wenn du dich bewegst", () -> surDisableMove, v -> surDisableMove = v, true);
                f.note("Die Blöcke müssen in der Hotbar sein; deine Auswahl bleibt, wie sie war.");
                f.footer(() -> surround, v -> surround = v);
            });
            case "Aim Assist" -> new ModuleScreen(parent, module, f -> {
                f.description("Dreht deinen Blick sanft zum Gegner, der am nächsten am Fadenkreuz ist. Schlagen "
                        + "musst du selbst.");
                f.slider("Tempo (Grad/s)", 20, 360, () -> aimSpeed, v -> aimSpeed = v, 120);
                f.slider("Reichweite (Zehntel-Blöcke)", 20, 80, () -> aimRange, v -> aimRange = v, 45);
                f.slider("Blickfeld (Grad)", 10, 180, () -> aimFov, v -> aimFov = v, 60);
                f.toggle("Nur, solange du die Angriffstaste hältst", () -> aimOnlyClick, v -> aimOnlyClick = v, false);
                f.toggle("Auch hoch und runter", () -> aimVertical, v -> aimVertical = v, true);
                f.toggle("Auch durch Wände", () -> aimWalls, v -> aimWalls = v, false);
                f.section("Ziele");
                f.toggle("Spieler", () -> aimPlayers, v -> aimPlayers = v, true);
                f.toggle("Monster", () -> aimHostile, v -> aimHostile = v, true);
                f.toggle("Tiere", () -> aimPassive, v -> aimPassive = v, false);
                f.footer(() -> aimAssist, v -> aimAssist = v);
            });
            case "Bow Aimbot" -> new ModuleScreen(parent, module, f -> {
                f.description("Solange du den Bogen spannst, zielt er auf den Gegner - mit Fallkurve und Vorhalten. "
                        + "Loslassen musst du selbst.");
                f.slider("Reichweite (Blöcke)", 10, 100, () -> bowRange, v -> bowRange = v, 60);
                f.slider("Blickfeld (Grad)", 20, 360, () -> bowFov, v -> bowFov = v, 90);
                f.slider("Tempo (Grad/s)", 90, 1440, () -> bowSpeed, v -> bowSpeed = v, 540);
                f.toggle("Vorhalten, wenn das Ziel läuft", () -> bowPredict, v -> bowPredict = v, true);
                f.toggle("Auch beim Dreizack", () -> bowTrident, v -> bowTrident = v, true);
                f.section("Ziele");
                f.toggle("Spieler", () -> bowPlayers, v -> bowPlayers = v, true);
                f.toggle("Monster", () -> bowHostile, v -> bowHostile = v, true);
                f.toggle("Tiere", () -> bowPassive, v -> bowPassive = v, false);
                f.note("Voll gespannt trifft er am besten. Armbrüste schießen sofort beim Klick - dafür hilft er "
                        + "nicht.");
                f.footer(() -> bowAim, v -> bowAim = v);
            });
            case "Auto Trap" -> new ModuleScreen(parent, module, f -> {
                f.description("Sperrt den nächsten Gegner in Obsidian ein.");
                f.slider("Reichweite (Zehntel-Blöcke)", 30, 60, () -> trapRange, v -> trapRange = v, 45);
                f.slider("Blöcke pro Tick", 1, 8, () -> trapPerTick, v -> trapPerTick = v, 3);
                f.toggle("Ganz einsperren (mit Decke)", () -> trapFull, v -> trapFull = v, true);
                f.toggle("Auch Bruchstein & Co. benutzen", () -> trapBuildBlocks, v -> trapBuildBlocks = v, false);
                f.toggle("Auch Monster", () -> trapMonsters, v -> trapMonsters = v, false);
                f.note("Die Blöcke müssen in der Hotbar sein. Freunde sperrt er nie ein.");
                f.footer(() -> autoTrap, v -> autoTrap = v);
            });
            case "Hitboxes" -> new ModuleScreen(parent, module, f -> {
                f.description("Gegner sind beim Anvisieren etwas größer - du triffst leichter.");
                f.slider("Größer um (Zehntel-Blöcke)", 1, 10, () -> hitboxSize, v -> hitboxSize = v, 3);
                f.toggle("Spieler", () -> hbPlayers, v -> hbPlayers = v, true);
                f.toggle("Monster", () -> hbHostile, v -> hbHostile = v, true);
                f.toggle("Tiere", () -> hbPassive, v -> hbPassive = v, false);
                f.note("Mehr als 3 fällt Anti-Cheats leicht auf, weil du dann von weiter weg triffst.");
                f.footer(() -> hitboxes, v -> hitboxes = v);
            });
            case "Auto Pot" -> new ModuleScreen(parent, module, f -> {
                f.description("Wirft bei wenig Leben einen Heil-Wurftrank aus der Hotbar unter dich.");
                f.slider("Bei Leben unter (2 = 1 Herz)", 2, 19, () -> potHealth, v -> potHealth = v, 10);
                f.slider("Pause zwischen zwei Tränken (Ticks)", 4, 40, () -> potDelay, v -> potDelay = v, 10);
                f.note("Nur Wurftränke der Heilung (auch Stufe II). Dein Blick springt dafür nur ganz kurz nach "
                        + "unten.");
                f.footer(() -> autoPot, v -> autoPot = v);
            });
            case "Auto Web" -> new ModuleScreen(parent, module, f -> {
                f.description("Setzt Spinnennetze auf Gegner in deiner Nähe - sie kommen dann kaum noch weg.");
                f.slider("Reichweite (Zehntel-Blöcke)", 30, 60, () -> webRange, v -> webRange = v, 45);
                f.slider("Pause (Ticks)", 1, 40, () -> webDelay, v -> webDelay = v, 10);
                f.toggle("Auch auf Kopfhöhe", () -> webHead, v -> webHead = v, false);
                f.toggle("Auch Monster", () -> webMonsters, v -> webMonsters = v, false);
                f.note("Spinnennetze in die Hotbar. Freunde und dich selbst spinnt er nie ein.");
                f.footer(() -> autoWeb, v -> autoWeb = v);
            });
            case "Arrow Dodge" -> new ModuleScreen(parent, module, f -> {
                f.description("Rechnet aus, ob ein Pfeil (oder Dreizack) dich treffen wird, und springt rechtzeitig "
                        + "zur Seite.");
                f.slider("Ausweichen (Zehntel-Blöcke)", 3, 12, () -> dodgeSpeed, v -> dodgeSpeed = v, 6);
                f.slider("So früh (Ticks vorher)", 2, 20, () -> dodgeTicks, v -> dodgeTicks = v, 10);
                f.toggle("Dabei springen", () -> dodgeJump, v -> dodgeJump = v, false);
                f.note("Eigene Pfeile und solche, die schon stecken, lässt er in Ruhe.");
                f.footer(() -> arrowDodge, v -> arrowDodge = v);
            });
            case "Hole Filler" -> new ModuleScreen(parent, module, f -> {
                f.description("Füllt Löcher (1x1, ringsum Obsidian) in deiner Nähe - Gegner können sich dann nicht "
                        + "vor Kristallen verstecken.");
                f.slider("Reichweite (Zehntel-Blöcke)", 30, 60, () -> hfRange, v -> hfRange = v, 45);
                f.slider("Blöcke pro Tick", 1, 4, () -> hfPerTick, v -> hfPerTick = v, 2);
                f.toggle("Nur Löcher in der Nähe von Gegnern", () -> hfNearEnemy, v -> hfNearEnemy = v, true);
                f.slider("So nah am Gegner (Blöcke)", 1, 6, () -> hfEnemyDist, v -> hfEnemyDist = v, 3);
                f.toggle("Nur Obsidian- und Grundgestein-Löcher", () -> hfOnlySafe, v -> hfOnlySafe = v, true);
                f.toggle("Auch Bruchstein & Co. benutzen", () -> hfBuildBlocks, v -> hfBuildBlocks = v, false);
                f.note("Das Loch, in dem du selbst stehst, lässt er frei.");
                f.footer(() -> holeFiller, v -> holeFiller = v);
            });
            default -> null;
        };
    }
}
