package com.tacz.guns.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.FileToIdConverter;
import com.tacz.guns.mcshim.GsonHelper;
import com.tacz.guns.mcshim.Resource;
import com.tacz.guns.mcshim.ResourceManager;

/**
 * 扫描「目录下所有 json」，允许注释，同路径只读取优先级最高的一份。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游依赖 1.20 的 {@code net.minecraft.server.packs.resources.ResourceManager} /
 * {@code net.minecraft.resources.FileToIdConverter} / {@code net.minecraft.server.packs.resources.Resource}
 * 与 {@code net.minecraft.util.GsonHelper}。1.7.10 <b>没有</b> datapack/ResourceManager 体系
 * （客户端只有 {@code IResourceManager}，且看不到 {@code data/**}），因此改用本工程的
 * {@link ResourceManager} / {@link FileToIdConverter} / {@link Resource} / {@link GsonHelper} 垫片：
 * 数据源由 {@code resource.pack.IGunPackSource} 注入，语义与上游一致（见各垫片类的 javadoc）。</li>
 * <li><b>键语义差异</b>：上游 {@code FileToIdConverter#listMatchingResources} 的键是「文件 id」，
 * 需要再调 {@code fileToId}；1.7.10 垫片的 {@code listMatchingResources} 已经返回
 * <b>转换后的 id</b>，所以本文件不再重复调用 {@code fileToId}（重复调用也幂等，但少一次转换更清楚）。
 * 原始文件 id 从 {@link Resource#id()} 取，仅用于日志。</li>
 * <li>{@code Resource#openAsReader()} 在 1.7.10 垫片里没有，改为
 * {@code new InputStreamReader(resource.open(), StandardCharsets.UTF_8)}（上游默认 UTF-8，语义一致）。</li>
 * <li>{@code listMatchingResourceStacks} 在 1.7.10 垫片里叫
 * {@code ResourceManager#listResourceStacks(String, Predicate)}，因此 {@link #scanDirectoryAll}
 * 直接调它、再对键做一次 {@code fileToId}。</li>
 * <li>{@code net.minecraft.util.GsonHelper} → {@link GsonHelper}（同签名
 * {@code fromJson(Gson, Reader, Class, boolean lenient)}）。</li>
 * <li><b>1.7.10 现状</b>：{@code resource/pack/GunPackResources} 已经提供了等价的
 * 「按目录扫描 json」能力（它自己就是本类的 1.7.10 替代），且本类在 1.7.10 侧<b>没有调用点</b>，
 * 仅为保持与上游的类/方法形状而移植。新代码请直接用 {@code GunPackResources}。</li>
 * </ul>
 *
 * @deprecated 1.7.10 请使用 {@code com.tacz.guns.resource.pack.GunPackResources}；本类仅为与上游保持形状。
 */
@Deprecated
public class ResourceScanner {

    /**
     * 扫描指定目录下的所有json文件<br>
     * 与原版的scanDirectory方法的区别在于，查询结果是作为返回值返回的，而且允许注释 对于相同的文件路径，只读取优先级最高的文件
     *
     * @param pResourceManager 资源管理器
     * @param pName            目录名
     * @param pGson            Gson实例
     * @return 扫描到的json文件
     */
    public static Map<ResourceLocation, JsonElement> scanDirectory(ResourceManager pResourceManager, String pName,
        Gson pGson) {
        return scanDirectory(pResourceManager, FileToIdConverter.json(pName), pGson);
    }

    public static Map<ResourceLocation, JsonElement> scanDirectory(ResourceManager pResourceManager,
        FileToIdConverter filetoidconverter, Gson pGson) {
        Map<ResourceLocation, JsonElement> output = Maps.newHashMap();
        for (Map.Entry<ResourceLocation, Resource> entry : filetoidconverter.listMatchingResources(pResourceManager)
            .entrySet()) {
            // 1.7.10 垫片返回的键已经是 fileToId 之后的结果，值里保留未转换的文件 id 供日志使用。
            ResourceLocation resourcelocation1 = entry.getKey();
            ResourceLocation resourcelocation = entry.getValue()
                .id();

            try (Reader reader = openReader(entry.getValue())) {
                JsonElement jsonelement = GsonHelper.fromJson(pGson, reader, JsonElement.class, true);
                JsonElement jsonelement1 = output.put(resourcelocation1, jsonelement);
                if (jsonelement1 != null) {
                    throw new IllegalStateException("Duplicate data file ignored with ID " + resourcelocation1);
                }
            } catch (IllegalArgumentException | IOException | JsonParseException jsonparseexception) {
                GunMod.LOGGER.error(
                    "Couldn't parse data file {} from {}",
                    resourcelocation1,
                    resourcelocation,
                    jsonparseexception);
            }
        }
        return output;
    }

    public static Map<ResourceLocation, ResourceLocation> scanDirectoryResources(ResourceManager pResourceManager,
        FileToIdConverter filetoidconverter) {
        Map<ResourceLocation, ResourceLocation> output = Maps.newHashMap();
        for (Map.Entry<ResourceLocation, Resource> entry : filetoidconverter.listMatchingResources(pResourceManager)
            .entrySet()) {
            ResourceLocation rawLocation = entry.getValue()
                .id();
            ResourceLocation id = entry.getKey();
            ResourceLocation old = output.put(id, rawLocation);
            if (old != null) {
                throw new IllegalStateException("Duplicate data file ignored with ID " + id);
            }
        }
        return output;
    }

    /**
     * 扫描指定目录下的所有json文件<br/>
     * 与{@link #scanDirectory(ResourceManager, String, Gson)}不同的是，该方法会读取所有json文件作为列表返回
     *
     * @param pResourceManager  资源管理器
     * @param filetoidconverter 文件路径和id的映射
     * @param pGson             Gson实例
     * @return 扫描到的json文件
     */
    public static Map<ResourceLocation, List<JsonElement>> scanDirectoryAll(ResourceManager pResourceManager,
        FileToIdConverter filetoidconverter, Gson pGson) {
        Map<ResourceLocation, List<JsonElement>> output = Maps.newHashMap();
        Map<ResourceLocation, List<Resource>> stacks = pResourceManager
            .listResourceStacks(filetoidconverter.prefix(), null);
        for (Map.Entry<ResourceLocation, List<Resource>> entry : stacks.entrySet()) {
            ResourceLocation resourcelocation = entry.getKey();
            ResourceLocation resourcelocation1 = filetoidconverter.fileToId(resourcelocation);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = openReader(resource)) {
                    JsonElement jsonelement = GsonHelper.fromJson(pGson, reader, JsonElement.class, true);
                    List<JsonElement> list = output.computeIfAbsent(resourcelocation1, k -> Lists.newArrayList());
                    list.add(jsonelement);
                } catch (IllegalArgumentException | IOException | JsonParseException jsonparseexception) {
                    GunMod.LOGGER.error(
                        "Couldn't parse data file {} from {}",
                        resourcelocation1,
                        resourcelocation,
                        jsonparseexception);
                }
            }
        }
        return output;
    }

    /** 上游 {@code Resource#openAsReader()}（默认 UTF-8）的 1.7.10 等价物。 */
    private static Reader openReader(Resource resource) {
        return new InputStreamReader(resource.open(), StandardCharsets.UTF_8);
    }
}
