// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/BedrockModelPOJO.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.NeedForRootMotion;
import com.google.gson.annotations.SerializedName;

public class BedrockModelPOJO {

    @SerializedName("format_version")
    @NeedForRootMotion
    private String formatVersion;

    @SerializedName("geometry.model")
    @Nullable
    @NeedForRootMotion
    private GeometryModelLegacy geometryModelLegacy;

    @SerializedName("minecraft:geometry")
    @Nullable
    @NeedForRootMotion
    private GeometryModelNew[] geometryModelNew;

    public String getFormatVersion() {
        return formatVersion;
    }

    @Nullable
    public GeometryModelLegacy getGeometryModelLegacy() {
        return geometryModelLegacy;
    }

    @Nullable
    public GeometryModelNew getGeometryModelNew() {
        if (geometryModelNew == null || geometryModelNew.length == 0) {
            return null;
        }
        return geometryModelNew[0];
    }
}
