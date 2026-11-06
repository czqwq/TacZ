// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/BedrockVersion.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;

import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;

// TODO(L2): 上游 v1/common/resource/pojo/BedrockVersion.java:9-11 用 Maven 的
// org.apache.maven.artifact.versioning.{DefaultArtifactVersion,VersionRange}。1.7.10 的 classpath
// 没有 maven-artifact，但 Forge/FML 自带同源代码 cpw.mods.fml.common.versioning.*
// (ArtifactVersion/DefaultArtifactVersion/VersionRange/InvalidVersionSpecificationException，
// 版本范围语法与 Maven 一致，例如 "[1.8.0,1.12.0)")，故直接改指向，语义等价。
public enum BedrockVersion {

    /**
     * 旧版本基岩版模型，仅限 1.10.0
     */
    LEGACY("[1.10.0]"),
    /**
     * 新版本基岩版模型，往后的 1.14.0，1.16.0 1.21.0 通通用此版本读取
     */
    NEW("[1.12.0,)");

    private final VersionRange versionRange;

    BedrockVersion(String version) {
        this.versionRange = createFromVersionSpec(version);
    }

    public static boolean isNewVersion(BedrockModelPOJO bedrockModel) {
        DefaultArtifactVersion inputVersion = new DefaultArtifactVersion(bedrockModel.getFormatVersion());
        return NEW.versionRange.containsVersion(inputVersion);
    }

    public static boolean isLegacyVersion(BedrockModelPOJO bedrockModel) {
        DefaultArtifactVersion inputVersion = new DefaultArtifactVersion(bedrockModel.getFormatVersion());
        return LEGACY.versionRange.containsVersion(inputVersion);
    }

    private static VersionRange createFromVersionSpec(final String spec) {
        try {
            return VersionRange.createFromVersionSpec(spec);
        } catch (InvalidVersionSpecificationException e) {
            SimpleBedrockModel.LOGGER.fatal("Failed to parse version spec {}", spec, e);
            throw new RuntimeException("Failed to parse spec", e);
        }
    }
}
