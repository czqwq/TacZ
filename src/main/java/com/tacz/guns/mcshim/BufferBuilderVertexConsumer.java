package com.tacz.guns.mcshim;

/**
 * {@link VertexConsumer} 的 {@link BufferBuilder} 版本，对应 1.20 里
 * 「{@code BufferBuilder} 自己就 implements {@code VertexConsumer}」这件事。
 *
 * <h2>为什么需要这个类</h2>
 * 1.20 的 {@code BufferBuilder} 本身就是 {@code VertexConsumer}，所以
 * {@code MultiBufferSource#getBuffer(RenderType)} 直接返回 {@code BufferBuilder}。
 * 本 shim 的 {@link BufferBuilder} <b>也</b> implements {@link VertexConsumer}，
 * 所以这个类只是给「需要静态区分类型」或「需要额外包一层过滤」的调用点用，
 * 内部把所有调用原样转发。
 *
 * <h2>行为差异 / 降级点</h2>
 * 与 {@link BufferBuilder} 完全相同：overlay 丢弃、无 VBO、绘制发生在 {@link #flush()} 时。
 */
public class BufferBuilderVertexConsumer implements VertexConsumer {

    private final BufferBuilder builder;

    public BufferBuilderVertexConsumer(BufferBuilder builder) {
        this.builder = builder;
    }

    /** 暴露底层 builder（例如要在其上调用 {@code begin}/{@code end}）。 */
    public BufferBuilder getBuilder() {
        return builder;
    }

    /**
     * 封口并绘制：等价 1.20 的 {@code builder.end()} + {@code BufferUploader.drawWithShader(buffer)}。
     *
     * @return 是否真的画了东西（顶点数为 0 时返回 false）
     */
    public boolean flush() {
        BufferBuilder.RenderedBuffer buffer = builder.end();
        if (buffer == null) {
            return false;
        }
        BufferUploader.draw(buffer);
        buffer.release();
        return true;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        builder.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        builder.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        builder.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        builder.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        builder.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        builder.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        builder.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        builder.endVertex();
    }
}
