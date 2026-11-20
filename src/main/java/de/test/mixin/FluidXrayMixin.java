package de.test.mixin;

import de.test.Xray;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wasser ausblenden und Lava neben ausgeblendeten Bloecken ganz zeichnen. */
@Mixin(FluidRenderer.class)
public abstract class FluidXrayMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$xrayWater(net.minecraft.client.renderer.block.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos, FluidRenderer.Output output, BlockState blockState,
            FluidState fluidState, CallbackInfo ci) {
        if (Xray.active && Xray.hideWater && fluidState.is(FluidTags.WATER)) ci.cancel();
    }

    @Inject(method = "isFaceOccludedByNeighbor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void test$xrayLava(Direction direction, float height, BlockState neighbor,
            CallbackInfoReturnable<Boolean> cir) {
        if (Xray.active && Xray.hidden(neighbor.getBlock())) cir.setReturnValue(false);
    }
}
