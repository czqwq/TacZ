package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tacz.guns.client.renderer.scope.ScopeThermalRenderer;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/EntityRenderDispatcherMixin.java}。
 *
 * <p>
 * <b>上游用途</b>：上游用 MixinExtras 的 {@code @WrapOperation} 包住
 * {@code EntityRenderDispatcher#render} 内部的 {@code EntityRenderer#render(...)} 调用，把
 * {@code MultiBufferSource} 换成 {@code ScopeThermalRenderer.wrap(source, entity)} —— 热成像瞄具因此能
 * 「顺手截一份实体几何」当遮罩，而<b>不重复渲染</b>实体。
 *
 * <p>
 * <b>1.7.10 为什么不能照搬、以及替代方案</b>：上游的价值建立在 1.20 的
 * {@code MultiBufferSource}/{@code VertexConsumer}/{@code RenderType} 三件套上；1.7.10 的实体渲染是固定管线
 * {@code Tessellator} 直接提交，<b>没有可插入的顶点流</b>。现方案：在 {@code RenderManager#func_147939_a}
 * 的 HEAD 把该实体的<b>相机相对坐标</b>交给 {@link ScopeThermalRenderer#beginEntity(Entity, double, double, double)}，
 * 由它按实体包围盒直接生成热源盒子（精确剪影 → 包围盒，平台降级，见 {@code ScopeThermalRenderer} 类注释）。
 *
 * <p>
 * <b>为什么目标是 {@code func_147939_a} 而不是 {@code renderEntityStatic}</b>：
 * {@code renderEntityStatic} / {@code renderEntityWithPosYaw} 都会转成相机相对坐标后调用
 * {@code func_147939_a}（{@code RenderManager.java:286}），它是唯一的「开始渲染某实体」汇聚点，
 * 且入参 x/y/z 正是相机相对坐标。
 *
 * <p>
 * <b>为什么只处理「非静态实体 display-list pass」</b>：{@code func_147939_a} 的最后一个 boolean 参数
 * （反编译名 {@code p_147939_10_}）为 true 时，是 {@code RenderGlobal#rebuildDisplayListEntities}
 * （{@code RenderGlobal.java:581-583}）把静态实体烘进 display list 的调用，<b>不是可见绘制</b>，
 * 且此时 {@code RenderManager.renderPosX/Y/Z} 为 0、坐标语义与正常渲染不同 ⇒ 跳过，避免录到错误位置的盒子。
 * 上游 1.20 没有 static entity/display list 概念，等价于「只包真正的 render 调用」。
 *
 * <p>
 * <b>零额外开销保证</b>：{@code beginEntity} 内部按成本顺序早退 —— {@code collecting} 布尔 →
 * {@code enabled()}（整帧一次缓存）→ 非相机实体 → 活且可见 → {@code ThermalImagingHelper#isHeatSource}
 * 判定；全过才写顶点。没开热成像瞄具时，本 Mixin 每帧每实体成本 ≈ 1 次字段读 + 1 次判空。
 */
@Mixin(RenderManager.class)
public class EntityRenderDispatcherMixin {

    /** 进入「该实体即将被绘制」的捕获窗口（是否真的记录由 {@code ScopeThermalRenderer} 内部判断）。 */
    @Inject(method = "func_147939_a", at = @At("HEAD"))
    private void tacz$beginEntityCapture(Entity entity, double x, double y, double z, float yaw, float partialTicks,
        boolean staticEntityDisplayList, CallbackInfoReturnable<Boolean> cir) {
        if (staticEntityDisplayList) {
            return;
        }
        ScopeThermalRenderer.beginEntity(entity, x, y, z);
    }

    /**
     * 结束捕获窗口（{@code endEntity} 是 no-op：新实现只写 Java 顶点缓冲、不改 GL 状态）。
     *
     * <p>
     * 保留 RETURN 钩子是为了在将来若重新引入「重绘到 FBO」方案时有成对的收尾入口；当前不依赖它，
     * 因此即使 {@code doRender} 抛异常（{@code RenderManager.java:302-305} 包成 {@code ReportedException}）
     * 也不会泄漏任何状态。
     */
    @Inject(method = "func_147939_a", at = @At("RETURN"))
    private void tacz$endEntityCapture(Entity entity, double x, double y, double z, float yaw, float partialTicks,
        boolean staticEntityDisplayList, CallbackInfoReturnable<Boolean> cir) {
        ScopeThermalRenderer.endEntity();
    }
}
