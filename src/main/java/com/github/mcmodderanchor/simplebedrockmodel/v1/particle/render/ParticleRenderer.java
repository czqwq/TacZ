// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/render/ParticleRenderer.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceLighting;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

/**
 * 粒子渲染器。将发射器中的局部空间粒子渲染到 PoseStack 局部坐标系中。
 * <p>
 * 世界空间粒子由原版 {@code ParticleEngine} 渲染，不经过此渲染器。
 */
public final class ParticleRenderer {

    private ParticleRenderer() {}

    /**
     * 渲染一个发射器的所有局部空间粒子。
     * <p>
     * 发射器变换矩阵从 emitter 实例上读取。粒子坐标在发射器局部空间中，
     * 渲染时叠加发射器变换。
     *
     * @param cameraPitch    摄像机 pitch（弧度）
     * @param cameraRoll     摄像机 roll（弧度）
     * @param cameraRotation 摄像机旋转矩阵（世界对齐空间 → 视图空间），
     *                       用于 fpDetached 粒子。可为 null。
     */
    public static void render(ParticleEmitterInstance emitter, PoseStack poseStack, MultiBufferSource bufferSource,
        int light, float partialTick, float cameraPitch, float cameraRoll, @Nullable Matrix4f cameraRotation) {
        List<ParticleInstance> particles = emitter.getParticles();
        if (particles.isEmpty()) return;

        var definition = emitter.getDefinition();
        ParticleDescription desc = definition.getDescription();
        ResourceLocation texture = desc.getTexture();

        // 选择 RenderType
        RenderType renderType = getRenderType(desc.getMaterial(), texture);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        // 确定朝向模式
        ParticleAppearanceBillboard billboard = definition.findComponent(ParticleAppearanceBillboard.class);
        ParticleAppearanceBillboard.FaceCameraMode mode = billboard != null ? billboard.faceCameraMode()
            : ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ;

        Matrix4f emitterTransform = emitter.getEmitterTransform();
        boolean localPos = emitter.isLocalPosition();
        boolean localRot = emitter.isLocalRotation();
        Matrix4f pose = poseStack.last()
            .pose();
        Matrix3f normal = poseStack.last()
            .normal();

        // 光照：存在 particle_appearance_lighting 组件时按环境光照（传入 light）着色，否则自发光恒满亮度
        boolean hasLighting = definition.findComponent(ParticleAppearanceLighting.class) != null;
        int particleLight = hasLighting ? light : LightTexture.FULL_BRIGHT;

        for (ParticleInstance particle : particles) {
            Matrix4f effectivePose = (localPos && !particle.fpDetached) ? new Matrix4f(pose).mul(emitterTransform)
                : pose;
            // TODO(L2): 上游先尝试 AcceleratedRenderingCompat.renderParticleBillboard 加速路径；
            // 1.7.10 无 AR（本仓不 vendor 该库），恒走普通顶点 billboard 路径。
            BillboardHelper.renderBillboard(
                particle,
                poseStack,
                consumer,
                particleLight,
                mode,
                emitterTransform,
                localPos,
                localRot,
                cameraPitch,
                cameraRoll,
                cameraRotation);
        }
    }

    /**
     * 根据材质类型选择 RenderType。
     */
    private static RenderType getRenderType(ParticleDescription.Material material, ResourceLocation texture) {
        return switch (material) {
            case PARTICLES_OPAQUE -> RenderType.entityCutout(texture);
            case PARTICLES_ALPHA -> RenderType.entityTranslucent(texture);
            case PARTICLES_BLEND -> RenderType.entityTranslucent(texture);
            case PARTICLES_ADD -> ParticleRenderType.additiveParticle(texture);
            // TODO(L2): 上游 v1/particle/render/ParticleRenderer.java:99 用 RenderType.energySwirl(texture,1,1)
            // （END_PORTAL/ENERGY_SWIRL shader + 滚动纹理坐标）。1.7.10 没有 shader 与纹理坐标系，
            // energySwirl 无法复现，降级为与上游同为叠加混合的 additive 粒子类型（视觉近似）。
            case ENERGY_SWIRL -> ParticleRenderType.additiveParticle(texture);
            case PARTICLES_EMISSIVE -> ParticleRenderType.emissiveParticle(texture);
        };
    }
}
