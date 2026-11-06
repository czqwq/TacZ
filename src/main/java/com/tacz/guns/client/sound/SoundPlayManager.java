package com.tacz.guns.client.sound;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.SoundData;
import com.tacz.guns.config.client.SoundConfig;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.init.ModSounds;
import com.tacz.guns.mcshim.FileToIdConverter;
import com.tacz.guns.mcshim.SoundEvent;
import com.tacz.guns.network.message.ServerMessageSound;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械音效的统一播放入口。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/sound/SoundPlayManager.java}。
 *
 * <h2>1.7.10 用什么实现（逐项对照）</h2>
 * <ul>
 * <li>{@code Minecraft.getInstance()} → {@link Minecraft#getMinecraft()}；
 * {@code minecraft.level} → {@code theWorld}；{@code minecraft.player} → {@code thePlayer}。</li>
 * <li>{@code minecraft.getSoundManager().play/stop/isActive(instance)} →
 * {@link SoundHandler#playSound}/{@link SoundHandler#stopSound}/{@link SoundHandler#isSoundPlaying}；
 * {@code minecraft.getSoundManager().getSoundEvent(name) != null} →
 * {@link SoundHandler#getSound(ResourceLocation) getSound(name) != null}（都是「注册表里有没有这个事件」）。</li>
 * <li>{@code SoundEvent} / {@code SoundSource} → {@code mcshim.SoundEvent}（只是名字的薄包装）与
 * {@link SoundCategory}（1.7.10 的「声音类别」挂在<b>事件</b>上而不是 {@code ISound} 上，见
 * {@link GunSoundInstance} 的类注释；{@code PLAYERS} 与上游 {@code SoundSource.PLAYERS} 同义）。</li>
 * <li>{@code RandomSource} → {@link Random}；{@code entity.level().random} → {@code entity.worldObj.rand}；
 * {@code entity.getRandom()} → {@code EntityLivingBase#getRNG()}。</li>
 * <li>{@code Entity#getId/getUUID/isRemoved} → {@code getEntityId()/getUniqueID()/isDead}；
 * {@code ClientLevel#getEntity(int)} → {@code World#getEntityByID(int)}。</li>
 * <li>{@code LivingEntity} → {@link EntityLivingBase}；{@code LocalPlayer} → {@link EntityPlayerSP}；
 * {@code ItemStack.EMPTY} 语义 → {@code null}（本类只用 {@code IAttachment#getIAttachmentOrNull} 判空）。</li>
 * <li>{@code @OnlyIn(Dist.CLIENT)} + {@code @Mod.EventBusSubscriber} → {@code @SideOnly(Side.CLIENT)} +
 * 显式注册（见 {@link #ensureTickHandlerRegistered()}）。</li>
 * <li>{@code TickEvent.ClientTickEvent}（{@code net.minecraftforge.eventbus}）→
 * {@code cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent}（1.7.10 的 tick 事件在 FML 事件总线上）。</li>
 * <li>上游 {@code record SoundKey/TrackedGunSound} → 普通 static final 类（保留 record 式访问器方法名，
 * 使方法体与上游逐行一致；不用 {@code record} 以免 jvmDowngrader 的边界情况）。</li>
 * </ul>
 *
 * <h2>「直接播放枪包原始 ogg」的机制差异</h2>
 * 上游用 {@code GunSoundInstance#resolve(SoundManager)} 把 {@code Sound} 换成指向 {@code tacz_sounds/**.ogg}
 * 的 {@code TaczSound}；1.7.10 没有 {@code resolve} 钩子，改由 {@link GunSoundInstance#resolve()} 把
 * 「声音 id → ogg 路径」注册进 {@code SoundHandler} 的注册表。因此
 * {@link #hasTaczSoundResource(Minecraft, ResourceLocation)} 的存在性判断仍然查
 * <b>声音引擎真正读取的那个</b> {@code IResourceManager}（与上游 {@code minecraft.getResourceManager()} 同义），
 * 前提是枪包内容已经被 {@link GunPackResourcePack} 挂进 1.7.10 资源包体系
 * （{@link #playClientSound} 里在判断之前调用 {@code GunPackResourcePack#ensureInstalled()}）；
 * 未挂上时回退到 {@code sounds.json} 里的同名事件，
 * 与上游「tacz_sounds 无对应音源时回退原版 sound event」的语义一致。
 */
@SideOnly(Side.CLIENT)
public class SoundPlayManager {

    private static final FileToIdConverter TACZ_SOUND_LISTER = new FileToIdConverter("tacz_sounds", ".ogg");
    private static final SoundData DEFAULT_UNLOAD_SOUND = SoundData
        .of(new ResourceLocation(GunMod.MOD_ID, "attachments/attachment_general_uninstall"));

    /** 上游 {@code ModSounds.GUN}（{@code tacz:gun}）的 shim 形态，作为 {@link GunSoundInstance} 的「模板事件」。 */
    private static final SoundEvent GUN_SOUND_EVENT = SoundEvent.create(new ResourceLocation(ModSounds.GUN));

    private static final Map<SoundKey, ArrayDeque<TrackedGunSound>> TRACKED_GUN_SOUNDS = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> SOUND_RESOURCE_EXISTS_CACHE = new HashMap<>();
    private static final Set<ResourceLocation> MISSING_SOUND_WARNED = new HashSet<>();

    private static int soundCleanupTickCounter = 0;

    /**
     * 用于阻止连发时，反复播放 DryFire 音效
     */
    private static boolean DRY_SOUND_TRACK = true;

    /**
     * 临时缓存，用于停止播放的
     */
    private static GunSoundInstance tmpSoundInstance = null;

    /**
     * 客户端 tick 处理器是否已注册（见 {@link #ensureTickHandlerRegistered()}）。
     */
    private static boolean tickHandlerRegistered = false;

    @Nullable
    public static GunSoundInstance playClientSound(Entity entity, @Nullable ResourceLocation name, float volume,
        float pitch, int distance, boolean mono) {
        boolean trackFirstPerson = SoundConfig.FIRST_PERSON_ANIMATION_SOUND_TRACKING.get();
        return playClientSound(
            entity,
            name,
            volume,
            pitch,
            distance,
            mono,
            SoundConfig.DEFAULT_SOUND_CONCURRENCY_LIMIT.get(),
            trackFirstPerson,
            true);
    }

    @Nullable
    public static GunSoundInstance playClientSound(Entity entity, @Nullable SoundData sound, float volume, float pitch,
        int distance) {
        return playClientSound(entity, sound, volume, pitch, distance, false);
    }

    @Nullable
    public static GunSoundInstance playClientSound(Entity entity, @Nullable SoundData sound, float volume, float pitch,
        int distance, boolean mono) {
        boolean trackFirstPerson = SoundConfig.FIRST_PERSON_ANIMATION_SOUND_TRACKING.get();
        return playClientSound(
            entity,
            sound,
            volume,
            pitch,
            distance,
            mono,
            SoundConfig.DEFAULT_SOUND_CONCURRENCY_LIMIT.get(),
            trackFirstPerson,
            true);
    }

    @Nullable
    private static GunSoundInstance playClientSound(Entity entity, @Nullable SoundData sound, float volume, float pitch,
        int distance, boolean mono, int concurrencyLimit, boolean trackEntity, boolean relative) {
        if (sound == null) {
            return null;
        }
        // 上游：entity.level().random / RandomSource.create()。1.7.10 的世界随机源是 World#rand。
        Random random = entity != null ? entity.worldObj.rand : new Random();
        float resolvedVolume = sound.resolveVolume(random, volume);
        float resolvedPitch = sound.resolvePitch(random, pitch);
        return playClientSound(
            entity,
            sound.getSound(),
            resolvedVolume,
            resolvedPitch,
            distance,
            mono,
            concurrencyLimit,
            trackEntity,
            relative);
    }

    @Nullable
    private static GunSoundInstance playClientSound(Entity entity, @Nullable ResourceLocation name, float volume,
        float pitch, int distance, boolean mono, int concurrencyLimit, boolean trackEntity, boolean relative) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (name == null) {
            return null;
        }
        SoundHandler soundHandler = minecraft.getSoundHandler();
        if (soundHandler == null) {
            return null;
        }
        ensureTickHandlerRegistered();
        // TODO(L2) 移植特有：上游不需要这一步 —— 1.20 的 ResourceManager 里本来就能看到枪包的
        // assets/tacz/tacz_sounds/**.ogg；1.7.10 的枪包不在原版资源包体系里（见 GunPackResourcePack 的类注释），
        // 所以先把「枪包 ogg 资源包」挂进原版资源管理器，下面的 hasTaczSoundResource 与声音引擎的取流
        // 才会命中同一份数据，语义与上游 minecraft.getResourceManager().getResource(...) 完全一致。
        GunPackResourcePack.ensureInstalled();
        boolean useTaczSound = hasTaczSoundResource(minecraft, name);
        // 上游：!useTaczSound && minecraft.getSoundManager().getSoundEvent(name) == null
        if (!useTaczSound && soundHandler.getSound(name) == null) {
            warnMissingSound(name);
            return null;
        }
        if (concurrencyLimit > 0) {
            limitConcurrentGunSound(soundHandler, entity.getEntityId(), name, concurrencyLimit);
        }
        SoundEvent soundEvent = useTaczSound ? GUN_SOUND_EVENT : SoundEvent.createVariableRangeEvent(name);
        GunSoundInstance instance = trackEntity
            ? new EntityTrackingGunSoundInstance(
                soundEvent,
                SoundCategory.PLAYERS,
                volume,
                pitch,
                entity,
                distance,
                name,
                mono,
                useTaczSound)
            : new GunSoundInstance(
                soundEvent,
                SoundCategory.PLAYERS,
                volume,
                pitch,
                entity,
                distance,
                name,
                mono,
                relative,
                useTaczSound);
        // 1.7.10 等价于「引擎在 play() 内部调用 instance.resolve(manager)」：先把 ogg 路径落进注册表，再播放。
        instance.resolve();
        // 上游的 canPlaySound() 由引擎在 play 时询问；1.7.10 的 ISound 没有这个钩子，改为播放前自己判断。
        // 第二个条件对应「resolve() 没能把事件注册进注册表」（只可能是反射被拦），此时引擎会对每次播放
        // 打一条 "Unable to play unknown soundEvent" 警告，这里提前拦掉；对 sounds.json 回退路径恒为真。
        if (instance.canPlaySound() && soundHandler.getSound(name) != null) {
            soundHandler.playSound(instance);
        }
        if (concurrencyLimit > 0) {
            trackGunSound(entity.getEntityId(), entity.getUniqueID(), name, instance);
        }
        // traceGunSoundPlay(minecraft, instance, entity, name, volume, pitch, distance, mono);
        return instance;
    }

    @Nullable
    public static GunSoundInstance playClientSound(Entity entity, @Nullable ResourceLocation name, float volume,
        float pitch, int distance) {
        return playClientSound(entity, name, volume, pitch, distance, false);
    }

    @Nullable
    public static GunSoundInstance playAnimationSound(Entity entity, @Nullable ResourceLocation name, float volume,
        float pitch, int distance) {
        if (isLocalPlayer(entity)) {
            boolean trackFirstPerson = SoundConfig.FIRST_PERSON_ANIMATION_SOUND_TRACKING.get();
            return playClientSound(
                entity,
                name,
                volume,
                pitch,
                distance,
                false,
                SoundConfig.HIGH_FREQUENCY_SOUND_CONCURRENCY_LIMIT.get(),
                trackFirstPerson,
                !trackFirstPerson);
        }
        return playClientSound(
            entity,
            name,
            volume,
            pitch,
            distance,
            false,
            SoundConfig.HIGH_FREQUENCY_SOUND_CONCURRENCY_LIMIT.get(),
            true,
            false);
    }

    public static void stopPlayGunSound() {
        if (tmpSoundInstance != null) {
            tmpSoundInstance.setStop();
        }
    }

    public static void stopPlayGunSound(GunDisplayInstance gunIndex, String animationName) {
        if (tmpSoundInstance != null) {
            SoundData sound = gunIndex.getSounds(animationName);
            if (sound != null && tmpSoundInstance.getRegistryName() != null
                && tmpSoundInstance.getRegistryName()
                    .equals(sound.getSound())) {
                tmpSoundInstance.setStop();
            }
        }
    }

    public static void playerRefitSound(ItemStack attachmentItem, EntityPlayerSP player, String soundName) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) {
            return;
        }
        ResourceLocation attachmentId = iAttachment.getAttachmentId(attachmentItem);
        TimelessAPI.getClientAttachmentIndex(attachmentId)
            .ifPresent(index -> {
                Map<String, SoundData> sounds = index.getSounds();
                if (sounds.containsKey(soundName)) {
                    SoundData sound = sounds.get(soundName);
                    SoundPlayManager
                        .playClientSound(player, sound, 1.0f, 1.0f, GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
                }
            });
    }

    /**
     * 播放开火音效。
     *
     * <p>
     * <b>1.7.10 判空（本轮同类崩溃专项）</b>：{@code gunIndex}（{@code TimelessAPI.getGunDisplay(...)} 的结果）
     * 与 {@code gunData}（客户端索引）在「枪包资源尚未就绪 / 该枪不在索引里」时都是 null ——
     * 上游 1.20 这些是从非 null 的 {@code GunDisplayInstance}/{@code GunData} 传入，1.7.10 侧可能出现 null。
     * 本方法是跨包共享入口（{@code client/gameplay/**}、{@code client/renderer/**}、{@code api/**} 都调），
     * 因此在这里一次性判空，而不是要求每个调用点重复判断。
     */
    public static void playShootSound(EntityLivingBase entity, GunDisplayInstance gunIndex, GunData gunData) {
        if (entity == null || gunIndex == null || gunData == null || gunData.getFireSound() == null) {
            return;
        }
        playClientSound(
            entity,
            gunIndex.getSounds(SoundManager.SHOOT_SOUND),
            0.8f,
            0.9f + entity.getRNG()
                .nextFloat() * 0.125f,
            (int) (GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get() * gunData.getFireSound()
                .getFireMultiplier()),
            false,
            SoundConfig.HIGH_FREQUENCY_SOUND_CONCURRENCY_LIMIT.get(),
            false,
            false);
    }

    public static void playSilenceSound(EntityLivingBase entity, GunDisplayInstance gunIndex, GunData gunData) {
        if (entity == null || gunIndex == null || gunData == null || gunData.getFireSound() == null) {
            return;
        }
        playClientSound(
            entity,
            gunIndex.getSounds(SoundManager.SILENCE_SOUND),
            0.6f,
            0.9f + entity.getRNG()
                .nextFloat() * 0.125f,
            (int) (GunConfig.DEFAULT_GUN_SILENCE_SOUND_DISTANCE.get() * gunData.getFireSound()
                .getSilenceMultiplier()),
            false,
            SoundConfig.HIGH_FREQUENCY_SOUND_CONCURRENCY_LIMIT.get(),
            false,
            false);
    }

    public static void playDryFireSound(EntityLivingBase entity, GunDisplayInstance gunIndex) {
        if (entity == null || gunIndex == null) {
            return;
        }
        if (DRY_SOUND_TRACK) {
            playClientSound(
                entity,
                gunIndex.getSounds(SoundManager.DRY_FIRE_SOUND),
                1.0f,
                0.9f + entity.getRNG()
                    .nextFloat() * 0.125f,
                GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
            DRY_SOUND_TRACK = false;
        }
    }

    /**
     * 只有松开鼠标时，才会重置
     */
    public static void resetDryFireSound() {
        DRY_SOUND_TRACK = true;
    }

    /**
     * 播放常规音效（换弹 / 检视 / 拉栓 / 收枪 / 切枪 / 近战 / 命中），见 {@link #playShootSound} 的判空说明。
     *
     * <p>
     * 这批方法的 {@code display} 参数在 1.7.10 可能为 null（{@code getGunDisplay} 拿不到索引），
     * 且它们都会解引用 {@code display.getSounds(...)} 与 {@code entity.getRNG()}，因此统一在入口判空。
     */
    public static void playReloadSound(EntityLivingBase entity, GunDisplayInstance display, boolean noAmmo) {
        if (entity == null || display == null) {
            return;
        }
        if (noAmmo) {
            tmpSoundInstance = playClientSound(
                entity,
                display.getSounds(SoundManager.RELOAD_EMPTY_SOUND),
                1.0f,
                1.0f,
                GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
        } else {
            tmpSoundInstance = playClientSound(
                entity,
                display.getSounds(SoundManager.RELOAD_TACTICAL_SOUND),
                1.0f,
                1.0f,
                GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
        }
    }

    public static void playInspectSound(EntityLivingBase entity, GunDisplayInstance display, boolean noAmmo) {
        if (entity == null || display == null) {
            return;
        }
        if (noAmmo) {
            tmpSoundInstance = playClientSound(
                entity,
                display.getSounds(SoundManager.INSPECT_EMPTY_SOUND),
                1.0f,
                1.0f,
                GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
        } else {
            tmpSoundInstance = playClientSound(
                entity,
                display.getSounds(SoundManager.INSPECT_SOUND),
                1.0f,
                1.0f,
                GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
        }
    }

    public static void playBoltSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        tmpSoundInstance = playClientSound(
            entity,
            display.getSounds(SoundManager.BOLT_SOUND),
            1.0f,
            1.0f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playDrawSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        tmpSoundInstance = playClientSound(
            entity,
            display.getSounds(SoundManager.DRAW_SOUND),
            1.0f,
            1.0f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playPutAwaySound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        tmpSoundInstance = playClientSound(
            entity,
            display.getSounds(SoundManager.PUT_AWAY_SOUND),
            1.0f,
            1.0f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playFireSelectSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        playClientSound(
            entity,
            display.getSounds(SoundManager.FIRE_SELECT),
            1.0f,
            1.0f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playMeleeBayonetSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        playClientSound(
            entity,
            display.getSounds(SoundManager.MELEE_BAYONET),
            1.0f,
            0.9f + entity.getRNG()
                .nextFloat() * 0.125f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playMeleePushSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        playClientSound(
            entity,
            display.getSounds(SoundManager.MELEE_PUSH),
            1.0f,
            0.9f + entity.getRNG()
                .nextFloat() * 0.125f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playMeleeStockSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        playClientSound(
            entity,
            display.getSounds(SoundManager.MELEE_STOCK),
            1.0f,
            0.9f + entity.getRNG()
                .nextFloat() * 0.125f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playUnloadSound(EntityLivingBase entity, ResourceLocation gunId, ResourceLocation gunDisplayId) {
        SoundData sound = TimelessAPI.getGunDisplay(gunDisplayId, gunId)
            .map(display -> display.getSounds(SoundManager.UNLOAD_SOUND))
            .orElse(DEFAULT_UNLOAD_SOUND);
        playClientSound(entity, sound, 1.0F, 1.0F, GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playHeadHitSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        boolean relative = isLocalPlayer(entity);
        playClientSound(
            entity,
            display.getSounds(SoundManager.HEAD_HIT_SOUND),
            1.0f,
            1.0f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get(),
            false,
            SoundConfig.HIT_SOUND_CONCURRENCY_LIMIT.get(),
            !relative,
            relative);
    }

    public static void playFleshHitSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        boolean relative = isLocalPlayer(entity);
        playClientSound(
            entity,
            display.getSounds(SoundManager.FLESH_HIT_SOUND),
            1.0f,
            1.0f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get(),
            false,
            SoundConfig.HIT_SOUND_CONCURRENCY_LIMIT.get(),
            !relative,
            relative);
    }

    public static void playKillSound(EntityLivingBase entity, GunDisplayInstance display) {
        if (entity == null || display == null) {
            return;
        }
        playClientSound(
            entity,
            display.getSounds(SoundManager.KILL_SOUND),
            1.0f,
            1.0f,
            GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
    }

    public static void playMessageSound(ServerMessageSound message) {
        // 上游 ClientLevel level = Minecraft.getInstance().level; 1.7.10 对应 theWorld。
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null || !(world.getEntityByID(message.getEntityId()) instanceof EntityLivingBase livingEntity)) {
            return;
        }
        ResourceLocation gunId = message.getGunId();
        ResourceLocation gunDisplayId = message.getGunDisplayId();
        TimelessAPI.getGunDisplay(gunDisplayId, gunId)
            .ifPresent(index -> {
                String soundName = message.getSoundName();
                SoundData sound = index.getSounds(soundName);
                if (sound == null) {
                    return;
                }
                if (SoundManager.SHOOT_3P_SOUND.equals(soundName) || SoundManager.SILENCE_3P_SOUND.equals(soundName)) {
                    playClientSound(
                        livingEntity,
                        sound,
                        message.getVolume(),
                        message.getPitch(),
                        message.getDistance(),
                        true,
                        SoundConfig.HIGH_FREQUENCY_SOUND_CONCURRENCY_LIMIT.get(),
                        true,
                        false);
                } else {
                    playClientSound(
                        livingEntity,
                        sound,
                        message.getVolume(),
                        message.getPitch(),
                        message.getDistance(),
                        true,
                        SoundConfig.DEFAULT_SOUND_CONCURRENCY_LIMIT.get(),
                        true,
                        false);
                }
            });
    }

    /**
     * 客户端 tick：定期清理已失效实体留下的追踪记录（上游 {@code @SubscribeEvent onClientTick}）。
     *
     * <p>
     * 1.7.10 的 FML 事件总线<b>不支持静态 {@code @SubscribeEvent} 方法</b>
     * （{@code ASMEventHandler} 生成的包装类用 {@code INVOKEVIRTUAL} 调用，静态方法会
     * {@code IncompatibleClassChangeError}），因此真正挂到总线上的是 {@link ClientTickHandler} 的实例方法。
     */
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) {
            cleanupInvalidEntitySounds(minecraft);
            return;
        }
        soundCleanupTickCounter++;
        if (soundCleanupTickCounter % 5 == 0) {
            cleanupInvalidEntitySounds(minecraft);
        }
    }

    public static void clearSoundResourceCache() {
        SOUND_RESOURCE_EXISTS_CACHE.clear();
        MISSING_SOUND_WARNED.clear();
        // TODO(L2) 移植特有：上游这里只有一个 ResourceManager，枪包重载与资源重载天然同步；
        // 1.7.10 的枪包 ogg 由 GunPackResourcePack 自行索引，必须一起失效。
        GunPackResourcePack.invalidateCache();
    }

    // TODO(L2): 上游 SoundPlayManager.java:36-37 用 `@Mod.EventBusSubscriber(value = Dist.CLIENT)` 让 Forge 自动把
    // `onClientTick`（上游 471-485）挂到 TickEvent.ClientTickEvent；1.7.10 没有该注解机制，且 FML 的 EventBus
    // 不支持静态 @SubscribeEvent，此处改用「首次播放时把实例处理器 TICK_HANDLER 注册到 FML 事件总线」。
    /**
     * 客户端 tick 处理器单例。
     *
     * <p>
     * 上游没有这个字段（{@code @Mod.EventBusSubscriber} 直接挂在类上）。1.7.10 必须注册一个<b>实例</b>
     * （FML 的 {@code EventBus} 不支持静态 {@code @SubscribeEvent}，见 {@link #ensureTickHandlerRegistered()}），
     * 做成单例后 {@code ClientProxy} 若之后要显式注册
     * （{@code FMLCommonHandler.instance().bus().register(SoundPlayManager.TICK_HANDLER)}）也不会与自注册重复。
     */
    public static final ClientTickHandler TICK_HANDLER = new ClientTickHandler();

    /**
     * 1.7.10 的 {@code @Mod.EventBusSubscriber} 替代：把 {@link #onClientTick(TickEvent.ClientTickEvent)}
     * 挂到 FML 事件总线（{@code TickEvent.ClientTickEvent} 在 1.7.10 走 FML 总线）。
     *
     * <p>
     * 双保险：{@code ClientProxy#init} 已显式注册 {@link #TICK_HANDLER}（幂等，{@code EventBus#register}
     * 对同一实例直接返回）；本方法只为「注册点缺失」时兜底，首次播放时自注册一次。
     * <b>不要</b>再 new 另一个处理器实例，否则每 tick 会跑两遍（清理本身幂等，只是多做一次遍历）。
     *
     * <p>
     * 注意：1.7.10 的 {@code EventBus#register} 会用 {@code Loader#activeModContainer()} 记 owner，游戏内调用时该值为
     * null 会回退到 {@code findActiveContainerFromStack()}（按包名反查 mod）；两者都失败时 FML 只会多打一条
     * ERROR 日志并回退到 Minecraft 的容器，<b>不影响注册本身</b>。
     */
    private static void ensureTickHandlerRegistered() {
        if (tickHandlerRegistered) {
            return;
        }
        tickHandlerRegistered = true;
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(TICK_HANDLER);
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ Sound] Failed to register client tick handler for gun sound cleanup", e);
        }
    }

    /** 见 {@link #ensureTickHandlerRegistered()}。 */
    public static final class ClientTickHandler {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            SoundPlayManager.onClientTick(event);
        }
    }

    private static void limitConcurrentGunSound(SoundHandler soundHandler, int entityId, ResourceLocation soundId,
        int limit) {
        SoundKey key = new SoundKey(entityId, soundId);
        ArrayDeque<TrackedGunSound> sounds = TRACKED_GUN_SOUNDS.get(key);
        if (sounds == null) {
            return;
        }
        int activeForKey = 0;
        Iterator<TrackedGunSound> iterator = sounds.iterator();
        while (iterator.hasNext()) {
            TrackedGunSound tracked = iterator.next();
            if (soundHandler.isSoundPlaying(tracked.instance())) {
                activeForKey++;
            } else {
                iterator.remove();
            }
        }
        if (sounds.isEmpty()) {
            TRACKED_GUN_SOUNDS.remove(key);
        }

        int toStop = activeForKey - limit + 1;
        if (toStop <= 0) {
            return;
        }

        int stopped = 0;
        iterator = sounds.iterator();
        while (iterator.hasNext() && stopped < toStop) {
            TrackedGunSound tracked = iterator.next();
            if (soundHandler.isSoundPlaying(tracked.instance())) {
                tracked.instance()
                    .setStop();
                iterator.remove();
                stopped++;
            }
        }
        if (sounds.isEmpty()) {
            TRACKED_GUN_SOUNDS.remove(key);
        }
    }

    private static void trackGunSound(int entityId, UUID entityUuid, ResourceLocation soundId,
        GunSoundInstance instance) {
        SoundKey key = new SoundKey(entityId, soundId);
        TRACKED_GUN_SOUNDS.computeIfAbsent(key, ignored -> new ArrayDeque<>())
            .addLast(new TrackedGunSound(instance, entityUuid));
    }

    private static void cleanupInvalidEntitySounds(Minecraft minecraft) {
        if (minecraft.theWorld == null) {
            stopAndClearTrackedSounds();
            return;
        }
        SoundHandler soundHandler = minecraft.getSoundHandler();
        if (soundHandler == null) {
            stopAndClearTrackedSounds();
            return;
        }
        Iterator<Map.Entry<SoundKey, ArrayDeque<TrackedGunSound>>> entryIterator = TRACKED_GUN_SOUNDS.entrySet()
            .iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<SoundKey, ArrayDeque<TrackedGunSound>> entry = entryIterator.next();
            Iterator<TrackedGunSound> soundIterator = entry.getValue()
                .iterator();
            while (soundIterator.hasNext()) {
                TrackedGunSound tracked = soundIterator.next();
                if (!soundHandler.isSoundPlaying(tracked.instance())) {
                    soundIterator.remove();
                    continue;
                }
                Entity owner = minecraft.theWorld.getEntityByID(
                    entry.getKey()
                        .entityId());
                if (isInvalidSoundOwner(owner, tracked.entityUuid())) {
                    tracked.instance()
                        .setStop();
                    soundIterator.remove();
                }
            }
            if (entry.getValue()
                .isEmpty()) {
                entryIterator.remove();
            }
        }
    }

    private static void stopAndClearTrackedSounds() {
        for (ArrayDeque<TrackedGunSound> sounds : TRACKED_GUN_SOUNDS.values()) {
            for (TrackedGunSound tracked : sounds) {
                tracked.instance()
                    .setStop();
            }
        }
        TRACKED_GUN_SOUNDS.clear();
    }

    private static boolean isInvalidSoundOwner(@Nullable Entity entity, UUID entityUuid) {
        // 上游：entity == null || !uuid.equals(...) || entity.isRemoved() || isDeadOrDying()
        // 1.7.10：isRemoved() → isDead；LivingEntity#isDeadOrDying() → !EntityLivingBase#isEntityAlive()
        return entity == null || !entity.getUniqueID()
            .equals(entityUuid)
            || entity.isDead
            || entity instanceof EntityLivingBase livingEntity && !livingEntity.isEntityAlive();
    }

    private static boolean isLocalPlayer(Entity entity) {
        return entity == Minecraft.getMinecraft().thePlayer;
    }

    private static boolean hasTaczSoundResource(Minecraft minecraft, ResourceLocation soundId) {
        return SOUND_RESOURCE_EXISTS_CACHE.computeIfAbsent(soundId, id -> {
            ResourceLocation soundPath = TACZ_SOUND_LISTER.idToFile(id);
            // 上游：minecraft.getResourceManager().getResource(soundPath).isPresent()
            // 1.7.10 的 IResourceManager#getResource 找不到时抛 IOException（没有 Optional）。
            if (minecraft.getResourceManager() == null) {
                return false;
            }
            try {
                minecraft.getResourceManager()
                    .getResource(soundPath);
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }

    private static void warnMissingSound(ResourceLocation soundId) {
        if (MISSING_SOUND_WARNED.add(soundId)) {
            ResourceLocation soundPath = TACZ_SOUND_LISTER.idToFile(soundId);
            GunMod.LOGGER.warn(
                "[TACZ Sound] Missing tacz_sounds resource and fallback sound event, skipped. sound={}, path={}",
                soundId,
                soundPath);
        }
    }

    /**
     * 上游 {@code private record SoundKey(int entityId, ResourceLocation soundId)}。
     *
     * <p>
     * 不用 {@code record}（jvmDowngrader 边界情况），但保留 record 式的访问器方法名，
     * 使 {@link #cleanupInvalidEntitySounds(Minecraft)} 等调用点与上游逐行一致；作为 {@code HashMap} 键必须实现
     * {@code equals/hashCode}。
     */
    private static final class SoundKey {

        private final int entityId;
        private final ResourceLocation soundId;

        private SoundKey(int entityId, ResourceLocation soundId) {
            this.entityId = entityId;
            this.soundId = soundId;
        }

        private int entityId() {
            return this.entityId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SoundKey soundKey)) {
                return false;
            }
            return this.entityId == soundKey.entityId && this.soundId.equals(soundKey.soundId);
        }

        @Override
        public int hashCode() {
            return 31 * this.entityId + this.soundId.hashCode();
        }
    }

    /**
     * 上游 {@code private record TrackedGunSound(GunSoundInstance instance, UUID entityUuid)}。
     */
    private static final class TrackedGunSound {

        private final GunSoundInstance instance;
        private final UUID entityUuid;

        private TrackedGunSound(GunSoundInstance instance, UUID entityUuid) {
            this.instance = instance;
            this.entityUuid = entityUuid;
        }

        private GunSoundInstance instance() {
            return this.instance;
        }

        private UUID entityUuid() {
            return this.entityUuid;
        }
    }
}
