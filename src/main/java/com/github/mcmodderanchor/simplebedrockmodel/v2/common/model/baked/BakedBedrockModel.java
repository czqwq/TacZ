// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/baked/BakedBedrockModel.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import java.util.ArrayList;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.port.AABB;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.BoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.maydaymemory.mae.basic.Pose;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

/**
 * v2版本的基岩模型<br/>
 * 构建时，所有永远不会变化的骨骼将退化为查询点，其全部顶点合并到最近的动态骨骼上<br/>
 * 退化后的骨骼仍可查询在当前姿态下的位置，但是无法再对其进行变换操作<br/>
 * 同时，运行时数据改为由{@link BakedModelInstance}承担，本类仅持有静态的骨骼和顶点信息
 */
public class BakedBedrockModel implements BoneIndexProvider {

    private static final int MAX_LIGHT_TEXTURE = LightTexture.pack(15, 15);
    // 压缩后的骨骼
    private final BakedBoneDefinition[] bones;
    private final Map<String, Integer> boneIndexByName;
    // 烘培过后的顶点信息
    private final BakedGeometryChunk[] chunks;
    private final BakedGeometryChunk[] chunkByBone;
    @Nullable
    private final BakedGeometryChunk rootChunk;
    private final boolean retainsCubeGeometry;
    private final BakedCubeGeometry[] cubeGeometry;

    // 定位器，以及退化骨骼的信息；因为退化了，所以没有实际的boneIndex了
    private final BoneLocator[] locators;
    private final Map<String, BoneLocator> locatorByName;
    private final QueryTransform[] queryTransforms;
    private final Map<String, QueryTransform> queryTransformByName;

    private final Pose bindPose;
    private final AABB renderBoundingBox;

    public BakedBedrockModel(BakedBoneDefinition[] bones, Map<String, Integer> boneIndexByName,
        BakedGeometryChunk[] chunks, BoneLocator[] locators, Map<String, BoneLocator> locatorByName,
        QueryTransform[] queryTransforms, Map<String, QueryTransform> queryTransformByName, Pose bindPose,
        AABB renderBoundingBox) {
        this(
            bones,
            boneIndexByName,
            chunks,
            new BakedCubeGeometry[0],
            false,
            locators,
            locatorByName,
            queryTransforms,
            queryTransformByName,
            bindPose,
            renderBoundingBox);
    }

    public BakedBedrockModel(BakedBoneDefinition[] bones, Map<String, Integer> boneIndexByName,
        BakedGeometryChunk[] chunks, BakedCubeGeometry[] cubeGeometry, boolean retainsCubeGeometry,
        BoneLocator[] locators, Map<String, BoneLocator> locatorByName, QueryTransform[] queryTransforms,
        Map<String, QueryTransform> queryTransformByName, Pose bindPose, AABB renderBoundingBox) {
        this.bones = bones.clone();
        this.boneIndexByName = Map.copyOf(boneIndexByName);
        this.chunks = chunks.clone();
        ChunkIndex chunkIndex = indexChunks(this.chunks, bones.length);
        this.chunkByBone = chunkIndex.chunkByBone();
        this.rootChunk = chunkIndex.rootChunk();
        this.retainsCubeGeometry = retainsCubeGeometry;
        this.cubeGeometry = cubeGeometry.clone();
        this.locators = locators.clone();
        this.locatorByName = Map.copyOf(locatorByName);
        this.queryTransforms = queryTransforms.clone();
        this.queryTransformByName = Map.copyOf(queryTransformByName);
        this.bindPose = bindPose;
        this.renderBoundingBox = renderBoundingBox;
    }

    public static BakedBedrockModel bake(BedrockModelPOJO pojo) {
        return bake(pojo, BakerOptions.defaults());
    }

    public static BakedBedrockModel bake(BedrockModelPOJO pojo, BakerOptions options) {
        return BedrockModelBaker.bake(pojo, options);
    }

    public BakedModelInstance createInstance() {
        return new BakedModelInstance(this);
    }

    public BakedBoneDefinition[] bones() {
        return bones.clone();
    }

    BakedBoneDefinition bone(int index) {
        return bones[index];
    }

    int boneCount() {
        return bones.length;
    }

    public BakedGeometryChunk[] chunks() {
        return chunks.clone();
    }

    public boolean retainsCubeGeometry() {
        return retainsCubeGeometry;
    }

    public BakedCubeGeometry[] cubeGeometry() {
        return cubeGeometry.clone();
    }

    public BakedGeometryChunk[] cubeChunks() {
        return chunksWithQuads();
    }

    public BakedGeometryChunk[] meshChunks() {
        return chunksWithVertices();
    }

    public BoneLocator[] locators() {
        return locators.clone();
    }

    @Nullable
    public BoneLocator locator(String name) {
        return locatorByName.get(name);
    }

    @Nullable
    public QueryTransform queryTransform(String name) {
        return queryTransformByName.get(name);
    }

    @Override
    public int getIndex(String boneName) {
        return boneIndexByName.getOrDefault(boneName, -1);
    }

    public AABB getRenderBoundingBox() {
        return renderBoundingBox;
    }

    public Pose getBindPose() {
        return bindPose;
    }

    public void renderToBuffer(BakedModelInstance instance, PoseStack poseStack, VertexConsumer buffer, int packedLight,
        int packedOverlay) {
        renderToBuffer(instance, poseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void renderToBuffer(BakedModelInstance instance, PoseStack poseStack, VertexConsumer buffer, int packedLight,
        int packedOverlay, float red, float green, float blue, float alpha) {
        renderToBuffer(instance, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha, false);
    }

    public void renderToBuffer(BakedModelInstance instance, PoseStack poseStack, VertexConsumer buffer, int packedLight,
        int packedOverlay, float red, float green, float blue, float alpha, boolean skipNormalVisibilityCull) {
        renderBoneTree(
            instance,
            poseStack,
            buffer,
            packedLight,
            packedOverlay,
            red,
            green,
            blue,
            alpha,
            true,
            skipNormalVisibilityCull);
    }

    public void renderToBuffer(BakedModelInstance instance, PoseStack poseStack, MultiBufferSource bufferSource,
        RenderType quadRenderType, RenderType triangleRenderType, int packedLight, int packedOverlay) {
        renderToBuffer(
            instance,
            poseStack,
            bufferSource,
            quadRenderType,
            triangleRenderType,
            packedLight,
            packedOverlay,
            1.0F,
            1.0F,
            1.0F,
            1.0F);
    }

    public void renderToBuffer(BakedModelInstance instance, PoseStack poseStack, MultiBufferSource bufferSource,
        RenderType quadRenderType, RenderType triangleRenderType, int packedLight, int packedOverlay, float red,
        float green, float blue, float alpha) {
        renderToBuffer(
            instance,
            poseStack,
            bufferSource,
            quadRenderType,
            triangleRenderType,
            packedLight,
            packedOverlay,
            red,
            green,
            blue,
            alpha,
            false);
    }

    public void renderToBuffer(BakedModelInstance instance, PoseStack poseStack, MultiBufferSource bufferSource,
        RenderType quadRenderType, RenderType triangleRenderType, int packedLight, int packedOverlay, float red,
        float green, float blue, float alpha, boolean skipNormalVisibilityCull) {
        VertexConsumer quadConsumer = bufferSource.getBuffer(quadRenderType);
        renderBoneTree(
            instance,
            poseStack,
            quadConsumer,
            packedLight,
            packedOverlay,
            red,
            green,
            blue,
            alpha,
            true,
            skipNormalVisibilityCull);
        VertexConsumer triangleConsumer = bufferSource.getBuffer(triangleRenderType);
        renderBoneTree(
            instance,
            poseStack,
            triangleConsumer,
            packedLight,
            packedOverlay,
            red,
            green,
            blue,
            alpha,
            false,
            skipNormalVisibilityCull);
    }

    public void renderBoneTree(BakedModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
        int packedLight, int packedOverlay, float red, float green, float blue, float alpha, boolean quadsPass) {
        renderBoneTree(
            instance,
            poseStack,
            consumer,
            packedLight,
            packedOverlay,
            red,
            green,
            blue,
            alpha,
            quadsPass,
            false);
    }

    public void renderBoneTree(BakedModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
        int packedLight, int packedOverlay, float red, float green, float blue, float alpha, boolean quadsPass,
        boolean skipNormalVisibilityCull) {
        if (rootChunk != null) {
            renderChunkForPass(
                rootChunk,
                poseStack,
                consumer,
                packedLight,
                packedOverlay,
                red,
                green,
                blue,
                alpha,
                quadsPass,
                skipNormalVisibilityCull);
        }
        for (BakedBoneDefinition bone : bones) {
            if (bone.parentIndex() < 0) {
                renderBone(
                    instance,
                    bone.index(),
                    poseStack,
                    consumer,
                    packedLight,
                    packedOverlay,
                    red,
                    green,
                    blue,
                    alpha,
                    quadsPass,
                    skipNormalVisibilityCull);
            }
        }
    }

    public void renderBone(BakedModelInstance instance, int boneIndex, PoseStack poseStack, VertexConsumer consumer,
        int packedLight, int packedOverlay, float red, float green, float blue, float alpha, boolean quadsPass) {
        renderBone(
            instance,
            boneIndex,
            poseStack,
            consumer,
            packedLight,
            packedOverlay,
            red,
            green,
            blue,
            alpha,
            quadsPass,
            false);
    }

    public void renderBone(BakedModelInstance instance, int boneIndex, PoseStack poseStack, VertexConsumer consumer,
        int packedLight, int packedOverlay, float red, float green, float blue, float alpha, boolean quadsPass,
        boolean skipNormalVisibilityCull) {
        BakedBoneDefinition def = bones[boneIndex];
        if (quadsPass ? !def.hasQuadsInTree() : !def.hasVerticesInTree()) {
            return;
        }
        BoneState bone = instance.getBone(boneIndex);
        if (bone == null || !bone.visible) {
            return;
        }

        int light = bone.illuminated ? MAX_LIGHT_TEXTURE : packedLight;
        poseStack.pushPose();
        bone.translateAndRotateAndScale(poseStack);

        BakedGeometryChunk chunk = chunkByBone[boneIndex];
        if (chunk != null) {
            renderChunkForPass(
                chunk,
                poseStack,
                consumer,
                light,
                packedOverlay,
                red,
                green,
                blue,
                alpha,
                quadsPass,
                skipNormalVisibilityCull);
        }

        for (int childIndex : def.children()) {
            renderBone(
                instance,
                childIndex,
                poseStack,
                consumer,
                light,
                packedOverlay,
                red,
                green,
                blue,
                alpha,
                quadsPass,
                skipNormalVisibilityCull);
        }

        poseStack.popPose();
    }

    public void renderChunkForPass(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer, int lightmap,
        int overlay, float red, float green, float blue, float alpha, boolean quadsPass) {
        renderChunkForPass(chunk, poseStack, consumer, lightmap, overlay, red, green, blue, alpha, quadsPass, false);
    }

    public void renderChunkForPass(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer, int lightmap,
        int overlay, float red, float green, float blue, float alpha, boolean quadsPass,
        boolean skipNormalVisibilityCull) {
        if (quadsPass) {
            BakedGeometryChunkRenderer.INSTANCE.renderQuadChunk(
                chunk,
                poseStack,
                consumer,
                lightmap,
                overlay,
                red,
                green,
                blue,
                alpha,
                skipNormalVisibilityCull);
        } else {
            BakedGeometryChunkRenderer.INSTANCE
                .renderVertexChunk(chunk, poseStack, consumer, lightmap, overlay, red, green, blue, alpha);
        }
    }

    private BakedGeometryChunk[] chunksWithQuads() {
        ArrayList<BakedGeometryChunk> result = new ArrayList<>();
        for (BakedGeometryChunk chunk : chunks) {
            if (chunk.hasQuads()) {
                result.add(chunk);
            }
        }
        return result.toArray(BakedGeometryChunk[]::new);
    }

    private BakedGeometryChunk[] chunksWithVertices() {
        ArrayList<BakedGeometryChunk> result = new ArrayList<>();
        for (BakedGeometryChunk chunk : chunks) {
            if (chunk.hasVertices()) {
                result.add(chunk);
            }
        }
        return result.toArray(BakedGeometryChunk[]::new);
    }

    private static ChunkIndex indexChunks(BakedGeometryChunk[] chunks, int boneCount) {
        BakedGeometryChunk[] chunkByBone = new BakedGeometryChunk[boneCount];
        BakedGeometryChunk rootChunk = null;
        for (BakedGeometryChunk chunk : chunks) {
            if (chunk.isRootAttached()) {
                rootChunk = chunk;
            } else if (chunk.attachBoneIndex() < boneCount) {
                chunkByBone[chunk.attachBoneIndex()] = chunk;
            }
        }
        return new ChunkIndex(chunkByBone, rootChunk);
    }

    private record ChunkIndex(BakedGeometryChunk[] chunkByBone, @Nullable BakedGeometryChunk rootChunk) {}
}
