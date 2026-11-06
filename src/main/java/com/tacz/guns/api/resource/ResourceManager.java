package com.tacz.guns.api.resource;

import java.nio.file.Paths;
import java.util.List;

import com.google.common.collect.Lists;
import com.tacz.guns.GunMod;

/**
 * 用于注册需要解压释放的枪包，为其他附属模组使用。
 * <p>
 * <b>1.7.10 契约（与上游 1.20.1 不同）</b>：上游枪包进入原版资源仓库（{@code AddPackFindersEvent} +
 * {@code mod_resources} pack）后再扫描，所以「把 assets/data 直接内置进模组 jar」即可生效；
 * 1.7.10 没有 datapack / 资源仓库回调，本工程的枪包只从 {@code <gamedir>/tacz/**} 的目录或 zip 发现
 * （{@code resource/GunPackLoader} + {@code IGunPackSource}）。因此附属模组**必须**调用
 * {@link #registerExportResource(Class, String)}，让本模组在首次启动时把 jar 内的枪包目录解压到
 * {@code <gamedir>/tacz/<目录名>/} 后再参与扫描；只把枪包文件放进自己 jar 而不同步导出，枪包不会被加载。
 */
public final class ResourceManager {

    /**
     * 存放所有待解压枪包路径
     */
    public static final List<ExtraEntry> EXTRA_ENTRIES = Lists.newArrayList();

    /**
     * @deprecated 旧的文件入口不再生效：1.7.10 无资源仓库回调，无法「直接把 assets/data 内置在模组 jar 中」被扫描到；
     *             请改用 {@link #registerExportResource(Class, String)} 把枪包目录解压到 {@code <gamedir>/tacz/}。
     *
     * @param modMainClass    附属模组的主类
     * @param extraFolderPath 需要解压的文件夹，比如 TACZ 自己就是 /assets/tacz/custom/tacz_default_gun <br>
     *                        这表示把 tacz_default_gun 文件夹解压出来，放置到枪械包安装目录下
     */
    @Deprecated
    public static void registerExtraGunPack(Class<?> modMainClass, String extraFolderPath) {
        GunMod.LOGGER.warn(
            "some mod is using deprecated method to export gun pack, notifying the mod author to update it: {}",
            extraFolderPath);
    }

    /**
     * 注册「从模组 jar 内导出枪包目录」：首次加载（且未启用 {@code overwrite} 配置）时，把
     * {@code modMainClass} 所属模组 jar/classpath 中的 {@code extraFolderPath} 目录解压到
     * {@code <gamedir>/tacz/<目录名>/}，随后由枪包扫描器加载。
     * <p>
     * 对应上游 1.20.1 的同名方法（上游由 {@code GunPackLoader#discoverExtensions} 遍历 {@link #EXTRA_ENTRIES}
     * 后经 {@code PathPackResources} 挂进资源仓库）。1.7.10 的落地点是
     * {@code resource/GunPackLoader#reloadPackList()} 首轮调用 {@code GetJarResources.copyModDirectory}。
     *
     * @param modMainClass    提供资源读取的附属模组主类（用于定位该模组的 jar/classpath）
     * @param extraFolderPath 模组内枪包目录，如 {@code /assets/mymod/custom/my_gun_pack}
     */
    public static void registerExportResource(Class<?> modMainClass, String extraFolderPath) {
        EXTRA_ENTRIES.add(
            new ExtraEntry(
                modMainClass,
                extraFolderPath,
                Paths.get(extraFolderPath)
                    .getFileName()
                    .toString()));
    }

    /**
     * 解压条目
     */
    public record ExtraEntry(Class<?> modMainClass, String srcPath, String extraDirName) {}
}
