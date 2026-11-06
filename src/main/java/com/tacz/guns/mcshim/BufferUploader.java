package com.tacz.guns.mcshim;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.vertex.BufferUploader}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.20 的 {@code BufferUploader} 把 {@code BufferBuilder.RenderedBuffer} 提交给当前 shader
 * （VBO + {@code glDrawElements}/{@code glDrawArrays}）。1.7.10 只有
 * {@link Tessellator}：{@code startDrawing(mode)} → 逐顶点 {@code setTextureUV/setColorRGBA_F/
 * setBrightness/setNormal/addVertex} → {@code draw()}。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li><b>逐顶点重灌</b>：每次 {@link #draw(BufferBuilder.RenderedBuffer)} 都要把 Java 数组里的
 * 顶点重新塞进 {@code Tessellator}，比 1.20 的 VBO 直接 {@code glDrawArrays} 慢。
 * 大网格建议不要每帧走这条路。</li>
 * <li><b>没有 shader / VBO / index buffer</b>，GL 状态由调用方（{@link RenderType#setupRenderState()}）
 * 负责，本类只做 {@code glDrawArrays}。</li>
 * <li><b>overlay 属性丢失</b>（1.7.10 顶点格式没有该槽位）。</li>
 * <li>{@code Tessellator#draw()} 会重置缓冲，因此本类<b>不能</b>在别的
 * {@code startDrawing} 期间被调用（1.7.10 会抛 {@code Already tesselating!}）。</li>
 * </ul>
 */
public final class BufferUploader {

    private BufferUploader() {}

    /**
     * 对应 1.20 {@code BufferUploader#drawWithShader(BufferBuilder.RenderedBuffer)}。
     *
     * <p>
     * 1.7.10 无 shader，等价于 {@link #draw(BufferBuilder.RenderedBuffer)}。
     */
    public static void drawWithShader(BufferBuilder.RenderedBuffer buffer) {
        draw(buffer);
    }

    /**
     * 对应 1.20 {@code BufferUploader#draw(BufferBuilder.RenderedBuffer)}：把顶点快照画出来。
     *
     * <p>
     * 空缓冲直接返回（1.20 同样会跳过）。
     */
    public static void draw(BufferBuilder.RenderedBuffer buffer) {
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        Tessellator tessellator = Tessellator.instance;
        int mode = buffer.mode()
            .glMode();
        tessellator.startDrawing(mode);

        float[] positions = buffer.rawPositions();
        float[] colors = buffer.rawColors();
        float[] uvs = buffer.rawUvs();
        float[] lights = buffer.rawLights();
        float[] normals = buffer.rawNormals();
        boolean hasUv = buffer.format()
            .hasUv();
        boolean hasNormal = buffer.format()
            .hasNormal();
        boolean hasLight = buffer.format()
            .hasLightmap();
        int count = buffer.vertexCount();

        for (int i = 0; i < count; i++) {
            if (hasUv) {
                tessellator.setTextureUV(uvs[i * 2], uvs[i * 2 + 1]);
            }
            tessellator.setColorRGBA(
                (int) (colors[i * 4] * 255.0F),
                (int) (colors[i * 4 + 1] * 255.0F),
                (int) (colors[i * 4 + 2] * 255.0F),
                (int) (colors[i * 4 + 3] * 255.0F));
            if (hasLight) {
                tessellator.setBrightness((int) lights[i]);
            }
            if (hasNormal) {
                tessellator.setNormal(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
            }
            tessellator.addVertex(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
        }
        tessellator.draw();
    }

    /** 便捷：直接把 {@link VertexFormat.Mode} 转成 GL 模式。 */
    public static int glModeOf(VertexFormat.Mode mode) {
        return mode == null ? GL11.GL_QUADS : mode.glMode();
    }
}
