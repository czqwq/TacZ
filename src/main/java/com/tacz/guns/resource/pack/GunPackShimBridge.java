package com.tacz.guns.resource.pack;

import java.util.List;
import java.util.function.Supplier;

import com.tacz.guns.mcshim.PackSource;
import com.tacz.guns.mcshim.ResourceManager;

/**
 * 枪包来源（L1 {@code resource/pack}）与 L0 资源垫片 {@link ResourceManager} 之间的唯一接线点。
 *
 * <p>
 * 为什么单独成类：{@code resource.GunPackLoader} 已经导入了 {@code com.tacz.guns.api.resource.ResourceManager}
 * （读 {@code EXTRA_ENTRIES}），与 {@code com.tacz.guns.mcshim.ResourceManager} 同名，无法在同一文件里同时 import。
 * 把跨层调用收在这里，既避免了内联全限定名，也让「谁把枪包列表交给 mcshim」只有一处可查。
 *
 * <p>
 * 移植特有：上游 1.20.1 由原版 {@code PackRepository} 统一持有 pack 列表，不存在这个接线点。
 */
public final class GunPackShimBridge {

    private GunPackShimBridge() {}

    /**
     * 把枪包来源注入 L0 垫片并让其重建索引。
     *
     * @param supplier 每次取值都返回「当前」枪包来源列表，重扫后无需重新注入也能读到新列表
     */
    public static void install(Supplier<List<PackSource>> supplier) {
        ResourceManager.setPackSources(supplier);
        new ResourceManager().invalidate();
    }
}
