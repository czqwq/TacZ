package com.tacz.guns.entity.shooter;

import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.luaj.vm2.LuaValue;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.util.time.GunTime;

/**
 * 枪械操作状态数据。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/ShooterDataHolder.java}。
 *
 * <p>
 * <b>1.7.10 移植注意：本类的字段名、可见性、默认值必须与上游逐字一致。</b>
 * 1.7.10 走 Mixin 方案（与上游相同）：{@code com.tacz.guns.mixin.common.LivingEntityMixin} 是
 * {@code @Mixin(EntityLivingBase.class) implements IGunOperator, KnockBackModifier}，内部持有本类实例，
 * 每 tick 直接读写这些 public 字段（{@code reloadStateType} / {@code isBolting} / {@code aimingProgress} /
 * {@code isAiming} / {@code sprintTimeS} / {@code knockbackStrength} / {@code cacheProperty} /
 * {@code currentGunItem} / {@code currentGunId} / {@code gunScriptFailed} / {@code scriptData} /
 * {@code shootCount} / {@code baseTimestamp}）。改名字段就会在 Mixin 里编译失败。
 *
 * <p>
 * 平台替换（相对上游）：仅
 * {@code net.minecraft.resources.ResourceLocation → net.minecraft.util.ResourceLocation}、
 * {@code net.minecraft.world.item.ItemStack → net.minecraft.item.ItemStack}。
 */
public class ShooterDataHolder {

    /**
     * 基时间戳，用于一些需要精密计算时间的场景。目前只有 shoot 使用。
     */
    public long baseTimestamp = GunTime.nowMillis();
    /**
     * 射击时间戳，射击成功时更新，单位 ms。
     * 用于计算射击的冷却时间。
     */
    public long shootTimestamp = -1L;
    public long lastShootTimestamp = -1L;
    /**
     * 近战时间戳，按下刺刀按键时更新，单位 ms
     * 用于计算射击的冷却时间
     */
    public long meleeTimestamp = -1L;
    /**
     * 近战有前摇，这个就是用于前摇的计数器
     * > 0 时：开始前摇计数，每 tick 减一
     * == 0 时：执行刺刀近战
     * < 0 时，默认情况，什么也不做
     */
    public int meleePrepTickCount = -1;
    /**
     * 切枪时间戳，在切枪开始时更新，单位 ms。
     * 用于计算切枪进度。切枪进度完成后，才能进行各种操作。
     */
    public long drawTimestamp = -1L;
    /**
     * 拉栓时间戳，在拉栓开始时更新，单位 ms。
     */
    public long boltTimestamp = -1;
    public boolean isBolting = false;
    /**
     * 瞄准的进度，范围 0 ~ 1
     */
    public float aimingProgress = 0;
    /**
     * 瞄准时间戳，在每个 tick 更新，单位 ms。
     * 用于在每个 tick 计算: 距离上一次更新 aimingProgress 的时长，并依此计算 aimingProgress 的增量。
     */
    public long aimingTimestamp = -1L;
    /**
     * 为 true 时表示正在 执行瞄准 状态，aimingProgress 会在每个 tick 叠加，
     * 为 false 时表示正在 取消瞄准 状态，aimingProgress 会在每个 tick 递减。
     */
    public boolean isAiming = false;
    /**
     * 装弹时间戳，在开始装弹的瞬间更新，单位 ms。
     * 用于在每个 tick 计算: 从开始装弹 到 当前时间点 的时长，并依此计算出换弹的状态和冷却。
     */
    public long reloadTimestamp = -1;
    /**
     * 装填状态的缓存。会在每个 tick 进行更新。
     */
    @Nonnull
    public ReloadState.StateType reloadStateType = ReloadState.StateType.NOT_RELOADING;
    /**
     * 当前操作的枪械物品的 Supplier。在切枪时 (draw 方法) 更新。
     */
    @Nullable
    public Supplier<ItemStack> currentGunItem = null;
    /**
     * draw 时确认的枪械 ID，拿来做简单检查
     */
    @Nullable
    public ResourceLocation currentGunId = null;
    /** 当前持枪期间的脚本发生异常后暂停执行，下一次初始化或 draw 时恢复。 */
    public boolean gunScriptFailed = false;
    /**
     * 缓存当前枪械的收枪时间，以确保下一次切枪的时候使用此时间计算收枪。
     * 此数值不会因 tacz$CurrentGunItem 提供的 ItemStack 改变而改变，因此应当在恰当的时机调用 updatePutAwayTime() 进行更新。
     */
    public float currentPutAwayTimeS = 0;
    /**
     * 与疾跑相关的参数，开镜时会阻止疾跑
     */
    public float sprintTimeS = 0;
    public long sprintTimestamp = -1;
    /**
     * 用来记录子弹击退能力，负数表示使用原版击退
     */
    public double knockbackStrength = -1;
    /**
     * 记录射击数，用以判定曳光弹
     */
    public int shootCount = 0;
    public float chargeProgress = 0f;
    /**
     * 是否处于趴下状态
     */
    public boolean isCrawling = false;
    /**
     * 用于缓存 lua 脚本的数据
     */
    @Nullable
    public LuaValue scriptData = null;

    public long heatTimestamp = -1;
    /**
     * 配件修改过的各种属性缓存
     */
    @Nullable
    public AttachmentCacheProperty cacheProperty = null;

    public void setCurrentGunItem(@Nullable Supplier<ItemStack> gunItemSupplier) {
        currentGunItem = gunItemSupplier;
        // 1.7.10 空栈等价化：上游 1.20.1 这里拿到的是恒非 null 的 ItemStack.EMPTY
        // （ShooterDataHolder.java:139-146 上游同位置），因此调用方从不判 get() 的返回值；
        // 1.7.10 的 supplier 常是 () -> shooter.getHeldItem()，收枪/空手时为 null，
        // 不判空会在这里就 NPE（IGun.getIGunOrNull 本身对 null 安全，只需防 supplier/get() 为 null）。
        ItemStack stack = gunItemSupplier == null ? null : gunItemSupplier.get();
        IGun gun = IGun.getIGunOrNull(stack);
        currentGunId = gun == null ? null : gun.getGunId(stack);
    }

    /**
     * 读取当前枪械物品栈的 1.7.10 空栈安全版本。
     *
     * <p>
     * <b>为什么需要它</b>：上游 1.20.1 的 {@link #currentGunItem} supplier 恒返回
     * {@code ItemStack.EMPTY}（非 null），所以上游各处只判 {@code data.currentGunItem == null}
     * ——那是在判「supplier 有没有被设置」。1.7.10 用 {@code null} 表示空栈，且 supplier 通常是
     * {@code () -> shooter.getHeldItem()}，空手/切枪过渡 tick 里 {@code get()} 返回 null，
     * 紧随其后的 {@code .get().getItem()} 必然 NPE（用户实机崩溃：
     * {@code LivingEntityAim.tickAimingProgress} ← {@code EntityLivingBase.handler$tacz$onTickServerSide}，
     * 服务端每 tick 每玩家的必经路径）。
     *
     * <p>
     * <b>语义等价</b>：把「空栈」的三种 1.7.10 表现（supplier 为 null / {@code get()} 为 null /
     * {@code getItem()==null} 或 {@code stackSize<=0}）统一归一成 {@code null}；
     * 调用方用 {@code if (stack == null)} 判空，即等价于上游的 {@code stack.isEmpty()}。
     *
     * @return 当前枪械物品栈；空手/未持枪/无效栈时返回 {@code null}
     */
    @Nullable
    public ItemStack getCurrentGunItem() {
        Supplier<ItemStack> supplier = this.currentGunItem;
        if (supplier == null) {
            return null;
        }
        ItemStack stack = supplier.get();
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
            return null;
        }
        return stack;
    }

    /** 中止旧枪操作，不调用可能已经属于新枪的脚本，也不重置射击冷却。 */
    public void cancelGunScriptState() {
        reloadTimestamp = -1;
        reloadStateType = ReloadState.StateType.NOT_RELOADING;
        boltTimestamp = -1;
        isBolting = false;
        meleePrepTickCount = -1;
        scriptData = null;
    }

    public void initialData() {
        // 重置各个状态
        shootTimestamp = -1;
        meleeTimestamp = -1;
        meleePrepTickCount = -1;
        isAiming = false;
        aimingProgress = 0;
        reloadTimestamp = -1;
        reloadStateType = ReloadState.StateType.NOT_RELOADING;
        sprintTimestamp = -1;
        sprintTimeS = 0;
        boltTimestamp = -1;
        isBolting = false;
        shootCount = 0;
        chargeProgress = 0f;
        scriptData = null;
        heatTimestamp = -1;
        currentGunId = null;
        gunScriptFailed = false;
    }
}
