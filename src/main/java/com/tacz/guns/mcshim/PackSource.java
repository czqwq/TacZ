package com.tacz.guns.mcshim;

import java.io.InputStream;
import java.util.List;

import javax.annotation.Nullable;

/**
 * 「一组资源文件的来源」。1.7.10 侧对 1.20 {@code PackResources}（资源包/数据包）的<b>极简替代</b>。
 *
 * <h2>为什么在 mcshim（L0）里</h2>
 * TACZ 的资源加载链是：
 * {@code GunPackLoader}（L1，知道「枪包」的业务概念）→ 需要被 L0 的
 * {@link ResourceManager} / {@link FileToIdConverter} 统一消费。
 * 为了<b>不</b>让 L0 反向依赖 L1 的 {@code com.tacz.guns.resource}，这里放一个只有三个方法的极小接口，
 * L1 的实现去 {@code implements} 它。
 *
 * <h2>与 1.20 的对应</h2>
 * <table border="1">
 * <caption>方法对应</caption>
 * <tr>
 * <th>本接口</th>
 * <th>1.20 {@code PackResources}</th>
 * </tr>
 * <tr>
 * <td>{@link #getName()}</td>
 * <td>{@code packId()}</td>
 * </tr>
 * <tr>
 * <td>{@link #listFiles(String)}</td>
 * <td>{@code getResources(PackType, String, String, ResourceOutput)}</td>
 * </tr>
 * <tr>
 * <td>{@link #open(String)}</td>
 * <td>{@code getRootResource(String...)} / {@code open}</td>
 * </tr>
 * </table>
 *
 * <h2>实现方约定（重要）</h2>
 * <ul>
 * <li>{@link #listFiles(String prefix)} 返回的路径是<b>相对路径</b>，`/` 分隔，
 * <b>包含</b> datapack/资源包的根段：例如
 * {@code "data/tacz/index/guns/ak47.json"}、{@code "assets/tacz/tacz_gun/ak47.json"}。</li>
 * <li>返回顺序必须<b>稳定</b>（建议排序后返回），否则 {@code ResourceManager} 的「同名资源栈」
 * 顺序会随文件系统变化。</li>
 * <li>{@link #open(String)} 必须能打开 {@link #listFiles(String)} 返回过的任何路径；
 * 打不开时返回 {@code null}（不要抛异常）。</li>
 * </ul>
 */
public interface PackSource {

    /** 人类可读的名字，用于日志与「资源栈」排序/去重（例如枪包名或 {@code "builtin"}）。 */
    String getName();

    /**
     * 列举 {@code prefix} 下所有文件的完整相对路径。
     *
     * @param prefix 相对路径前缀（例如 {@code "data"}、{@code "assets/tacz"}）；传 {@code ""} 表示全部
     * @return 稳定顺序的路径列表（永不为 null）
     */
    List<String> listFiles(String prefix);

    /**
     * 按完整相对路径打开资源流。
     *
     * @return 打不开返回 {@code null}；调用方负责关闭
     */
    @Nullable
    InputStream open(String path);
}
