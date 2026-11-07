package com.tacz.guns.entity.shooter;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.command.sub.DebugCommand;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunMelee;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.MeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunDefaultMeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunMeleeData;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.relauncher.Side;

/**
 * 刺刀近战（melee）逻辑与近战冷却。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityMelee.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder, LivingEntityDrawGun)}，
 * 以及 {@code melee()} / {@code scheduleTickMelee()} / {@code getMeleeCoolDown()} 被
 * {@code LivingEntityMixin} 直接调用。
 *
 * <p>
 * 平台替换（相对上游）：
 * {@code LivingEntity → EntityLivingBase}、{@code LogicalSide → cpw.mods.fml.relauncher.Side}、
 * {@code Entity#getId() → getEntityId()}、{@code net.minecraft.resources.ResourceLocation → net.minecraft.util.ResourceLocation}。
 */
public class LivingEntityMelee {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;
    private final LivingEntityDrawGun draw;

    public LivingEntityMelee(EntityLivingBase shooter, ShooterDataHolder data, LivingEntityDrawGun draw) {
        this.shooter = shooter;
        this.data = data;
        this.draw = draw;
    }

    public void melee() {
        // 1.7.10：Supplier#get() 可能返回 null（空手），用 getCurrentGunItem() 归一化后判空。
        ItemStack currentGunItem = data.getCurrentGunItem();
        if (currentGunItem == null) {
            debugLog("server stop: currentGunItem is null");
            return;
        }
        // 检查是否在切枪
        long drawCoolDown = draw.getDrawCoolDown();
        if (drawCoolDown != 0) {
            debugLog("server stop: drawing (drawCoolDown={})", drawCoolDown);
            return;
        }
        // 检查是否在拉栓
        if (data.isBolting) {
            debugLog("server stop: bolting");
            return;
        }
        long coolDown = getMeleeCoolDown();
        if (coolDown != 0) {
            debugLog("server stop: melee cool down {}", coolDown);
            return;
        }
        // 触发近战事件
        if (MinecraftForge.EVENT_BUS.post(new GunMeleeEvent(shooter, currentGunItem, Side.SERVER))) {
            debugLog("server stop: GunMeleeEvent canceled on SERVER");
            return;
        }
        NetworkHandler.sendToTrackingEntity(new ServerMessageGunMelee(shooter.getEntityId(), currentGunItem), shooter);
        if (currentGunItem.getItem() instanceof AbstractGunItem logicGun) {
            data.meleeTimestamp = GunTime.nowMillis();

            ResourceLocation muzzleId = logicGun.getAttachmentId(currentGunItem, AttachmentType.MUZZLE);
            MeleeData muzzleMeleeData = getMeleeData(muzzleId);
            if (muzzleMeleeData != null) {
                float prepTime = muzzleMeleeData.getPrepTime();
                data.meleePrepTickCount = (int) Math.max(0, prepTime * 20);
                debugLog("server branch: muzzle melee, prepTicks={}", data.meleePrepTickCount);
                return;
            }

            ResourceLocation stockId = logicGun.getAttachmentId(currentGunItem, AttachmentType.STOCK);
            MeleeData stockMeleeData = getMeleeData(stockId);
            if (stockMeleeData != null) {
                float prepTime = stockMeleeData.getPrepTime();
                data.meleePrepTickCount = (int) Math.max(0, prepTime * 20);
                debugLog("server branch: stock melee, prepTicks={}", data.meleePrepTickCount);
                return;
            }

            ResourceLocation gunId = logicGun.getGunId(currentGunItem);
            TimelessAPI.getCommonGunIndex(gunId)
                .ifPresentOrElse(index -> {
                    GunDefaultMeleeData defaultMeleeData = index.getGunData()
                        .getMeleeData()
                        .getDefaultMeleeData();
                    if (defaultMeleeData == null) {
                        debugLog("server stop: {} has no default melee data", gunId);
                        return;
                    }
                    float prepTime = defaultMeleeData.getPrepTime();
                    data.meleePrepTickCount = (int) Math.max(0, prepTime * 20);
                    debugLog(
                        "server branch: default melee ({}), damage={}, distance={}, rangeAngle={}, knockback={}, prepTicks={}",
                        gunId,
                        defaultMeleeData.getDamage(),
                        defaultMeleeData.getDistance(),
                        defaultMeleeData.getRangeAngle(),
                        defaultMeleeData.getKnockback(),
                        data.meleePrepTickCount);
                }, () -> debugLog("server stop: gun index for {} is missing on server", gunId));
        } else {
            debugLog("server stop: held item is not an AbstractGunItem");
        }
    }

    public void scheduleTickMelee() {
        if (this.data.meleePrepTickCount > 0) {
            this.data.meleePrepTickCount--;
            debugLog("server tick_melee: prep countdown -> {}", this.data.meleePrepTickCount);
            return;
        }
        if (this.data.meleePrepTickCount == 0) {
            this.data.meleePrepTickCount = -1;
            // 1.7.10：空手时 getCurrentGunItem() 返回 null（上游为 ItemStack.EMPTY）
            ItemStack currentGunItem = data.getCurrentGunItem();
            if (currentGunItem == null) {
                debugLog("server tick_melee: fire skipped, currentGunItem is null");
                return;
            }
            if (currentGunItem.getItem() instanceof AbstractGunItem logicGun) {
                debugLog("server tick_melee: firing doMelee for {}", logicGun.getGunId(currentGunItem));
                logicGun.melee(data, this.shooter, currentGunItem);
            } else {
                debugLog("server tick_melee: fire skipped, held item is not an AbstractGunItem");
            }
        }
    }

    public long getMeleeCoolDown() {
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
            GunMeleeData meleeData = index.getGunData()
                .getMeleeData();
            // 获取枪口，看看有没有近战数据
            ResourceLocation muzzleId = iGun.getAttachmentId(currentGunItem, AttachmentType.MUZZLE);
            MeleeData muzzleMeleeData = getMeleeData(muzzleId);
            if (muzzleMeleeData != null) {
                return getTotalCooldownTime(meleeData, muzzleMeleeData.getCooldown());
            }

            // 枪托
            ResourceLocation stockId = iGun.getAttachmentId(currentGunItem, AttachmentType.STOCK);
            MeleeData stockMeleeData = getMeleeData(stockId);
            if (stockMeleeData != null) {
                return getTotalCooldownTime(meleeData, stockMeleeData.getCooldown());
            }

            GunDefaultMeleeData defaultMeleeData = meleeData.getDefaultMeleeData();
            float defaultMeleeCooldownTime = defaultMeleeData == null ? 0 : defaultMeleeData.getCooldown();
            return getTotalCooldownTime(meleeData, defaultMeleeCooldownTime);
        })
            .orElse(-1L);
    }

    private long getTotalCooldownTime(GunMeleeData meleeData, float extraCooldownTime) {
        float totalCooldownTime = meleeData.getCooldown() + extraCooldownTime;
        long coolDown = (long) (totalCooldownTime * 1000) - (GunTime.nowMillis() - data.meleeTimestamp);
        // 给 5 ms 的窗口时间，以平衡延迟
        coolDown = coolDown - 5;
        if (coolDown < 0) {
            return 0L;
        }
        return coolDown;
    }

    @Nullable
    private MeleeData getMeleeData(ResourceLocation attachmentId) {
        if (DefaultAssets.isEmptyAttachmentId(attachmentId)) {
            return null;
        }
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
            .map(
                index -> index.getData()
                    .getMeleeData())
            .orElse(null);
    }

    /**
     * 近战链路诊断日志，仅在 {@code /tacz debug true} 时输出。
     *
     * <p>
     * 移植特有：上游没有近战调试输出，{@code /tacz debug} 上游只用于近战范围粒子与靶板压测；
     * 这里补「服务端在哪一步拒绝 / 走了哪条分支」的日志，便于实机定位「按 V 没伤害」这类
     * 客户端、网络、服务端表现完全一样的问题（见 docs/review/r8/README.md）。
     */
    private static void debugLog(String format, Object... args) {
        if (DebugCommand.DEBUG) {
            GunMod.LOGGER.info("[TACZ Melee] " + format, args);
        }
    }
}
