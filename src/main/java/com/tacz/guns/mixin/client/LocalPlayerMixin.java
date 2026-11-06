package com.tacz.guns.mixin.client;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.client.gameplay.LocalPlayerAim;
import com.tacz.guns.client.gameplay.LocalPlayerBolt;
import com.tacz.guns.client.gameplay.LocalPlayerCrawl;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gameplay.LocalPlayerDraw;
import com.tacz.guns.client.gameplay.LocalPlayerFireSelect;
import com.tacz.guns.client.gameplay.LocalPlayerInspect;
import com.tacz.guns.client.gameplay.LocalPlayerMelee;
import com.tacz.guns.client.gameplay.LocalPlayerReload;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.tacz.guns.client.gameplay.LocalPlayerSprint;
import com.tacz.guns.client.gameplay.LocalPlayerUnload;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/LocalPlayerMixin.java}。
 *
 * <p>
 * <b>这是客户端枪械操作的总入口</b>：让 {@code EntityPlayerSP} 实现
 * {@link IClientPlayerGunOperator}，从而 {@code IClientPlayerGunOperator#fromLocalPlayer} 的
 * <b>裸强转</b>（{@code api/client/gameplay/IClientPlayerGunOperator.java:22-24}）不会抛
 * {@code ClassCastException}。没有这个 Mixin，第一次开火 / 瞄准 / 切枪就会崩。
 *
 * <p>
 * <b>1.7.10 等价改写</b>（对照 {@code build/rfg/minecraft-src/java/net/minecraft/client/entity/EntityPlayerSP.java}）：
 * <table border="1">
 * <tr>
 * <th>上游</th>
 * <th>1.7.10</th>
 * <th>说明</th>
 * </tr>
 * <tr>
 * <td>{@code net.minecraft.client.player.LocalPlayer}</td>
 * <td>{@link EntityPlayerSP}</td>
 * <td>客户端玩家实体</td>
 * </tr>
 * <tr>
 * <td>{@code tick()} HEAD</td>
 * <td>{@code onLivingUpdate()} HEAD（第 108 行）</td>
 * <td>1.7.10 的 {@code EntityPlayerSP} <b>没有覆写 {@code onUpdate()}</b>，
 * 每 tick 的客户端逻辑入口是 {@code onLivingUpdate}；与
 * {@code mixin/common/LivingEntityMixin} 用 {@code onLivingUpdate} 承接上游 {@code LivingEntity#tick}
 * 的做法保持一致</td>
 * </tr>
 * <tr>
 * <td>{@code aiStep()} 里 {@code LocalPlayer#setSprinting(Z)} 的 {@code @WrapOperation}</td>
 * <td>{@code onLivingUpdate} 里 {@code EntityPlayerSP#setSprinting(Z)} 的 {@code @Redirect}</td>
 * <td>1.7.10 用核心 Mixin 的 {@code @Redirect} 替代 MixinExtras；见下方「sprint 语义」</td>
 * </tr>
 * <tr>
 * <td>{@code respawn()} RETURN</td>
 * <td><i>无对应</i></td>
 * <td>见下方「respawn」</td>
 * </tr>
 * <tr>
 * <td>{@code player.level().isClientSide()}</td>
 * <td>{@code player.worldObj.isRemote}</td>
 * <td>—</td>
 * </tr>
 * <tr>
 * <td>{@code ItemStack.EMPTY}</td>
 * <td>{@code null}</td>
 * <td>1.7.10 空栈约定（见 PORT_STATUS「api 跨包契约」1）</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>sprint 语义</b>：{@code EntityPlayerSP#onLivingUpdate} 里有 <b>4 处</b>
 * {@code this.setSprinting(...)}（第 116/217/223/228 行），{@code @Redirect} 会对全部 4 处生效。
 * 这是安全的：{@code getProcessedSprintStatus(false)} 恒为 {@code false}
 * （见 {@code LocalPlayerSprint}：只有「未瞄准 && 未换弹 && !stopSprint」才可能返回 {@code true}），
 * 所以两处 {@code setSprinting(false)} 的语义不变，真正被改写的是两处
 * {@code setSprinting(true)} —— 正是上游要拦的那条路径。
 * 另外上游「用原始输入打断换弹」保留为：{@code if (sprinting) tac$reload.cancelReload();}，
 * 这里的 {@code sprinting} 是<b>处理前</b>的原始值。
 *
 * <p>
 * <b>respawn 降级</b>：1.7.10 客户端没有 {@code LocalPlayer#respawn()}；玩家重生时
 * {@code NetHandlerPlayClient} 会<b>新建一个 {@code EntityPlayerSP} 实例</b>，
 * 而本 Mixin 的 {@code tac$data}/{@code tac$aim}/... 都是实例字段（Mixin 把它们注入到构造器里初始化），
 * 新实例天然是全新状态 —— 与上游「重生后 reset」等价，因此不需要这条注入。
 * 唯一行为差异：上游在重置后会额外调一次 {@code draw(ItemStack.EMPTY)} 通知「手上没有物品」，
 * 1.7.10 没有对应时机；由于 {@code LocalPlayerDraw} 在下一个 tick 会按当前手持物重新判定，
 * 影响不超过 1 tick。
 *
 * <p>
 * <b>生效状态</b>：完全生效（接口实现 + 每 tick 驱动 + sprint 拦截）。
 */
@SuppressWarnings("all")
@Mixin(EntityPlayerSP.class)
public abstract class LocalPlayerMixin implements IClientPlayerGunOperator {

    @Unique
    private final EntityPlayerSP tac$player = (EntityPlayerSP) (Object) this;
    @Unique
    private final LocalPlayerDataHolder tac$data = new LocalPlayerDataHolder(tac$player);
    @Unique
    private final LocalPlayerAim tac$aim = new LocalPlayerAim(tac$data, tac$player);
    @Unique
    private final LocalPlayerCrawl tac$crawl = new LocalPlayerCrawl(tac$player);
    @Unique
    private final LocalPlayerBolt tac$bolt = new LocalPlayerBolt(tac$data, tac$player);
    @Unique
    private final LocalPlayerDraw tac$draw = new LocalPlayerDraw(tac$data, tac$player);
    @Unique
    private final LocalPlayerFireSelect tac$fireSelect = new LocalPlayerFireSelect(tac$data, tac$player);
    @Unique
    private final LocalPlayerMelee tac$melee = new LocalPlayerMelee(tac$data, tac$player);
    @Unique
    private final LocalPlayerInspect tac$inspect = new LocalPlayerInspect(tac$data, tac$player);
    @Unique
    private final LocalPlayerReload tac$reload = new LocalPlayerReload(tac$data, tac$player);
    @Unique
    private final LocalPlayerUnload tac$unload = new LocalPlayerUnload(tac$data, tac$player);
    @Unique
    private final LocalPlayerShoot tac$shoot = new LocalPlayerShoot(tac$data, tac$player);
    @Unique
    private final LocalPlayerSprint tac$sprint = new LocalPlayerSprint(tac$data, tac$player);

    @Override
    public ShootResult shoot() {
        if (tac$data.isTimedStateLocked()) {
            return ShootResult.COOL_DOWN;
        }
        tac$data.autoReloadSuppressed = false;
        tac$reload.cancelReload();
        return tac$shoot.shoot();
    }

    @Override
    public void draw(ItemStack lastItem) {
        tac$draw.draw(lastItem);
    }

    @Override
    public void bolt() {
        tac$bolt.bolt();
    }

    @Override
    public void reload() {
        if (tac$data.isTimedStateLocked()) {
            return;
        }
        tac$data.autoReloadSuppressed = false;
        tac$reload.reload();
    }

    @Override
    public void unloadAmmo() {
        tac$unload.unloadAmmo();
    }

    @Override
    public void inspect() {
        tac$inspect.inspect();
    }

    @Override
    public void fireSelect() {
        tac$fireSelect.fireSelect();
    }

    @Override
    public void melee() {
        tac$melee.melee();
    }

    @Override
    public void aim(boolean isAim) {
        tac$aim.aim(isAim);
    }

    @Override
    public boolean isCrawl() {
        return tac$crawl.isCrawling();
    }

    @Override
    public LocalPlayerDataHolder getDataHolder() {
        return tac$data;
    }

    @Override
    public void crawl(boolean isCrawl) {
        tac$crawl.crawl(isCrawl);
    }

    @Override
    public float getClientAimingProgress(float partialTicks) {
        return tac$aim.getClientAimingProgress(partialTicks);
    }

    @Override
    public long getClientShootCoolDown() {
        return tac$shoot.getClientShootCoolDown();
    }

    /** 1.7.10 对应上游 {@code LocalPlayer#tick} HEAD 注入（见类 javadoc 的方法映射表）。 */
    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    public void onTickClientSide(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (player.worldObj.isRemote) {
            tac$aim.tickAimingProgress();
            tac$crawl.tickCrawl();
            tac$data.tickStateLock();
            tac$bolt.tickAutoBolt();
            player.setSprinting(tac$sprint.getProcessedSprintStatus(player.isSprinting()));
        }
    }

    /**
     * 1.7.10 对应上游 {@code aiStep} 里 {@code LocalPlayer#setSprinting(Z)} 的 {@code @WrapOperation}。
     *
     * <p>
     * {@code @Redirect} 会覆盖 {@code EntityPlayerSP#onLivingUpdate} 里全部 4 处
     * {@code setSprinting} 调用；语义安全性与理由见类 javadoc「sprint 语义」。
     */
    @Redirect(
        method = "onLivingUpdate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;setSprinting(Z)V"))
    public void swapSprintStatus(EntityPlayerSP player, boolean sprinting) {
        if (sprinting) { // 用原始的输入尝试打断换弹
            tac$reload.cancelReload();
        }
        player.setSprinting(tac$sprint.getProcessedSprintStatus(sprinting));
    }

    @Override
    public boolean isAim() {
        return tac$aim.isAim();
    }

    @Override
    public void resetDraw() {
        tac$draw.readyToDraw = false;
    }

    @Override
    public boolean isReadyToDraw() {
        return tac$draw.readyToDraw;
    }

    @Override
    public boolean chargeShoot(boolean isCharge) {
        if (tac$data.isTimedStateLocked()) {
            return false;
        }
        if (isCharge) {
            tac$data.autoReloadSuppressed = false;
        }
        boolean canShoot = tac$shoot.chargeShoot(isCharge);
        if (isCharge && !canShoot) {
            // 蓄力检查拒绝开火时仍响应装弹中断；允许开火时由 shoot() 处理。
            tac$reload.cancelReload();
        }
        return canShoot;
    }

    @Override
    public float getChargeProgress() {
        return tac$data.chargeProgress;
    }

    @Override
    public boolean isCharging() {
        return tac$data.isCharging;
    }
}
