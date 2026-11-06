package com.tacz.guns.client.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.mcshim.EntityPose;
import com.tacz.guns.mcshim.EntityStateHelper;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerCrawl;

/**
 * 客户端趴下（crawl）状态机。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerCrawl.java}。
 * 必须与 {@code entity/shooter/LivingEntityCrawl} 一致（服务端同样只维护 {@code ShooterDataHolder#isCrawling}；
 * 服务端若要给 Lua API 提供 {@code is_crawling}，也需同样写 {@link EntityStateHelper} 姿势表，属 {@code entity/**}）。
 *
 * <p>
 * 平台替换（相对上游）：{@code LocalPlayer} → {@link EntityPlayerSP}、
 * {@code getMainHandItem()} → {@code getHeldItem()}、{@code isPassenger()} → {@code isRiding()}、
 * {@code isSleeping()} → {@code isPlayerSleeping()}、{@code onGround()} → {@code onGround} 字段、
 * {@code jumping} → {@code movementInput.jump}（客户端可用）。
 *
 * <p>
 * <b>1.7.10 降级（姿态系统）：</b>1.20.1 的 {@code net.minecraft.world.entity.Pose}（含
 * {@code Pose.SWIMMING}）与 {@code Player#setForcedPose/getForcedPose} 在 1.7.10 <b>完全不存在</b>；
 * 移植用 {@link EntityStateHelper#ENTITY_POSE_OVERRIDES} 表达同一个「强制姿势」语义
 * （该表就是 mcshim 为 crawl 预留的等价物），因此：
 * <ul>
 * <li>{@code tickCrawl()} 的「姿态被外部改回」检测保持上游逻辑，只是改为读
 * {@link EntityStateHelper#getForcedPose}；</li>
 * <li>{@code setCrawling()} 用 {@link EntityStateHelper#setForcedPose} 写入/清除
 * {@link EntityPose#SWIMMING}，{@code MouseHandlerMixin}（趴下俯仰限制）、
 * {@code CameraSetupEvent}（趴下后坐力系数）、{@code TickAnimationEvent}（趴下不播移动动画）都读这个姿势。</li>
 * </ul>
 * 仍属降级的是「真正改变体型/视高」：{@code EntityPlayerSP} 的 AABB/eyeHeight 在 1.7.10 无姿势驱动，
 * 需要在实体上做 Mixin（属 mixin 工作流，见 {@code LivingEntityCrawl} 的同类说明）。
 */
public class LocalPlayerCrawl {

    /**
     * 冷却时间为 10 tick
     */
    private static final int COOLDOWN_TICKS = 10;
    private final EntityPlayerSP player;
    private boolean isCrawling = false;
    private int crawCooldownTicks = 0;

    public LocalPlayerCrawl(EntityPlayerSP player) {
        this.player = player;
    }

    public void crawl(boolean isCrawl) {
        // 退出不能被冷却、切枪或离地拦截，且只同步实际发生的状态变化。
        if (!isCrawl) {
            setCrawling(false);
            return;
        }
        ItemStack mainHandItem = player.getHeldItem();
        if (isCrawling || crawCooldownTicks > 0 || !canCrawl()) {
            return;
        }
        if (mainHandItem != null && mainHandItem.getItem() instanceof IGun gun
            && TimelessAPI.getClientGunIndex(gun.getGunId(mainHandItem))
                .isEmpty()) {
            return;
        }
        setCrawling(true);
        this.crawCooldownTicks = COOLDOWN_TICKS;
    }

    public void tickCrawl() {
        if (crawCooldownTicks > 0) {
            crawCooldownTicks--;
        }
        if (!isCrawling) {
            return;
        }
        // 上游 LocalPlayerCrawl.java:52：player.getForcedPose() != Pose.SWIMMING → 姿态被外部改回就退出趴下。
        boolean poseChanged = EntityStateHelper.getForcedPose(player) != EntityPose.SWIMMING;
        if (poseChanged || !canCrawl()) {
            setCrawling(false);
        }
    }

    public boolean isCrawling() {
        return isCrawling;
    }

    private boolean canCrawl() {
        if (!SyncConfig.ENABLE_CRAWL.get()) {
            return false;
        }
        // 1.7.10 无旁观者模式（isSpectator 恒 false），invalidState 只保留死亡与骑乘两项。
        boolean invalidState = !player.isEntityAlive() || player.isRiding();
        // 1.7.10 没有游泳状态，用「在水里」近似（上游 isSwimming() 还要求处于水中疾跑姿态，
        // 1.7.10 只有 isInWater() 这一个可用信息）。
        boolean jumping = player.movementInput != null && player.movementInput.jump;
        boolean invalidMovement = jumping || player.isInWater() || !player.onGround;
        // TODO(port): 上游还检查 isFallFlying()（鞘翅）与 isAutoSpinAttack()（三叉戟），1.7.10 均不存在，恒 false。
        boolean incompatiblePose = player.isPlayerSleeping();
        if (invalidState || invalidMovement || incompatiblePose) {
            return false;
        }
        ItemStack mainHandItem = player.getHeldItem();
        if (mainHandItem != null && mainHandItem.getItem() instanceof IGun gun) {
            return gun.isCanCrawl(mainHandItem) && TimelessAPI.getCommonGunIndex(gun.getGunId(mainHandItem))
                .isPresent();
        }
        return SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get();
    }

    private void setCrawling(boolean crawling) {
        if (isCrawling == crawling) {
            return;
        }
        isCrawling = crawling;
        // 上游 LocalPlayerCrawl.java:86-88：趴下状态变化时设置/清除一次强制姿势（1.7.10 用 mcshim 的姿势覆盖表）。
        if (crawling || EntityStateHelper.getForcedPose(player) == EntityPose.SWIMMING) {
            EntityStateHelper.setForcedPose(player, crawling ? EntityPose.SWIMMING : null);
        }
        NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerCrawl(crawling));
    }
}
