// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/resource/ParticleDefinitionLoader.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDeserializer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tacz.guns.mcshim.ProfilerFiller;
import com.tacz.guns.mcshim.ResourceManager;
import com.tacz.guns.mcshim.SimplePreparableReloadListener;

/**
 * 粒子定义资源加载器。
 * <p>
 * 扫描 {@code assets/<namespace>/particle_definitions/<name>.json} 路径，
 * 解析基岩版粒子效果定义并缓存。
 * <p>
 * 每个粒子效果定义在加载时使用共享的 {@link ParticleMolangEnvironment} 编译 Molang 表达式。
 */
public class ParticleDefinitionLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {

    private static final String DIRECTORY = "particle_definitions";
    private static final ParticleEffectDeserializer DESERIALIZER = new ParticleEffectDeserializer();

    private static ParticleDefinitionLoader INSTANCE;

    private final ParticleMolangEnvironment molang;
    private final Map<ResourceLocation, ParticleEffectDefinition> cache = Maps.newHashMap();
    private final Map<ResourceLocation, ParticleEffectDefinition> identifierIndex = Maps.newHashMap();

    public ParticleDefinitionLoader(ParticleMolangEnvironment molang) {
        this.molang = molang;
    }

    public static ParticleDefinitionLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ParticleDefinitionLoader(new ParticleMolangEnvironment());
        }
        return INSTANCE;
    }

    /**
     * 1.7.10 移植修正（SBM vendor）：{@code mcshim.ResourceManager} 的 id 推导规则与 1.20 不同 ——
     * 它<b>已经把扩展名剥掉</b>，且 id 的 path 仍然带着目录段
     * （{@code assets/tacz/particle_definitions/muzzle_flash.particle.json}
     * → {@code tacz:particle_definitions/muzzle_flash.particle}，见
     * {@code mcshim/ResourceManager.java:120-151} 的 {@code idFromPath}）。
     * 上游这里写的 {@code loc.getResourcePath().endsWith(".json")} 与
     * {@code path.substring(DIRECTORY.length() + 1, path.length() - 5)} 在 1.7.10 下会把
     * <b>全部</b>定义过滤掉（前者恒 false），即使侥幸通过也会把名字截错。
     * 因此改成「剥离目录前缀 + 可选地剥 .json 后缀」的稳健写法，两种管理器形态都能用。
     */
    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> result = Maps.newHashMap();
        resourceManager.listResources(DIRECTORY, loc -> nameOf(loc) != null)
            .forEach((location, resource) -> {
                String name = nameOf(location);
                if (name == null) {
                    return;
                }
                try (InputStream stream = resource.open()) {
                    // TODO(L2): 上游用 gson 2.10 的静态 JsonParser.parseReader(Reader)；
                    // MC 1.7.10 自带 gson 2.2.4 只有实例方法 new JsonParser().parse(Reader)，语义相同。
                    JsonElement json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    result.put(new ResourceLocation(location.getResourceDomain(), name), json);
                } catch (IOException | RuntimeException e) {
                    SimpleBedrockModel.LOGGER.error("Failed to read particle definition: {}", location, e);
                }
            });
        return result;
    }

    /**
     * 资源 id → 定义名（{@code tacz:particle_definitions/muzzle_flash[.json]} → {@code muzzle_flash}）。
     *
     * @return 不在 {@code particle_definitions/} 下、或去掉目录/后缀后为空时返回 {@code null}
     */
    @Nullable
    private static String nameOf(ResourceLocation location) {
        String path = location.getResourcePath();
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        if (!path.startsWith(DIRECTORY + "/")) {
            return null;
        }
        String name = path.substring(DIRECTORY.length() + 1);
        return name.isEmpty() || name.indexOf('.') >= 0 ? null : name;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager resourceManager,
        ProfilerFiller profiler) {
        applyPrepared(prepared);
    }

    /**
     * 1.7.10 移植补充（SBM vendor）：由 TACZ 的枪包重载链（{@code IGunPackReloadListener}）直接把
     * 「已扫描到的 JSON」灌进缓存，绕过 {@link #prepare}／{@link #apply} 这两段式。
     *
     * <p>
     * 必要性：上游 SBM 是一个 Forge 模组，在
     * {@code RegisterClientReloadListenersEvent} 里把自己注册进原版资源重载管线；本工程把 SBM
     * <b>vendor 成库</b>（{@code SimpleBedrockModel} 只保留常量，见该类 javadoc），没有任何代码
     * 驱动 {@code ParticleDefinitionLoader} 的 reload —— 若不给这个入口，{@link #getDefinition} 恒返回
     * {@code null}，{@code MuzzleParticleManager}／{@code ThirdPersonMuzzleParticleManager} 建不出发射器，
     * <b>renew 型枪械的第一/第三人称枪口火光与枪烟全部不显示</b>。
     */
    public void applyPrepared(Map<ResourceLocation, JsonElement> prepared) {
        cache.clear();
        identifierIndex.clear();
        prepared.forEach((id, json) -> {
            try {
                ParticleEffectDefinition definition = DESERIALIZER.parse(json, molang);
                if (definition != null) {
                    cache.put(id, definition);
                    identifierIndex.put(definition.getIdentifier(), definition);
                    SimpleBedrockModel.LOGGER.debug("Loaded particle definition: {}", id);
                }
            } catch (Exception e) {
                SimpleBedrockModel.LOGGER.error("Failed to parse particle definition: {}", id, e);
            }
        });
        SimpleBedrockModel.LOGGER.info("Loaded {} particle definitions", cache.size());
    }

    @Nullable
    public ParticleEffectDefinition getDefinition(ResourceLocation id) {
        ParticleEffectDefinition def = cache.get(id);
        if (def != null) return def;
        return identifierIndex.get(id);
    }

    public Map<ResourceLocation, ParticleEffectDefinition> getAllDefinitions() {
        return Collections.unmodifiableMap(cache);
    }

    public ParticleMolangEnvironment getMolang() {
        return molang;
    }
}
