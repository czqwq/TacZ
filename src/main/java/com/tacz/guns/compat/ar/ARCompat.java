package com.tacz.guns.compat.ar;

import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.VertexConsumer;

import cpw.mods.fml.common.Loader;

/**
 * Accelerated Rendering（{@code acceleratedrendering}）兼容门面。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/ar/}
 * —— {@code ARCompat}（门面，按 {@code LOADED} 分派）+ {@code ARCompatImpl}（真正调用 AR API）+
 * {@code AcceleratedBeamRenderer} + {@code BeamRenderContext}。
 *
 * <p>
 * <b>1.7.10 形态</b>：Accelerated Rendering 是 1.20 专属模组（建立在 1.20 的
 * {@code VertexConsumer}/{@code RenderType}/{@code IBufferGraph} + VAO/网格缓存管线之上），
 * 1.7.10 <b>没有</b>对应版本，也没有可反射的等价 API（1.7.10 走固定管线 + {@code Tessellator}）。
 * 因此 {@code ARCompatImpl} / {@code AcceleratedBeamRenderer} / {@code BeamRenderContext}
 * 三个纯 AR 绑定的内部实现类已删除（逐条原因见 {@code docs/PORT_STATUS.md}）。
 *
 * <p>
 * 本类保留上游<b>同名同签名</b>的门面，语义为「探测 + 静默降级」：
 * <ul>
 * <li>{@link #LOADED} = 模组在 <b>且</b> AR 核心类可加载。1.7.10 上恒为 {@code false}。</li>
 * <li>所有查询方法返回中性值（{@code false} / 无操作的 {@link Runnable}），
 * 所有设置方法空操作 —— 调用方（{@code client/model/functional/BeamRenderer}、
 * {@code mixin/client/ar/BedrockPartMixin}）无需改动即自动回退到非加速路径。</li>
 * </ul>
 *
 * <p>
 * 若日后出现 1.7.10 版 AR，接线方式：把上游 {@code ARCompatImpl} 的同名方法按
 * {@code Class.forName(...).getMethod(...)} 反射调用即可，本类公开签名不需要改。
 */
public final class ARCompat {

    public static final String MOD_ID = "acceleratedrendering";
    /** 只用于反射探测：AR 的核心特性类（上游 {@code ARCompatImpl} 直接引用它）。 */
    private static final String PROBE_CLASS = "com.github.argon4w.acceleratedrendering.core.CoreFeature";

    /** AR 是否可用。恒为 {@code false} 时本类所有方法都是中性值 / 空操作。 */
    public static final boolean LOADED = Loader.isModLoaded(MOD_ID) && probe();

    private static boolean logged;

    private ARCompat() {}

    /** 对应上游 {@code ARCompat#init()}（在 {@code FMLClientSetupEvent} 的 {@code enqueueWork} 里调用）。 */
    public static void init() {
        if (!LOADED) {
            return;
        }
        // 1.7.10 侧没有可反射的等价 API（见类注释）；这里只提示「检测到 AR，但尚未接线」，
        // TODO(L2): 出现 1.7.10 版 AR 后在此反射初始化上游 ARCompatImpl 承接的 CoreFeature 等。
        if (!logged) {
            logged = true;
            GunMod.LOGGER
                .info("Detected Accelerated Rendering, but no 1.7.10 build exists yet; acceleration stays off");
        }
    }

    /** 对应上游 {@code ARCompatImpl#shouldAccelerate()}。1.7.10 上恒为 {@code false}（走非加速路径）。 */
    public static boolean shouldAccelerate() {
        return false;
    }

    /** 对应上游 {@code ARCompatImpl#isAccelerated(VertexConsumer)}。1.7.10 上没有加速顶点消费者。 */
    public static boolean isAccelerated(VertexConsumer vertexConsumer) {
        return false;
    }

    public static void setRenderingLevel() {
        // 空操作：1.7.10 无 AR 的 render level 概念
    }

    public static void resetRenderingLevel() {
        // 空操作
    }

    public static void setRenderLayer(int layer) {
        // 空操作：1.7.10 无「按层重排绘制顺序」
    }

    public static void setRenderBeforeFunction(Runnable runnable) {
        // 空操作
    }

    public static void setRenderAfterFunction(Runnable runnable) {
        // 空操作
    }

    public static Runnable getRenderBeforeFunction() {
        return NO_OP;
    }

    public static Runnable getRenderAfterFunction() {
        return NO_OP;
    }

    public static void resetRenderLayer() {
        // 空操作
    }

    public static void resetRenderBeforeFunction() {
        // 空操作
    }

    public static void resetRenderAfterFunction() {
        // 空操作
    }

    public static void disableAcceleration() {
        // 空操作
    }

    public static void resetAcceleration() {
        // 空操作
    }

    /**
     * 对应上游 {@code ARCompat#renderLaser(...)}（镭射束的加速绘制）。
     *
     * <p>
     * 1.7.10 侧由 {@code BeamRenderer} 的非加速分支直接绘制，本方法空操作。
     * 上游 `ARCompat` 之所以把 {@code AcceleratedBeamRenderer.INSTANCE} 藏在 {@code ARCompatImpl} 后面，
     * 正是为了避免「类意外加载」——本实现沿用同一约束：不引用任何 AR 类型。
     */
    public static void renderLaser(VertexConsumer extension, float z, float width, boolean fadeOut, PoseStack poseStack,
        int color) {
        // 空操作：1.7.10 无 AR 加速管线
    }

    private static final Runnable NO_OP = () -> {};

    /** 反射探测 {@link #PROBE_CLASS}（{@code initialize=false}，不触发对方静态初始化）。 */
    private static boolean probe() {
        try {
            Class.forName(PROBE_CLASS, false, ARCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
