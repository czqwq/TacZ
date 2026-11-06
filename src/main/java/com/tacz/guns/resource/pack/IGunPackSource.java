package com.tacz.guns.resource.pack;

import com.tacz.guns.mcshim.PackSource;

/**
 * 一个枪包（gun pack）的只读视图。
 *
 * <p>
 * 上游 1.20.1 把每个枪包包成 {@code net.minecraftforge.resource.PathPackResources} /
 * {@code net.minecraft.server.packs.PackResources}，交给原版 {@code PackRepository} 与
 * {@code ResourceManager} 统一列举与读取（参考
 * {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/GunPackLoader.java:96-134}）。
 * 1.7.10 既没有 {@code PackResources} / {@code ResourceManager} 的那套列举 API，也没有数据包（datapack），
 * 因此这里只复刻上游真正用到的两件事：<b>按目录列举文件</b>与<b>按路径读取文件</b>。
 *
 * <p>
 * 本接口按 lead 裁决继承 L0 层 {@link com.tacz.guns.mcshim.PackSource}，
 * 使「枪包数据源」成为 mcshim（L0）与 resource（L1）之间的唯一契约：
 * {@code mcshim.ResourceManager} 通过 {@link com.tacz.guns.mcshim.ResourceManager#setPackSources}
 * 拿到同一份枪包列表，供 client-port 的 {@code client/resource/**} 复用，
 * 全仓不会出现第二套枪包扫描逻辑。依赖方向是 resource → mcshim，mcshim 不反向依赖。
 *
 * <p>
 * 方法语义（定义在 {@code mcshim.PackSource}）：
 * <ul>
 * <li>{@code getName()}：包名，仅用于日志与排序，通常是枪包目录名 / zip 文件名。</li>
 * <li>{@code listFiles(String prefix)}：列举 {@code prefix} 下所有文件的完整相对路径，
 * 以 {@code '/'} 分隔（如 {@code "data/tacz/index/guns/ak47.json"}）；传空串表示整个包；
 * 返回顺序固定（字典序），保证加载结果可复现。</li>
 * <li>{@code open(String path)}：按完整相对路径打开流，不存在返回 {@code null}。</li>
 * </ul>
 *
 * <p>
 * 1.7.10 差异说明：这里刻意<b>不</b>依赖 {@code net.minecraft.client.resources.IResourceManager}。
 * 原版 1.7.10 的 {@code IResourceManager} 只能看到 {@code assets/} 下的资源包内容，
 * 看不到 {@code <gamedir>/tacz/**} 这类枪包安装目录，也无法提供 {@code data/} 侧数据，
 * 用它反而会丢掉枪包加载能力。
 *
 * <p>
 * 使用方：服务端 {@code com.tacz.guns.resource.CommonAssetsManager}（读 {@code data/}）、
 * 客户端 {@code com.tacz.guns.client.resource.ClientAssetsManager}（读 {@code assets/}）。
 */
public interface IGunPackSource extends PackSource {
}
