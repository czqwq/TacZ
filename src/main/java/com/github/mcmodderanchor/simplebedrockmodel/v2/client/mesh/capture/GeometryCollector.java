// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/capture/GeometryCollector.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.EmissiveMeshRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 库提供的一次性几何收集出口；同材质／拓扑的顶点自动合并，不接触 GPU。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游用 {@code net.minecraft.core.Direction} 表示方块朝向并调用 {@code Direction#toYRot()}；
 * 1.7.10 只有 {@link EnumFacing}（无 {@code toYRot()}），本类改用 {@code EnumFacing}
 * 并按 1.20 的 switch 手算 yaw（见 {@link #yRot(EnumFacing)}）。其余逻辑不变。
 */
public class GeometryCollector {

    /** 捕获结果的材质／拓扑分组键，仅为内部数据。 */
    public record Pass(RenderType material, VertexFormat.Mode mode) {

        public Pass {
            Objects.requireNonNull(material, "material");
            if (mode != VertexFormat.Mode.QUADS && mode != VertexFormat.Mode.TRIANGLES) {
                throw new IllegalArgumentException("Unsupported geometry mode: " + mode);
            }
        }
    }

    private final Map<Pass, MeshSink> meshes = new LinkedHashMap<>();

    public GeometryCollector() {}

    /** 单个 pass 的 UV2 须全部为 0 或全部固定非零；混合时使用双材质重载。 */
    public VertexConsumer buffer(RenderType material, VertexFormat.Mode mode) {
        return this.meshes.computeIfAbsent(new Pass(material, mode), pass -> new MeshSink().begin(mode));
    }

    /** 将完整图元按 UV2 分流：0 跟随实例，非 0 使用独立发光材质。 */
    public VertexConsumer buffer(RenderType ordinary, RenderType emissive, VertexFormat.Mode mode) {
        if (Objects.requireNonNull(ordinary, "ordinary")
            .equals(Objects.requireNonNull(emissive, "emissive"))) {
            throw new IllegalArgumentException("Ordinary and emissive RenderTypes must differ");
        }
        return new LightPassVertexRouter(buffer(ordinary, mode), buffer(emissive, mode), mode);
    }

    /**
     * TODO(L2): 上游 v2/client/mesh/capture/GeometryCollector.java:56 用 {@code net.minecraft.core.Direction}；
     * 1.7.10 无该类型，改用语义等价的 {@link EnumFacing}（枚举顺序同为 D/U/N/S/E/W）。
     */
    public record BlockModelKey(ResourceLocation model, ResourceLocation texture, EnumFacing facing) {}

    /** 与 blockModel 使用相同参数生成稳定的共享键，不包含世界位置和实例光照。 */
    public static Object blockModelKey(ResourceLocation modelId, ResourceLocation texture, EnumFacing facing) {
        return new BlockModelKey(
            Objects.requireNonNull(modelId),
            Objects.requireNonNull(texture),
            Objects.requireNonNull(facing));
    }

    /**
     * 1.20 {@code Direction#toYRot()} 的 1.7.10 等价实现。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/capture/GeometryCollector.java:73 调 {@code facing.toYRot()}；
     * 1.7.10 的 {@link EnumFacing} <b>没有</b> {@code toYRot()}，此处按 1.20 的 switch 手算：
     * SOUTH=0、WEST=90、NORTH=180、EAST=-90（DOWN/UP=0）。
     */
    private static float yRot(EnumFacing facing) {
        switch (facing) {
            case SOUTH:
                return 0.0F;
            case WEST:
                return 90.0F;
            case NORTH:
                return 180.0F;
            case EAST:
                return -90.0F;
            default:
                return 0.0F; // DOWN / UP
        }
    }

    /** 便捷捕获 SBM 方块树模型：绑定姿势、局部平移 (0.5,0,0.5)、应用朝向，自动收集两种拓扑。 */
    public boolean blockModel(ResourceLocation modelId, ResourceLocation texture, EnumFacing facing) {
        var model = BedrockModelResources.getInstance()
            .getTreeModel(modelId);
        if (model == null) return false;
        PoseStack pose = new PoseStack();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(facing)));
        var instance = model.createInstance();
        instance.resetPose();
        model.renderBoneTree(
            instance,
            pose,
            buffer(
                RenderType.entityCutout(texture),
                EmissiveMeshRenderTypes.cutout(texture, VertexFormat.Mode.QUADS),
                VertexFormat.Mode.QUADS),
            0,
            OverlayTexture.NO_OVERLAY,
            1,
            1,
            1,
            1,
            true);
        model.renderBoneTree(
            instance,
            pose,
            buffer(
                BedrockModelRenderTypes.polyMeshCutout(texture),
                EmissiveMeshRenderTypes.cutout(texture, VertexFormat.Mode.TRIANGLES),
                VertexFormat.Mode.TRIANGLES),
            0,
            OverlayTexture.NO_OVERLAY,
            1,
            1,
            1,
            1,
            false);
        return true;
    }

    public Map<Pass, MeshSink> snapshot() {
        Map<Pass, MeshSink> result = new LinkedHashMap<>();
        this.meshes.forEach((pass, mesh) -> { if (!mesh.isEmpty()) result.put(pass, mesh); });
        return result;
    }

    public void clear() {
        this.meshes.clear();
    }
}
