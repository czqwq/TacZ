// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/tree/TreeBedrockModel.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.github.mcmodderanchor.simplebedrockmodel.port.AABB;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.BoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.TreeModelInstance;
import com.maydaymemory.mae.basic.Pose;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

public class TreeBedrockModel implements BoneIndexProvider {

    private static final int MAX_LIGHT_TEXTURE = LightTexture.pack(15, 15);

    private final TreeBoneDefinition[] bones;
    private final Map<String, Integer> boneIndexByName;
    private final Map<String, Map.Entry<Integer, LocatorData>> locatorByName;
    private final Pose bindPose;
    private final AABB renderBoundingBox;

    public TreeBedrockModel(TreeBoneDefinition[] bones, Map<String, Integer> boneIndexByName,
        Map<String, Map.Entry<Integer, LocatorData>> locatorByName, Pose bindPose, AABB renderBoundingBox) {
        this.bones = bones;
        for (TreeBoneDefinition bone : bones) {
            bone.linkReferences(bones);
        }
        this.boneIndexByName = Map.copyOf(boneIndexByName);
        this.locatorByName = Map.copyOf(locatorByName);
        this.bindPose = bindPose;
        this.renderBoundingBox = renderBoundingBox;
    }

    public static TreeBedrockModel bake(BedrockModelPOJO pojo) {
        return TreeBedrockModelBaker.bake(pojo);
    }

    public TreeModelInstance createInstance() {
        return new TreeModelInstance(this);
    }

    public TreeBoneDefinition[] bones() {
        return bones;
    }

    public TreeBoneDefinition bone(int index) {
        return bones[index];
    }

    public int boneCount() {
        return bones.length;
    }

    public AABB getRenderBoundingBox() {
        return renderBoundingBox;
    }

    public Pose getBindPose() {
        return bindPose;
    }

    @Override
    public int getIndex(String boneName) {
        return boneIndexByName.getOrDefault(boneName, -1);
    }

    @Nullable
    public Map.Entry<Integer, LocatorData> locator(String locatorName) {
        return locatorByName.get(locatorName);
    }

    public TreeBoneDefinition[] roots() {
        ArrayList<TreeBoneDefinition> roots = new ArrayList<>();
        for (TreeBoneDefinition bone : bones) {
            if (bone.parentIndex() < 0) roots.add(bone);
        }
        return roots.toArray(TreeBoneDefinition[]::new);
    }

    public void renderToBuffer(TreeModelInstance instance, PoseStack poseStack, VertexConsumer buffer, int packedLight,
        int packedOverlay) {
        renderToBuffer(instance, poseStack, buffer, packedLight, packedOverlay, 1, 1, 1, 1);
    }

    public void renderToBuffer(TreeModelInstance instance, PoseStack poseStack, VertexConsumer buffer, int packedLight,
        int packedOverlay, float red, float green, float blue, float alpha) {
        renderToBuffer(instance, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha, false);
    }

    public void renderToBuffer(TreeModelInstance instance, PoseStack poseStack, VertexConsumer buffer, int packedLight,
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

    public void renderToBuffer(TreeModelInstance instance, PoseStack poseStack, MultiBufferSource bufferSource,
        RenderType quadRenderType, RenderType triangleRenderType, int packedLight, int packedOverlay) {
        renderToBuffer(
            instance,
            poseStack,
            bufferSource,
            quadRenderType,
            triangleRenderType,
            packedLight,
            packedOverlay,
            1,
            1,
            1,
            1);
    }

    public void renderToBuffer(TreeModelInstance instance, PoseStack poseStack, MultiBufferSource bufferSource,
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

    public void renderToBuffer(TreeModelInstance instance, PoseStack poseStack, MultiBufferSource bufferSource,
        RenderType quadRenderType, RenderType triangleRenderType, int packedLight, int packedOverlay, float red,
        float green, float blue, float alpha, boolean skipNormalVisibilityCull) {
        renderBoneTree(
            instance,
            poseStack,
            bufferSource.getBuffer(quadRenderType),
            packedLight,
            packedOverlay,
            red,
            green,
            blue,
            alpha,
            true,
            skipNormalVisibilityCull);
        renderBoneTree(
            instance,
            poseStack,
            bufferSource.getBuffer(triangleRenderType),
            packedLight,
            packedOverlay,
            red,
            green,
            blue,
            alpha,
            false,
            skipNormalVisibilityCull);
    }

    public void renderBoneTree(TreeModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
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

    public void renderBoneTree(TreeModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
        int packedLight, int packedOverlay, float red, float green, float blue, float alpha, boolean quadsPass,
        boolean skipNormalVisibilityCull) {
        for (TreeBoneDefinition bone : bones) {
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

    public void renderBone(TreeModelInstance instance, int boneIndex, PoseStack poseStack, VertexConsumer consumer,
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

    public void renderBone(TreeModelInstance instance, int boneIndex, PoseStack poseStack, VertexConsumer consumer,
        int packedLight, int packedOverlay, float red, float green, float blue, float alpha, boolean quadsPass,
        boolean skipNormalVisibilityCull) {
        TreeBoneDefinition def = bones[boneIndex];
        if (quadsPass ? !def.hasQuadsInTree() : !def.hasVerticesInTree()) return;
        BoneState bone = instance.getBone(boneIndex);
        if (bone == null || !bone.visible) return;

        int light = bone.illuminated ? MAX_LIGHT_TEXTURE : packedLight;
        poseStack.pushPose();
        bone.translateAndRotateAndScale(poseStack);

        if (quadsPass) {
            renderBoneCubes(
                def,
                poseStack.last(),
                consumer,
                light,
                packedOverlay,
                red,
                green,
                blue,
                alpha,
                skipNormalVisibilityCull);
        } else {
            renderBonePolyMeshes(def, poseStack.last(), consumer, light, packedOverlay, red, green, blue, alpha);
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

    private static void renderBoneCubes(TreeBoneDefinition def, PoseStack.Pose pose, VertexConsumer consumer, int light,
        int overlay, float red, float green, float blue, float alpha, boolean skipNormalVisibilityCull) {
        ICube[] cubes = def.cubes();
        if (cubes.length == 0) return;
        TreeGeometryWriter.writeCubes(
            cubes,
            consumer,
            pose.pose(),
            pose.normal(),
            light,
            overlay,
            red,
            green,
            blue,
            alpha,
            skipNormalVisibilityCull);
    }

    private static void renderBonePolyMeshes(TreeBoneDefinition def, PoseStack.Pose pose, VertexConsumer consumer,
        int light, int overlay, float red, float green, float blue, float alpha) {
        PolyMesh[] polyMeshes = def.polyMeshes();
        if (polyMeshes.length == 0) return;
        TreeGeometryWriter
            .writePolyMeshes(polyMeshes, consumer, pose.pose(), pose.normal(), light, overlay, red, green, blue, alpha);
    }

    public static void applyLocatorLocalTransform(Matrix4f transform, LocatorData locator) {
        float[] offset = locator.offset();
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) transform.translate(offset[0], offset[1], offset[2]);
        float[] rotation = locator.rotation();
        if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
            transform.rotate(
                new Quaternionf().rotateZ((float) Math.toRadians(rotation[2]))
                    .rotateY((float) Math.toRadians(rotation[1]))
                    .rotateX((float) Math.toRadians(rotation[0])));
        }
    }

    public static Map.Entry<Integer, LocatorData> locatorEntry(int boneIndex, LocatorData locatorData) {
        return new AbstractMap.SimpleImmutableEntry<>(boneIndex, locatorData);
    }
}
