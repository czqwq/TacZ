// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/capture/LightPassVertexRouter.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture;

import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.mcshim.VertexFormat;

/** 根据捕获时的 UV2 将完整图元送往普通或固定发光 pass。 */
public class LightPassVertexRouter implements VertexConsumer {

    private final VertexConsumer ordinary;
    private final VertexConsumer emissive;
    private final int primitiveSize;
    private int primitiveVertices;
    private boolean primitiveEmissive;
    private double x, y, z;
    private int red = 255, green = 255, blue = 255, alpha = 255;
    private int defaultRed = 255, defaultGreen = 255, defaultBlue = 255, defaultAlpha = 255;
    private float u, v;
    private int overlayU, overlayV, lightU, lightV;
    private float normalX, normalY, normalZ;

    public LightPassVertexRouter(VertexConsumer ordinary, VertexConsumer emissive, VertexFormat.Mode mode) {
        this.ordinary = ordinary;
        this.emissive = emissive;
        this.primitiveSize = switch (mode) {
            case QUADS -> 4;
            case TRIANGLES -> 3;
            default -> throw new IllegalArgumentException("Unsupported geometry mode: " + mode);
        };
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    /**
     * 对应 1.20 {@code VertexConsumer#color(float,float,float,float)}。
     *
     * <p>
     * 1.20 的 {@code VertexConsumer} 默认把这个重载转成 int 版本；本 shim 的
     * {@code com.tacz.guns.mcshim.VertexConsumer} 把它声明为<b>抽象</b>方法，
     * 因此这里必须显式实现（量化语义与 1.20 默认实现一致）。
     */
    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        return color(
            Math.round(red * 255.0F),
            Math.round(green * 255.0F),
            Math.round(blue * 255.0F),
            Math.round(alpha * 255.0F));
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        this.overlayU = u;
        this.overlayV = v;
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        this.lightU = u;
        this.lightV = v;
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.normalX = x;
        this.normalY = y;
        this.normalZ = z;
        return this;
    }

    @Override
    public void endVertex() {
        boolean fixed = this.lightU != 0 || this.lightV != 0;
        if (this.primitiveVertices == 0) {
            this.primitiveEmissive = fixed;
        } else if (this.primitiveEmissive != fixed) {
            throw new IllegalArgumentException("Mixed light values within one mesh primitive");
        }

        VertexConsumer target = fixed ? this.emissive : this.ordinary;
        target.vertex(this.x, this.y, this.z)
            .color(this.red, this.green, this.blue, this.alpha)
            .uv(this.u, this.v)
            .overlayCoords(this.overlayU, this.overlayV)
            .uv2(this.lightU, this.lightV)
            .normal(this.normalX, this.normalY, this.normalZ)
            .endVertex();

        this.primitiveVertices = (this.primitiveVertices + 1) % this.primitiveSize;
        this.red = this.defaultRed;
        this.green = this.defaultGreen;
        this.blue = this.defaultBlue;
        this.alpha = this.defaultAlpha;
        this.u = this.v = 0;
        this.overlayU = this.overlayV = this.lightU = this.lightV = 0;
        this.normalX = this.normalY = this.normalZ = 0;
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        this.defaultRed = this.red = red;
        this.defaultGreen = this.green = green;
        this.defaultBlue = this.blue = blue;
        this.defaultAlpha = this.alpha = alpha;
    }

    @Override
    public void unsetDefaultColor() {
        this.defaultRed = this.red = 255;
        this.defaultGreen = this.green = 255;
        this.defaultBlue = this.blue = 255;
        this.defaultAlpha = this.alpha = 255;
    }
}
