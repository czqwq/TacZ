// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/BedrockAnimationFile.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class BedrockAnimationFile {

    @SerializedName("format_version")
    private String version;

    @SerializedName("animations")
    private Map<String, BedrockAnimationPOJO> animations;

    public String getVersion() {
        return version;
    }

    public Map<String, BedrockAnimationPOJO> getAnimations() {
        return animations;
    }
}
