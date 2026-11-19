package de.test;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Oeffentlicher Zugang fuer die Mixins (ChunkScan selbst bleibt im Paket). */
public final class ChunkScanHooks {
    private ChunkScanHooks() {
    }

    public static void chunkChanged(int x, int z) {
        ChunkScan.chunkChanged(x, z);
    }

    public static void blockChanged(ClientLevel level, BlockPos pos) {
        ChunkScan.blockChanged(level, pos);
    }
}
