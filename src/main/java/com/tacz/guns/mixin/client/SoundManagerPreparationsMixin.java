package com.tacz.guns.mixin.client;

import net.minecraft.client.audio.SoundHandler;

import org.spongepowered.asm.mixin.Mixin;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/SoundManagerPreparationsMixin.java}
 * （<b>降级：注入点已注释</b>）。
 *
 * <p>
 * <b>上游用途</b>：上游 {@code SoundManagerPreparationsMixin.java:16-31} mixin 到
 * {@code net.minecraft.client.sounds.SoundManager$Preparations#listResources} 的 TAIL，
 * 用 {@code new FileToIdConverter("tacz_sounds", ".ogg")} 把枪包里
 * {@code assets/<ns>/tacz_sounds/**.ogg} 的资源<b>并进原版 soundCache</b>，
 * 于是枪包音效可以不带 {@code sounds.json}、直接用 {@code Reservation} 化的 id 播放。
 *
 * <p>
 * <b>为什么 1.7.10 无法照搬</b>：
 * <ul>
 * <li>1.7.10 没有 {@code SoundManager$Preparations} 这个内部类。
 * 音效注册链路是
 * {@code SoundHandler#onResourceManagerReload(IResourceManager)}（{@code SoundHandler.java:63}）
 * → 遍历各资源包读取 {@code sounds.json}（{@code loadSoundResource}，第 106 行）
 * → 写进 {@code SoundRegistry sndRegistry}（{@code SoundRegistry#registerSound(SoundEventAccessorComposite)}，第 24 行）。</li>
 * <li>1.7.10 的 {@link net.minecraft.client.audio.SoundRegistry} 是
 * 「sound event id → {@code SoundEventAccessorComposite}」的映射，
 * 每个 event 里挂着若干 {@code SoundPoolEntry}（真实 {@code .ogg} 路径 + 音量/音高）；
 * 一个「目录里的裸 ogg」没有对应的 sound event id，<b>必须</b>由我们自己造
 * {@code SoundEventAccessorComposite} 才能被播放。</li>
 * <li>最关键的差异：{@code tacz_sounds/} 在 1.20 上游属于<b>枪包资源</b>
 * （{@code assets/tacz/custom/tacz_default_gun/assets/tacz/tacz_sounds/}，以及运行期从游戏目录加载的外部枪包），
 * 后者根本不在原版 {@code IResourceManager} 的可见范围内（1.20 上游能并进去，是因为
 * {@code GunPackLoader} 把枪包注册成了资源包；1.7.10 的枪包加载器是自己扫目录的，
 * 不经过 {@code IResourceManager}）。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 替代钩子（给 lead / client-render 的接入说明）</b>：两段式
 * <ol>
 * <li>把枪包里的每个 {@code tacz_sounds/**.ogg} 注册成一个 sound event：
 * 
 * <pre>
 * 
 * {
 *     &#64;code
 *     SoundEventAccessorComposite composite = new SoundEventAccessorComposite(
 *         soundId,
 *         1.0D,
 *         1.0D,
 *         SoundCategory.PLAYERS); // 第 23 行构造器
 *     composite.addSoundToEventPool(new SoundPoolEntry(fileLocation, 1.0D, 1.0D, false)); // 第 77 / 16 行
 *     soundRegistry.registerSound(composite); // 需要 @Shadow SoundHandler#sndRegistry
 * }
 * </pre>
 * 
 * 其中 {@code soundId} 是上游 {@code SoundData#getSound()} 给出的 id，
 * {@code fileLocation} 是枪包里 ogg 的 {@code ResourceLocation}（由
 * {@code client/resource/.../FileToIdConverter#idToFile} 的 1.7.10 版给出）。</li>
 * <li>注入时机：{@code SoundHandler#onResourceManagerReload(IResourceManager)} 的 TAIL
 * （{@code SoundHandler.java:63}）—— 每次 F3+T 重载资源后都要重建，与上游 {@code listResources} 语义一致。</li>
 * </ol>
 * 由于 {@code client/sound/SoundPlayManager} 与 {@code GunSoundInstance}（task-9 作用域）自己也在用
 * {@code FileToIdConverter}，越早和它们对齐「枪包 ogg 的 ResourceLocation 怎么来」越好；
 * 本 Mixin 只保留目标类与注入点位置。
 *
 * <p>
 * <b>生效状态</b>：<b>降级（完全不生效）</b>。当前是空 {@code @Mixin}：
 * 目标类换成 1.7.10 的 {@code SoundHandler}。
 * 可以安全加载、不改变行为；lead 可自行决定是否留在 {@code mixins.tacz.json}。
 */
@Mixin(SoundHandler.class)
public class SoundManagerPreparationsMixin {
    // 上游注入（1.20 形态，无法照搬）：
    //
    // private static final FileToIdConverter TACZ_SOUND_LISTER = new FileToIdConverter("tacz_sounds", ".ogg");
    //
    // @Shadow
    // private Map<ResourceLocation, Resource> soundCache;
    //
    // @Inject(method = "listResources", at = @At("TAIL"))
    // private void tacz$includeTaczSoundResources(ResourceManager resourceManager, CallbackInfo ci) {
    // Map<ResourceLocation, Resource> merged = Maps.newHashMap(this.soundCache);
    // merged.putAll(TACZ_SOUND_LISTER.listMatchingResources(resourceManager));
    // this.soundCache = merged;
    // }
    //
    // 1.7.10 落点（启用时改成这样）：
    //
    // @Shadow private SoundRegistry sndRegistry;
    //
    // @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    // private void tacz$includeTaczSoundResources(IResourceManager resourceManager, CallbackInfo ci) {
    // // 见类 javadoc 的两段式说明
    // }
}
