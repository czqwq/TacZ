package com.tacz.guns.config;

import java.io.File;
import java.nio.file.Path;

/**
 * 预加载配置，对应上游 1.20.1
 * {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/PreLoadConfig.java}。
 *
 * <p>
 * 上游用 {@code ModConfig} + {@code CommentedFileConfig} 读一份「比标准 Forge 配置更早加载」的独立配置
 * （{@code <gamedir>/tacz/tacz-pre.toml}），用来决定「是否保留用户对默认枪包目录的改动」。
 * 1.7.10 没有 {@code ModConfig}/{@code nightconfig}，这里用一份独立的 {@link TaczConfigSpec}
 * （{@link net.minecraftforge.common.config.Configuration}）实现同样语义，文件改为
 * {@code <gamedir>/tacz/tacz-pre.cfg}（扩展名差异来自 1.7.10 的 cfg 格式）。
 *
 * <p>
 * {@code Loader}（上游 {@code ConfigTracker}）的「从默认目录移除，避免重复加载」逻辑在 1.7.10 不需要：
 * 这份配置由 {@link #load(Path)} 独家持有，不参与 {@code config/} 目录的加载。
 */
public class PreLoadConfig {

    public static final String FILE_NAME = "tacz-pre.cfg";
    /** 上游 {@code FMLPaths.GAMEDIR}/tacz，1.7.10 的工作目录就是游戏目录 */
    public static final String DEFAULT_DIR_NAME = "tacz";

    private static TaczConfigSpec spec;
    private static File currentFile;
    public static BooleanConfigValue override;

    static {
        // 默认位置先建好，保证任何在 load() 之前读 override 的代码（例如兼容层）拿到的是默认值而不是 NPE
        create(new File(new File("."), DEFAULT_DIR_NAME + File.separator + FILE_NAME));
    }

    private static void create(File file) {
        currentFile = file.getAbsoluteFile();
        spec = new TaczConfigSpec(currentFile);
        ConfigBuilder builder = new ConfigBuilder(spec);
        builder.push("gunpack");
        builder.comment(
            "Enable to preserve local default gun pack edits.",
            "When disabled, changes to the TACZ version or bundled resources trigger replacement at startup.",
            "To regenerate, disable this option, delete <game directory>/tacz/tacz_default_gun, then restart.");
        override = builder.define("DefaultPackDebug", false);
        builder.pop();
        spec = builder.build();
    }

    /**
     * 加载（或重新加载）预加载配置。由 {@code GunPackLoader#discoverExtensions} 在扫描枪包前调用，
     * 因此本方法必须早于任何依赖 {@link #override} 的逻辑。
     *
     * @param configBasePath 枪包根目录（上游同签名），配置文件名固定为 {@value #FILE_NAME}
     */
    public static void load(Path configBasePath) {
        File file = configBasePath.resolve(FILE_NAME)
            .toFile()
            .getAbsoluteFile();
        if (spec == null || !file.equals(currentFile)) {
            create(file);
        }
        if (spec.isLoaded()) {
            return;
        }
        spec.load();
        spec.save();
    }

    public static void save() {
        if (spec != null) {
            spec.save();
        }
    }

    public static boolean isLoaded() {
        return spec != null && spec.isLoaded();
    }

    public static File getFile() {
        return currentFile;
    }

    private PreLoadConfig() {}
}
