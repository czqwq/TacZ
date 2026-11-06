package com.tacz.guns.mcshim;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.renderer.texture.OverlayTexture}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 没有 overlay 纹理（受伤红闪 + 附魔光效）通道，顶点格式里也没有 overlay 槽位。
 * 本类只提供常量，值硬编码为 1.20 的 {@code NO_OVERLAY = 0}。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@link #NO_OVERLAY} 只是「无 overlay」的<b>语义占位</b>。把非 0 值传给
 * 本 shim 的 {@link VertexConsumer#overlay(int)} 不会产生任何视觉变化
 * （见 {@link TessellatorVertexConsumer} 的相关说明）。</li>
 * <li>1.20 的 {@code OverlayTexture#pack(int, boolean)} / {@code v(int)} / {@code u(int)} 未移植，
 * 上游没有用到；需要时按 1.20 公式自行计算即可。</li>
 * </ul>
 */
public final class OverlayTexture {

    /** 对应 1.20 {@code OverlayTexture.NO_OVERLAY}。 */
    public static final int NO_OVERLAY = 0;

    private OverlayTexture() {}
}
