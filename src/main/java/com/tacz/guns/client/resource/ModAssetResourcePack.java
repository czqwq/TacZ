package com.tacz.guns.client.resource;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 把**模组 jar 自带的 {@code assets/tacz/**} 从 classpath** 暴露成一个 1.7.10 资源包
 * （{@link IResourcePack}）；移植特有类（上游 1.20 里这些资源由 jar 资源包直接提供，见
 * {@code PackRepository}/{@code VanillaPackResources}）。
 *
 * <h2>为什么需要它</h2>
 * 1.7.10 Forge 会把每个模组 jar 注册成 {@code FMLFileResourcePack}，理论上
 * {@code Minecraft#getResourceManager()} 就能读到 {@code assets/tacz/**}。但在本项目的实机运行环境
 * （RFB + lwjgl3ify，Java 25）里，该 pack 对 TACZ 的 **blockstate/model JSON 和方块图集贴图**
 * 出现了「{@code resourceExists()==true} 但 {@code getInputStream()} 抛
 * {@code ResourcePackFileNotFoundException}」的不一致（详见 {@code docs/review/r8/block.md}
 * 「Lead 追加修正 2」；同一时刻用 {@code ZipFile} 直读 jar 条目正常）。直接后果：
 * {@code registerBlockIcons(...)} 拿不到贴图 → 方块渲染成「空气贴图」。
 *
 * <p>
 * 因此这里用**类加载器**（模组 jar 必然在 classpath 上、与 mod 版本严格一致）自建一个资源包，
 * 并像 {@code GunPackResourcePack} 一样在**每轮原版资源重载**里挂到
 * {@link IResourceManager} 上（由 {@code GunPackResourcePackReloadListener} 调用，
 * 该监听器在 preInit 注册、排在 {@code TextureManager} 之前，见 {@code GunPackResourcePack} 类注释）。
 * 因为 {@code FallbackResourceManager} 从后往前查 pack（{@code FallbackResourceManager.java:43}），
 * 后挂的包优先 ⇒ 本包会先于出问题的 {@code FMLFileResourcePack} 命中，从而绕开该平台问题。
 *
 * <h2>优先级</h2>
 * 本包在枪包 {@link com.tacz.guns.client.sound.GunPackResourcePack} **之前**挂载，
 * 因此枪包资源（namespace 过滤后的 {@code assets/<ns>/**}）仍然覆盖模组自带资源，与上游
 * {@code Pack.Position.BOTTOM} 的枪包语义一致。
 *
 * <h2>降级点</h2>
 * {@link #getPackMetadata}/{@link #getPackImage} 返回 {@code null}（只被资源包选择界面使用）；
 * 本包无法被玩家在资源包界面勾选（没有 {pack.mcmeta}），这与「模组内置资源」的定位一致。
 */
@SideOnly(Side.CLIENT)
public final class ModAssetResourcePack implements IResourcePack {

    /** 本模组 namespace（{@code assets/tacz/**}）。 */
    private static final String DOMAIN = GunMod.MOD_ID;
    /** 原版域：只用于 {@code textures/block → textures/blocks} 别名（见 {@link #aliasTextureBlockPath}）。 */
    private static final String VANILLA_DOMAIN = "minecraft";
    private static final String ASSETS_PREFIX = "assets/";
    private static final Set<String> DOMAINS = new java.util.LinkedHashSet<>(
        java.util.Arrays.asList(DOMAIN, VANILLA_DOMAIN));

    private static final ModAssetResourcePack INSTANCE = new ModAssetResourcePack();

    private static boolean installLogged;

    private ModAssetResourcePack() {}

    /**
     * 把本包挂到资源管理器（幂等：每轮重载后管理器已被 {@code clearResources()} 清空，
     * 必须重新挂；同一轮内重复调用只是重复追加同一个实例，{@code FallbackResourceManager}
     * 原版资源重载会清空 pack 列表，因此每轮都必须重挂）。
     */
    public static void install(@Nullable IResourceManager resourceManager) {
        if (!(resourceManager instanceof SimpleReloadableResourceManager)) {
            return;
        }
        // 注意：**不能**用「同一资源管理器只挂一次」短路 —— 原版 reloadResources 会先 clearResources()
        // 清空 domain 管理器（SimpleReloadableResourceManager.java:89-97），资源管理器实例不变但 pack 列表
        // 已被清空，必须每轮重挂。本方法每轮重载只会被监听器调用一次。
        SimpleReloadableResourceManager manager = (SimpleReloadableResourceManager) resourceManager;
        manager.reloadResourcePack(INSTANCE);
        if (!installLogged) {
            installLogged = true;
            GunMod.LOGGER.info(
                "[TACZ] installed classpath-backed mod asset pack ({}) so blockstate/model/textures bypass the 1.7.10 mod-jar resource pack issue",
                ASSETS_PREFIX + DOMAIN + "/");
        }
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        String domain = location.getResourceDomain();
        String path = ASSETS_PREFIX + domain + "/" + location.getResourcePath();
        InputStream stream = ModAssetResourcePack.class.getResourceAsStream("/" + path);
        if (stream != null) {
            return stream;
        }
        String alias = aliasTextureBlockPath(domain, location.getResourcePath());
        if (alias != null) {
            stream = ModAssetResourcePack.class.getResourceAsStream("/" + ASSETS_PREFIX + domain + "/" + alias);
            if (stream != null) {
                return stream;
            }
        }
        throw new FileNotFoundException(path);
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        String domain = location.getResourceDomain();
        if (VANILLA_DOMAIN.equals(domain)) {
            // 原版资源只做 1.20→1.7.10 的贴图路径别名，不接管任何原版文件
            return aliasTextureBlockPath(domain, location.getResourcePath()) != null;
        }
        if (!DOMAIN.equals(domain)) {
            return false;
        }
        String path = ASSETS_PREFIX + domain + "/" + location.getResourcePath();
        if (ModAssetResourcePack.class.getResource("/" + path) != null) {
            return true;
        }
        return aliasTextureBlockPath(domain, location.getResourcePath()) != null;
    }

    /**
     * 1.20 贴图路径 → 1.7.10 路径的别名（仅当目标为**原版/任意域**的 {@code textures/block/*.png} 且文件不存在时）。
     *
     * <p>
     * 上游 1.20 模型里的 {@code particle} 引用写的是 {@code minecraft:block/iron_block} 这类 1.20 路径
     * （{@code assets/minecraft/textures/block/iron_block.png}），而 1.7.10 原版贴图在
     * {@code assets/minecraft/textures/blocks/iron_block.png}（复数）。
     * GTNHLib 的 {@code TexHelper} 会把这些模型贴图（含 particle）注册进方块图集并按 1.20 路径解析 ⇒
     * 实机出现「3 missing textures」（iron_block / oak_planks / stone，都只影响破坏粒子的 particle）。
     * 这里在类路径可见时做单复数别名，让这些 vanilla 路径也能解析。
     */
    private static String aliasTextureBlockPath(String domain, String path) {
        if (!path.startsWith("textures/block/") || !path.endsWith(".png")) {
            return null;
        }
        String alias = "textures/blocks/" + path.substring("textures/block/".length());
        String full = ASSETS_PREFIX + domain + "/" + alias;
        return ModAssetResourcePack.class.getResource("/" + full) != null ? alias : null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return DOMAINS;
    }

    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer metadataSerializer, String metadataSectionName)
        throws IOException {
        return null;
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        return null;
    }

    @Override
    public String getPackName() {
        return "TACZ Mod Assets (classpath)";
    }
}
