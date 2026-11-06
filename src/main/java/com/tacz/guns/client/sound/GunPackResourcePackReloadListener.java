package com.tacz.guns.client.sound;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 资源重载钩子：把 {@link GunPackResourcePack} 钉进<b>每一轮</b>原版资源重载。
 *
 * <p>
 * <b>为什么需要它（这是一个真 bug 的成因）</b>：1.7.10 的
 * {@code SimpleReloadableResourceManager#reloadResources(List)} 第一步就是
 * {@code clearResources()}（{@code SimpleReloadableResourceManager.java:89-97}，
 * {@code domainResourceManagers.clear() + setResourceDomains.clear()}），随后
 * <b>只按传入的包列表</b>重新 {@code reloadResourcePack(...)}（同文件 {@code :111-118}）。
 * 而 {@code Minecraft#refreshResources()} 组出来的列表只有
 * {@code defaultResourcePacks} + 资源包仓库里的包（{@code Minecraft.java:636-654}），
 * <b>不含</b>本模组的 {@link GunPackResourcePack}。于是每一次原版资源重载
 * （启动期 {@code FMLClientHandler.beginMinecraftLoading} 的 {@code finally}:{@code client.refreshResources()}
 * ——{@code FMLClientHandler.java:236-239}、玩家的 {@code F3+T}、{@code /tacz reload}）
 * 都会把枪包从 domain managers 里清掉，枪包贴图 / ogg 全部解析失败。
 *
 * <p>
 * 靠 {@code SoundLoadEvent} 兜底是不够的：{@code notifyReloadListeners()}
 * （{@code SimpleReloadableResourceManager.java:134-145}）按 <b>注册顺序</b>遍历
 * （{@code reloadListeners} 是 {@code Lists.newArrayList()}，插入有序，见同文件 {@code :28}），
 * 而 {@code TextureManager} 在 {@code Minecraft.java:524} 才注册 ——
 * 只要本监听器在 {@code Minecraft.java:524} <b>之前</b>注册，它就一定排在
 * {@code TextureManager#onResourceManagerReload}（→ {@code TextureMap.loadTextureAtlas}
 * → {@code TextureStitchEvent}）<b>之前</b>执行，贴图 stitch 的那一刻枪包已经在管理器里了。
 * 反之（靠 {@code SoundHandler} 的 {@code SoundLoadEvent}）必然晚于 stitch。
 *
 * <p>
 * <b>注册落点</b>：{@code Minecraft.startGame()} 在 {@code Minecraft.java:519} 创建
 * {@code mcResourceManager}，在 {@code :522} 调 {@code FMLClientHandler.beginMinecraftLoading}
 * （mod 加载 / preInit 在其中的 {@code Loader.instance().loadMods()}，见
 * {@code FMLClientHandler.java:208}），在 {@code :524} 才注册 {@code TextureManager}。
 * 因此在 <b>preInit</b> 里注册本监听器即天然满足「早于 TextureManager」。
 *
 * <p>
 * <b>注意</b>：{@code SimpleReloadableResourceManager#registerReloadListener}（同文件 {@code :125-132}）
 * 会在加入列表后<b>同步立刻回调一次</b> {@code onResourceManagerReload(this)}，
 * 因此回调必须可重入且幂等（见 {@link GunPackResourcePack#onResourceManagerReload}）。
 *
 * <p>
 * 本类是普通包里的<b>顶层类</b>（不是 Mixin，也没有放进 {@code com.tacz.guns.mixin}），
 * 单例由 {@link GunPackResourcePack} 持有。
 */
@SideOnly(Side.CLIENT)
public final class GunPackResourcePackReloadListener implements IResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        // 先挂「模组 jar 资产」classpath 资源包（方块图集贴图 / blockstate / model JSON 的可靠来源），
        // 再挂枪包资源包 ⇒ 枪包仍然后挂、优先级更高（与上游枪包覆盖本体资源的语义一致）。
        com.tacz.guns.client.resource.ModAssetResourcePack.install(resourceManager);
        GunPackResourcePack.onResourceManagerReload(resourceManager);
    }
}
