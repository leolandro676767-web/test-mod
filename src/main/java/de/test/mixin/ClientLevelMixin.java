package de.test.mixin;

import de.test.ChunkScanHooks;
import de.test.Safe;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ESP: einzelne Blockaenderungen sofort in die Block-Suchen uebernehmen (z.B. wenn ein Anti-Xray Erze aufdeckt). */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    // Blockaenderungen vom Server
    @Inject(method = "setServerVerifiedBlockState", at = @At("RETURN"), require = 0)
    private void test$serverBlock(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        ClientLevel self = (ClientLevel) (Object) this;
        Safe.run("ESP", () -> ChunkScanHooks.blockChanged(self, pos));
    }

    // eigene Aenderungen (z.B. selbst abgebaut)
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"), require = 0)
    private void test$setBlock(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        Safe.run("ESP", () -> ChunkScanHooks.blockChanged(self, pos));
    }
}
