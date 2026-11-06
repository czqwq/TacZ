// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/render/MeshDrawTransform.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * 提交时保存独立的变换副本；调用方随后可以立即复用矩阵或弹出姿势栈。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游只保存 {@code modelView = baseView × pose} 与 {@code inverseLinear = inverse(pose 线性部分)}，
 * 因为 1.20 的 shader 用 {@code ModelViewMat} uniform 一次性乘进去。
 * 1.7.10 没有 shader，{@code MeshBatchRenderer} 必须用固定管线的 {@code glMultMatrix} 施加变换，
 * 而当前 {@code GL_MODELVIEW} <b>已经</b>等于 {@code baseView}，所以只需要「pose 这一段」。
 * 因此本类<b>多保存一个 {@link #pose}</b>（这是唯一的新增字段，不改动任何上游签名/语义）。
 */
public class MeshDrawTransform {

    public final Matrix4f modelView;
    public final Matrix3f inverseLinear;
    /** pose 段（不含 base view）；1.7.10 的固定管线需要它单独乘进 GL_MODELVIEW。 */
    public final Matrix4f pose;

    public MeshDrawTransform(Matrix4fc baseView, Matrix4fc pose) {
        this.pose = new Matrix4f(pose);
        modelView = new Matrix4f(baseView).mul(pose);
        inverseLinear = new Matrix3f(pose);
        float determinant = inverseLinear.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0E-10F) inverseLinear.invert();
        else inverseLinear.identity();
    }
}
