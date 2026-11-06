package com.tacz.guns.client.particle;

import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.IGunPackReloadListener;
import com.tacz.guns.resource.pack.GunPackResources;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪包内 {@code assets/<namespace>/particle_definitions/*.json}（基岩版 snowstorm 粒子定义）的装载器。
 *
 * <h2>为什么需要这个类（移植缺口）</h2>
 * 上游 TACZ 里这些定义由 <b>simplebedrockmodel 模组自己</b>装载：SBM 是独立 Forge 模组，
 * 在 {@code RegisterClientReloadListenersEvent} 里把
 * {@code com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader}
 * 注册进原版资源重载管线，TACZ 只负责
 * {@code ParticleDefinitionLoader.getInstance().getDefinition(id)}（见
 * {@code tmp/TACZ-1.20.1/.../client/particle/MuzzleParticleManager.java:399,426} 与
 * {@code ThirdPersonMuzzleParticleManager.java:100,138}）。
 *
 * <p>
 * 本工程把 SBM <b>vendor 成库</b>（{@code SimpleBedrockModel} 只剩常量 + 工具方法，
 * 见 {@code com/github/mcmodderanchor/simplebedrockmodel/SimpleBedrockModel.java:12-26}），
 * 它的模组入口与重载注册<b>没有被搬过来</b>；而 1.7.10 也没有
 * {@code RegisterClientReloadListenersEvent} 这个事件。结果就是
 * {@code ParticleDefinitionLoader} 的缓存永远为空 ⇒
 * <ul>
 * <li>{@code MuzzleParticleManager#createFlashEmitter/createSmokeEmitter}
 * （{@code MuzzleParticleManager.java:569-574} / {@code :595-600}）拿到 {@code null} 定义，
 * 打一条 warn 后直接 return；</li>
 * <li>而默认枪包的每一把枪在 display json 里都是 {@code "muzzle_flash": {"type": "renew", ...}}
 * （例：{@code assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/aa12_display.json}），
 * 且 {@code MuzzleFlashRender#render} 对 {@code renew} 型<b>直接 return</b>
 * （{@code MuzzleFlashRender.java:226-228}）—— 旧四边形火光也不画；</li>
 * <li>⇒ <b>第一/第三人称枪口火光与枪烟完全不显示，且每次开火刷一条 warn</b>。</li>
 * </ul>
 *
 * <h2>1.7.10 落点</h2>
 * 与其余客户端枪包数据管理器一致：实现 {@link IGunPackReloadListener}，由
 * {@code client/resource/ClientGunPackReloader} 按注册顺序串行驱动（每次原版资源重载 + {@code /tacz reload}）。
 * 扫描走 {@link GunPackResources#scanDirectory}（{@code root = assets}，
 * 与 {@code ClientAssetsManager} 里 {@code bedrockModel}/{@code bedrockAnimation} 的
 * {@code GunPackResources.ASSETS_ROOT} 一致），id 形如 {@code tacz:muzzle_flash}，
 * 正是 {@code MuzzleFlash#DEFAULT_FIRST_PERSON_FLASH} 等默认常量期望的键。
 *
 * <p>
 * 装载结果通过 {@code ParticleDefinitionLoader#applyPrepared(Map)}（移植新增入口）灌进 SBM 缓存。
 */
@SideOnly(Side.CLIENT)
public final class ParticleDefinitionReloader implements IGunPackReloadListener {

    /** 相对 {@code assets/<namespace>/} 的目录名，与 SBM {@code ParticleDefinitionLoader} 的 {@code DIRECTORY} 一致。 */
    private static final String DIRECTORY = "particle_definitions";
    private static final String EXTENSION = ".json";
    /**
     * {@link GunPackResources#readElement} 内部只用 {@code new JsonParser().parse(reader)}（lenient），
     * 传入的 gson 实例实际上不参与解析，这里给一个空实例即可，避免引入
     * {@code ClientAssetsManager.GSON}（其自定义适配器对本用途无意义）。
     */
    private static final Gson GSON = new Gson();

    public static final ParticleDefinitionReloader INSTANCE = new ParticleDefinitionReloader();

    private ParticleDefinitionReloader() {}

    @Override
    public void reload() {
        Map<ResourceLocation, JsonElement> definitions = GunPackResources.scanDirectory(
            GunPackLoader.INSTANCE.getPackSources(),
            GunPackResources.ASSETS_ROOT,
            DIRECTORY,
            EXTENSION,
            GSON);
        // 即使一个定义都没有也要走一遍：applyPrepared 会清空上一轮缓存，
        // 保证换包后旧定义不会残留（与其它 manager 的「reload 先清空」约定一致）。
        ParticleDefinitionLoader.getInstance()
            .applyPrepared(definitions);
    }
}
