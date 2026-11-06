package com.tacz.guns.client.animation.statemachine;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovementInput;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.util.LuaNbtAccessor;
import com.tacz.guns.client.gameplay.ClientAmmoCount;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.util.time.GunTime;

/**
 * 枪械动画状态机的上下文（暴露给 Lua 脚本）。
 *
 * <p>
 * 移植说明（1.20.1 → 1.7.10）：{@code LivingEntity} → {@link EntityLivingBase}；{@code cameraEntity} →
 * {@code renderViewEntity}；{@code onGround()} → public 字段 {@code onGround}；{@code isCrouching()} →
 * {@code isSneaking()}；{@code walkDist} → {@code distanceWalkedModified}；{@code player.input.*} →
 * {@link MovementInput} 的 {@code moveForward}/{@code moveStrafe}/{@code jump}；
 * {@code ForgeCapabilities.ITEM_HANDLER} 不存在 ⇒ 直接读 {@link IInventory}。
 */
@SuppressWarnings("unused")
public class GunAnimationStateContext extends ItemAnimationStateContext {

    private ItemStack currentGunItem;
    private IGun iGun;
    private GunDisplayInstance display;
    private GunData gunData;
    private float walkDistAnchor = 0f;
    private LuaNbtAccessor nbtUtil;

    private <T> Optional<T> processGunData(BiFunction<IGun, GunDisplayInstance, T> processor) {
        // 1.20 由资源重载保证非 null；1.7.10「索引/展示数据缺失」是常态 ⇒ gunData 也必须作为守卫，
        // 否则本方法内直接解引用 gunData 的 getter 会 NPE。
        if (iGun != null && display != null && gunData != null) {
            return Optional.ofNullable(processor.apply(iGun, display));
        }
        return Optional.empty();
    }

    private <T> Optional<T> processGunOperator(Function<IClientPlayerGunOperator, T> processor) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            return Optional.ofNullable(processor.apply(IClientPlayerGunOperator.fromLocalPlayer(player)));
        }
        return Optional.empty();
    }

    private <T> Optional<T> processRemoteGunOperator(Function<IGunOperator, T> processor) {
        return processCameraEntity(entity -> {
            if (entity instanceof EntityLivingBase) {
                IGunOperator gunOperator = IGunOperator.fromLivingEntity((EntityLivingBase) entity);
                return processor.apply(gunOperator);
            }
            return null;
        });
    }

    private <T> Optional<T> processCameraEntity(Function<Entity, T> processor) {
        // 1.20: Minecraft.getMinecraft().cameraEntity；1.7.10: Minecraft#renderViewEntity
        Entity entity = Minecraft.getMinecraft().renderViewEntity;
        if (entity != null) {
            return Optional.ofNullable(processor.apply(entity));
        }
        return Optional.empty();
    }

    /**
     * 获取枪膛中是否有子弹。
     * 
     * @return 枪膛中是否有子弹。如果是开膛待击的枪械，则此方法返回 false。
     */
    public boolean hasBulletInBarrel() {
        return processGunData((iGun, gunIndex) -> {
            Bolt boltType = gunData.getBolt();
            return boltType != Bolt.OPEN_BOLT && iGun.hasBulletInBarrel(currentGunItem);
        }).orElse(false);
    }

    public boolean isOverHeat() {
        // iGun/gunData 在 1.7.10 侧可能为 null（索引未就绪），按「未知即未过热」降级。
        if (gunData == null || iGun == null) {
            return false;
        }
        return gunData.getHeatData() != null && iGun.isOverheatLocked(currentGunItem);
    }

    public float getHeatProgress() {
        if (gunData == null || iGun == null) {
            return 0f;
        }
        return gunData.getHeatData() != null ? Mth.clamp(
            iGun.getHeatAmount(currentGunItem) / gunData.getHeatData()
                .getHeatMax(),
            0f,
            1f) : 0f;
    }

    /**
     * 获取枪械的射击间隔，单位毫秒
     * 
     * @return 射击间隔
     */
    public long getShootInterval() {
        // 同 isOverHeat：1.7.10 侧 iGun/gunData 可能为 null（索引未就绪），未知按 0 处理
        if (iGun == null || gunData == null) {
            return 0L;
        }
        return processCameraEntity(entity -> {
            if (entity instanceof EntityLivingBase) {
                EntityLivingBase livingEntity = (EntityLivingBase) entity;
                FireMode fireMode = iGun.getFireMode(currentGunItem);
                if (fireMode == FireMode.BURST) {
                    long coolDown = (long) (gunData.getBurstData()
                        .getMinInterval() * 1000f);
                    return Math.max(coolDown, 0L);
                }
                long coolDown = gunData.getShootInterval(livingEntity, fireMode, currentGunItem);
                return Math.max(coolDown, 0L);
            }
            return 0L;
        }).orElse(0L);
    }

    /**
     * 返回上次射击的枪械时间，单位为毫秒。此值在切枪时会重置为 -1。
     * 
     * @return 上次射击的 timestamp，在切枪时会重置为 -1。
     */
    public long getLastShootTimestamp() {
        return processGunOperator(operator -> operator.getDataHolder().clientLastShootTimestamp).orElse(-1L);
    }

    /**
     * 获取当前枪械时间，单位毫秒。单人游戏暂停时此时间不前进。
     * 此时间不是 Unix 时间戳；如需真实系统时间，使用 {@link #getRealSystemTimestamp()}。
     * 
     * @return 当前枪械时间
     */
    public long getCurrentTimestamp() {
        return GunTime.nowMillis();
    }

    /** 获取真实系统时间（Unix 毫秒时间戳），不受游戏暂停影响。 */
    public long getRealSystemTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 调整射击间隔。(仅在客户端表现)
     * 
     * @param alpha 需要加上或减少的射击间隔，单位为毫秒。正数即增加射击间隔，负数则是减少。
     */
    public void adjustClientShootInterval(long alpha) {
        processGunOperator(operator -> {
            long timestamp = operator.getDataHolder().clientShootTimestamp;
            operator.getDataHolder().clientShootTimestamp = timestamp + alpha;
            return null;
        });
    }

    /**
     * 获取弹匣中的备弹数。背包直读枪械返回背包弹药数。
     * 
     * @return 不计算已在枪管中的弹药。
     */
    public int getAmmoCount() {
        return processGunData((iGun, gunIndex) -> {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (iGun.useInventoryAmmo(currentGunItem) && player != null && player.getHeldItem() == currentGunItem) {
                return ClientAmmoCount.getCached(player, currentGunItem, iGun);
            }
            return iGun.getCurrentAmmoCount(currentGunItem);
        }).orElse(0);
    }

    /**
     * 获取枪械弹匣的最大备弹数。
     * 
     * @return 返回枪械弹匣的最大备弹数，不计算已在枪管中的弹药。
     */
    public int getMaxAmmoCount() {
        return processGunData(
            (iGun, gunIndex) -> AttachmentDataUtils.getAmmoCountWithAttachment(currentGunItem, gunData)).orElse(0);
    }

    /**
     * 检查玩家身上（或者虚拟备弹）是否有弹药可以消耗，通常用于循环换弹的打断。
     * 创造模式的玩家会直接返回 true
     * 
     * @return 玩家身上（或者虚拟备弹）是否有弹药可以消耗
     */
    public boolean hasAmmoToConsume() {
        if (!processRemoteGunOperator(IGunOperator::needCheckAmmo).orElse(true)) {
            return true;
        }
        // 1.7.10 侧 iGun 可能为 null（空手/未就绪的索引），未知按「无弹可消耗」处理
        if (iGun == null) {
            return false;
        }
        if (iGun.useDummyAmmo(currentGunItem)) {
            return iGun.getDummyAmmoAmount(currentGunItem) > 0;
        }
        return processCameraEntity(this::hasAmmoInInventoryOf).orElse(false);
    }

    /**
     * 1.7.10 替身：上游用 {@code ForgeCapabilities.ITEM_HANDLER} 遍历背包，这里改为直接读 {@link IInventory}。
     */
    private boolean hasAmmoInInventoryOf(Entity entity) {
        IInventory inv = null;
        if (entity instanceof EntityPlayer) {
            inv = ((EntityPlayer) entity).inventory;
        } else if (entity instanceof IInventory) {
            inv = (IInventory) entity;
        }
        if (inv == null) {
            return false;
        }
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack checkAmmoStack = inv.getStackInSlot(i);
            if (checkAmmoStack == null || checkAmmoStack.getItem() == null) {
                continue;
            }
            if (checkAmmoStack.getItem() instanceof IAmmo) {
                IAmmo iAmmo = (IAmmo) checkAmmoStack.getItem();
                if (iAmmo.isAmmoOfGun(currentGunItem, checkAmmoStack)) {
                    return true;
                }
            }
            if (checkAmmoStack.getItem() instanceof IAmmoBox) {
                IAmmoBox iAmmoBox = (IAmmoBox) checkAmmoStack.getItem();
                if (iAmmoBox.isAmmoBoxOfGun(currentGunItem, checkAmmoStack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取枪械扩容等级。
     * 
     * @return 扩容等级，范围 0 ~ 3。0 表示没有安装扩容弹匣，1 ~ 3 表示安装了扩容等级 1 ~ 3 的扩容弹匣
     */
    public int getMagExtentLevel() {
        return processGunData((iGun, gunIndex) -> AttachmentDataUtils.getMagExtendLevel(currentGunItem, gunData))
            .orElse(0);
    }

    /**
     * 获取枪械当前的开火模式。
     * 
     * @return FireMode 枚举的 ordinal 值
     */
    public int getFireMode() {
        return processGunData(
            (iGun, gunIndex) -> iGun.getFireMode(currentGunItem)
                .ordinal()).orElse(0);
    }

    /**
     * 获取持枪玩家的瞄准进度。
     * 
     * @return 持枪玩家的瞄准进度，取值范围：0 ~ 1。
     *         0 代表没有喵准，1 代表喵准完成。
     */
    public float getAimingProgress() {
        return processGunOperator(operator -> operator.getClientAimingProgress(partialTicks)).orElse(0f);
    }

    /**
     * 获取玩家当前是否在瞄准。如果正在瞄准，aiming progress 会增加，否则减少。
     * 
     * @return 玩家当前是否在瞄准
     */
    public boolean isAiming() {
        return processGunOperator(IClientPlayerGunOperator::isAim).orElse(false);
    }

    /**
     * 获取玩家的射击冷却。
     * 
     * @return 玩家的射击冷却，单位为毫秒(ms)。
     */
    public long getShootCoolDown() {
        return processGunOperator(IClientPlayerGunOperator::getClientShootCoolDown).orElse(0L);
    }

    /**
     * 获取玩家的换弹状态
     * 
     * @return 玩家的换弹状态
     */
    public int getReloadStateType() {
        return processCameraEntity(entity -> {
            if (entity instanceof EntityLivingBase) {
                return IGunOperator.fromLivingEntity((EntityLivingBase) entity)
                    .getSynReloadState()
                    .getStateType()
                    .ordinal();
            }
            return ReloadState.StateType.NOT_RELOADING.ordinal();
        }).orElse(ReloadState.StateType.NOT_RELOADING.ordinal());
    }

    // 1.7.10 MovementInput 只有 moveForward/moveStrafe/jump/sneak，
    // 因此 up = moveForward > 0、down = moveForward < 0、left = moveStrafe > 0、right = moveStrafe < 0。
    private static MovementInput currentInput() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        return player == null ? null : player.movementInput;
    }

    /**
     * 获取玩家的按键输入是否为上。
     * 
     * @return 玩家的按键输入是否为上 (对应着移动中的前进按键，如 W)
     */
    public boolean isInputUp() {
        MovementInput input = currentInput();
        return input != null && input.moveForward > 0;
    }

    /**
     * 获取玩家的按键输入是否为下。
     * 
     * @return 玩家的按键输入是否为下 (对应着移动中的后退按键，如 S)
     */
    public boolean isInputDown() {
        MovementInput input = currentInput();
        return input != null && input.moveForward < 0;
    }

    /**
     * 获取玩家的按键输入是否为左。
     * 
     * @return 玩家的按键输入是否为左 (对应着移动中的左移按键，如 A)
     */
    public boolean isInputLeft() {
        MovementInput input = currentInput();
        return input != null && input.moveStrafe > 0;
    }

    /**
     * 获取玩家的按键输入是否为右。
     * 
     * @return 玩家的按键输入是否为右 (对应着移动中的右移按键，如 D)
     */
    public boolean isInputRight() {
        MovementInput input = currentInput();
        return input != null && input.moveStrafe < 0;
    }

    /**
     * 获取玩家的按键输入是否为跳跃。
     * 
     * @return 玩家的按键输入是否为跳跃 (对应着移动中的跳跃按键，如 Space)
     */
    public boolean isInputJumping() {
        MovementInput input = currentInput();
        return input != null && input.jump;
    }

    /**
     * 获取玩家当前是否正在匍匐
     * 
     * @return 玩家当前是否正在匍匐
     */
    public boolean isCrawl() {
        return processGunOperator(IClientPlayerGunOperator::isCrawl).orElse(false);
    }

    /**
     * 获取玩家是否接触地面
     * 
     * @return 玩家是否接触地面
     */
    public boolean isOnGround() {
        // 1.20: Entity#onGround()；1.7.10: Entity#onGround 是 public 字段
        return processCameraEntity(entity -> entity.onGround).orElse(false);
    }

    /**
     * 获取 玩家是否蹲伏
     * 
     * @return 玩家是否蹲伏
     */
    public boolean isCrouching() {
        // 1.20: Entity#isCrouching()；1.7.10: Entity#isSneaking()
        return processCameraEntity(Entity::isSneaking).orElse(false);
    }

    /**
     * 获取 玩家当前是否应该斜握枪械
     * 需要同时满足蹲伏和枪械允许斜握
     * 
     * @return 玩家当前是否应该斜握枪械
     */
    public boolean shouldSlide() {
        // gunData 在 1.7.10 侧可能为 null（索引未就绪），未知按「不斜握」处理
        return gunData != null && processCameraEntity(e -> e.isSneaking() && gunData.canSlide()).orElse(false);
    }

    /**
     * 在玩家当前的行走距离打上锚点。此后，getWalkDist() 将返回与此锚点的相对值
     */
    public void anchorWalkDist() {
        processCameraEntity(entity -> {
            walkDistAnchor = entity.distanceWalkedModified
                + (entity.distanceWalkedModified - entity.prevDistanceWalkedModified) * partialTicks;
            return null;
        });
    }

    /**
     * 获取与锚点相对的行走距离。如果没有打锚点，则直接获取行走距离。
     * 
     * @return 与锚点相对的行走距离。如果没有打锚点，则直接返回行走距离。
     */
    public float getWalkDist() {
        return processCameraEntity(entity -> {
            float currentWalkDist = entity.distanceWalkedModified
                + (entity.distanceWalkedModified - entity.prevDistanceWalkedModified) * partialTicks;
            return currentWalkDist - walkDistAnchor;
        }).orElse(0f);
    }

    /**
     * 从指定序号的抛壳窗弹出一枚弹壳
     * 
     * @param index 抛壳窗序号
     */
    public void popShellFrom(int index) {
        // display 在 1.7.10 侧可能为 null（索引未就绪 / display 解析失败），直接跳过避免 NPE。
        if (display != null && display.getShellEjection() != null) {
            Vector3f velocity = display.getShellEjection()
                .getRandomVelocity();
            display.getExternalShellRenderer()
                .addShell(index, velocity);
            BedrockGunModel gunModel = display.getGunModel();
            if (gunModel != null) {
                ShellRender shellRender = gunModel.getShellRender(index);
                if (shellRender != null) {
                    shellRender.addShell(velocity);
                }

                Pair<BedrockGunModel, ResourceLocation> lod = display.getLodModel();
                if (lod != null) {
                    ShellRender lodShell = lod.getLeft()
                        .getShellRender(index);
                    if (lodShell != null) {
                        lodShell.addShell(velocity);
                    }
                }
            }
        }
    }

    /**
     * 获取在枪械 display 中声明的状态机参数
     *
     * @return 状态机参数表
     */
    public LuaTable getStateMachineParams() {
        // display 可能为 null（见 popShellFrom 的说明），返回空表让脚本读到 nil 而不是崩
        LuaTable param = display == null ? null : display.getStateMachineParam();
        return param == null ? new LuaTable() : param;
    }

    /**
     * 获取当前枪械物品的 NBT 数据访问器。<br/>
     * 注意，你不应该在客户端侧修改 NBT 数据，这可能会导致与服务端的数据不一致。<br/>
     * 你应该确保在状态机脚本内仅进行读操作
     * 
     * @return NBT 数据访问器
     */
    public LuaNbtAccessor getNbtAccessor() {
        return nbtUtil;
    }

    /**
     * 获取枪械的配件 ID
     *
     * @return 配件 ID, 如果类型错误或者对应的配件不存在则返回空配件 ID 'tacz:empty'
     */
    public String getAttachment(String type) {
        // iGun 可能为 null（空手 / 索引未就绪），与「类型错误」同路径返回空配件 ID。
        if (iGun == null) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID.toString();
        }
        try {
            AttachmentType t = AttachmentType.valueOf(type);
            return iGun.getAttachmentId(currentGunItem, t)
                .toString();
        } catch (IllegalArgumentException e) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID.toString();
        }
    }

    /**
     * 获取当前的蓄力进度
     * 
     * @return 当前的蓄力进度
     */
    public float getChargeProgress() {
        return processGunOperator(IClientPlayerGunOperator::getChargeProgress).orElse(0f);
    }

    /**
     * 获取当前枪械的最大蓄力值
     * 
     * @return 当前枪械的最大蓄力值
     */
    public float getMaxCharge() {
        return processGunData((iGun, gunIndex) -> {
            ChargeData chargeData = gunData.getChargeData(iGun.getFireMode(currentGunItem));
            return chargeData != null ? chargeData.getMaxCharge() : 0f;
        }).orElse(0f);
    }

    /**
     * 获取当前枪械的蓄力触发阈值(仅hold模式有效)
     * 
     * @return 当前枪械的蓄力触发阈值
     */
    public float getChargeThreshold() {
        return processGunData((iGun, gunIndex) -> {
            ChargeData chargeData = gunData.getChargeData(iGun.getFireMode(currentGunItem));
            return chargeData != null ? chargeData.getFireThreshold() : 0f;
        }).orElse(0f);
    }

    /**
     * 获取当前是否正在蓄力
     * 
     * @return 当前是否正在蓄力
     */
    public boolean isCharging() {
        return processGunOperator(IClientPlayerGunOperator::isCharging).orElse(false);
    }

    /**
     * 状态机脚本请不要调用此方法。此方法用于状态机更新时设置当前的物品对象。
     *
     * <p>
     * 切到空手 / 非枪时 {@code display}/{@code gunData}/{@code nbtUtil} 必须显式清空：{@code iGun == null}
     * 只挡住 {@link #processGunData} 这条路径，挡不住直接读字段的 getter（{@link #isOverHeat()}、
     * {@link #getHeatProgress()}、{@link #shouldSlide()}、{@link #popShellFrom(int)} 等），会拿到上一把枪的
     * 陈旧值；{@code nbtUtil} 不清则 {@link #getNbtAccessor()} 读到上一把枪的 NBT。
     */
    public void setCurrentGunItem(ItemStack currentGunItem) {
        this.currentGunItem = currentGunItem;
        this.iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun != null) {
            display = TimelessAPI.getGunDisplay(currentGunItem)
                .orElse(null);
            gunData = TimelessAPI.getClientGunIndex(iGun.getGunId(currentGunItem))
                .map(ClientGunIndex::getGunData)
                .orElse(null);
        } else {
            // 切到空手 / 非枪 → 显式清空，否则直接读字段的 getter 会拿到上一把枪的陈旧值。
            display = null;
            gunData = null;
        }
        // 1.20: hasTag()/getTag()；1.7.10: hasTagCompound()/getTagCompound()
        if (currentGunItem != null && currentGunItem.hasTagCompound()) {
            nbtUtil = new LuaNbtAccessor(currentGunItem.getTagCompound());
        } else {
            // 无 NBT 时也要清，否则 getNbtAccessor() 读到上一把枪的 tag。
            nbtUtil = null;
        }
    }
}
