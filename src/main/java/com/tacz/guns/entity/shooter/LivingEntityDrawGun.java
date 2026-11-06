package com.tacz.guns.entity.shooter;

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunDraw;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.relauncher.Side;

/**
 * 切枪（draw）逻辑与切枪冷却。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityDrawGun.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder)}、{@code draw(Supplier<ItemStack>)} 与
 * {@code getDrawCoolDown()} 被 {@code LivingEntityMixin} 直接调用。
 *
 * <p>
 * 平台替换（相对上游）：
 * {@code LivingEntity → EntityLivingBase}、{@code ItemStack.EMPTY → null}、
 * {@code net.minecraftforge.fml.LogicalSide → cpw.mods.fml.relauncher.Side}、
 * {@code Entity#getId() → Entity#getEntityId()}（1.7.10 实体网络 id 只有 getEntityId）、
 * {@code net.minecraft.resources.ResourceLocation → net.minecraft.util.ResourceLocation}。
 */
public class LivingEntityDrawGun {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;

    public LivingEntityDrawGun(EntityLivingBase shooter, ShooterDataHolder data) {
        this.shooter = shooter;
        this.data = data;
    }

    public void draw(@Nullable Supplier<ItemStack> gunItemSupplier) {
        // 重置各个状态
        data.initialData();

        // 更新切枪时间戳
        if (data.drawTimestamp == -1) {
            data.drawTimestamp = GunTime.nowMillis();
        }
        if (data.heatTimestamp == -1) {
            data.heatTimestamp = GunTime.nowMillis();
        }
        long drawTime = GunTime.nowMillis() - data.drawTimestamp;
        if (drawTime >= 0) {
            // 如果不处于收枪状态，则需要计算收枪时长
            if (drawTime < data.currentPutAwayTimeS * 1000) {
                // 从开始切枪到现在，抬枪的时间小于收枪需要的时间，则按抬枪时间计算。
                data.drawTimestamp = GunTime.nowMillis() + drawTime;
            } else {
                // 从开始切枪到现在，抬枪的时间大于收枪需要的时间，则按收枪时间计算。
                data.drawTimestamp = GunTime.nowMillis() + (long) (data.currentPutAwayTimeS * 1000);
            }
        }
        ItemStack lastItem = data.getCurrentGunItem();
        // 1.7.10：supplier 本身可能为 null（收枪/初始化路径），且 get() 可能返回 null（空手）。
        // 上游这两处拿到的是恒非 null 的 ItemStack.EMPTY，这里归一成 null，后续按空栈处理。
        ItemStack drawnItem = gunItemSupplier == null ? null : gunItemSupplier.get();
        MinecraftForge.EVENT_BUS.post(new GunDrawEvent(shooter, lastItem, drawnItem, Side.SERVER));
        NetworkHandler
            .sendToTrackingEntity(new ServerMessageGunDraw(shooter.getEntityId(), lastItem, drawnItem), shooter);
        data.setCurrentGunItem(gunItemSupplier);
        // 刷新配件数据
        // 1.7.10 getHeldItem() 空手时返回 null（1.20 的 getMainHandItem() 恒返回 ItemStack.EMPTY），
        // 这里跳过空栈等价于上游 postChangeEvent 内部对「非 IGun 物品」的提前返回。
        if (drawnItem != null) {
            AttachmentPropertyManager.postChangeEvent(shooter, drawnItem);
        }
        updatePutAwayTime();
    }

    public long getDrawCoolDown() {
        ItemStack currentGunItem = data.getCurrentGunItem();
        if (currentGunItem == null) {
            return 0;
        }
        if (!(currentGunItem.getItem() instanceof IGun iGun)) {
            return 0;
        }
        ResourceLocation gunId = iGun.getGunId(currentGunItem);
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunId);
        return gunIndex.map(index -> {
            long coolDown = (long) (index.getGunData()
                .getDrawTime() * 1000) - (GunTime.nowMillis() - data.drawTimestamp);
            // 给 5 ms 的窗口时间，以平衡延迟
            coolDown = coolDown - 5;
            if (coolDown < 0) {
                return 0L;
            }
            return coolDown;
        })
            .orElse(-1L);
    }

    private void updatePutAwayTime() {
        ItemStack gunItem = data.getCurrentGunItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun != null) {
            Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem));
            data.currentPutAwayTimeS = gunIndex.map(
                index -> index.getGunData()
                    .getPutAwayTime())
                .orElse(0F);
        } else {
            data.currentPutAwayTimeS = 0;
        }
    }
}
