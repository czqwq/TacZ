// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/tree/TreeBoneDefinition.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.LocalCubeBounds;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneDefinition;

public final class TreeBoneDefinition implements BoneDefinition {

    private final String name;
    private final int index;
    private final int parentIndex;
    private final int[] children;
    @Nullable
    private TreeBoneDefinition parent;
    private TreeBoneDefinition[] childBones;
    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;
    private final float bindX;
    private final float bindY;
    private final float bindZ;
    private final Quaternionf bindRotation;
    private final Vector3f bindEulerRotation;
    private final Map<String, LocatorData> locators;
    private final ICube[] cubes;
    @Nullable
    private final LocalCubeBounds ownCubeBounds;
    private final PolyMesh[] polyMeshes;
    private final boolean hasQuadsInTree;
    private final boolean hasVerticesInTree;

    // TODO(L2): 上游有 `private AcceleratedBedrockGeometryCache cache`（AR 加速网格缓存）；1.7.10 无 AR，删除。

    public TreeBoneDefinition(String name, int index, int parentIndex, int[] children, float pivotX, float pivotY,
        float pivotZ, float bindX, float bindY, float bindZ, Quaternionf bindRotation, Vector3f bindEulerRotation,
        Map<String, LocatorData> locators, ICube[] cubes, PolyMesh[] polyMeshes, boolean hasQuadsInTree,
        boolean hasVerticesInTree) {
        this(
            name,
            index,
            parentIndex,
            children,
            pivotX,
            pivotY,
            pivotZ,
            bindX,
            bindY,
            bindZ,
            bindRotation,
            bindEulerRotation,
            locators,
            cubes,
            null,
            polyMeshes,
            hasQuadsInTree,
            hasVerticesInTree);
    }

    public TreeBoneDefinition(String name, int index, int parentIndex, int[] children, float pivotX, float pivotY,
        float pivotZ, float bindX, float bindY, float bindZ, Quaternionf bindRotation, Vector3f bindEulerRotation,
        Map<String, LocatorData> locators, ICube[] cubes, @Nullable LocalCubeBounds ownCubeBounds,
        PolyMesh[] polyMeshes, boolean hasQuadsInTree, boolean hasVerticesInTree) {
        this.name = name;
        this.index = index;
        this.parentIndex = parentIndex;
        this.children = children;
        this.childBones = new TreeBoneDefinition[children.length];
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.bindX = bindX;
        this.bindY = bindY;
        this.bindZ = bindZ;
        this.bindRotation = new Quaternionf(bindRotation);
        this.bindEulerRotation = new Vector3f(bindEulerRotation);
        this.locators = Map.copyOf(locators);
        this.cubes = cubes;
        this.ownCubeBounds = ownCubeBounds;
        this.polyMeshes = polyMeshes;
        this.hasQuadsInTree = hasQuadsInTree;
        this.hasVerticesInTree = hasVerticesInTree;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public int parentIndex() {
        return parentIndex;
    }

    @Override
    public int[] children() {
        return children;
    }

    @Nullable
    public TreeBoneDefinition parent() {
        return parent;
    }

    public TreeBoneDefinition[] childBones() {
        return childBones;
    }

    @Override
    public float pivotX() {
        return pivotX;
    }

    @Override
    public float pivotY() {
        return pivotY;
    }

    @Override
    public float pivotZ() {
        return pivotZ;
    }

    @Override
    public float bindX() {
        return bindX;
    }

    @Override
    public float bindY() {
        return bindY;
    }

    @Override
    public float bindZ() {
        return bindZ;
    }

    @Override
    public Quaternionf bindRotation() {
        return new Quaternionf(bindRotation);
    }

    @Override
    public Vector3f bindEulerRotation() {
        return new Vector3f(bindEulerRotation);
    }

    public Map<String, LocatorData> locators() {
        return locators;
    }

    public ICube[] cubes() {
        return cubes;
    }

    @Nullable
    public LocalCubeBounds ownCubeBounds() {
        return ownCubeBounds;
    }

    public PolyMesh[] polyMeshes() {
        return polyMeshes;
    }

    public boolean hasQuadsInTree() {
        return hasQuadsInTree;
    }

    public boolean hasVerticesInTree() {
        return hasVerticesInTree;
    }

    public boolean hasQuads() {
        return cubes.length > 0;
    }

    public boolean hasVertices() {
        return TreeGeometryWriter.hasTriangles(polyMeshes);
    }

    @Override
    public boolean rotateAroundPivot() {
        return false;
    }

    @ApiStatus.Internal
    void linkReferences(TreeBoneDefinition[] bones) {
        parent = parentIndex < 0 ? null : bones[parentIndex];
        TreeBoneDefinition[] linkedChildren = new TreeBoneDefinition[children.length];
        for (int i = 0; i < children.length; i++) {
            linkedChildren[i] = bones[children[i]];
        }
        childBones = linkedChildren;
    }

    // TODO(L2): 上游 getOrCreateCache() 返回 AR 的加速网格缓存；1.7.10 无 AR，该访问器删除。
}
