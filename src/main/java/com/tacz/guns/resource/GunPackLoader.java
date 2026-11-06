package com.tacz.guns.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringTranslate;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.resource.ResourceManager;
import com.tacz.guns.config.PreLoadConfig;
import com.tacz.guns.mcshim.PackSource;
import com.tacz.guns.resource.pack.DirectoryGunPackSource;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.GunPackShimBridge;
import com.tacz.guns.resource.pack.IGunPackSource;
import com.tacz.guns.resource.pack.ZipGunPackSource;
import com.tacz.guns.util.GetJarResources;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;
import cpw.mods.fml.relauncher.FMLInjectionData;

/**
 * 枪包（gun pack）发现器。
 *
 * <p>
 * 上游 1.20.1 的形态是 {@code public enum GunPackLoader implements RepositorySource}：注册成原版资源仓库来源
 * （{@code AddPackFindersEvent#addRepositorySource}），由 {@code PackRepository} 回调 {@code loadPacks}，再把每个
 * 枪包包成 {@code PathPackResources} / {@code SecureJar} 并统一塞进一个 {@code NamespaceFilteredPackResources}
 * （tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/GunPackLoader.java:50-134）。
 *
 * <p>
 * 1.7.10 移植要点：{@code RepositorySource} / {@code PackRepository} / {@code PackResources} / {@code SecureJar} /
 * {@code IoSupplier} 与 datapack 全都不存在 ⇒ 保留上游「目录/zip 扫描 + {@code gunpack.meta.json} 版本依赖校验 +
 * 默认枪包导出」这套主体逻辑，只把「注册进原版资源仓库」换成「把扫描结果以 {@link IGunPackSource} 列表暴露出来」，
 * 由 {@link CommonAssetsManager}（服务端，读 {@code data/}）与 {@code client.resource.ClientAssetsManager}
 * （客户端，读 {@code assets/}）各自消费。
 *
 * <p>
 * 调用时机（取代上游的 pack 仓库回调）：{@code CommonProxy#preInit} 里调用 {@link #reloadPackList()}（建目录、读
 * {@code PreLoadConfig}、导出内置默认枪包、扫描全部枪包），必须早于第一次 {@link CommonAssetsManager#reload()}；
 * 服务端启动时调用 {@link CommonAssetsManager#reload()}。
 *
 * <p>
 * <b>与上游的已知差异（有据降级）</b>：本类<b>只认</b> {@code <gamedir>/tacz/} 下的目录/zip 两种来源
 * （{@link IGunPackSource}）；上游因为走原版资源管理器，模组 jar 内直接摆的
 * {@code assets/<ns>/display|geo_models|animations/...} 与 {@code data/<ns>/index|data/...} 也会被扫到。
 * 1.7.10 没有「每个模组 jar 都是数据包/资源包」的统一栈，因此附属模组必须用
 * {@code api.resource.ResourceManager#registerExportResource} 把内容导出到 {@code <gamedir>/tacz/} 后再由本类扫描；
 * TACZ 自身 jar 根的 {@code assets/tacz/**} 仍可由原版 {@code IResourceManager} 寻址（Forge 会把模组注册成资源包，
 * 见 1.7.10 {@code FMLClientHandler#beginMinecraftLoading}），所以 {@code InternalAssetLoader} 那批内置模型/默认动画
 * 不受影响。
 */
public enum GunPackLoader {

    INSTANCE;

    private static final Marker MARKER = MarkerManager.getMarker("GunPackFinder");

    /** 枪包安装目录名，位于游戏根目录下（对应上游 {@code FMLPaths.GAMEDIR.get().resolve("tacz")}）。 */
    public static final String PACK_DIR_NAME = "tacz";

    /**
     * 作用侧。上游在 {@code GunMod} 构造器里按 {@code Dist} 赋值，1.7.10 保留同名字段以对齐调用点，
     * 语义见 {@link GunPackType}。默认按当前实际侧取值，{@code CommonProxy} 可覆盖。
     */
    public GunPackType packType = GunPackType.current();

    private boolean firstLoad = true;
    private List<GunPack> packs = Collections.emptyList();
    private List<IGunPackSource> packSources = Collections.emptyList();

    /**
     * 上一次扫描得到的「包路径 → 文件源」句柄，用于跨重扫复用同一份 {@link IGunPackSource}。
     *
     * <p>
     * <b>为什么必须复用（1.7.10 单人游戏平台差异）</b>：单人游戏里客户端与服务端共用同一个
     * {@link GunPackLoader} 静态实例，但两侧会各自触发一次 {@link #reloadPackList()}（客户端
     * {@code ClientAssetsManager#reloadAllPack} 第一步 + 集成服 {@code CommonAssetsManager#reloadAllPack}）。
     * 旧实现每次重扫都会 {@code close()} 上一轮的 {@link ZipGunPackSource}，而客户端数据管理器
     * （{@code LazyJsonDataManager} 的懒加载器）手里还握着上一轮扫描得到的「source + 包内路径」，
     * 等到集成服那条重扫把 zip 关掉后，懒加载再 {@code open()} 就返回 {@code null}：
     * 表现为 {@code Failed to read raw data file assets/<ns>/geo_models/...}、
     * {@code there is no corresponding model file}，自定义枪包（zip 形态）的所有枪无模型（第一人称不渲染、
     * 第三人称退化成物品图标）。
     *
     * <p>
     * 复用判定：目录源（实时读文件，无句柄）恒复用；zip 源仅当文件 mtime/大小未变时复用，
     * 这样玩家替换同名 zip 后再次 {@code /tacz reload} 仍能读到新内容。
     */
    private Map<Path, SourceHandle> sourceHandles = new HashMap<>();

    /**
     * 重扫枪包目录。对应上游 {@code GunPackLoader#discoverExtensions}（含默认枪包导出与
     * {@code PreLoadConfig} 加载），可重复调用；每次调用会释放上一次打开的 zip 句柄。
     */
    public synchronized void reloadPackList() {
        Path resourcePacksPath = getPackDirectory();
        File folder = resourcePacksPath.toFile();
        if (!folder.isDirectory()) {
            try {
                Files.createDirectories(folder.toPath());
            } catch (Exception exception) {
                GunMod.LOGGER.warn(MARKER, "Failed to init tacz resource directory...", exception);
                closeAllSources();
                packs = Collections.emptyList();
                packSources = Collections.emptyList();
                return;
            }
        }

        // 确保配置文件加载，这个阶段将比标准的 forge 配置文件加载早
        // （1.7.10 保留上游时序差异：PreLoadConfig 由此处首次触发加载，而不是 Forge 配置阶段）
        PreLoadConfig.load(resourcePacksPath);

        // 仅在启动后的第一次加载时检查默认资源包是否需要导出；后续重载不重新导出
        if (firstLoad) {
            // 1.7.10 差异：PreLoadConfig 由本工程自研的 BooleanConfigValue 承载，get() 返回 Boolean，
            // 显式剥一次 null（配置项异常时按「不覆盖」处理，避免首次启动 NPE）
            Boolean override = PreLoadConfig.override.get();
            if (!Boolean.TRUE.equals(override)) {
                for (ResourceManager.ExtraEntry entry : ResourceManager.EXTRA_ENTRIES) {
                    GetJarResources.copyModDirectory(
                        entry.modMainClass(),
                        entry.srcPath(),
                        resourcePacksPath,
                        entry.extraDirName());
                }
            }
            firstLoad = false;
        }

        GunMod.LOGGER.info(MARKER, "Start scanning for gun packs in {}", resourcePacksPath);
        List<GunPack> scanned = scanExtensions(resourcePacksPath);
        GunMod.LOGGER.info(MARKER, "Found {} possible gunpack(s) and added them to resource set.", scanned.size());

        List<IGunPackSource> sources = new ArrayList<>(scanned.size());
        Map<Path, SourceHandle> handles = new HashMap<>(scanned.size());
        for (GunPack gunPack : scanned) {
            Path path = gunPack.path()
                .toAbsolutePath()
                .normalize();
            boolean directory = Files.isDirectory(gunPack.path());
            long lastModified = 0L;
            long size = 0L;
            if (!directory) {
                try {
                    lastModified = Files.getLastModifiedTime(gunPack.path())
                        .toMillis();
                    size = Files.size(gunPack.path());
                } catch (IOException exception) {
                    // 读不到时间戳就当作「变了」，宁可新开句柄也不要复用陈旧内容
                    lastModified = 0L;
                    size = -1L;
                }
            }
            SourceHandle previous = sourceHandles.get(path);
            SourceHandle handle;
            if (previous != null && previous.canReuse(directory, lastModified, size)) {
                handle = previous;
            } else if (directory) {
                handle = new SourceHandle(new DirectoryGunPackSource(gunPack.path(), gunPack.name()), true, 0L, 0L);
            } else {
                handle = new SourceHandle(
                    new ZipGunPackSource(gunPack.path(), gunPack.name()),
                    false,
                    lastModified,
                    size);
            }
            handles.put(path, handle);
            sources.add(handle.source());
        }
        // 只释放「被删除或被替换」的旧 zip 句柄；可复用的旧源已经带到 handles 里继续服务懒加载
        for (Map.Entry<Path, SourceHandle> entry : sourceHandles.entrySet()) {
            if (handles.get(entry.getKey()) != entry.getValue()) {
                closeSource(
                    entry.getValue()
                        .source());
            }
        }
        this.sourceHandles = handles;
        this.packs = List.copyOf(scanned);
        this.packSources = List.copyOf(sources);

        // 把枪包列表交给 L0 层的 mcshim.ResourceManager（lead 裁决的三方共用契约）：
        // 这样 client-port 的 client/resource/** 与资源侧的 manager 用的是同一份枪包视图
        // （唯一生效的扫描路径；util.ResourceScanner 是已废弃的 0 调用点孪生实现）。
        // mcshim 不反向依赖 resource，依赖方向仍是 resource → mcshim。
        GunPackShimBridge.install(GunPackLoader::packSourcesForShim);
    }

    /** 供 {@code mcshim.ResourceManager#setPackSources} 用的视图（元素类型上移到 L0 的 {@code PackSource}）。 */
    private static List<PackSource> packSourcesForShim() {
        return List.copyOf(INSTANCE.getPackSources());
    }

    /** 已扫描到的枪包列表（含路径与主命名空间）。 */
    public List<GunPack> getPacks() {
        return packs;
    }

    /**
     * 已扫描到的枪包读取入口，按包名排序。
     * 服务端读 {@code data/<ns>/**}，客户端读 {@code assets/<ns>/**}。
     */
    public List<IGunPackSource> getPackSources() {
        if (packSources.isEmpty() && firstLoad) {
            // 上游 loadPacks 由原版在合适的时机回调；1.7.10 若调用方忘了显式调用，
            // 这里兜底扫一次，避免「枪包一个都读不到」这种静默失败。
            reloadPackList();
        }
        return packSources;
    }

    /** 枪包安装根目录（{@code <gamedir>/tacz}）。对应上游 {@code FMLPaths.GAMEDIR.get().resolve("tacz")}。 */
    public static Path getPackDirectory() {
        return getGameDirectory().toPath()
            .resolve(PACK_DIR_NAME);
    }

    /**
     * 游戏根目录。
     *
     * <p>
     * 1.7.10 差异：上游用 {@code FMLPaths.GAMEDIR}。1.7.10 客户端在 {@code Minecraft#mcDataDir}，
     * 专用服务端的工作目录就是游戏根目录。这里取 FML 注入的 {@code minecraftHome}
     * （{@code FMLInjectionData#data()} 第 7 项），两侧通用且不会在服务端触发客户端类加载。
     */
    public static File getGameDirectory() {
        try {
            Object home = FMLInjectionData.data()[6];
            if (home instanceof File file) {
                return file;
            }
        } catch (Throwable ignored) {
            // 极端情况下（非 FML 启动，如单测）退化到工作目录
        }
        return new File(".");
    }

    /**
     * 把所有枪包 {@code data/<namespace>/<subDir>/**}{@code .json} 读成 id → JsonObject。
     *
     * <p>
     * 供 1.7.10 无 datapack 的数据（{@code recipes}、{@code advancements}、{@code loot_tables}、
     * {@code tags}）自己消费：上游这些内容由原版 {@code RecipeManager} / {@code AdvancementManager} /
     * loot modifier 消费，1.7.10 只能由模组代码读出来再注册。同 id 后扫到的包覆盖先扫到的。
     */
    public static Map<ResourceLocation, JsonObject> readPackData(String subDir) {
        Map<ResourceLocation, JsonObject> output = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : GunPackResources
            .scanDataDirectory(INSTANCE.getPackSources(), subDir, CommonAssetsManager.GSON)
            .entrySet()) {
            if (entry.getValue()
                .isJsonObject()) {
                output.put(
                    entry.getKey(),
                    entry.getValue()
                        .getAsJsonObject());
            } else {
                GunMod.LOGGER.warn(MARKER, "Gun pack data {} is not a json object, skipped", entry.getKey());
            }
        }
        return output;
    }

    /**
     * 把所有枪包里<b>全部语种</b>的 {@code assets/<namespace>/lang/<locale>.lang} 一次性合入 1.7.10 语言表。
     *
     * <p>
     * <b>不能只注入「当前语种」</b>：1.7.10 的语言表装配在 {@code LanguageManager#onResourceManagerReload}
     * （build/rfg/minecraft-src/java/net/minecraft/client/resources/LanguageManager.java:78-90）里调
     * {@code LanguageRegistry.instance().mergeLanguageTable(...)}（{@code :88}），而
     * {@code mergeLanguageTable}（LanguageRegistry.java:160-170）按 {@code modLanguageData.get(lang)} 取<b>当前
     * 语种</b>那张表「只补缺不覆盖」地并进 locale；{@code LanguageManager} 在 {@code Minecraft} 构造器
     * （Minecraft.java:520-521）里就是<b>第一个</b>注册的资源重载监听器，mod 的监听器只能排它后面 ⇒「切完语言那一刻
     * 再注入」在 1.7.10 <b>不可达</b>（除非 mixin {@code Locale#loadLocaleDataFiles}，见
     * {@code mixin/client/LanguageMixin}，而该 mixin 目前有意未列入 {@code mixins.tacz.json}）。
     *
     * <p>
     * 因此改成启动时把每个枪包的每个语种都灌进 {@code LanguageRegistry}：之后玩家切任何语种
     * {@code mergeLanguageTable} 都能取到对应那张表，不依赖任何事件时序、也不需要 mixin，代价只是一次性多解析几十个
     * 文本文件。内置默认枪包同样走这条路（其 {@code .lang} 随 {@code GetJarResources} 导出到
     * {@code <gamedir>/tacz/tacz_default_gun/assets/tacz/lang/}），与 FML 自己扫 jar 的结果一致，重复注入无害。
     *
     * <p>
     * 调用时机：客户端 preInit（{@code ClientSetupEvent#onClientSetup} 的一步），必须晚于
     * {@code GunPackLoader.INSTANCE.reloadPackList()}、早于加载期最后一次 {@code Minecraft#refreshResources()}
     * （{@code FMLClientHandler#finishMinecraftLoading} 先 initializeMods() 再 refreshResources()）。
     *
     * @return 注入的语种数量
     */
    public static int injectAllGunPackLanguages() {
        if (!FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            return 0;
        }
        Map<String, HashMap<String, String>> byLocale = new LinkedHashMap<>();
        List<IGunPackSource> sources = INSTANCE.getPackSources();
        // 两遍：先 1.7.10 的 .lang，再 1.20 的 .json（同键时 JSON 作为上游权威格式覆盖）
        for (String extension : new String[] { ".lang", ".json" }) {
            for (IGunPackSource source : sources) {
                for (String path : source.listFiles(GunPackResources.ASSETS_ROOT)) {
                    if (!path.endsWith(extension)) {
                        continue;
                    }
                    String locale = localeOfLangFile(path);
                    if (locale == null) {
                        continue;
                    }
                    Map<String, String> parsed = parseLangResource(source, path);
                    if (parsed != null && !parsed.isEmpty()) {
                        byLocale.computeIfAbsent(locale, key -> new HashMap<>())
                            .putAll(parsed);
                    }
                }
            }
        }
        int injected = 0;
        for (Map.Entry<String, HashMap<String, String>> entry : byLocale.entrySet()) {
            LanguageRegistry.instance()
                .injectLanguage(entry.getKey(), entry.getValue());
            injected++;
        }
        if (injected > 0) {
            GunMod.LOGGER
                .info(MARKER, "Injected gun pack localization for {} locale(s): {}", injected, byLocale.keySet());
        } else {
            GunMod.LOGGER.warn(MARKER, "No gun pack .lang file found; gun/attachment names will fall back to raw keys");
        }
        return injected;
    }

    /**
     * 从包内路径解析语种码（{@code assets/<ns>/lang/zh_CN.lang} → {@code zh_CN}）。
     *
     * <p>
     * 同时接受 1.20 枪包使用的 JSON 语言文件（{@code assets/<ns>/lang/zh_cn.json}），并归一化成
     * 1.7.10 语言表的语种码形状（小写语言 + 大写地区，如 {@code zh_cn → zh_CN}）。
     *
     * @return 不是语言文件 / 语种码形状不合法时返回 {@code null}
     */
    private static @Nullable String localeOfLangFile(String path) {
        String prefix = GunPackResources.ASSETS_ROOT + "/";
        if (path == null || !path.startsWith(prefix) || !path.contains("/lang/")) {
            return null;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String code;
        if (fileName.endsWith(".lang")) {
            code = fileName.substring(0, fileName.length() - ".lang".length());
        } else if (fileName.endsWith(".json")) {
            code = fileName.substring(0, fileName.length() - ".json".length());
        } else {
            return null;
        }
        return normalizeLocaleCode(code);
    }

    /**
     * 1.20 语言文件用 {@code en_us} / {@code zh_cn}，1.7.10 语言表用 {@code en_US} / {@code zh_CN}；
     * 本方法把前者归一成后者（首段小写、其余段大写）。
     *
     * @return 形状不合法（非字母数字/下划线）时返回 {@code null}
     */
    private static @Nullable String normalizeLocaleCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        String[] parts = code.split("_");
        StringBuilder builder = new StringBuilder(code.length());
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                return null;
            }
            if (i > 0) {
                builder.append('_');
            }
            builder.append(
                i == 0 ? parts[i].toLowerCase(java.util.Locale.ROOT) : parts[i].toUpperCase(java.util.Locale.ROOT));
        }
        String locale = builder.toString();
        // 只接受字母/数字/下划线，避免把任意文件当语种注入
        for (int i = 0; i < locale.length(); i++) {
            char c = locale.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return null;
            }
        }
        return locale;
    }

    /**
     * 读取一个枪包语言文件到键值表。
     *
     * <p>
     * 1.7.10 原版 {@code .lang} 走 {@link StringTranslate#parseLangFile}（语义见
     * {@link #injectGunPackLanguage} 注释）；1.20 枪包用 JSON（{@code {"key": "value"}}），
     * 这里用 Gson 解析。移植早期只认 {@code .lang}，导致新增的 1.20 自定义枪包名称/描述全显示原始键名
     * （{@code sk25tkm.gun.s25o.name} 这类）。
     *
     * @return 读不到/格式不支持时返回 {@code null}
     */
    private static @Nullable Map<String, String> parseLangResource(IGunPackSource source, String path) {
        try (InputStream stream = source.open(path)) {
            if (stream == null) {
                return null;
            }
            if (path.endsWith(".lang")) {
                return StringTranslate.parseLangFile(stream);
            }
            JsonElement element = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                return null;
            }
            Map<String, String> parsed = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject()
                .entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    parsed.put(entry.getKey(), value.getAsString());
                }
            }
            return parsed;
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            GunMod.LOGGER.warn(MARKER, "Failed to read gun pack language file {}", path, exception);
            return null;
        }
    }

    /**
     * 把枪包里 {@code assets/<namespace>/lang/<locale>.lang} 合入 1.7.10 的语言表。
     *
     * <p>
     * <b>移植特有实现（上游没有对应代码）</b>：上游 1.20 靠
     * {@code mixin/client/LanguageMixin} 把枪包 {@code .lang} 注入 {@code I18n}；
     * 该 mixin 在 1.20.1 源码中已被整段注释（它引用的 ClientAssetManager 已不存在），
     * 而 1.7.10 也没有 {@code I18n}/{@code LanguageMixin} 概念，因此改为在客户端直接调
     * {@code LanguageRegistry#injectLanguage}。
     *
     * <p>
     * 走 {@link net.minecraft.util.StringTranslate#parseLangFile} 的理由：它用 {@code IOUtils.readLines(in, UTF_8)}
     * 并按客户端 {@code Locale} 的规则解析（跳过 {@code #} 注释、按首个 {@code =} 切分、{@code %d|%f} → {@code %s}），
     * <b>不做</b> {@code \n}、{@code \t}、{@code uXXXX} 反转义；改用 {@code Properties#load} 会引入 ISO-8859-1
     * 解码与额外一层转义（把枪包描述里的字面 {@code \n} 变成真换行），与本体 {@code .lang} 语义分叉。
     *
     * <p>
     * 覆盖顺序：按 {@link #getPackSources()} 的顺序合并，后扫到的枪包覆盖先扫到的，与
     * {@link com.tacz.guns.resource.pack.GunPackResources} 的 pack 优先级一致。内置默认枪包
     * （{@code assets/tacz/custom/tacz_default_gun/assets/tacz/lang/*.lang}）FML 的 {@code LanguageRegistry}
     * 正则本身就会扫到并自动注入，本方法再注入一次无害（值相同，且 {@code mergeLanguageTable} 只补缺不覆盖）。
     *
     * <p>
     * <b>优先用 {@link #injectAllGunPackLanguages()}</b>：只注入当前语种的做法在玩家切语种后会失效（原因见那个方法）。
     * 本方法保留给「已知当前语种、想立刻补一次」的场合（如 {@code /tacz reload} 后的自愈）。
     *
     * @param locale 形如 {@code en_US} / {@code zh_CN}，取自
     *               {@code Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode()}
     */
    public static void injectGunPackLanguage(String locale) {
        if (locale == null || locale.isEmpty()) {
            return;
        }
        if (!FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            return;
        }
        HashMap<String, String> merged = new HashMap<>();
        String target = normalizeLocaleCode(locale);
        if (target == null) {
            return;
        }
        // 两遍：先 .lang 再 .json（与 injectAllGunPackLanguages 同一优先级）
        for (String extension : new String[] { ".lang", ".json" }) {
            for (IGunPackSource source : INSTANCE.getPackSources()) {
                for (String path : source.listFiles(GunPackResources.ASSETS_ROOT)) {
                    if (!path.endsWith(extension)) {
                        continue;
                    }
                    String pathLocale = localeOfLangFile(path);
                    if (pathLocale == null || !pathLocale.equalsIgnoreCase(target)) {
                        continue;
                    }
                    Map<String, String> parsed = parseLangResource(source, path);
                    if (parsed != null) {
                        merged.putAll(parsed);
                    }
                }
            }
        }
        if (!merged.isEmpty()) {
            LanguageRegistry.instance()
                .injectLanguage(locale, merged);
            GunMod.LOGGER.info(MARKER, "Injected {} gun pack localization keys for {}", merged.size(), locale);
        }
    }

    /** 释放一个 zip 枪包的文件句柄（目录源无句柄可释放）。 */
    private static void closeSource(IGunPackSource source) {
        if (source instanceof ZipGunPackSource zip) {
            zip.close();
        }
    }

    /** 释放所有 zip 枪包的文件句柄。 */
    private void closeAllSources() {
        for (SourceHandle handle : sourceHandles.values()) {
            closeSource(handle.source());
        }
        sourceHandles = new HashMap<>();
        for (IGunPackSource source : packSources) {
            closeSource(source);
        }
    }

    /**
     * 一次扫描得到的枪包文件源 + 复用判定戳。
     *
     * @see #sourceHandles 为什么需要复用
     */
    private record SourceHandle(IGunPackSource source, boolean directory, long lastModified, long size) {

        /** 目录源恒可复用（无句柄、实时读文件）；zip 源要求 mtime 与大小都没变。 */
        private boolean canReuse(boolean directory, long lastModified, long size) {
            if (this.directory != directory) {
                return false;
            }
            if (directory) {
                return true;
            }
            return this.lastModified == lastModified && this.size == size;
        }
    }

    private static @Nullable GunPack fromDirPath(Path path) {
        Path packInfoFilePath = path.resolve("gunpack.meta.json");
        try (InputStream stream = Files.newInputStream(packInfoFilePath)) {
            PackMeta info = CommonAssetsManager.GSON
                .fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PackMeta.class);

            if (info == null) {
                GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", packInfoFilePath.getFileName());
                return null;
            }

            if (info.getDependencies() != null && !modVersionAllMatch(info)) {
                GunMod.LOGGER.warn(MARKER, "Mod version mismatch: {}", packInfoFilePath.getFileName());
                return null;
            }

            return new GunPack(path, info.getName());
        } catch (IOException | JsonParseException | InvalidVersionSpecificationException exception) {
            GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", packInfoFilePath.getFileName());
            GunMod.LOGGER.warn(exception.getMessage());
        }
        return null;
    }

    private static @Nullable GunPack fromZipPath(Path path) {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry extDescriptorEntry = zipFile.getEntry("gunpack.meta.json");
            if (extDescriptorEntry == null) {
                GunMod.LOGGER.error(
                    MARKER,
                    "Failed to load extension from ZIP {}. Error: {}",
                    path.getFileName(),
                    "No gunpack.meta.json found");
                return null;
            }

            try (InputStream stream = zipFile.getInputStream(extDescriptorEntry)) {
                PackMeta info = CommonAssetsManager.GSON
                    .fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PackMeta.class);

                if (info == null) {
                    GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", path.getFileName());
                    return null;
                }

                if (info.getDependencies() != null && !modVersionAllMatch(info)) {
                    GunMod.LOGGER.warn(MARKER, "Mod version mismatch: {}", path.getFileName());
                    return null;
                }

                return new GunPack(path, info.getName());
            } catch (IOException | JsonParseException | InvalidVersionSpecificationException exception) {
                GunMod.LOGGER
                    .error(MARKER, "Failed to load extension from ZIP {}. Error: {}", path.getFileName(), exception);
                return null;
            }
        } catch (IOException exception) {
            GunMod.LOGGER
                .error(MARKER, "Failed to load extension from ZIP {}. Error: {}", path.getFileName(), exception);
            return null;
        }
    }

    private static List<GunPack> scanExtensions(Path extensionsPath) {
        List<GunPack> gunPacks = new ArrayList<>();

        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extensionsPath)) {
            stream.forEach(entries::add);
        } catch (IOException exception) {
            GunMod.LOGGER.error(MARKER, "Failed to scan extensions from {}. Error: {}", extensionsPath, exception);
            return gunPacks;
        }
        // 上游顺序取决于目录遍历；1.7.10 这里排序，保证「后扫到的包覆盖先扫到的」可复现
        entries.sort(
            (left, right) -> left.getFileName()
                .toString()
                .compareToIgnoreCase(
                    right.getFileName()
                        .toString()));

        for (Path entry : entries) {
            GunPack gunPack = null;
            if (Files.isDirectory(entry)) {
                gunPack = fromDirPath(entry);
            } else if (entry.toString()
                .endsWith(".zip")) {
                    gunPack = fromZipPath(entry);
                }
            if (gunPack != null) {
                GunMod.LOGGER.info(
                    MARKER,
                    "- {}, Main namespace: {}",
                    gunPack.path()
                        .getFileName(),
                    gunPack.name());
                gunPacks.add(gunPack);
            }
        }

        return gunPacks;
    }

    private static boolean modVersionAllMatch(PackMeta info) throws InvalidVersionSpecificationException {
        Map<String, String> dependencies = info.getDependencies();
        for (String modId : dependencies.keySet()) {
            if (!modVersionMatch(modId, dependencies.get(modId))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 版本区间校验。
     *
     * <p>
     * 1.7.10 差异：上游用 {@code net.minecraftforge.fml.ModList} + {@code org.apache.maven.artifact.versioning}，
     * 1.7.10 对应物是 {@code cpw.mods.fml.common.Loader#getIndexedModList()} +
     * {@code cpw.mods.fml.common.versioning.VersionRange}（FML 自带同一套 maven 版本语义）。
     */
    private static boolean modVersionMatch(String modId, String version) throws InvalidVersionSpecificationException {
        VersionRange versionRange = VersionRange.createFromVersionSpec(version);
        ModContainer container = Loader.instance()
            .getIndexedModList()
            .get(modId);
        if (container == null) {
            return false;
        }
        String rawVersion = container.getVersion();
        if (rawVersion == null || rawVersion.isEmpty()) {
            return false;
        }
        ArtifactVersion modVersion = new DefaultArtifactVersion(rawVersion);
        return versionRange.containsVersion(modVersion);
    }

    public record GunPack(Path path, String name) {}
}
