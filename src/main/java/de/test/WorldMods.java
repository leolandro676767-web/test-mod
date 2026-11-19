package de.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Auto Farm, Nuker, Chest Stealer, Baum-Fäller, Auto Light, Bonemeal Aura, Tier-Automatik, Villager Roller,
 * Auto Smelt und Auto Sign.
 */
public final class WorldMods {
    private WorldMods() {
    }

    // =====================================================================================
    // Einstellungen (werden mit dem Praefix "wo." gespeichert)
    // =====================================================================================

    // --- Auto Farm ---
    public static boolean farm = false;
    public static int farmRadius = 4;
    public static int farmPerTick = 3;
    public static boolean farmReplant = true;
    public static boolean farmFillEmpty = false;   // auch Ackerboden bepflanzen, auf dem vorher nichts war
    public static boolean farmMelons = true;
    public static boolean farmCane = true;
    public static boolean farmWart = true;
    public static boolean farmCocoa = true;
    public static boolean farmBerries = true;
    public static boolean farmCollect = false;

    // --- Nuker ---
    public static boolean nuker = false;
    public static int nukerRadius = 4;
    public static int nukerMode = 0;               // 0 alles, 1 nur sofort abbaubare, 2 nur ausgewaehlte
    public static int nukerPerTick = 4;
    public static boolean nukerKeepFloor = true;
    public static boolean nukerTopDown = true;
    public static boolean nukerTool = true;
    public static final Set<Block> nukerBlocks = new LinkedHashSet<>();

    // --- Chest Stealer ---
    public static boolean stealer = false;
    public static int stealDelay = 2;              // Ticks zwischen zwei Griffen
    public static boolean stealRandom = true;
    public static boolean stealClose = true;
    public static boolean stealOnlyChests = true;

    // --- Baum-Faeller ---
    public static boolean tree = false;
    public static boolean treeOnlyNatural = true;
    public static boolean treeStep = true;
    public static boolean treeCollect = true;
    public static boolean treePlant = true;
    public static boolean treeTool = true;

    // --- Auto Light ---
    public static boolean autoLight = false;
    public static int lightRadius = 4;
    public static int lightDelay = 5;              // Ticks zwischen zwei Fackeln
    public static boolean lightOutside = false;

    // --- Bonemeal Aura ---
    public static boolean bonemeal = false;
    public static int bmRadius = 4;
    public static int bmDelay = 2;
    public static boolean bmOnlyCrops = true;

    // --- Tier-Automatik ---
    public static boolean animals = false;
    public static boolean anBreed = true;
    public static boolean anShear = true;

    // --- Villager Roller ---
    public static boolean roller = false;
    public static String rollWish = "mending";     // Verzauberungen, mit Komma getrennt
    public static int rollMaxPrice = 64;           // Smaragde
    public static boolean rollMaxLevel = true;     // nur die hoechste Stufe

    // --- Auto Smelt ---
    public static boolean smelt = false;
    public static boolean smeltOres = true;
    public static boolean smeltFood = true;
    public static boolean smeltOther = false;      // Sand, Bruchstein, Ton ...
    public static boolean smeltOnlyCoal = true;
    public static int smeltDelay = 2;
    public static boolean smeltClose = false;

    // --- Auto Sign ---
    public static boolean autoSign = false;
    public static String sign1 = "";
    public static String sign2 = "";
    public static String sign3 = "";
    public static String sign4 = "";
    public static boolean signOnlyEmpty = true;

    static final String[] NUKER_MODES = {"Alles", "Nur sofort abbaubare", "Nur ausgewählte Blöcke"};

    private static final Random RNG = new Random();

    static void reset() {
        dig = null;
        toolPrev = -1;
        treeLogs.clear();
        treeStage = 0;
        stealMenu = -1;
        plantAt.clear();
    }

    /** Laeuft schon ein Bot, der selbst laeuft und abbaut? Dann machen wir nichts. */
    private static boolean otherBot() {
        boolean ours = farmCollecting || treeStage == 2 || treeStage == 3; // da laufen wir selbst
        return DebrisMiner.isRunning() || TunnelMiner.isRunning() || TestClient.areaActive
                || Walker.isActive() && !ours;
    }

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || !p.isAlive()) {
            reset();
            return;
        }
        Safe.run("Chest Stealer", () -> stealTick(mc, p));
        Safe.run("Auto Smelt", () -> smeltTick(mc, p));
        Safe.run("Auto Sign", () -> signTick(mc, p));
        boolean[] rolling = {false};
        Safe.run("Villager Roller", () -> rolling[0] = rollerTick(mc, p));
        if (rolling[0]) return; // der Roller braucht Hand und Blick fuer sich
        if (mc.gui.screen() != null || p.isSpectator() || otherBot()) {
            stopDig(mc, p);
            return;
        }
        // nur ein Modul baut gleichzeitig ab: erst der Baum, dann das Feld, dann der Nuker
        Safe.run("Baum-Fäller", () -> treeTick(mc, p));
        if (owner == TREE && dig != null || treeStage != 0) return;
        Safe.run("Auto Farm", () -> farmTick(mc, p));
        if (owner == FARM && dig != null) return;
        Safe.run("Nuker", () -> nukerTick(mc, p));
        if (dig != null) return; // beim Abbauen nicht dazwischen den Platz wechseln
        restoreTool(p);
        Safe.run("Auto Light", () -> lightTick(mc, p));
        Safe.run("Bonemeal Aura", () -> bonemealTick(mc, p));
        Safe.run("Tier-Automatik", () -> animalTick(mc, p));
    }

    // =====================================================================================
    // Abbauen (gemeinsam fuer Baum, Feld und Nuker)
    // =====================================================================================

    private static final int TREE = 1;
    private static final int FARM = 2;
    private static final int NUKER = 3;
    private static final int ROLLER = 4;

    private static BlockPos dig;      // Block, der gerade abgebaut wird
    private static int owner;
    private static int digTicks;
    private static int toolPrev = -1;

    /** Baut gerade eines der Module ab? Dann soll das Spiel das Abbauen nicht selbst abbrechen. */
    public static boolean digging() {
        return dig != null;
    }

    /** Ein Tick Abbauen. true = der Block ist weg (oder aufgegeben). */
    private static boolean dig(Minecraft mc, LocalPlayer p, BlockPos pos, int who, boolean tool) {
        Level level = mc.level;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.getDestroySpeed(level, pos) < 0) {
            if (pos.equals(dig)) dig = null;
            return true;
        }
        if (!pos.equals(dig)) {
            dig = pos;
            digTicks = 0;
        }
        owner = who;
        if (++digTicks > 20 * 20) { // 20 Sekunden ohne Erfolg: aufgeben
            stopDig(mc, p);
            return true;
        }
        if (tool && !p.isCreative()) selectTool(p, s);
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 eye = p.getEyePosition();
        double dx = eye.x - c.x;
        double dy = eye.y - c.y;
        double dz = eye.z - c.z;
        Direction face = Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : Math.abs(dy) >= Math.abs(dz) ? (dy > 0 ? Direction.UP : Direction.DOWN)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        Combat.sendRotation(mc, p, Combat.rotationTo(p, c));
        if (mc.gameMode.continueDestroyBlock(pos, face)) p.swing(InteractionHand.MAIN_HAND);
        if (level.getBlockState(pos).isAir()) {
            dig = null;
            return true;
        }
        return false;
    }

    private static void stopDig(Minecraft mc, LocalPlayer p) {
        if (dig != null && mc.gameMode != null && mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
        dig = null;
        restoreTool(p);
    }

    private static boolean instant(Level level, LocalPlayer p, BlockPos pos, BlockState s) {
        return p.isCreative() || s.getDestroyProgress(p, level, pos) >= 1.0f;
    }

    private static boolean inReach(LocalPlayer p, BlockPos pos) {
        return p.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= p.blockInteractionRange();
    }

    /** Bestes Werkzeug aus der Hotbar nehmen (wie Auto Tool); beim Aufhoeren wieder zurueck. */
    private static void selectTool(LocalPlayer p, BlockState s) {
        var inv = p.getInventory();
        int cur = inv.getSelectedSlot();
        float best = inv.getItem(cur).getDestroySpeed(s);
        int bestSlot = cur;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isDamageableItem() && st.getMaxDamage() - st.getDamageValue() <= 5) continue; // nicht kaputt machen
            float sp = st.getDestroySpeed(s);
            if (sp > best + 0.01f) {
                best = sp;
                bestSlot = i;
            }
        }
        if (bestSlot == cur) return;
        if (toolPrev < 0) toolPrev = cur;
        inv.setSelectedSlot(bestSlot);
    }

    private static void restoreTool(LocalPlayer p) {
        if (toolPrev < 0) return;
        p.getInventory().setSelectedSlot(toolPrev);
        toolPrev = -1;
    }

    private static String id(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).getPath();
    }

    private static String itemId(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    private static int hotbarSlot(LocalPlayer p, java.util.function.Predicate<ItemStack> test) {
        var inv = p.getInventory();
        int sel = inv.getSelectedSlot();
        if (test.test(inv.getItem(sel))) return sel;
        for (int i = 0; i < 9; i++) {
            if (test.test(inv.getItem(i))) return i;
        }
        return -1;
    }

    /** Benutzt ein Item aus der Hotbar auf die Oberseite von 'on' (Saat, Setzling). */
    private static void plantOn(Minecraft mc, LocalPlayer p, BlockPos on, int slot) {
        Vec3 hit = new Vec3(on.getX() + 0.5, on.getY() + 1.0, on.getZ() + 0.5);
        Combat.sendRotation(mc, p, Combat.rotationTo(p, hit));
        var inv = p.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, on, false));
        p.swing(InteractionHand.MAIN_HAND);
        inv.setSelectedSlot(prev);
    }

    // =====================================================================================
    // Auto Farm
    // =====================================================================================

    /** Wo geerntet wurde -> welche Saat dort wieder hin soll. */
    private static final Map<BlockPos, String> plantAt = new HashMap<>();
    private static boolean farmCollecting;

    private static String seedFor(Block b) {
        return switch (id(b)) {
            case "wheat" -> "wheat_seeds";
            case "carrots" -> "carrot";
            case "potatoes" -> "potato";
            case "beetroots" -> "beetroot_seeds";
            case "torchflower_crop" -> "torchflower_seeds";
            case "nether_wart" -> "nether_wart";
            default -> null;
        };
    }

    private static boolean isSeed(ItemStack st) {
        if (st.isEmpty()) return false;
        String i = itemId(st);
        return i.equals("wheat_seeds") || i.equals("carrot") || i.equals("potato") || i.equals("beetroot_seeds");
    }

    /** Reif zum Abbauen? (Beeren werden gepflueckt, nicht abgebaut - siehe berryRipe) */
    private static boolean ripe(Level level, BlockPos pos, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof CropBlock cb) return cb.isMaxAge(s);
        String i = id(b);
        switch (i) {
            case "nether_wart":
                return farmWart && s.hasProperty(BlockStateProperties.AGE_3) && s.getValue(BlockStateProperties.AGE_3) >= 3;
            case "cocoa":
                return farmCocoa && s.hasProperty(BlockStateProperties.AGE_2) && s.getValue(BlockStateProperties.AGE_2) >= 2;
            case "melon":
            case "pumpkin":
                return farmMelons && attachedStem(level, pos); // nur gewachsene, keine gebauten Kuerbisse
            case "sugar_cane":
                return farmCane && level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE); // unterster bleibt
            default:
                return false;
        }
    }

    private static boolean attachedStem(Level level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            String i = id(level.getBlockState(pos.relative(d)).getBlock());
            if (i.equals("attached_melon_stem") || i.equals("attached_pumpkin_stem")) return true;
        }
        return false;
    }

    private static boolean berryRipe(BlockState s) {
        return farmBerries && id(s.getBlock()).equals("sweet_berry_bush")
                && s.hasProperty(BlockStateProperties.AGE_3) && s.getValue(BlockStateProperties.AGE_3) >= 2;
    }

    private static void farmTick(Minecraft mc, LocalPlayer p) {
        if (!farm) {
            if (owner == FARM && dig != null) stopDig(mc, p);
            if (farmCollecting) {
                Pickup.stop(mc);
                farmCollecting = false;
            }
            return;
        }
        Level level = mc.level;
        if (farmCollecting) {
            if (Pickup.collect(mc, level, p, farmWant())) return;
            farmCollecting = false;
        }
        // angefangenen Block (Melone, Kuerbis) erst fertig abbauen
        if (owner == FARM && dig != null) {
            if (!inReach(p, dig)) stopDig(mc, p);
            else if (!dig(mc, p, dig, FARM, true)) return;
        }
        List<BlockPos> ripe = new ArrayList<>();
        List<BlockPos> berries = new ArrayList<>();
        BlockPos c = p.blockPosition();
        int r = Math.max(1, farmRadius);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -3; y <= 4; y++) {
                    BlockPos pos = c.offset(x, y, z);
                    BlockState s = level.getBlockState(pos);
                    if (s.isAir()) continue;
                    if (!inReach(p, pos)) continue;
                    if (ripe(level, pos, s)) ripe.add(pos);
                    else if (berryRipe(s)) berries.add(pos);
                }
            }
        }
        Vec3 eye = p.getEyePosition();
        ripe.sort(Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos))));
        int done = 0;
        for (BlockPos pos : ripe) {
            if (done >= Math.max(1, farmPerTick)) break;
            BlockState s = level.getBlockState(pos);
            String seed = seedFor(s.getBlock());
            if (!instant(level, p, pos, s)) {
                if (done > 0) break;
                dig(mc, p, pos, FARM, true); // Melone/Kuerbis: dauert ein paar Ticks
                return;
            }
            if (dig(mc, p, pos, FARM, false)) {
                done++;
                if (seed != null) plantAt.put(pos.immutable(), seed);
            }
        }
        for (BlockPos pos : berries) {
            if (done >= Math.max(1, farmPerTick)) break;
            if (itemId(p.getMainHandItem()).equals("bone_meal")) break; // sonst duengt er nur
            Vec3 hit = Vec3.atCenterOf(pos);
            Combat.sendRotation(mc, p, Combat.rotationTo(p, hit));
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, pos, false));
            p.swing(InteractionHand.MAIN_HAND);
            done++;
        }
        if (farmReplant) done += replant(mc, p, done);
        if (done == 0 && farmCollect && !Walker.isActive()) {
            Pickup.Want w = farmWant();
            if (Pickup.has(mc, p, w)) {
                Pickup.begin();
                farmCollecting = true;
            }
        }
    }

    private static Pickup.Want farmWant() {
        Pickup.Want w = new Pickup.Want();
        w.radius = Math.max(2, farmRadius + 2);
        return w;
    }

    private static int replant(Minecraft mc, LocalPlayer p, int done) {
        Level level = mc.level;
        int n = 0;
        // 1) wo gerade geerntet wurde: dieselbe Saat wieder hin
        var it = plantAt.entrySet().iterator();
        while (it.hasNext() && done + n < Math.max(1, farmPerTick)) {
            var e = it.next();
            BlockPos pos = e.getKey();
            if (pos.distSqr(p.blockPosition()) > 16 * 16) {
                it.remove(); // weit weg gegangen
                continue;
            }
            if (!level.getBlockState(pos).isAir()) {
                if (!level.getBlockState(pos).canBeReplaced()) it.remove(); // schon wieder etwas drauf
                continue;
            }
            BlockPos below = pos.below();
            if (!inReach(p, below)) continue;
            String seed = e.getValue();
            boolean soil = seed.equals("nether_wart") ? level.getBlockState(below).is(Blocks.SOUL_SAND)
                    : level.getBlockState(below).is(Blocks.FARMLAND);
            if (!soil) {
                it.remove();
                continue;
            }
            int slot = hotbarSlot(p, st -> !st.isEmpty() && itemId(st).equals(seed));
            if (slot < 0) continue;
            plantOn(mc, p, below, slot);
            it.remove();
            n++;
        }
        // 2) leerer Ackerboden: irgendeine Saat aus der Hotbar
        if (!farmFillEmpty || done + n >= Math.max(1, farmPerTick)) return n;
        int slot = hotbarSlot(p, WorldMods::isSeed);
        if (slot < 0) return n;
        BlockPos c = p.blockPosition();
        int r = Math.max(1, farmRadius);
        for (int x = -r; x <= r && done + n < Math.max(1, farmPerTick); x++) {
            for (int z = -r; z <= r && done + n < Math.max(1, farmPerTick); z++) {
                for (int y = -3; y <= 2; y++) {
                    BlockPos soil = c.offset(x, y, z);
                    if (!level.getBlockState(soil).is(Blocks.FARMLAND)) continue;
                    if (!level.getBlockState(soil.above()).isAir() || !inReach(p, soil)) continue;
                    plantOn(mc, p, soil, slot);
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    // =====================================================================================
    // Nuker
    // =====================================================================================

    private static boolean nukeable(Level level, LocalPlayer p, BlockPos pos, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() && s.canBeReplaced()) return false;
        if (s.getDestroySpeed(level, pos) < 0) return false; // Grundgestein & Co.
        if (s.hasBlockEntity() && nukerMode != 2) return false; // Truhen usw. nur, wenn ausdruecklich gewaehlt
        return switch (nukerMode) {
            case 1 -> instant(level, p, pos, s);
            case 2 -> nukerBlocks.contains(s.getBlock());
            default -> true;
        };
    }

    private static void nukerTick(Minecraft mc, LocalPlayer p) {
        if (!nuker) {
            if (owner == NUKER && dig != null) stopDig(mc, p);
            return;
        }
        Level level = mc.level;
        if (owner == NUKER && dig != null) {
            BlockState s = level.getBlockState(dig);
            if (inReach(p, dig) && nukeable(level, p, dig, s)) {
                if (!dig(mc, p, dig, NUKER, nukerTool)) return;
            } else {
                stopDig(mc, p);
            }
        }
        BlockPos c = p.blockPosition();
        int r = Math.max(1, nukerRadius);
        int floorY = c.getY();
        List<BlockPos> list = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r + 1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = c.offset(x, y, z);
                    if (nukerKeepFloor && pos.getY() < floorY) continue;
                    if (!inReach(p, pos)) continue;
                    if (nukeable(level, p, pos, level.getBlockState(pos))) list.add(pos);
                }
            }
        }
        if (list.isEmpty()) return;
        Vec3 eye = p.getEyePosition();
        Comparator<BlockPos> near = Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos)));
        list.sort(nukerTopDown ? Comparator.<BlockPos>comparingInt(pos -> -pos.getY()).thenComparing(near) : near);
        int done = 0;
        for (BlockPos pos : list) {
            if (done >= Math.max(1, nukerPerTick)) break;
            BlockState s = level.getBlockState(pos);
            if (!instant(level, p, pos, s)) {
                if (done > 0) break;
                dig(mc, p, pos, NUKER, nukerTool);
                return;
            }
            if (dig(mc, p, pos, NUKER, false)) done++;
        }
    }

    // =====================================================================================
    // Chest Stealer
    // =====================================================================================

    private static int stealMenu = -1;
    private static int stealWait;
    private static int stealTaken;
    private static boolean stealFullWarned;

    private static void stealTick(Minecraft mc, LocalPlayer p) {
        Screen screen = mc.gui.screen();
        if (!stealer || !(screen instanceof AbstractContainerScreen<?> acs)) {
            stealMenu = -1;
            return;
        }
        AbstractContainerMenu menu = acs.getMenu();
        if (menu == p.inventoryMenu) return;
        boolean chest = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
        if (stealOnlyChests && !chest) return;
        if (menu.containerId != stealMenu) {
            stealMenu = menu.containerId;
            stealWait = 3 + stealDelay; // erst warten, bis der Inhalt angekommen ist
            stealTaken = 0;
            stealFullWarned = false;
        }
        if (stealWait > 0) {
            stealWait--;
            return;
        }
        if (!menu.getCarried().isEmpty()) return; // haelt etwas am Mauszeiger
        Slot next = null;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory || !s.hasItem()) continue;
            next = s;
            break;
        }
        if (next == null) {
            if (stealClose && stealTaken > 0) p.closeContainer();
            stealTaken = 0;
            return;
        }
        if (!hasRoom(p, next.getItem())) {
            if (!stealFullWarned) {
                stealFullWarned = true;
                Keybinds.message(Lang.t("Chest Stealer: Inventar voll"), "");
            }
            return;
        }
        mc.gameMode.handleContainerInput(menu.containerId, next.index, 0, ContainerInput.QUICK_MOVE, p);
        stealTaken++;
        stealWait = stealDelay + (stealRandom && stealDelay > 0 ? RNG.nextInt(2) : 0);
    }

    private static boolean hasRoom(LocalPlayer p, ItemStack want) {
        var inv = p.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(st, want) && st.getCount() < st.getMaxStackSize()) return true;
        }
        return false;
    }

    // =====================================================================================
    // Baum-Faeller
    // =====================================================================================

    private static final List<BlockPos> treeLogs = new ArrayList<>();
    private static BlockPos treeOrigin;
    private static int treeStage;          // 0 nichts, 1 faellen, 2 unter den Stamm gehen, 3 einsammeln
    private static boolean treeStepped;
    private static int treeIdle;

    /** Vom MultiPlayerGameModeMixin, bevor ein Block abgebaut wird (er ist noch da). */
    public static void onBreak(BlockPos pos, BlockState state) {
        if (!tree || treeStage != 0 || !state.is(BlockTags.LOGS)) return;
        if (pos.equals(dig)) return; // das waren wir selbst (Nuker usw.)
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || otherBot()) return;
        List<BlockPos> logs = connectedLogs(mc.level, pos);
        if (logs.isEmpty()) return;
        if (treeOnlyNatural && !natural(mc.level, pos, logs)) return; // Holzhaus? Lieber nicht.
        treeLogs.clear();
        treeLogs.addAll(logs);
        treeOrigin = pos.immutable();
        treeStage = 1;
        treeStepped = false;
        treeIdle = 0;
    }

    /** Alle Staemme, die mit diesem verbunden sind (auch schraeg), ohne ihn selbst. */
    private static List<BlockPos> connectedLogs(Level level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        seen.add(start);
        todo.add(start);
        while (!todo.isEmpty() && out.size() < 256) {
            BlockPos cur = todo.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = cur.offset(dx, dy, dz);
                        if (seen.contains(n)) continue;
                        seen.add(n);
                        if (Math.abs(n.getX() - start.getX()) > 8 || Math.abs(n.getZ() - start.getZ()) > 8) continue;
                        if (n.getY() < start.getY() - 1 || n.getY() > start.getY() + 40) continue;
                        if (!level.getBlockState(n).is(BlockTags.LOGS)) continue;
                        out.add(n);
                        todo.add(n);
                    }
                }
            }
        }
        return out;
    }

    /** Ein echter Baum hat natuerliche (nicht gesetzte) Blaetter am Stamm. */
    private static boolean natural(Level level, BlockPos start, List<BlockPos> logs) {
        List<BlockPos> all = new ArrayList<>(logs);
        all.add(start);
        for (BlockPos l : all) {
            for (Direction d : Direction.values()) {
                BlockState s = level.getBlockState(l.relative(d));
                if (s.is(BlockTags.LEAVES) && s.hasProperty(BlockStateProperties.PERSISTENT)
                        && !s.getValue(BlockStateProperties.PERSISTENT)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void treeTick(Minecraft mc, LocalPlayer p) {
        if (!tree) {
            if (treeStage != 0) {
                if (owner == TREE && dig != null) stopDig(mc, p);
                if (treeStage == 2 || treeStage == 3) {
                    Pickup.stop(mc);
                    Walker.stop(mc);
                }
                treeLogs.clear();
                treeStage = 0;
            }
            return;
        }
        if (treeStage == 0) return;
        Level level = mc.level;
        treeLogs.removeIf(pos -> !level.getBlockState(pos).is(BlockTags.LOGS));

        if (treeStage == 2) { // unter den Stamm gehen, um hoehere Staemme zu erreichen
            String r = Walker.tick(mc, level, p);
            if (r == null && ++treeIdle < 20 * 15) return;
            Walker.stop(mc);
            treeStage = 1;
        }
        if (treeStage == 1) {
            if (owner == TREE && dig != null) {
                if (!inReach(p, dig)) stopDig(mc, p);
                else if (!dig(mc, p, dig, TREE, treeTool)) return;
            }
            Vec3 eye = p.getEyePosition();
            BlockPos next = null;
            double best = Double.MAX_VALUE;
            for (BlockPos pos : treeLogs) {
                if (!inReach(p, pos)) continue;
                double d = eye.distanceToSqr(Vec3.atCenterOf(pos));
                if (d < best) {
                    best = d;
                    next = pos;
                }
            }
            if (next != null) {
                dig(mc, p, next, TREE, treeTool);
                return;
            }
            if (!treeLogs.isEmpty() && treeStep && !treeStepped && standable(level, treeOrigin)) {
                treeStepped = true;
                treeIdle = 0;
                Walker.startDigExact(treeOrigin.getX(), treeOrigin.getY(), treeOrigin.getZ());
                treeStage = 2;
                return;
            }
            // fertig (oder der Rest ist zu hoch)
            restoreTool(p);
            if (!treeLogs.isEmpty()) {
                p.sendSystemMessage(Lang.literal("§e1912: " + Lang.t("Baum-Fäller: ") + treeLogs.size()
                        + Lang.t(" Stämme waren zu hoch")));
            }
            treeLogs.clear();
            if (treeCollect) {
                Pickup.begin();
                treeStage = 3;
            } else {
                plantSapling(mc, p);
                treeStage = 0;
            }
            return;
        }
        if (treeStage == 3) {
            Pickup.Want w = new Pickup.Want();
            w.radius = 10;
            if (Pickup.collect(mc, level, p, w)) return;
            plantSapling(mc, p);
            treeStage = 0;
        }
    }

    private static boolean standable(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir();
    }

    private static void plantSapling(Minecraft mc, LocalPlayer p) {
        if (!treePlant || treeOrigin == null) return;
        Level level = mc.level;
        BlockPos below = treeOrigin.below();
        if (!level.getBlockState(treeOrigin).isAir() || !level.getBlockState(below).is(BlockTags.DIRT)) return;
        if (!inReach(p, below)) return;
        int slot = hotbarSlot(p, st -> !st.isEmpty() && (itemId(st).endsWith("_sapling") || itemId(st).endsWith("_propagule")));
        if (slot < 0) return;
        plantOn(mc, p, below, slot);
    }

    // =====================================================================================
    // Auto Light
    // =====================================================================================

    private static int lightTimer;
    private static boolean lightWarned;

    private static boolean isTorch(ItemStack st) {
        if (st.isEmpty()) return false;
        String i = itemId(st);
        return i.equals("torch") || i.equals("soul_torch") || i.equals("copper_torch");
    }

    private static void lightTick(Minecraft mc, LocalPlayer p) {
        if (!autoLight) {
            lightWarned = false;
            return;
        }
        if (lightTimer > 0) {
            lightTimer--;
            return;
        }
        int slot = hotbarSlot(p, WorldMods::isTorch);
        if (slot < 0) {
            if (!lightWarned) {
                lightWarned = true;
                Keybinds.message(Lang.t("Auto Light: keine Fackeln in der Hotbar"), "");
            }
            return;
        }
        lightWarned = false;
        Level level = mc.level;
        BlockPos c = p.blockPosition();
        int r = Math.max(1, lightRadius);
        Vec3 eye = p.getEyePosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos pos = c.offset(x, y, z);
                    // Monster koennen hier entstehen: Luft (2 hoch) auf festem Boden, kein Blocklicht
                    if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
                    BlockPos floor = pos.below();
                    BlockState fs = level.getBlockState(floor);
                    if (!fs.isFaceSturdy(level, floor, Direction.UP) || fs.is(Blocks.BEDROCK)) continue;
                    if (level.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
                    if (!lightOutside && level.getBrightness(LightLayer.SKY, pos) > 7) continue;
                    if (!inReach(p, floor)) continue;
                    double d = eye.distanceToSqr(Vec3.atCenterOf(pos));
                    if (d < bestD) {
                        bestD = d;
                        best = floor;
                    }
                }
            }
        }
        if (best == null) return;
        plantOn(mc, p, best, slot); // Fackel oben auf den Boden
        lightTimer = Math.max(1, lightDelay); // das Licht muss sich erst ausbreiten
    }

    // =====================================================================================
    // Bonemeal Aura
    // =====================================================================================

    private static int bmTimer;

    private static boolean cropLike(Block b) {
        if (b instanceof CropBlock) return true;
        String i = id(b);
        return i.endsWith("_sapling") || i.endsWith("_propagule") || i.equals("melon_stem") || i.equals("pumpkin_stem")
                || i.equals("sweet_berry_bush") || i.equals("cocoa") || i.equals("pitcher_crop");
    }

    private static void bonemealTick(Minecraft mc, LocalPlayer p) {
        if (!bonemeal) return;
        if (bmTimer > 0) {
            bmTimer--;
            return;
        }
        int slot = hotbarSlot(p, st -> !st.isEmpty() && itemId(st).equals("bone_meal"));
        if (slot < 0) return;
        Level level = mc.level;
        BlockPos c = p.blockPosition();
        int r = Math.max(1, bmRadius);
        Vec3 eye = p.getEyePosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -2; y <= 3; y++) {
                    BlockPos pos = c.offset(x, y, z);
                    BlockState s = level.getBlockState(pos);
                    if (!(s.getBlock() instanceof BonemealableBlock bb)) continue;
                    if (bmOnlyCrops && !cropLike(s.getBlock())) continue;
                    if (!bb.isValidBonemealTarget(level, pos, s) || !inReach(p, pos)) continue;
                    double d = eye.distanceToSqr(Vec3.atCenterOf(pos));
                    if (d < bestD) {
                        bestD = d;
                        best = pos;
                    }
                }
            }
        }
        if (best == null) return;
        Vec3 hit = Vec3.atCenterOf(best);
        Combat.sendRotation(mc, p, Combat.rotationTo(p, hit));
        var inv = p.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, best, false));
        p.swing(InteractionHand.MAIN_HAND);
        inv.setSelectedSlot(prev);
        bmTimer = Math.max(0, bmDelay);
    }

    // =====================================================================================
    // Tier-Automatik
    // =====================================================================================

    /** Welches Tier wann zuletzt dran war (Tick) - das Spiel verraet nicht, ob es schon verliebt ist. */
    private static final Map<Integer, Integer> anDone = new HashMap<>();
    private static int anNow;
    private static int anTimer;

    private static void animalTick(Minecraft mc, LocalPlayer p) {
        anNow++;
        if (!animals) return;
        if (anTimer > 0) {
            anTimer--;
            return;
        }
        if (anNow % 200 == 0) anDone.values().removeIf(t -> anNow - t > 6000);
        double r = p.entityInteractionRange();
        Vec3 eye = p.getEyePosition();
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(r + 1.0))) {
            if (!(e instanceof Animal a) || !a.isAlive()) continue;
            if (Combat.distance(eye, a.getBoundingBox()) > r) continue;
            Integer last = anDone.get(a.getId());
            if (anShear && a instanceof Shearable sh && sh.readyForShearing()
                    && (last == null || anNow - last > 40)) {
                int slot = hotbarSlot(p, st -> !st.isEmpty() && itemId(st).equals("shears"));
                if (slot >= 0) {
                    interact(mc, p, a, slot);
                    anDone.put(a.getId(), anNow);
                    anTimer = 4;
                    return;
                }
            }
            // vermehren: erwachsen und seit 5 Minuten nicht gefuettert (so lange kann es eh nicht)
            if (anBreed && !a.isBaby() && (last == null || anNow - last > 6000)) {
                int slot = hotbarSlot(p, a::isFood);
                if (slot >= 0) {
                    interact(mc, p, a, slot);
                    anDone.put(a.getId(), anNow);
                    anTimer = 4;
                    return;
                }
            }
        }
    }

    private static void interact(Minecraft mc, LocalPlayer p, Entity e, int slot) {
        Vec3 c = e.getBoundingBox().getCenter();
        Combat.sendRotation(mc, p, Combat.rotationTo(p, c));
        var inv = p.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.gameMode.interact(p, e, new EntityHitResult(e, c), InteractionHand.MAIN_HAND);
        p.swing(InteractionHand.MAIN_HAND);
        inv.setSelectedSlot(prev);
    }

    // =====================================================================================
    // Villager Roller
    // =====================================================================================

    private static int rollStage;          // 0 aus, 1 auf Beruf warten, 2 Handel oeffnen, 3 Angebote lesen,
    private static int rollTicks;          // 4 Pult abbauen, 5 auf Berufsverlust warten, 6 Pult setzen
    private static int rollTries;
    private static Villager rollVillager;
    private static BlockPos rollLectern;

    private static boolean rollFail(Minecraft mc, LocalPlayer p, String why) {
        roller = false;
        if (owner == ROLLER && dig != null) stopDig(mc, p);
        rollStage = 0;
        Keybinds.message(Lang.t("Villager Roller: ") + Lang.t(why), "");
        return false;
    }

    private static boolean hasJob(Villager v, net.minecraft.resources.ResourceKey<VillagerProfession> job) {
        return v.getVillagerData().profession().is(job);
    }

    /** true = der Roller arbeitet gerade (dann macht der Rest von WorldMods nichts). */
    private static boolean rollerTick(Minecraft mc, LocalPlayer p) {
        if (!roller) {
            if (rollStage != 0) {
                if (owner == ROLLER && dig != null) stopDig(mc, p);
                rollStage = 0;
            }
            return false;
        }
        Level level = mc.level;
        if (rollStage == 0) {
            rollVillager = null;
            rollLectern = null;
            double best = 6 * 6;
            for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(6))) {
                if (e instanceof Villager v && !v.isBaby() && p.distanceToSqr(v) < best) {
                    best = p.distanceToSqr(v);
                    rollVillager = v;
                }
            }
            BlockPos c = p.blockPosition();
            double bestL = Double.MAX_VALUE;
            for (int x = -5; x <= 5; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -5; z <= 5; z++) {
                        BlockPos pos = c.offset(x, y, z);
                        if (!id(level.getBlockState(pos).getBlock()).equals("lectern") || !inReach(p, pos)) continue;
                        double d = p.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                        if (d < bestL) {
                            bestL = d;
                            rollLectern = pos.immutable();
                        }
                    }
                }
            }
            if (rollVillager == null || rollLectern == null) {
                return rollFail(mc, p, "stell dich neben einen Dorfbewohner und ein Lesepult");
            }
            rollStage = 1;
            rollTicks = 0;
            rollTries = 0;
        }
        if (!rollVillager.isAlive() || rollVillager.isRemoved()) return rollFail(mc, p, "der Dorfbewohner ist weg");
        rollTicks++;
        Screen screen = mc.gui.screen();
        switch (rollStage) {
            case 1 -> { // warten, bis er Bibliothekar ist
                if (hasJob(rollVillager, VillagerProfession.LIBRARIAN)) {
                    rollStage = 2;
                    rollTicks = 0;
                } else if (rollTicks > 20 * 30) {
                    return rollFail(mc, p, "er nimmt das Lesepult nicht (steht es nah genug?)");
                }
            }
            case 2 -> { // Handel oeffnen
                if (screen instanceof MerchantScreen) {
                    rollStage = 3;
                    rollTicks = 0;
                } else if (screen == null && rollTicks % 20 == 1) {
                    interact(mc, p, rollVillager, p.getInventory().getSelectedSlot());
                } else if (rollTicks > 20 * 6) {
                    return rollFail(mc, p, "der Handel geht nicht auf");
                }
            }
            case 3 -> { // Angebote lesen
                if (!(screen instanceof MerchantScreen ms)) {
                    rollStage = 2;
                    rollTicks = 0;
                    return true;
                }
                var offers = ms.getMenu().getOffers();
                if (offers.isEmpty()) {
                    if (rollTicks > 40) return rollFail(mc, p, "keine Angebote angekommen");
                    return true;
                }
                for (MerchantOffer o : offers) {
                    String found = offerMatch(o);
                    if (found != null) {
                        roller = false;
                        rollStage = 0;
                        Keybinds.message(Lang.t("Gefunden: ") + found, "");
                        p.sendSystemMessage(Lang.literal("§a1912: " + Lang.t("Villager Roller: gefunden nach ")
                                + (rollTries + 1) + Lang.t(" Versuchen: ") + found));
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0f));
                        return false;
                    }
                }
                rollTries++;
                p.closeContainer();
                rollStage = 4;
                rollTicks = 0;
            }
            case 4 -> { // Lesepult abbauen
                if (screen != null) return true;
                if (!inReach(p, rollLectern)) return rollFail(mc, p, "das Lesepult ist zu weit weg");
                if (dig(mc, p, rollLectern, ROLLER, true)) {
                    restoreTool(p);
                    rollStage = 5;
                    rollTicks = 0;
                }
            }
            case 5 -> { // warten, bis er den Beruf verliert
                if (hasJob(rollVillager, VillagerProfession.NONE)) {
                    rollStage = 6;
                    rollTicks = 0;
                } else if (rollTicks > 20 * 20) {
                    return rollFail(mc, p, "er behält seinen Beruf (hat schon gehandelt oder hat ein anderes Pult)");
                }
            }
            default -> { // Lesepult wieder hinstellen
                if (id(level.getBlockState(rollLectern).getBlock()).equals("lectern")) {
                    rollStage = 1;
                    rollTicks = 0;
                    return true;
                }
                int slot = hotbarSlot(p, st -> !st.isEmpty() && itemId(st).equals("lectern"));
                if (slot < 0) {
                    // das abgebaute Pult liegt vielleicht noch am Boden: kurz warten
                    if (rollTicks > 20 * 4) return rollFail(mc, p, "kein Lesepult in der Hotbar");
                    return true;
                }
                if (level.getBlockState(rollLectern).canBeReplaced() && rollTicks % 5 == 1) {
                    CombatMods.placeBlock(mc, p, rollLectern, slot);
                }
                if (rollTicks > 20 * 5) return rollFail(mc, p, "das Lesepult lässt sich nicht hinstellen");
            }
        }
        return true;
    }

    /** Passt das Angebot (Buch mit gewuenschter Verzauberung, nicht zu teuer)? Text fuer die Meldung oder null. */
    private static String offerMatch(MerchantOffer o) {
        ItemStack res = o.getResult();
        if (res.isEmpty() || !itemId(res).equals("enchanted_book")) return null;
        ItemEnchantments ench = res.get(DataComponents.STORED_ENCHANTMENTS);
        if (ench == null) return null;
        int price = o.getCostA().getCount();
        if (price > rollMaxPrice) return null;
        for (var en : ench.entrySet()) {
            Holder<Enchantment> h = en.getKey();
            int lvl = en.getIntValue();
            String key = h.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            String name = h.value().description().getString();
            if (!wished(key, name)) continue;
            if (rollMaxLevel && lvl < h.value().getMaxLevel()) continue;
            return name + " " + lvl + " (" + price + " " + Lang.t("Smaragde") + ")";
        }
        return null;
    }

    private static boolean wished(String key, String name) {
        String k = key.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String w : rollWish.split(",")) {
            String t = w.trim().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            if (t.isEmpty()) continue;
            if (k.equals(t) || n.equals(t) || k.contains(t) || n.contains(t)) return true;
        }
        return false;
    }

    // =====================================================================================
    // Auto Smelt
    // =====================================================================================

    private static final Set<String> ORES = Set.of("raw_iron", "raw_gold", "raw_copper", "iron_ore", "gold_ore",
            "copper_ore", "deepslate_iron_ore", "deepslate_gold_ore", "deepslate_copper_ore", "nether_gold_ore",
            "ancient_debris");
    private static final Set<String> FOOD = Set.of("beef", "porkchop", "chicken", "mutton", "rabbit", "cod",
            "salmon", "potato", "kelp");
    private static final Set<String> OTHER = Set.of("sand", "red_sand", "cobblestone", "cobbled_deepslate",
            "clay_ball", "clay", "netherrack", "cactus", "wet_sponge", "stone");
    private static final Set<String> COAL = Set.of("coal", "charcoal", "coal_block", "blaze_rod", "dried_kelp_block");

    private static int smeltMenu = -1;
    private static int smeltWait;
    private static int smeltDone;

    /** kind: 0 Ofen, 1 Schmelzofen (nur Erze), 2 Raeucherofen (nur Essen). */
    private static boolean smeltable(ItemStack st, int kind) {
        if (st.isEmpty()) return false;
        String i = itemId(st);
        if (kind == 1) return ORES.contains(i);
        if (kind == 2) return FOOD.contains(i);
        return smeltOres && ORES.contains(i) || smeltFood && FOOD.contains(i) || smeltOther && OTHER.contains(i);
    }

    private static boolean fuel(Minecraft mc, ItemStack st) {
        if (st.isEmpty()) return false;
        String i = itemId(st);
        if (COAL.contains(i)) return true;
        if (smeltOnlyCoal || st.isDamageableItem() || i.contains("bucket")) return false;
        // Holzstaemme werden lieber zu Holzkohle (die schiebt das Spiel in den oberen Platz)
        if (i.endsWith("_log") || i.endsWith("_wood") || i.endsWith("_stem") || i.endsWith("_hyphae")) return false;
        return mc.level.fuelValues().isFuel(st);
    }

    private static void smeltTick(Minecraft mc, LocalPlayer p) {
        Screen screen = mc.gui.screen();
        if (!smelt || !(screen instanceof AbstractContainerScreen<?> acs)
                || !(acs.getMenu() instanceof AbstractFurnaceMenu menu)) {
            smeltMenu = -1;
            return;
        }
        if (menu.containerId != smeltMenu) {
            smeltMenu = menu.containerId;
            smeltWait = 3 + smeltDelay; // erst warten, bis der Inhalt angekommen ist
            smeltDone = 0;
        }
        if (smeltWait > 0) {
            smeltWait--;
            return;
        }
        if (!menu.getCarried().isEmpty() || menu.slots.size() < 3) return;
        int kind = menu instanceof BlastFurnaceMenu ? 1 : menu instanceof SmokerMenu ? 2 : 0;
        Slot in = menu.slots.get(AbstractFurnaceMenu.INGREDIENT_SLOT);
        Slot fuelSlot = menu.slots.get(AbstractFurnaceMenu.FUEL_SLOT);
        Slot out = menu.slots.get(AbstractFurnaceMenu.RESULT_SLOT);
        // 1) Fertiges herausnehmen
        if (out.hasItem() && hasRoom(p, out.getItem())) {
            smeltClick(mc, p, menu, out);
            return;
        }
        // 2) Brennstoff nachlegen
        ItemStack f = fuelSlot.getItem();
        if (f.isEmpty() || f.getCount() < f.getMaxStackSize()) {
            for (Slot s : menu.slots) {
                if (!(s.container instanceof Inventory) || !s.hasItem() || !fuel(mc, s.getItem())) continue;
                if (!f.isEmpty() && !ItemStack.isSameItemSameComponents(s.getItem(), f)) continue;
                smeltClick(mc, p, menu, s);
                return;
            }
        }
        // 3) Zutaten nachlegen
        ItemStack i = in.getItem();
        if (i.isEmpty() || i.getCount() < i.getMaxStackSize()) {
            for (Slot s : menu.slots) {
                if (!(s.container instanceof Inventory) || !s.hasItem() || !smeltable(s.getItem(), kind)) continue;
                if (!i.isEmpty() && !ItemStack.isSameItemSameComponents(s.getItem(), i)) continue;
                smeltClick(mc, p, menu, s);
                return;
            }
        }
        if (smeltClose && smeltDone > 0) {
            smeltDone = 0;
            p.closeContainer();
        }
    }

    private static void smeltClick(Minecraft mc, LocalPlayer p, AbstractContainerMenu menu, Slot s) {
        mc.gameMode.handleContainerInput(menu.containerId, s.index, 0, ContainerInput.QUICK_MOVE, p);
        smeltDone++;
        smeltWait = Math.max(0, smeltDelay);
    }

    // =====================================================================================
    // Auto Sign
    // =====================================================================================

    private static Screen signDone;

    private static void signTick(Minecraft mc, LocalPlayer p) {
        Screen screen = mc.gui.screen();
        if (!autoSign || !(screen instanceof AbstractSignEditScreen) || screen == signDone) return;
        signDone = screen;
        String[] lines = ((de.test.mixin.SignEditAccessor) screen).test$messages();
        if (lines == null || lines.length < 4) return;
        if (signOnlyEmpty) {
            for (String l : lines) {
                if (l != null && !l.isEmpty()) return; // schon beschrieben: selbst bearbeiten lassen
            }
        }
        String[] want = {sign1, sign2, sign3, sign4};
        boolean any = false;
        for (String w : want) any |= w != null && !w.isEmpty();
        if (!any) return; // nichts eingestellt
        for (int i = 0; i < 4; i++) lines[i] = want[i] == null ? "" : want[i];
        screen.onClose(); // beim Schliessen geht der Text an den Server
    }

    // =====================================================================================
    // Einstellungen
    // =====================================================================================

    static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Auto Farm" -> new ModuleScreen(parent, module, f -> {
                f.description("Erntet alles Reife um dich herum und pflanzt gleich wieder neu.");
                f.slider("Umkreis (Blöcke)", 1, 6, () -> farmRadius, v -> farmRadius = v, 4);
                f.slider("Pro Tick", 1, 8, () -> farmPerTick, v -> farmPerTick = v, 3);
                f.section("Pflanzen");
                f.toggle("Wieder einpflanzen", () -> farmReplant, v -> farmReplant = v, true);
                f.toggle("Auch leeren Ackerboden bepflanzen", () -> farmFillEmpty, v -> farmFillEmpty = v, false);
                f.toggle("Melonen und Kürbisse", () -> farmMelons, v -> farmMelons = v, true);
                f.toggle("Zuckerrohr (unterstes bleibt)", () -> farmCane, v -> farmCane = v, true);
                f.toggle("Netherwarzen", () -> farmWart, v -> farmWart = v, true);
                f.toggle("Kakao", () -> farmCocoa, v -> farmCocoa = v, true);
                f.toggle("Süßbeeren pflücken", () -> farmBerries, v -> farmBerries = v, true);
                f.section("Einsammeln");
                f.toggle("Ernte einsammeln (läuft hin)", () -> farmCollect, v -> farmCollect = v, false);
                f.note("Die Saat muss in der Hotbar sein. Weizen, Karotten, Kartoffeln, Rote Bete, Fackellilien und "
                        + "Netherwarzen pflanzt er dorthin, wo er sie geerntet hat.");
                f.footer(() -> farm, v -> farm = v);
            });
            case "Nuker" -> new ModuleScreen(parent, module, f -> {
                f.description("Baut alle Blöcke um dich herum ab, so weit du reichst.");
                f.choice("Was", NUKER_MODES, () -> nukerMode, v -> nukerMode = v, 0);
                f.wideButton("Blöcke wählen (" + nukerBlocks.size() + ")", false,
                        () -> Minecraft.getInstance().gui.setScreen(new PickerScreen(Minecraft.getInstance().gui.screen(),
                                "Nuker: Blöcke", PickerScreen.Kind.BLOCK, PickerScreen.ofSet(nukerBlocks), null, null)));
                f.slider("Umkreis (Blöcke)", 1, 6, () -> nukerRadius, v -> nukerRadius = v, 4);
                f.slider("Sofort abbaubare pro Tick", 1, 10, () -> nukerPerTick, v -> nukerPerTick = v, 4);
                f.toggle("Nichts unter deinen Füßen", () -> nukerKeepFloor, v -> nukerKeepFloor = v, true);
                f.toggle("Von oben nach unten", () -> nukerTopDown, v -> nukerTopDown = v, true);
                f.toggle("Bestes Werkzeug nehmen", () -> nukerTool, v -> nukerTool = v, true);
                f.note("„Nur ausgewählte Blöcke“ nimmt die Liste unter „Blöcke wählen“. Truhen und andere Blöcke "
                        + "mit Inhalt baut er sonst nie ab, Grundgestein sowieso nicht.");
                f.footer(() -> nuker, v -> nuker = v);
            });
            case "Chest Stealer" -> new ModuleScreen(parent, module, f -> {
                f.description("Nimmt beim Öffnen einer Truhe automatisch alles heraus.");
                f.slider("Pause zwischen zwei Griffen (Ticks)", 0, 10, () -> stealDelay, v -> stealDelay = v, 2);
                f.toggle("Etwas zufällig (wirkt menschlicher)", () -> stealRandom, v -> stealRandom = v, true);
                f.toggle("Danach schließen", () -> stealClose, v -> stealClose = v, true);
                f.toggle("Nur Truhen, Fässer und Shulker-Kisten", () -> stealOnlyChests, v -> stealOnlyChests = v, true);
                f.note("Ist dein Inventar voll, hört er auf. Ohne „Nur Truhen, Fässer und Shulker-Kisten“ leert er "
                        + "auch Öfen, Trichter und Werfer.");
                f.footer(() -> stealer, v -> stealer = v);
            });
            case "Baum-Fäller" -> new ModuleScreen(parent, module, f -> {
                f.description("Bau einen Stamm ab - er fällt den ganzen Baum, sammelt das Holz ein und pflanzt "
                        + "einen Setzling.");
                f.toggle("Nur echte Bäume (mit Blättern)", () -> treeOnlyNatural, v -> treeOnlyNatural = v, true);
                f.toggle("Unter den Stamm stellen (für hohe Bäume)", () -> treeStep, v -> treeStep = v, true);
                f.toggle("Holz einsammeln", () -> treeCollect, v -> treeCollect = v, true);
                f.toggle("Setzling pflanzen", () -> treePlant, v -> treePlant = v, true);
                f.toggle("Beste Axt nehmen", () -> treeTool, v -> treeTool = v, true);
                f.note("Er kommt nur so hoch, wie du reichst - bei großen Fichten und Dschungelbäumen bleibt oben "
                        + "etwas stehen. „Nur echte Bäume“ schützt Holzhäuser.");
                f.footer(() -> tree, v -> tree = v);
            });
            case "Auto Light" -> new ModuleScreen(parent, module, f -> {
                f.description("Setzt Fackeln überall dort, wo es so dunkel ist, dass Monster entstehen können.");
                f.slider("Umkreis (Blöcke)", 1, 5, () -> lightRadius, v -> lightRadius = v, 4);
                f.slider("Pause zwischen zwei Fackeln (Ticks)", 1, 20, () -> lightDelay, v -> lightDelay = v, 5);
                f.toggle("Auch draußen (nachts)", () -> lightOutside, v -> lightOutside = v, false);
                f.note("Fackeln müssen in der Hotbar sein. Er setzt nur dort eine, wo noch gar kein Licht ist - so "
                        + "reichen wenige Fackeln für eine ganze Höhle.");
                f.footer(() -> autoLight, v -> autoLight = v);
            });
            case "Bonemeal Aura" -> new ModuleScreen(parent, module, f -> {
                f.description("Düngt Pflanzen und Setzlinge um dich herum mit Knochenmehl aus der Hotbar.");
                f.slider("Umkreis (Blöcke)", 1, 5, () -> bmRadius, v -> bmRadius = v, 4);
                f.slider("Pause (Ticks)", 0, 10, () -> bmDelay, v -> bmDelay = v, 2);
                f.toggle("Nur Felder, Setzlinge und Beeren", () -> bmOnlyCrops, v -> bmOnlyCrops = v, true);
                f.note("Ohne „Nur Felder“ düngt er auch Gras - dann wachsen dort überall Blumen.");
                f.footer(() -> bonemeal, v -> bonemeal = v);
            });
            case "Tier-Automatik" -> new ModuleScreen(parent, module, f -> {
                f.description("Füttert Tiere in Reichweite, damit sie Junge bekommen, und schert Schafe.");
                f.toggle("Vermehren", () -> anBreed, v -> anBreed = v, true);
                f.toggle("Schafe scheren", () -> anShear, v -> anShear = v, true);
                f.note("Das passende Futter (z. B. Weizen, Karotten oder Samen) und die Schere müssen in der Hotbar "
                        + "sein. Jedes Tier füttert er höchstens alle 5 Minuten - öfter geht es im Spiel auch nicht.");
                f.footer(() -> animals, v -> animals = v);
            });
            case "Villager Roller" -> new ModuleScreen(parent, module, f -> {
                f.description("Baut das Lesepult ab und wieder hin, bis der Bibliothekar das Buch anbietet, das du "
                        + "willst. Dann hört er auf und spielt einen Ton.");
                f.text("Verzauberungen (mit Komma)", () -> rollWish, v -> rollWish = v, "mending");
                f.slider("Höchstens (Smaragde)", 5, 64, () -> rollMaxPrice, v -> rollMaxPrice = v, 64);
                f.toggle("Nur die höchste Stufe", () -> rollMaxLevel, v -> rollMaxLevel = v, true);
                f.note("Stell dich neben einen Dorfbewohner ohne Beruf (oder einen Bibliothekar, mit dem noch "
                        + "keiner gehandelt hat) und sein Lesepult, ein zweites Lesepult in der Hotbar schadet nicht. "
                        + "Namen auf Englisch (mending, unbreaking, efficiency) oder so, wie sie im Spiel heißen.");
                f.footer(() -> roller, v -> roller = v);
            });
            case "Auto Smelt" -> new ModuleScreen(parent, module, f -> {
                f.description("Füllt Öfen beim Öffnen mit Brennstoff und Zutaten aus deinem Inventar und nimmt das "
                        + "Fertige heraus.");
                f.toggle("Erze und Rohmetall", () -> smeltOres, v -> smeltOres = v, true);
                f.toggle("Essen", () -> smeltFood, v -> smeltFood = v, true);
                f.toggle("Sand, Bruchstein, Ton und Co.", () -> smeltOther, v -> smeltOther = v, false);
                f.toggle("Nur Kohle als Brennstoff", () -> smeltOnlyCoal, v -> smeltOnlyCoal = v, true);
                f.slider("Pause zwischen zwei Griffen (Ticks)", 0, 10, () -> smeltDelay, v -> smeltDelay = v, 2);
                f.toggle("Danach schließen", () -> smeltClose, v -> smeltClose = v, false);
                f.note("Der Schmelzofen bekommt nur Erze, der Räucherofen nur Essen. Ohne „Nur Kohle“ verbrennt er "
                        + "auch Bretter und Stöcke, aber nie Werkzeuge oder Eimer.");
                f.footer(() -> smelt, v -> smelt = v);
            });
            case "Auto Sign" -> new ModuleScreen(parent, module, f -> {
                f.description("Schreibt beim Aufstellen eines Schilds sofort deinen Text drauf.");
                f.text("Zeile 1", () -> sign1, v -> sign1 = v, "");
                f.text("Zeile 2", () -> sign2, v -> sign2 = v, "");
                f.text("Zeile 3", () -> sign3, v -> sign3 = v, "");
                f.text("Zeile 4", () -> sign4, v -> sign4 = v, "");
                f.toggle("Nur leere Schilder", () -> signOnlyEmpty, v -> signOnlyEmpty = v, true);
                f.note("Pro Zeile passen etwa 15 Zeichen. Sind alle Zeilen leer, kannst du ganz normal schreiben.");
                f.footer(() -> autoSign, v -> autoSign = v);
            });
            default -> null;
        };
    }
}
