package com.tacz.guns.entity.shooter;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.common.GunFireSelectEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunFireSelect;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;

import cpw.mods.fml.relauncher.Side;

/**
 * 切换开火模式（fire select）逻辑。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityFireSelect.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder)} 与 {@code fireSelect()} 被 {@code LivingEntityMixin} 直接调用。
 *
 * <p>
 * 平台替换（相对上游）：
 * {@code LivingEntity → EntityLivingBase}、{@code LogicalSide → cpw.mods.fml.relauncher.Side}、
 * {@code Entity#getId() → getEntityId()}。
 */
public class LivingEntityFireSelect {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;

    public LivingEntityFireSelect(EntityLivingBase shooter, ShooterDataHolder data) {
        this.shooter = shooter;
        this.data = data;
    }

    public void fireSelect() {
        // 1.7.10：Supplier#get() 可能返回 null（空手），用 getCurrentGunItem() 归一化后判空。
        ItemStack currentGunItem = data.getCurrentGunItem();
        if (currentGunItem == null) {
            return;
        }
        if (!(currentGunItem.getItem() instanceof IGun iGun)) {
            return;
        }
        if (MinecraftForge.EVENT_BUS.post(new GunFireSelectEvent(shooter, currentGunItem, Side.SERVER))) {
            return;
        }
        NetworkHandler
            .sendToTrackingEntity(new ServerMessageGunFireSelect(shooter.getEntityId(), currentGunItem), shooter);
        if (iGun instanceof AbstractGunItem logicGun) {
            logicGun.fireSelect(data, currentGunItem);
            // 刷新配件缓存
            AttachmentPropertyManager.postChangeEvent(shooter, currentGunItem);
        }
    }
}
