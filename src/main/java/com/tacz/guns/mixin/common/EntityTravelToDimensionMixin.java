package com.tacz.guns.mixin.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;

/**
 * 上游 1.20.1 {@code event/TravelToDimensionEvent}（监听 {@code EntityTravelToDimensionEvent}，
 * 对<b>任意</b> {@code LivingEntity} 生效：主手是枪时 {@code initialData()} 刷新枪械数据）的 1.7.10 落点。
 *
 * <p>
 * <b>为什么是 Mixin</b>：1.7.10 Forge 没有 {@code EntityTravelToDimensionEvent}（全仓确认），
 * 只有 FML 的 {@code PlayerEvent.PlayerChangedDimensionEvent}（仅玩家、且是换完维度之后）——
 * 该路径已由 {@code event/TravelToDimensionEvent} 承担（r7-core scope）。非玩家活体实体的等价
 * 注入点就是 {@link Entity#travelToDimension(int)}（{@code Entity.java:2369}）的 HEAD：
 * 与上游事件「旅行开始前」的时机一致；{@code EntityPlayerMP} 重写了该方法（{@code :545}，
 * 玩家走 {@code ServerConfigurationManager} 专用路径）⇒ 本注入只覆盖非玩家，不与玩家路径重复。
 *
 * <p>
 * <b>1.7.10 差异（记录）</b>：1.7.10 的非玩家实体跨维度是「移除旧实例 + {@code EntityList.createEntityByName}
 * 重建 + NBT 拷贝」（{@code Entity.java:2386-2400}），而 1.20 Forge 的 {@code ITeleporter} 保留原实例。
 * 因此本注入对 1.7.10 的实际作用是「旧实例在离开前刷新一次」；新实例由 NBT 重建、{@code ShooterDataHolder}
 * 本来就不入 NBT ⇒ 等价于「新实例持有默认数据」。保留本注入以对齐上游覆盖面（例如其它模组重写
 * {@code travelToDimension} 但复用原实例时）。
 */
@Mixin(Entity.class)
public class EntityTravelToDimensionMixin {

    @Inject(method = "travelToDimension", at = @At("HEAD"))
    private void tacz$refreshGunDataOnTravel(int dimensionId, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        // 上游事件只在服务端逻辑里触发（Entity#travelToDimension 的整个方法体也在 !isRemote 分支内）
        if (self.worldObj == null || self.worldObj.isRemote) {
            return;
        }
        if (!(self instanceof EntityLivingBase living)) {
            return;
        }
        ItemStack held = living.getHeldItem();
        if (held == null || !(held.getItem() instanceof IGun)) {
            return;
        }
        // instanceof 而非 fromLivingEntity 强转：仅当 LivingEntityMixin 已 apply 时才刷新，避免极端环境 CCE
        if (living instanceof IGunOperator operator) {
            operator.initialData();
        }
    }
}
