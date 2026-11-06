package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;

import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.client.sound.SoundPlayManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 原版方块图集（{@code textures/atlas/blocks.png}）stitch 完成后，重新加载内置资产（默认动画/模型）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/ReloadResourceEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD)}）。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>上游 MOD 总线的 {@code TextureStitchEvent.Post}；1.7.10 的
 * {@link TextureStitchEvent.Post} 直接在 {@code MinecraftForge.EVENT_BUS} 上，语义相同；</li>
 * <li>上游用 {@code event.getAtlas().location()} 与常量比较；1.7.10 的
 * {@code TextureStitchEvent} 只有 {@code map} 字段（{@code TextureMap}），没有「atlas 的
 * ResourceLocation」，因此改为 <b>身份比较</b> {@code event.map == Minecraft#getTextureMapBlocks()}
 * —— 这也正是 1.7.10 里「方块图集」判定的常用做法，{@link #BLOCK_ATLAS_TEXTURE} 保留给可能的外部引用。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（{@code cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115}）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在事件派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法(...)} 在这里改为 {@code public void 处理方法(...)}
 * （类名 / 方法名 / 参数 / 语义一律不变；非处理方法的静态工具方法与字段仍保持 {@code static}）。
 * 本类也因为「{@code EventBus#register(Class)} 在 1.7.10 无效」而提供 public 无参构造，
 * 由 {@code client/ClientProxy} 反射 {@code new} 后注册到正确的总线。
 */
public class ReloadResourceEvent {

    public static final ResourceLocation BLOCK_ATLAS_TEXTURE = new ResourceLocation("textures/atlas/blocks.png");

    @SubscribeEvent
    public void onTextureStitchEventPost(TextureStitchEvent.Post event) {
        // 1.7.10: TextureStitchEvent 没有 getAtlas()，用 TextureMap 实例身份判定方块图集
        if (event.map == Minecraft.getMinecraft()
            .getTextureMapBlocks()) {
            // InternalAssetLoader 需要加载一些默认的动画、模型，需要先于枪包加载。
            InternalAssetLoader.onResourceReload();
            SoundPlayManager.clearSoundResourceCache();
            // ClientReloadManager.reloadAllPack();
        }
    }
}
