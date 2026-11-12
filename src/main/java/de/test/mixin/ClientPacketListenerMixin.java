package de.test.mixin;

import de.test.PlayerMods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Rotate: Blickrichtung vor einem Server-Teleport merken und danach wiederherstellen. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private float test$yaw;
    @Unique
    private float test$pitch;

    @Unique
    private void test$save() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        test$yaw = p.getYRot();
        test$pitch = p.getXRot();
    }

    @Unique
    private void test$restore() {
        var p = Minecraft.getInstance().player;
        if (p == null || !PlayerMods.noRotate) return;
        if (PlayerMods.noRotateYaw) {
            p.setYRot(test$yaw);
            p.yRotO = test$yaw;
        }
        if (PlayerMods.noRotatePitch) {
            p.setXRot(test$pitch);
            p.xRotO = test$pitch;
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), require = 0)
    private void test$moveHead(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        test$save();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"), require = 0)
    private void test$moveTail(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        test$restore();
    }

    @Inject(method = "handleRotatePlayer", at = @At("HEAD"), require = 0)
    private void test$rotHead(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        test$save();
    }

    @Inject(method = "handleRotatePlayer", at = @At("TAIL"), require = 0)
    private void test$rotTail(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        test$restore();
    }
}
