package com.tacz.guns.mixin.common;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.api.entity.IGunOperator;

/**
 * 搬运自上游 1.20.1 {@code mixin/common/ServerPlayerMixin.java}。
 *
 * <p>
 * 1.7.10 适配：上游 {@code ServerPlayer#restoreFrom} → 1.7.10 的
 * {@code EntityPlayerMP#clonePlayer(EntityPlayer, boolean)}（死亡重生 / 维度切换时从旧实体拷贝数据），
 * 注入点在 RETURN，语义对齐上游「重置枪械操作状态」。
 */
@Mixin(EntityPlayerMP.class)
public class ServerPlayerMixin {

    @Inject(method = "clonePlayer", at = @At("RETURN"))
    public void initialGunOperateData(EntityPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        EntityPlayerMP player = (EntityPlayerMP) (Object) this;
        IGunOperator.fromLivingEntity(player)
            .initialData();
    }
}
