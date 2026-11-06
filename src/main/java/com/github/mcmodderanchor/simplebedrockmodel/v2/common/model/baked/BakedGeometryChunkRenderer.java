// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/baked/BakedGeometryChunkRenderer.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import java.nio.FloatBuffer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.VertexConsumer;

public class BakedGeometryChunkRenderer {

    public static final BakedGeometryChunkRenderer INSTANCE = new BakedGeometryChunkRenderer();

    private final Vector3f renderPosition = new Vector3f();
    private final Vector3f renderNormal = new Vector3f();

    public void render(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer quadConsumer,
        VertexConsumer triangleConsumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        render(chunk, poseStack, quadConsumer, triangleConsumer, lightmap, overlay, red, green, blue, alpha, false);
    }

    public void render(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer quadConsumer,
        VertexConsumer triangleConsumer, int lightmap, int overlay, float red, float green, float blue, float alpha,
        boolean skipNormalVisibilityCull) {
        if (chunk.hasQuads()) {
            renderQuadChunk(
                chunk,
                poseStack,
                quadConsumer,
                lightmap,
                overlay,
                red,
                green,
                blue,
                alpha,
                skipNormalVisibilityCull);
        }
        if (chunk.hasVertices()) {
            renderVertexChunk(chunk, poseStack, triangleConsumer, lightmap, overlay, red, green, blue, alpha);
        }
    }

    public void renderQuadChunk(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer, int lightmap,
        int overlay, float red, float green, float blue, float alpha) {
        renderQuadChunk(chunk, poseStack, consumer, lightmap, overlay, red, green, blue, alpha, false);
    }

    public void renderQuadChunk(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer, int lightmap,
        int overlay, float red, float green, float blue, float alpha, boolean skipNormalVisibilityCull) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        BakedQuadData quads = chunk.quads();
        float[] positions = quads.positions();
        float[] normals = quads.normals();
        float[] uvs = quads.uvs();
        // TODO(L2): 上游 v2/common/model/baked/BakedGeometryChunkRenderer.java:62 用
        // RenderSystem.getModelViewMatrix().m32()（JOML Matrix4f 的平移 Z）判断「是否第一人称（模型视图未平移）」。
        // mcshim 的 RenderSystem.getModelViewMatrix() 返回 java.nio.FloatBuffer（列主序 16 float），
        // 故改为从缓冲区取 m32（列主序下标 14）。
        boolean cull = !skipNormalVisibilityCull && modelViewTranslationZ() == 0.0F;
        for (int i = 0; i < quads.quadCount(); i++) {
            int pb = i * BakedQuadData.POSITION_STRIDE;
            int nb = i * BakedQuadData.NORMAL_STRIDE;
            int ub = i * BakedQuadData.UV_STRIDE;
            float nx = normals[nb];
            float ny = normals[nb + 1];
            float nz = normals[nb + 2];
            float tnx = normalMatrix.m00() * nx + normalMatrix.m10() * ny + normalMatrix.m20() * nz;
            float tny = normalMatrix.m01() * nx + normalMatrix.m11() * ny + normalMatrix.m21() * nz;
            float tnz = normalMatrix.m02() * nx + normalMatrix.m12() * ny + normalMatrix.m22() * nz;
            if (cull && shouldCullQuad(positions, pb, poseMatrix, tnx, tny, tnz)) {
                continue;
            }
            emitVertex(
                consumer,
                poseMatrix,
                red,
                green,
                blue,
                alpha,
                positions[pb],
                positions[pb + 1],
                positions[pb + 2],
                uvs[ub],
                uvs[ub + 1],
                overlay,
                lightmap,
                tnx,
                tny,
                tnz);
            emitVertex(
                consumer,
                poseMatrix,
                red,
                green,
                blue,
                alpha,
                positions[pb + 3],
                positions[pb + 4],
                positions[pb + 5],
                uvs[ub + 2],
                uvs[ub + 3],
                overlay,
                lightmap,
                tnx,
                tny,
                tnz);
            emitVertex(
                consumer,
                poseMatrix,
                red,
                green,
                blue,
                alpha,
                positions[pb + 6],
                positions[pb + 7],
                positions[pb + 8],
                uvs[ub + 4],
                uvs[ub + 5],
                overlay,
                lightmap,
                tnx,
                tny,
                tnz);
            emitVertex(
                consumer,
                poseMatrix,
                red,
                green,
                blue,
                alpha,
                positions[pb + 9],
                positions[pb + 10],
                positions[pb + 11],
                uvs[ub + 6],
                uvs[ub + 7],
                overlay,
                lightmap,
                tnx,
                tny,
                tnz);
        }
    }

    public void renderVertexChunk(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer, int lightmap,
        int overlay, float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        BakedVertexData vertices = chunk.vertices();
        float[] positions = vertices.positions();
        float[] normals = vertices.normals();
        float[] uvs = vertices.uvs();
        for (int i = 0; i < vertices.vertexCount(); i++) {
            int pb = i * BakedVertexData.POSITION_STRIDE;
            int nb = i * BakedVertexData.NORMAL_STRIDE;
            int ub = i * BakedVertexData.UV_STRIDE;
            renderPosition.set(positions[pb], positions[pb + 1], positions[pb + 2])
                .mulPosition(poseMatrix);
            renderNormal.set(normals[nb], normals[nb + 1], normals[nb + 2])
                .mul(normalMatrix);
            if (renderNormal.lengthSquared() > 1.0E-12f) {
                renderNormal.normalize();
            }
            // TODO(L2): 上游用 1.20.1 VertexConsumer#vertex(x,y,z,r,g,b,a,u,v,overlay,light,nx,ny,nz)
            // 的「一次性写全顶点」重载；mcshim 的 VertexConsumer 只提供逐属性链式调用，
            // 这里拆成 vertex→color→uv→overlay→lightmap→normal→endVertex，语义与参数一一对应。
            consumer.vertex(renderPosition.x, renderPosition.y, renderPosition.z)
                .color(red, green, blue, alpha)
                .uv(uvs[ub], uvs[ub + 1])
                .overlay(overlay)
                .lightmap(lightmap)
                .normal(renderNormal.x, renderNormal.y, renderNormal.z)
                .endVertex();
        }
    }

    private void emitVertex(VertexConsumer consumer, Matrix4f poseMatrix, float red, float green, float blue,
        float alpha, float x, float y, float z, float u, float v, int overlay, int lightmap, float nx, float ny,
        float nz) {
        renderPosition.set(x, y, z)
            .mulPosition(poseMatrix);
        // TODO(L2): 同 renderVertexChunk —— 1.20.1 的「一次性写全顶点」重载在
        // mcshim 的 VertexConsumer 上不存在，拆成链式调用。
        consumer.vertex(renderPosition.x, renderPosition.y, renderPosition.z)
            .color(red, green, blue, alpha)
            .uv(u, v)
            .overlay(overlay)
            .lightmap(lightmap)
            .normal(nx, ny, nz)
            .endVertex();
    }

    private boolean shouldCullQuad(float[] positions, int positionBase, Matrix4f poseMatrix, float nx, float ny,
        float nz) {
        float cx = positions[positionBase] + positions[positionBase + 6];
        float cy = positions[positionBase + 1] + positions[positionBase + 7];
        float cz = positions[positionBase + 2] + positions[positionBase + 8];
        float tx = poseMatrix.m00() * cx + poseMatrix.m10() * cy
            + poseMatrix.m20() * cz
            + poseMatrix.m30()
            + poseMatrix.m30();
        float ty = poseMatrix.m01() * cx + poseMatrix.m11() * cy
            + poseMatrix.m21() * cz
            + poseMatrix.m31()
            + poseMatrix.m31();
        float tz = poseMatrix.m02() * cx + poseMatrix.m12() * cy
            + poseMatrix.m22() * cz
            + poseMatrix.m32()
            + poseMatrix.m32();
        return tx * nx + ty * ny + tz * nz > 0;
    }

    /**
     * TODO(L2): 取「模型视图矩阵的平移 Z」。
     * 1.20 直接 {@code RenderSystem.getModelViewMatrix().m32()}；mcshim 返回列主序 16 float 的
     * {@link java.nio.FloatBuffer}，对应下标是 14（m32）。
     */
    private static float modelViewTranslationZ() {
        FloatBuffer matrix = RenderSystem.getModelViewMatrix();
        if (matrix == null || matrix.capacity() < 16) {
            return 0.0F;
        }
        return matrix.get(matrix.position() + 14);
    }
}
