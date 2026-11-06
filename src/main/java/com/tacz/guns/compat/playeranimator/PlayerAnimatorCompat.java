package com.tacz.guns.compat.playeranimator;

import java.io.File;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.mcshim.EntityPose;
import com.tacz.guns.mcshim.EntityStateHelper;
import com.tacz.guns.mcshim.PreparableReloadListener;

import cpw.mods.fml.common.Loader;

/**
 * Player Animator（{@code playeranimator}）兼容门面。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/playeranimator/}
 * —— 本类（门面）+ {@code AnimationName}（动画名常量，本移植保留）+ {@code animation/*}（8 个文件：
 * {@code AnimationManager}/{@code PlayerAnimationLayer}/{@code RotationAnimation}/
 * {@code PlayerAnimatorAssetManager}/{@code PlayerAnimatorLoader}/{@code PlayerAnimatorModelState}/
 * {@code AnimationDataRegisterFactory}/{@code AdjustmentYRotModifier}），其中绝大多数方法签名里出现
 * {@code dev.kosmx.playerAnim.*} 类型。
 *
 * <p>
 * <b>1.7.10 形态</b>：Player Animator 只支持 1.16.5+（{@code ModifierLayer}/{@code KeyframeAnimationPlayer}/
 * {@code PlayerAnimatorAccess} 全无），1.7.10 <b>没有</b>对应版本。那 8 个纯库绑定实现类已删除
 * （逐条原因见 {@code docs/PORT_STATUS.md}）；本类保留上游全部公开签名，语义分层如下：
 * <ul>
 * <li><b>真逻辑</b>：{@link #canPlayAnimation(EntityLivingBase)} —— 上游这段判断<b>不依赖</b> Player Animator
 * （只判「手上是不是枪 + 姿势是否允许」），因此完整移植（仅把 1.20 的
 * {@code Pose}/{@code onClimbable()}/{@code isSwimming()} 换成 {@link EntityStateHelper} 推导的
 * {@link EntityPose}，见方法注释）。{@code client/animation/third/InnerThirdPersonManager} 正是靠它决定
 * 「走原版第三人称动画」还是「交给 Player Animator」。</li>
 * <li><b>探测 + 静默降级</b>：其余方法在 {@link #isInstalled()} 为 {@code false} 时保持上游的「没装库」分支
 * —— 返回 {@code false}/空操作/{@code null}，绝无硬依赖。</li>
 * </ul>
 *
 * <p>
 * 调用点：{@code client/animation/third/InnerThirdPersonManager#setRotationAnglesHead}、
 * {@code client/animation/third/PlayerModelRenderState}、{@code client/init/ClientSetupEvent}。
 */
public final class PlayerAnimatorCompat {

    /** 上游 4 个动画注册名（保持不变）。 */
    public static ResourceLocation LOWER_ANIMATION = new ResourceLocation(GunMod.MOD_ID, "lower_animation");
    public static ResourceLocation LOOP_UPPER_ANIMATION = new ResourceLocation(GunMod.MOD_ID, "loop_upper_animation");
    public static ResourceLocation ONCE_UPPER_ANIMATION = new ResourceLocation(GunMod.MOD_ID, "once_upper_animation");
    public static ResourceLocation ROTATION_ANIMATION = new ResourceLocation(GunMod.MOD_ID, "rotation");

    private static final String MOD_ID = "playeranimator";
    /** 只用于反射探测：Player Animator 的 Minecraft 侧入口类。 */
    private static final String PROBE_CLASS = "dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry";

    /** 上游是 {@code private static boolean INSTALLED}（由 {@code init()} 赋值）；1.7.10 恒 {@code false}。 */
    public static final boolean LOADED = Loader.isModLoaded(MOD_ID) && probe();

    private PlayerAnimatorCompat() {}

    /** 对应上游 {@code PlayerAnimatorCompat#init()}。 */
    public static void init() {
        if (isInstalled()) {
            // TODO(L2): 出现 1.7.10 版 Player Animator 后，反射接线上游 AnimationDataRegisterFactory.registerData()
            // 与 AnimationManager 的事件总线注册
            GunMod.LOGGER.info("Detected Player Animator, but 1.7.10 has no compatible animation-layer API");
        }
    }

    /** 上游 {@code loadAnimationFromZip(ZipFile, String)}：1.7.10 无库时恒 {@code false}。 */
    public static boolean loadAnimationFromZip(ZipFile zipFile, String zipPath) {
        // TODO(L2): 反射 PlayerAnimationRegistry 序列化入口
        return false;
    }

    /** 上游 {@code loadAnimationFromFile(File)}：1.7.10 无库时空操作。 */
    public static void loadAnimationFromFile(File file) {
        // TODO(L2): 反射 PlayerAnimationRegistry 序列化入口
    }

    /** 上游 {@code clearAllAnimationCache()}：1.7.10 无库时空操作。 */
    public static void clearAllAnimationCache() {
        // TODO(L2): 反射 PlayerAnimatorAssetManager 的缓存在此清空
    }

    /** 上游 {@code hasPlayerAnimator3rd(LivingEntity, GunDisplayInstance)}：无库时恒 {@code false}。 */
    public static boolean hasPlayerAnimator3rd(EntityLivingBase livingEntity, GunDisplayInstance display) {
        // 上游：isInstalled() && livingEntity instanceof AbstractClientPlayer →
        // AnimationManager.hasPlayerAnimator3rd(display)
        // TODO(L2): 反射接线上游 AnimationManager.hasPlayerAnimator3rd(display)，届时把下面的 return 换成真实值
        return false;
    }

    /**
     * 上游 {@code canPlayAnimation(LivingEntity)} —— <b>不依赖</b> Player Animator 的真逻辑，完整移植。
     *
     * <p>
     * 上游源码（{@code tmp/TACZ-1.20.1/.../PlayerAnimatorCompat.java:67-72}）：
     *
     * <pre>
     * return IGun.getIGunOrNull(entity.getMainHandItem()) != null &amp;&amp; entity.getPose() != Pose.SLEEPING
     *     &amp;&amp; !entity.onClimbable()
     *     &amp;&amp; !entity.isSwimming()
     *     &amp;&amp; entity.getPose() != Pose.FALL_FLYING;
     * </pre>
     *
     * 1.7.10 映射：{@code getMainHandItem()} → {@code getHeldItem()}；{@code getPose()} →
     * {@link EntityStateHelper#getPose(EntityLivingBase)}（1.7.10 无姿势系统，见该类的推导规则）；
     * {@code onClimbable()} → {@code isOnLadder()}；{@code isSwimming()} →
     * {@link EntityPose#SWIMMING}（{@code EntityStateHelper} 用「在水里 + 疾跑」推导，与 1.20 的游泳语义最接近）；
     * {@code Pose.FALL_FLYING} 在 1.7.10 恒不出现。
     */
    public static boolean canPlayAnimation(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        if (IGun.getIGunOrNull(entity.getHeldItem()) == null) {
            return false;
        }
        if (entity.isOnLadder()) {
            return false;
        }
        EntityPose pose = EntityStateHelper.getPose(entity);
        return pose != EntityPose.SLEEPING && pose != EntityPose.SWIMMING && pose != EntityPose.FALL_FLYING;
    }

    /** 上游 {@code stopAllAnimation(LivingEntity)}：无库时空操作。 */
    public static void stopAllAnimation(EntityLivingBase livingEntity) {
        if (isInstalled()) {
            // TODO(L2): 反射 AnimationManager.stopAllAnimation(player)
        }
    }

    /** 上游 {@code stopAllAnimation(LivingEntity, int fadeTime)}：无库时空操作。 */
    public static void stopAllAnimation(EntityLivingBase livingEntity, int fadeTime) {
        if (isInstalled()) {
            // TODO(L2): 反射 AnimationManager.stopAllAnimation(player, fadeTime)
        }
    }

    /** 上游 {@code playAnimation(LivingEntity, GunDisplayInstance, float)}：无库时空操作。 */
    public static void playAnimation(EntityLivingBase livingEntity, GunDisplayInstance display, float limbSwingAmount) {
        if (isInstalled()) {
            // TODO(L2): 反射 AnimationManager.playLowerAnimation / playLoopUpperAnimation / playRotationAnimation
        }
    }

    /** 上游 {@code isInstalled()}。 */
    public static boolean isInstalled() {
        return LOADED;
    }

    /**
     * 上游 {@code captureModelState(PlayerModel&lt;?&gt;)} → 1.7.10 用 {@link ModelBiped}（无 {@code PlayerModel} 类）。
     *
     * <p>
     * 返回的 {@link Runnable} 由 {@code client/animation/third/PlayerModelRenderState#close()} 调用以「还原
     * Player Animator 改动过的部件姿态」；1.7.10 无库 → 返回空操作，调用方无需分支。
     */
    public static Runnable captureModelState(ModelBiped model) {
        // TODO(L2): 反射 PlayerAnimatorModelState.capture(model)
        return NO_OP;
    }

    /**
     * 上游 {@code registerReloadListener(Consumer<PreparableReloadListener>)}。
     *
     * <p>
     * 1.20 的 {@code RegisterClientReloadListenersEvent#registerReloadListener} →
     * 1.7.10 侧的监听器注册函数（形参类型是本工程 {@code mcshim} 的
     * {@link PreparableReloadListener}，由 {@code resource/**} 的重载调度器驱动）。无库时空操作。
     */
    public static void registerReloadListener(Consumer<PreparableReloadListener> register) {
        if (isInstalled() && register != null) {
            // TODO(L2): register.accept(PlayerAnimatorAssetManager.get());
        }
    }

    private static final Runnable NO_OP = () -> {};

    /** 反射探测 {@link #PROBE_CLASS}（{@code initialize=false}，不触发对方静态初始化）。 */
    private static boolean probe() {
        try {
            Class.forName(PROBE_CLASS, false, PlayerAnimatorCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
