// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: SimpleBedrockModel.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 上游是一个独立的 Forge 模组入口（{@code @Mod("simplebedrockmodel")}）；在 TACZ 里它被
 * <b>vendor 成库</b>，因此这里只保留纯常量 + 工具方法。
 *
 * <p>
 * TODO(L2): 上游 SimpleBedrockModel.java:22-25 的构造器做两件事：
 * <ul>
 * <li>{@code NetworkHandler.init()} —— 注册 1.20 的 {@code SimpleChannel} 网络包；1.7.10 走
 * {@code SimpleNetworkWrapper}，而 SBM 的网络包（切副手物品）在 TACZ 移植里不使用，
 * 由 {@code v1.network.NetworkHandler} 的降级实现处理，故此处不再调用；</li>
 * <li>{@code ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WorldMeshConfig.SPEC)}
 * —— 1.7.10 没有 ForgeConfigSpec/ModConfig，配置改由 {@code WorldMeshConfig} 的静态字段承担
 * （见该类注释），无需在此注册。</li>
 * </ul>
 */
public final class SimpleBedrockModel {

    public static final String MOD_ID = "simplebedrockmodel";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private SimpleBedrockModel() {}

    @SuppressWarnings("removal")
    public static ResourceLocation modLoc(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
