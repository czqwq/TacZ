package com.tacz.guns.mcshim;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.vertex.VertexConsumer}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 没有顶点消费者抽象，几何体直接喂给 {@code Tessellator}
 * （{@code startDrawing}/{@code setTextureUV}/{@code setColorRGBA_F}/{@code setNormal}/{@code addVertex}）。
 * 本接口保留 1.20 的<b>链式调用形状</b>，由两个实现类落地：
 * <ul>
 * <li>{@link TessellatorVertexConsumer}：包 1.7.10 的 {@code Tessellator}（立即写进顶点缓冲）。</li>
 * <li>{@link BufferBuilderVertexConsumer}：包本 shim 的 {@link BufferBuilder}（延后 {@code end()} 才绘制）。</li>
 * </ul>
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code VertexConsumer} 是 {@code java.util.function.Consumer<VertexConsumer>}，
 * 且带 {@code addVertex}/{@code putBulkData} 这类「按 VertexFormat 批量写」的方法。上游没用到，
 * 本接口不提供（需自行拆成逐元素调用）。</li>
 * <li>{@code overlay(int)} / {@code lightmap(int)} 是 1.20.1 的名字（{@code overlayCoords(int,int)} /
 * {@code uv2(int,int)} 的打包版）。1.7.10 的 lightmap 走 {@code Tessellator#setBrightness(int)}，
 * overlay（受伤红闪）在 1.7.10 <b>没有</b>顶点级通道，{@link TessellatorVertexConsumer} 会丢弃它
 * （降级点，见该类注释）。</li>
 * <li>{@code defaultColor}/{@code defaultNormal} 的语义与 1.20 一致：给了默认值后，
 * 本顶点若没显式设过颜色/法线就使用默认值。</li>
 * </ul>
 */
public interface VertexConsumer {

    // ---------------------------------------------------------------- 位置

    /** 对应 1.20 {@code VertexConsumer#vertex(double, double, double)}。 */
    VertexConsumer vertex(double x, double y, double z);

    /** 1.20 便利重载（`float` 版本）。 */
    default VertexConsumer vertex(float x, float y, float z) {
        return vertex((double) x, (double) y, (double) z);
    }

    /** 对应 1.20 {@code VertexConsumer#vertex(Matrix4f, float, float, float)}：用位姿矩阵变换后再写入。 */
    default VertexConsumer vertex(Matrix4f pose, float x, float y, float z) {
        Vector3f transformed = pose.transformPosition(x, y, z, new Vector3f());
        return vertex((double) transformed.x, (double) transformed.y, (double) transformed.z);
    }

    // ---------------------------------------------------------------- 颜色

    /** 对应 1.20 {@code VertexConsumer#color(float, float, float, float)}（分量 0~1）。 */
    VertexConsumer color(float red, float green, float blue, float alpha);

    /** 对应 1.20 {@code VertexConsumer#color(int, int, int, int)}（分量 0~255，越界自动 clamp）。 */
    VertexConsumer color(int red, int green, int blue, int alpha);

    /** 1.20 便利重载：{@code 0xAARRGGBB} 打包颜色。 */
    default VertexConsumer color(int argb) {
        return color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    // ---------------------------------------------------------------- 纹理坐标

    /** 对应 1.20 {@code VertexConsumer#uv(float, float)}。 */
    VertexConsumer uv(float u, float v);

    /** 对应 1.20 {@code VertexConsumer#overlayCoords(int, int)}。 */
    VertexConsumer overlayCoords(int u, int v);

    /** 对应 1.20.1 {@code VertexConsumer#overlay(int)}：把 overlay 的 u/v 分别装在 int 的高/低 16 位。 */
    default VertexConsumer overlay(int packedOverlay) {
        return overlayCoords(packedOverlay & 0xFFFF, (packedOverlay >> 16) & 0xFFFF);
    }

    /** 对应 1.20 {@code VertexConsumer#uv2(int, int)}：光照贴图坐标。 */
    VertexConsumer uv2(int u, int v);

    /** 对应 1.20.1 {@code VertexConsumer#lightmap(int)}：打包的 lightmap 坐标。 */
    default VertexConsumer lightmap(int packedLight) {
        return uv2(packedLight & 0xFFFF, (packedLight >> 16) & 0xFFFF);
    }

    // ---------------------------------------------------------------- 法线

    /** 对应 1.20 {@code VertexConsumer#normal(float, float, float)}。 */
    VertexConsumer normal(float x, float y, float z);

    /** 对应 1.20 {@code VertexConsumer#normal(Matrix3f, float, float, float)}：用法线矩阵变换后再写入。 */
    default VertexConsumer normal(Matrix3f normal, float x, float y, float z) {
        Vector3f transformed = normal.transform(x, y, z, new Vector3f());
        return normal(transformed.x, transformed.y, transformed.z);
    }

    // ---------------------------------------------------------------- 顶点收尾

    /** 对应 1.20 {@code VertexConsumer#endVertex()}。 */
    void endVertex();

    // ---------------------------------------------------------------- 默认值

    /** 对应 1.20 {@code VertexConsumer#defaultColor(int, int, int, int)}。 */
    default void defaultColor(int red, int green, int blue, int alpha) {}

    /** 对应 1.20 {@code VertexConsumer#defaultColor(int)}（打包 ARGB）。 */
    default void defaultColor(int argb) {
        defaultColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    /** 对应 1.20 {@code VertexConsumer#defaultNormal(float, float, float)}。 */
    default void defaultNormal(float x, float y, float z) {}

    /** 对应 1.20 {@code VertexConsumer#unsetDefaultColor()}。 */
    default void unsetDefaultColor() {}
}
