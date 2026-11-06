package com.tacz.guns.entity.shooter;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.mcshim.EntityPose;
import com.tacz.guns.mcshim.EntityStateHelper;

/**
 * 趴下（crawl）状态机。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityCrawl.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder)} 与 {@code crawl(boolean)} / {@code tickCrawling()} 被
 * {@code LivingEntityMixin} 直接调用。{@code ShooterDataHolder#isCrawling} 仍是本类的状态源。
 *
 * <p>
 * 平台替换（相对上游）：{@code LivingEntity → EntityLivingBase}、{@code Player → EntityPlayer}、
 * {@code ItemStack.EMPTY → null}、{@code getMainHandItem() → getHeldItem()}。
 *
 * <p>
 * <b>姿态（Pose）的 1.7.10 等价物</b>：1.20 的 {@code Pose}/{@code setForcedPose}/{@code setPose} 在 1.7.10
 * 不存在，用 mcshim 的 {@link EntityStateHelper#setForcedPose} 姿势覆盖表表达（与
 * {@code client/gameplay/LocalPlayerCrawl} 完全同一写法）。<b>服务端也必须写这张表</b>：
 * 上游的 pose 是「服务端实体状态」，服务端代码（如 {@code item/ModernKineticGunScriptAPI#applyRecoilPush}
 * 的趴下后坐力系数）直接读得到；本移植的读端是 {@link EntityStateHelper#getPose}，
 * 不写表那侧就永远读到非 SWIMMING。
 * <p>
 * 仍然降级的两点（1.7.10 平台限制，非本类可补）：
 * <ul>
 * <li>{@code shooter.jumping}：{@code EntityLivingBase#isJumping} 是 protected 且无公开访问器，服务端读不到；</li>
 * <li>{@code isFallFlying()}/{@code isAutoSpinAttack()}/{@code isSwimming()} 的精确语义：1.7.10 均不存在
 * （{@code canCrawl} 里用 {@code !onGround} / 不检查表达，与客户端同一近似）。</li>
 * </ul>
 */
public class LivingEntityCrawl {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;

    public LivingEntityCrawl(EntityLivingBase shooter, ShooterDataHolder data) {
        this.shooter = shooter;
        this.data = data;
    }

    public void crawl(boolean isCrawl) {
        setCrawling(isCrawl && canCrawl());
    }

    public void tickCrawling() {
        if (!data.isCrawling) {
            return;
        }
        // 上游 LivingEntityCrawl.java:28：player.getForcedPose() != Pose.SWIMMING → 姿态被外部改回就退出趴下。
        // 1.7.10 用姿势覆盖表表达，读法与 client/gameplay/LocalPlayerCrawl#tickCrawl 一致。
        boolean poseChanged = shooter instanceof EntityPlayer
            && EntityStateHelper.getForcedPose(shooter) != EntityPose.SWIMMING;
        if (poseChanged || !canCrawl()) {
            setCrawling(false);
        }
    }

    private boolean canCrawl() {
        if (!SyncConfig.ENABLE_CRAWL.get()) {
            return false;
        }
        // 1.7.10 无旁观者模式（isSpectator 恒 false），故 invalidState 只保留死亡与骑乘两项。
        boolean invalidState = !shooter.isEntityAlive() || shooter.isRiding();
        // TODO(port): 上游还检查 shooter.jumping / shooter.isSwimming()。
        // 1.7.10 EntityLivingBase#isJumping 是 protected 且无公开访问器
        // （EntityPlayerSP#movementInput 只在客户端存在，不能用于服务端判定），
        // 1.7.10 也没有游泳状态，这两项检查降级为不检查。
        boolean invalidMovement = !shooter.onGround;
        // TODO(port): 上游还检查 isFallFlying()（鞘翅）与 isAutoSpinAttack()（三叉戟），1.7.10 均不存在。
        boolean incompatiblePose = shooter.isPlayerSleeping();
        if (invalidState || invalidMovement || incompatiblePose) {
            return false;
        }
        // 玩家可以从未持过枪，也可能刚切换物品，以实际主手物品为准。
        ItemStack gunItem;
        if (shooter instanceof EntityPlayer) {
            gunItem = shooter.getHeldItem();
        } else {
            // 1.7.10：Supplier#get() 可能返回 null，统一走 getCurrentGunItem() 归一化
            gunItem = data.getCurrentGunItem();
        }
        if (gunItem != null && gunItem.getItem() instanceof IGun gun) {
            return gun.isCanCrawl(gunItem) && TimelessAPI.getCommonGunIndex(gun.getGunId(gunItem))
                .isPresent();
        }
        return shooter instanceof EntityPlayer && SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get();
    }

    private void setCrawling(boolean crawling) {
        if (data.isCrawling == crawling) {
            return;
        }
        data.isCrawling = crawling;
        // 上游 LivingEntityCrawl.java:59-66：状态变化时设置/清除一次姿态 ——
        // 玩家走 {@code setForcedPose(crawling ? Pose.SWIMMING : null)}（只在当前强制姿势就是 SWIMMING 时才清除，
        // 不覆盖别人设的姿势），其它生物走 {@code setPose(SWIMMING/STANDING)}。
        // 1.7.10 用 mcshim 的姿势覆盖表统一表达两者（写 SWIMMING / 移除条目）。
        //
        // 服务端写这张表是**必需**的：上游 pose 是服务端实体状态，服务端逻辑能直接读到；
        // 本移植的服务端读端是 {@code EntityStateHelper.getPose}——
        // 例如 {@code item/ModernKineticGunScriptAPI#applyRecoilPush}（`:292`）用
        // {@code getPose(player) == SWIMMING} 取「趴下」后坐力系数，不写表就恒为 false（趴下不减后坐力）。
        if (crawling || EntityStateHelper.getForcedPose(shooter) == EntityPose.SWIMMING) {
            EntityStateHelper.setForcedPose(shooter, crawling ? EntityPose.SWIMMING : null);
        }
    }
}
