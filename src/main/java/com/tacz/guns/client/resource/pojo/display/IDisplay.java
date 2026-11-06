package com.tacz.guns.client.resource.pojo.display;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.resource.pack.PackDirectory;

/**
 * 这个接口其实是妥协用的，用于将旧的texture路径转换为新的路径<br/>
 * 在反序列化完成后，会调用init方法，将所有的路径转换为新的路径
 */
public interface IDisplay {

    PackDirectory converter = PackDirectory.of("textures", ".png");

    void init();

    /**
     * 对应 1.20 {@code FileToIdConverter#idToFile(ResourceLocation)}：把 display JSON 里的贴图 id
     * 转成真实贴图路径，例如 {@code tacz:ak47} → {@code tacz:textures/ak47.png}。
     * <p>
     * TODO(port): 上游写成 {@code IDisplay.converter.idToFile(texture)}（{@code converter} 是 1.20 的
     * {@code FileToIdConverter}）。本工程的 {@code converter} 改成了纯数据载体 {@link PackDirectory}
     * （只有 prefix/extension，没有 idToFile），因此转换逻辑放在这个静态方法里，
     * 各 display POJO 的 {@code init()} 调用 {@code IDisplay.idToFile(...)}。语义与 1.20 逐字一致。
     */
    static ResourceLocation idToFile(ResourceLocation id) {
        String prefix = converter.prefix();
        String path = prefix.isEmpty() ? id.getResourcePath() : prefix + "/" + id.getResourcePath();
        return new ResourceLocation(id.getResourceDomain(), path + converter.extension());
    }
}
