package de.test.mixin;

import de.test.Xray;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X-Ray: ausgeblendete Bloecke werden nicht gezeichnet, verdecken nichts und werfen keine Schatten (Umgebungs-
 * verdeckung). Die Kollision bleibt, wie sie ist - man laeuft also nicht durch Waende.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateXrayMixin {
    @Unique
    private boolean test$hidden() {
        return Xray.active && Xray.hidden(((BlockBehaviour.BlockStateBase) (Object) this).getBlock());
    }

    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$xrayShape(CallbackInfoReturnable<RenderShape> cir) {
        if (test$hidden()) cir.setReturnValue(RenderShape.INVISIBLE);
    }

    @Inject(method = "isSolidRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$xraySolid(CallbackInfoReturnable<Boolean> cir) {
        if (test$hidden()) cir.setReturnValue(false);
    }

    @Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$xrayOcclude(CallbackInfoReturnable<Boolean> cir) {
        if (test$hidden()) cir.setReturnValue(false);
    }

    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$xrayShade(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (Xray.active) cir.setReturnValue(1.0f);
    }
}
