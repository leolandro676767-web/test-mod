package de.test.mixin;

import de.test.Xray;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** X-Ray: Seiten neben ausgeblendeten Bloecken immer zeichnen. */
@Mixin(Block.class)
public abstract class BlockXrayMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private static void test$xrayFace(BlockState state, BlockState neighbor, Direction direction,
            CallbackInfoReturnable<Boolean> cir) {
        if (Xray.active && Xray.hidden(neighbor.getBlock())) cir.setReturnValue(true);
    }
}
