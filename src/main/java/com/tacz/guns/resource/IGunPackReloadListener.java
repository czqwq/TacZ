package com.tacz.guns.resource;

/**
 * 枪包数据重载监听器。
 *
 * <p>
 * 上游 1.20.1 里所有管理器都实现 {@code net.minecraft.server.packs.resources.SimplePreparableReloadListener}，
 * 通过 {@code AddReloadListenerEvent#addListener} 挂到原版资源重载管线（两段式 prepare/apply + 后台线程池），
 * 参考 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/CommonAssetsManager.java:94-133}。
 *
 * <p>
 * 1.7.10 没有 datapack / {@code PreparableReloadListener} / {@code ProfilerFiller}，
 * 也没有「服务端资源重载」这件事，因此这里把两段式折叠成单段同步 {@link #reload()}：
 * 由 {@link CommonAssetsManager#reload()} 按上游注册顺序**串行**调用，
 * 保证 index 类数据（依赖 data 类数据）在上游的先后关系不变。
 */
public interface IGunPackReloadListener {

    /** 重新扫描并装载该管理器负责的数据。要求可重复调用（每次先清空旧数据）。 */
    void reload();
}
