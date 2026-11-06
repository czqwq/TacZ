package com.tacz.guns.client.sound;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ISoundEventAccessor;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.FileToIdConverter;
import com.tacz.guns.mcshim.SoundEvent;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械音效实例。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/sound/GunSoundInstance.java}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * <ul>
 * <li>上游的基类 {@code AbstractSoundInstance}（{@code net.minecraft.client.resources.sounds}）在 1.7.10 不存在，
 * 最近的等价物是 {@link PositionedSound}：同样持有 {@code volume / pitch / xPosF / yPosF / zPosF / attenuation /
 * repeat}，并由 {@code SoundHandler#playSound(ISound)} 播放。因此本类改为
 * {@code extends PositionedSound}，字段语义一一对应（{@code pitch} 在 1.7.10 是 {@code field_147663_c}）。</li>
 * <li>上游 {@code SoundSource}（MASTER/PLAYER/...）在 1.7.10 不是 {@code ISound} 的属性，而是
 * <b>声音事件</b>的属性（{@code SoundEventAccessorComposite#getSoundCategory()}，来源是 {@code sounds.json}
 * 的 {@code category}）。因此上游的 {@code source} 参数保留为 {@link #source} 字段，只在
 * {@link #resolve()} 注册事件时作为 {@link SoundCategory} 使用；{@link SoundCategory#PLAYERS} 对应上游
 * {@code SoundSource.PLAYERS}，于是「玩家」音量滑条对枪声的语义与上游一致。</li>
 * <li>上游用 {@code SoundManager#stop(instance)} 停止播放 → 1.7.10 用
 * {@code SoundHandler#stopSound(ISound)}。</li>
 * <li>上游的 {@code mono} 参数在 1.20 实现里<b>并未使用</b>（本类保留同签名参数）；1.7.10 也没有按实例
 * 强制单声道的能力，声道数由 ogg 文件自身决定。</li>
 * </ul>
 *
 * <h2>「直接播放枪包原始 ogg」在 1.7.10 怎么落地</h2>
 * 见 {@link #resolve()}。
 */
// TODO(L2): 上游 GunSoundInstance.java:18 `extends AbstractSoundInstance`；1.7.10 无
// net.minecraft.client.resources.sounds.AbstractSoundInstance，此处改用
// net.minecraft.client.audio.PositionedSound（volume/pitch/xPosF/yPosF/zPosF/attenuation 字段一一对应）。
@SideOnly(Side.CLIENT)
public class GunSoundInstance extends PositionedSound {

    private static final FileToIdConverter TACZ_SOUND_LISTER = new FileToIdConverter("tacz_sounds", ".ogg");

    /**
     * 本类塞进 {@code SoundHandler} 注册表的事件（键 = 声音 id）。用于判断「注册表里那条是不是我们注册的」——
     * 资源重载（F3+T / 枪包重载）会清空整个 {@code SoundRegistry}，并可能用 {@code sounds.json} 里的同名事件
     * 覆盖我们那条，所以必须能在每次播放前识别并重新注册。
     */
    private static final Map<ResourceLocation, SoundEventAccessorComposite> REGISTERED_TACZ_SOUNDS = new HashMap<>();

    /** 见 {@link #soundRegistryField()}。 */
    @Nullable
    private static Field soundRegistryField;
    private static boolean soundRegistryFieldResolved;

    @Nullable
    private final ResourceLocation registryName;
    private final boolean canPlay;
    /**
     * 上游 {@code GunSoundInstance#redirectedSound}（{@code Sound}）的 1.7.10 形态：指向枪包原始 ogg 的路径。
     * 1.7.10 没有「按实例替换 Sound」的概念，这个路径被写进注册表里那条 {@link SoundPoolEntry}，
     * 由声音引擎自己按路径取流。
     */
    @Nullable
    private ResourceLocation redirectedSound;
    private final boolean useTaczSound;
    /** 上游 {@code AbstractSoundInstance#source}；见类 javadoc。 */
    private final SoundCategory source;

    public GunSoundInstance(SoundEvent soundEvent, SoundCategory source, float volume, float pitch, Entity entity,
        int soundDistance, ResourceLocation registryName, boolean mono, boolean relative, boolean useTaczSound) {
        super(registryName);
        this.field_147666_i = ISound.AttenuationType.NONE;
        this.source = source;
        this.registryName = registryName;
        this.useTaczSound = useTaczSound;
        // TODO(L2): 上游 GunSoundInstance.java:36 是 `this.canPlay = !entity.isSilent();`。
        // 1.7.10 的 Entity 没有 isSilent()（1.9 才加入），也没有任何「按实体静音」的原版机制，
        // 因此此处改用「恒可播放」近似，等价于上游实体从未被静音的情况。
        this.canPlay = true;
        this.volume = volume;
        this.field_147663_c = pitch;
        this.xPosF = relative ? 0 : (float) entity.posX;
        this.yPosF = relative ? 0 : (float) entity.posY;
        this.zPosF = relative ? 0 : (float) entity.posZ;
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && !relative) {
            // 上游 GunSoundInstance.java:42-47：距离衰减自己算（引擎侧 attenuation = NONE）。
            // 1.7.10 的 EntityPlayerSP#getDistanceSq(double,double,double) 与上游 distanceToSqr(x,y,z) 等价。
            this.volume = volume * (1.0F - Math.min(
                1.0F,
                (float) Math.sqrt(player.getDistanceSq(this.xPosF, this.yPosF, this.zPosF)) / soundDistance));
            this.volume *= this.volume;
        }
        // 上游的第 1 个参数 soundEvent 只用于 AbstractSoundInstance 的模板事件（resolve 时被 TaczSound 覆盖），
        // 1.7.10 播放时按 ISound#getPositionedSoundLocation()（= registryName）查事件，
        // 模板的 volume/pitch/weight 由 resolve() 注册的 SoundPoolEntry 承担，故该参数在 1.7.10 无实际用途。
    }

    /**
     * 停止播放（上游 {@code GunSoundInstance#setStop()}：{@code getSoundManager().stop(this)}）。
     */
    public void setStop() {
        SoundHandler soundHandler = Minecraft.getMinecraft()
            .getSoundHandler();
        if (soundHandler != null) {
            soundHandler.stopSound(this);
        }
    }

    /**
     * 上游 {@code AbstractSoundInstance#canPlaySound()}。
     *
     * <p>
     * 1.7.10 的 {@code ISound} 没有这个钩子（{@code SoundHandler#playSound} 不会询问 ISound 能否播放），
     * 所以由 {@link SoundPlayManager} 在调用 {@code playSound} 之前判断。
     */
    public boolean canPlaySound() {
        return this.canPlay;
    }

    @Nullable
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    // TODO(L2): 上游 GunSoundInstance.java:63-80 `resolve(SoundManager)` 返回 WeighedSoundEvents，并把 this.sound
    // 换成 TaczSound（getLocation()=声音 id、getPath()=tacz_sounds/<id>.ogg）；1.7.10 没有
    // Sound/WeighedSoundEvents/resolve 钩子，此处改用「往 SoundHandler 的 SoundRegistry 注册一条
    // SoundPoolEntry 指向该 ogg 的事件」近似，时机同为播放前。
    /**
     * 1.7.10 对上游 {@code GunSoundInstance#resolve(SoundManager)}（上游 63-80 行）的等价物。
     *
     * <p>
     * 上游在 {@code resolve} 里把 {@code this.sound} 换成一个 {@code TaczSound}：{@code getLocation()} 返回声音 id、
     * {@code getPath()} 返回 {@code tacz_sounds/<path>.ogg}，从而<b>绕开 {@code sounds.json} 直接播放枪包里的原始
     * ogg</b>（这也是枪包能自带任意音效的原因）。
     *
     * <p>
     * 1.7.10 的声音引擎（{@code SoundHandler#playSound} → {@code SoundManager#playSound}）没有 {@code resolve}
     * 钩子，流程是：按 {@code ISound#getPositionedSoundLocation()} 在 {@code SoundRegistry} 里查
     * {@code SoundEventAccessorComposite}，取它随机选出的 {@code SoundPoolEntry}，再用该条目的 location 去
     * {@code IResourceManager} 取 ogg 流（{@code SoundManager#getURLForSoundResource}）。所以「指向原始 ogg」
     * 只能通过<b>往注册表里塞一条池条目指向 {@code tacz_sounds} 路径的事件</b>实现 —— 本方法做这件事，
     * 时机与上游一致（播放前，由 {@link SoundPlayManager} 调用）。
     *
     * <p>
     * 注册的键用 {@code registryName}（声音 id），与上游 {@code TaczSound#getLocation()} 一致：
     * 若枪包同时提供了同名 {@code sounds.json} 事件，我们这条后注册、优先，等价于上游
     * {@code hasTaczSoundResource} 为真时优先用 tacz 音源。
     */
    public void resolve() {
        if (this.useTaczSound && this.registryName != null) {
            this.redirectedSound = TACZ_SOUND_LISTER.idToFile(this.registryName);
            registerTaczSound(this.registryName, this.redirectedSound, this.source);
        } else {
            this.redirectedSound = null;
        }
    }

    /**
     * 把「声音 id → 原始 ogg 路径」注册进 {@code SoundHandler} 的 {@code SoundRegistry}（幂等）。
     */
    private static void registerTaczSound(ResourceLocation registryName, ResourceLocation soundPath,
        SoundCategory category) {
        SoundHandler soundHandler = Minecraft.getMinecraft()
            .getSoundHandler();
        if (soundHandler == null) {
            return;
        }
        SoundEventAccessorComposite registered = REGISTERED_TACZ_SOUNDS.get(registryName);
        if (registered != null && soundHandler.getSound(registryName) == registered) {
            // 还是我们注册的那一条（资源重载没清掉，也没被同名 sounds.json 事件覆盖）
            return;
        }
        SoundRegistry soundRegistry = getSoundRegistry(soundHandler);
        if (soundRegistry == null) {
            GunMod.LOGGER.warn(
                "[TACZ Sound] Cannot locate SoundHandler#sndRegistry, gun pack sound is not playable: {}",
                soundPath);
            return;
        }
        // 上游 TaczSound 从模板 Sound（tacz:gun 事件）复制 volume/pitch/weight/streaming；
        // 1.7.10 的模板就是 sounds.json 里 tacz:gun 的条目，其值即 1.0 / 1.0 / weight 1 / 非流式，故这里写死同值。
        SoundEventAccessorComposite composite = new SoundEventAccessorComposite(registryName, 1.0D, 1.0D, category);
        composite.addSoundToEventPool(new TaczSoundAccessor(soundPath));
        soundRegistry.registerSound(composite);
        REGISTERED_TACZ_SOUNDS.put(registryName, composite);
    }

    /**
     * 取 {@code SoundHandler} 私有的 {@code sndRegistry}。
     *
     * <p>
     * 为什么用反射：1.7.10 的 {@code SoundHandler} 没有任何公开的「注册声音事件」入口
     * （{@code getSound} 只读、{@code onResourceManagerReload} 私有、注册表字段 {@code sndRegistry} 私有），
     * 而上游靠 {@code Sound#getPath()} 覆盖 + {@code SoundManager$Preparations#soundCache} 注入
     * （见本工程 {@code mixin/client/SoundManagerPreparationsMixin}）达到同样目的。
     * 本工程的 {@code mixin} 包不属于 sound 工作流的写作用域，故改在这里用反射。
     *
     * <p>
     * 生产环境字段名会被 SRG 混淆，<b>按字段类型</b>（{@code SoundRegistry}）定位而不依赖名字。
     */
    @Nullable
    private static SoundRegistry getSoundRegistry(SoundHandler soundHandler) {
        Field field = soundRegistryField();
        if (field == null) {
            return null;
        }
        try {
            return (SoundRegistry) field.get(soundHandler);
        } catch (IllegalAccessException | RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ Sound] Cannot read SoundHandler#sndRegistry", e);
            return null;
        }
    }

    @Nullable
    private static Field soundRegistryField() {
        if (!soundRegistryFieldResolved) {
            soundRegistryFieldResolved = true;
            for (Field field : SoundHandler.class.getDeclaredFields()) {
                if (field.getType() == SoundRegistry.class) {
                    try {
                        field.setAccessible(true);
                        soundRegistryField = field;
                    } catch (RuntimeException e) {
                        GunMod.LOGGER.warn("[TACZ Sound] Cannot access SoundHandler#sndRegistry", e);
                    }
                    break;
                }
            }
        }
        return soundRegistryField;
    }

    /**
     * 注册表里的一条「指向枪包 ogg」的池条目。
     *
     * <p>
     * 1.7.10 的 {@code SoundEventAccessor} 构造器是包私有（无法在别的包里 new），但它实现的
     * {@link ISoundEventAccessor} 是公开接口，所以这里直接实现该接口：{@code func_148721_a()} 是权重，
     * {@code func_148720_g()} 是取出（并复制一份）池条目 —— 与 {@code SoundEventAccessor} 的行为一致。
     */
    private static final class TaczSoundAccessor implements ISoundEventAccessor {

        private final SoundPoolEntry entry;
        private final int weight;

        private TaczSoundAccessor(ResourceLocation soundPath) {
            // SoundPoolEntry(location, pitch, volume, streaming)：pitch/volume 是引擎侧乘数，1.0 为中性。
            this.entry = new SoundPoolEntry(soundPath, 1.0D, 1.0D, false);
            this.weight = 1;
        }

        @Override
        public int func_148721_a() {
            return this.weight;
        }

        @Override
        public SoundPoolEntry func_148720_g() {
            return new SoundPoolEntry(this.entry);
        }
    }
}
