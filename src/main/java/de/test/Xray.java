package de.test;

import java.util.Set;

import net.minecraft.world.level.block.Block;

/**
 * Zustand des X-Ray. Die Render-Mixins lesen ihn sehr oft und auch aus den Hintergrund-Threads, die die
 * Chunks bauen - deshalb nur einfache Felder, ein unveraenderliches Set und nichts, was beim Laden der Klasse
 * Minecraft braucht. Eingestellt wird alles in {@link RenderMods} (X-Ray).
 */
public final class Xray {
    private Xray() {
    }

    /** X-Ray an (und eine Welt geladen). */
    public static volatile boolean active;
    /** Wasser nicht zeichnen. */
    public static volatile boolean hideWater;
    /** Diese Bloecke bleiben sichtbar, alle anderen werden unsichtbar. */
    private static volatile Set<Block> shown = Set.of();

    /** Wird dieser Block gerade ausgeblendet? (nur sinnvoll, wenn {@link #active}) */
    public static boolean hidden(Block b) {
        return !shown.contains(b);
    }

    static void set(boolean on, boolean water, Set<Block> blocks) {
        shown = Set.copyOf(blocks);
        hideWater = water;
        active = on;
    }
}
