package com.tacz.guns.client.renderer.item;

import net.minecraft.item.ItemStack;

import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 第三人称手持 / 掉落物 / 固定展示枪械的「静态网格缓存」快速路径。
 *
 * <h2>1.7.10 下的处理：整体关闭快速路径</h2>
 * 上游（1.20.1）这条路径把枪械几何<b>烘成 VBO</b>，按
 * {@code ItemGeometryKey(外观快照 + ItemDisplayContext)} 缓存，命中后只做一次
 * {@code glDrawElements}，避免每帧重算上千个顶点。它依赖 SBM v2 的
 * {@code StaticMeshRenderer} / {@code StaticMeshCache} / {@code WorldMeshGroup} / {@code MeshRenderableAdapter}
 * 这一整套 GPU 网格设施。
 *
 * <p>
 * 1.7.10 侧做不到，原因不是「少个类」，而是<b>本工程没有 VBO 后端</b>：
 * {@code mcshim.BufferBuilder} 用 Java {@code float[]} 存顶点，{@code BufferUploader#draw}
 * 每次都把顶点重新灌进 {@code Tessellator}（见这两个类的 javadoc 与
 * {@code docs/PORT_PLAN.md} §3.2「无 VBO / 无顶点排序」）。也就是说**没有可复用的 GPU 侧几何对象**，
 * 缓存键再准也只能省下「填 Java 数组」这一步，而这一步本身不是瓶颈；真正的瓶颈是逐顶点重灌。
 * 因此这条路在 1.7.10 上<b>恒定返回 {@link StaticGunRenderResult#UNSUPPORTED}</b>，
 * 让 {@code GunItemRendererWrapper} 走<b>完整保真</b>的
 * {@code BedrockGunModel#render(...)} 路径（那条路径才是 1.7.10 的主渲染链路，逻辑与上游一致）。
 *
 * <p>
 * // TODO(L2): 若将来在 1.7.10 引入真正的 VBO 后端（GL15 {@code glGenBuffers/glBufferData} +
 * // {@code glDrawArrays}），本类可以对齐上游：
 * // ① {@code StaticMeshRenderer.tryDraw(owner, key, previousKey, provider, pose, light, overlay, retention)}
 * // 的 {@code provider} 传入
 * // {@code collector -> DisplayGunGeometry.capture(appearance, context, true, buffers, packedLight)}
 * // ② {@code DisplayGunGeometry.capture} 需要把 {@code MultiBufferSource} 改回
 * // SBM 的 {@code GeometryCollector}（当前 1.7.10 版签名收的是 {@code MultiBufferSource}）。
 * // 上游对应文件：{@code client/renderer/item/ExternalGunItemVboRenderer.java:30-85}。
 *
 * <h2>开关</h2>
 * {@code RenderConfig.STATIC_ITEM_GUN_VBO} 仍被读取（保持与上游配置项同名同义），
 * 但无论开关如何都返回 {@code UNSUPPORTED} —— 因为本平台没有可用的 VBO 后端。
 */
public class ExternalGunItemVboRenderer {

    private ExternalGunItemVboRenderer() {}

    /**
     * 上游 {@code tryRender(stack, context, useLod, pose, buffers, packedLight, packedOverlay)}。
     *
     * @return 恒 {@link StaticGunRenderResult#UNSUPPORTED}（见类注释）
     */
    public static StaticGunRenderResult tryRender(ItemStack stack, ItemDisplayContext context, boolean useLod,
        PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // 读一次配置以保留上游的「开关会清缓存」副作用语义；1.7.10 无缓存可清。
        RenderConfig.STATIC_ITEM_GUN_VBO.get();
        return StaticGunRenderResult.UNSUPPORTED;
    }
}
