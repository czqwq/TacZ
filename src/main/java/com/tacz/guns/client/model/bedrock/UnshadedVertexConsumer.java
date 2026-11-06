package com.tacz.guns.client.model.bedrock;

import org.joml.Matrix3f;

import com.tacz.guns.mcshim.VertexConsumer;

public final class UnshadedVertexConsumer implements VertexConsumer {

    private static final float FALLBACK_NORMAL_X = 0.0F;
    private static final float FALLBACK_NORMAL_Y = 1.0F;
    private static final float FALLBACK_NORMAL_Z = 0.0F;

    private final VertexConsumer delegate;
    private final float normalX;
    private final float normalY;
    private final float normalZ;

    private UnshadedVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;

        // TODO(port): 上游 UnshadedVertexConsumer.java:21 通过 RenderSystemAccessor 读取
        // RenderSystem#shaderLightDirections（1.20 的实体 shader 主光方向），把顶点法线缩放到与主光方向一致，
        // 让 minecraft_mix_light 饱和到满亮度，从而实现「不受方向光影响」的无光照效果。
        // 1.7.10 是固定管线、没有 shader program（mcshim.RenderSystem#setShaderLightDirections 为 no-op），
        // 没有任何可读的主光方向，因此此处降级为固定法线 (0,1,0)。
        // 1.7.10 的「无光照」实际由两部分达成：BedrockPart 对这些部分使用 LightTexture.pack(15,15) 的满亮度
        // lightmap；法线只影响 GL_LIGHTING 的方向性成分，不影响贴图亮度。
        this.normalX = FALLBACK_NORMAL_X;
        this.normalY = FALLBACK_NORMAL_Y;
        this.normalZ = FALLBACK_NORMAL_Z;
    }

    private UnshadedVertexConsumer(VertexConsumer delegate, float normalX, float normalY, float normalZ) {
        this.delegate = delegate;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
    }

    public static VertexConsumer wrap(VertexConsumer consumer) {
        if (consumer instanceof UnshadedVertexConsumer) {
            return consumer;
        }
        return new UnshadedVertexConsumer(consumer);
    }

    public static VertexConsumer wrapFixedNormal(VertexConsumer consumer) {
        return new UnshadedVertexConsumer(consumer, FALLBACK_NORMAL_X, FALLBACK_NORMAL_Y, FALLBACK_NORMAL_Z);
    }

    public VertexConsumer getDelegate() {
        return delegate;
    }

    public Matrix3f createNormalMatrix() {
        // Accelerated meshes store +Y as their canonical unshaded normal. The second matrix column
        // maps that normal to the shader light direction captured for this render call.
        return new Matrix3f().m10(normalX)
            .m11(normalY)
            .m12(normalZ);
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(normalX, normalY, normalZ);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
