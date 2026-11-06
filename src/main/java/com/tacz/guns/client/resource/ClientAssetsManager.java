package com.tacz.guns.client.resource;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.gltf.AnimationStructure;
import com.tacz.guns.api.vmlib.LuaAnimationConstant;
import com.tacz.guns.api.vmlib.LuaGunAnimationConstant;
import com.tacz.guns.api.vmlib.LuaLibrary;
import com.tacz.guns.client.resource.manager.DisplayManager;
import com.tacz.guns.client.resource.manager.GltfManager;
import com.tacz.guns.client.resource.manager.PackInfoManager;
import com.tacz.guns.client.resource.pojo.CommonTransformObject;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.client.resource.pojo.animation.bedrock.AnimationKeyframes;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.resource.pojo.animation.bedrock.ParticleEffectKeyframes;
import com.tacz.guns.client.resource.pojo.animation.bedrock.SoundEffectKeyframes;
import com.tacz.guns.client.resource.pojo.display.SoundData;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoDisplay;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.block.BlockDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.AdsPreset;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import com.tacz.guns.client.resource.serialize.AnimationKeyframesSerializer;
import com.tacz.guns.client.resource.serialize.ItemStackSerializer;
import com.tacz.guns.client.resource.serialize.ParticleEffectKeyframesSerializer;
import com.tacz.guns.client.resource.serialize.SoundEffectKeyframesSerializer;
import com.tacz.guns.client.resource.serialize.Vector3fSerializer;
import com.tacz.guns.client.sound.GunPackResourcePack;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.IGunPackReloadListener;
import com.tacz.guns.resource.manager.JsonDataManager;
import com.tacz.guns.resource.manager.LazyJsonDataManager;
import com.tacz.guns.resource.manager.ScriptManager;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.PackDirectory;
import com.tacz.guns.resource.serialize.EnumMapTypeAdapterFactory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端资源管理器<br/>
 * 所有枪包资源缓存在此
 *
 * <p>
 * <b>移植说明</b>：上游把这里的数据管理器注册成 1.20 的 {@code PreparableReloadListener}，由原版资源重载管线驱动。
 * 1.7.10 的枪包资源不在原版资源包体系里（原版 {@code IResourcePack} 只认 {@code assets/}，也没有 datapack），
 * 因此数据管理器改成 {@code IGunPackReloadListener}：{@link #reloadAndRegister} 只在客户端 postInit 建立一次管理器，
 * 之后由 {@code ClientGunPackReloader} 在每轮原版资源重载时按注册顺序串行驱动 {@code reload()}，
 * 扫描数据源是 {@code GunPackLoader} 暴露的枪包来源（{@code GunPackResources#scanDirectory}）。
 */
@SideOnly(Side.CLIENT)
public enum ClientAssetsManager {

    INSTANCE;

    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter())
        .registerTypeAdapter(CubesItem.class, new CubesItem.Deserializer())
        .registerTypeAdapter(Vector3f.class, new Vector3fSerializer())
        .registerTypeAdapter(CommonTransformObject.class, new CommonTransformObject.Serializer())
        .registerTypeAdapter(ItemStack.class, new ItemStackSerializer())
        .registerTypeAdapter(AnimationKeyframes.class, new AnimationKeyframesSerializer())
        .registerTypeAdapter(SoundEffectKeyframes.class, new SoundEffectKeyframesSerializer())
        .registerTypeAdapter(ParticleEffectKeyframes.class, new ParticleEffectKeyframesSerializer())
        .registerTypeAdapter(SoundData.class, new SoundData.Deserializer())
        // TODO(port): 上游 ClientAssetsManager.java:67-68 还注册了
        // ItemTransforms.Deserializer / ItemTransform.Deserializer。1.7.10 完全没有物品模型 JSON 体系
        // （net.minecraft.client.renderer.block.model.ItemTransforms / ItemTransform 不存在），
        // BlockDisplay#transforms 改用 TACZ 自有的等价 POJO 承载，这两个注册项被移除。
        // 1.7.10 差异：gson 2.2.4 不会为 EnumMap 字段构造 EnumMap（会构造 LinkedHashMap 导致字段 set 失败），
        // 例如 GunDisplay#controllableData；上游 gson 2.10 原生支持，这里显式补齐。
        .registerTypeAdapterFactory(new EnumMapTypeAdapterFactory())
        .create();

    // 枪械展示数据
    private DisplayManager<GunDisplay> gunDisplay;
    private JsonDataManager<AdsPreset> adsPresets;
    // 弹药展示数据
    private DisplayManager<AmmoDisplay> ammoDisplay;
    // 配件展示数据
    private DisplayManager<AttachmentDisplay> attachmentDisplay;
    private DisplayManager<AttachmentDisplay> attachmentVariantDisplay;
    private DisplayManager<AttachmentDisplay> slotAdapterDisplay;
    // 方块展示数据
    private DisplayManager<BlockDisplay> blockDisplay;
    // 原始基岩版模型
    private LazyJsonDataManager<BedrockModelPOJO> bedrockModel;
    // 基岩版模型动画
    private LazyJsonDataManager<BedrockAnimationFile> bedrockAnimation;
    // gltf 动画
    private GltfManager gltfAnimation;
    // 客户端脚本
    private final List<LuaLibrary> libList = new ArrayList<LuaLibrary>();
    private ScriptManager scriptManager;
    // 枪包元数据
    private PackInfoManager packInfo;

    /**
     * 1.7.10 替身：上游用 {@code PreparableReloadListener} 列表挂进原版资源重载管线。
     * common-resource 把所有管理器改造为 {@link IGunPackReloadListener}（同步单段 {@code reload()}），
     * 因此这里也用同一种接口收集，由 {@code ClientProxy} 的资源重载钩子按顺序驱动。
     */
    private List<IGunPackReloadListener> listeners;

    ClientAssetsManager() {
        // 1.7.10 的 Java 8 目标不支持 List.of(...)，改用可变列表填充
        libList.add(new LuaAnimationConstant());
        libList.add(new LuaGunAnimationConstant());
    }

    public void reloadAndRegister(Consumer<IGunPackReloadListener> register) {
        if (listeners == null) {
            listeners = new ArrayList<>();
            gunDisplay = register(
                new DisplayManager<>(
                    GunDisplay.class,
                    GSON,
                    PackDirectory.of("display/guns", ".json"),
                    "GunDisplayLoader",
                    GunPackResources.ASSETS_ROOT));
            adsPresets = register(
                new JsonDataManager<>(
                    AdsPreset.class,
                    GSON,
                    PackDirectory.of("ads_presets", ".json"),
                    "AdsPresetLoader",
                    GunPackResources.ASSETS_ROOT));
            ammoDisplay = register(
                new DisplayManager<>(
                    AmmoDisplay.class,
                    GSON,
                    PackDirectory.of("display/ammo", ".json"),
                    "AmmoDisplayLoader",
                    GunPackResources.ASSETS_ROOT));
            attachmentDisplay = register(
                new DisplayManager<>(
                    AttachmentDisplay.class,
                    GSON,
                    PackDirectory.of("display/attachments", ".json"),
                    "AttachmentDisplayLoader",
                    GunPackResources.ASSETS_ROOT));
            attachmentVariantDisplay = register(
                new DisplayManager<>(
                    AttachmentDisplay.class,
                    GSON,
                    PackDirectory.of("display/attachment_variants", ".json"),
                    "AttachmentVariantDisplayLoader",
                    GunPackResources.ASSETS_ROOT));
            slotAdapterDisplay = register(
                new DisplayManager<>(
                    AttachmentDisplay.class,
                    GSON,
                    PackDirectory.of("display/slot_adapters", ".json"),
                    "SlotAdapterDisplayLoader",
                    GunPackResources.ASSETS_ROOT));
            blockDisplay = register(
                new DisplayManager<>(
                    BlockDisplay.class,
                    GSON,
                    PackDirectory.of("display/blocks", ".json"),
                    "BlockDisplayLoader",
                    GunPackResources.ASSETS_ROOT));

            bedrockModel = register(
                new LazyJsonDataManager<>(
                    BedrockModelPOJO.class,
                    GSON,
                    PackDirectory.of("geo_models", ".json"),
                    "BedrockModelLoader",
                    GunPackResources.ASSETS_ROOT,
                    id -> GunMod.MOD_ID.equals(id.getResourceDomain())));
            bedrockAnimation = register(
                new LazyJsonDataManager<>(
                    BedrockAnimationFile.class,
                    GSON,
                    PackDirectory.of("animations", ".animation.json"),
                    "BedrockAnimationLoader",
                    // 客户端动画同样在 assets/ 下（上游 ClientAssetsManager 用 assets 根）；漏掉这个参数会
                    // 去 data/ 找 → 全部查不到 → GunDisplayInstance#checkAnimation 抛 "animation not found"
                    // → ClientIndexManager 丢弃 display → 枪无模型/无动画（实测 r7run 前状态）。
                    GunPackResources.ASSETS_ROOT,
                    id -> GunMod.MOD_ID.equals(id.getResourceDomain())));
            gltfAnimation = register(new GltfManager());
            scriptManager = register(
                new ScriptManager(PackDirectory.of("scripts", ".lua"), libList, GunPackResources.ASSETS_ROOT));
            packInfo = register(new PackInfoManager());
            // 上游这里用一个匿名的 PreparableReloadListener 在重载末尾跑 ClientIndexManager::reload。
            // 1.7.10 侧改为 IGunPackReloadListener（同步，排在所有数据管理器之后）。
            register(ClientIndexManager::reload);
        }
        listeners.forEach(register);
    }

    private <T extends IGunPackReloadListener> T register(T listener) {
        listeners.add(listener);
        return listener;
    }

    @Nullable
    public GunDisplay getGunDisplay(ResourceLocation id) {
        return gunDisplay == null ? null : gunDisplay.getData(id);
    }

    public @Nullable AdsPreset getAdsPreset(ResourceLocation id) {
        return adsPresets == null ? null : adsPresets.getData(id);
    }

    public Set<Map.Entry<ResourceLocation, GunDisplay>> getGunDisplays() {
        if (gunDisplay == null) {
            // 见 getPackInfo(String) 的说明：管理器就绪前返回空集合，让上层「无枪包数据」而不是崩溃。
            return Collections.emptySet();
        }
        return gunDisplay.getAllData()
            .entrySet();
    }

    public Set<ResourceLocation> getGunDisplayIds() {
        if (gunDisplay == null) {
            return Collections.emptySet();
        }
        return gunDisplay.getAllData()
            .keySet();
    }

    @Nullable
    public AttachmentDisplay getAttachmentDisplay(ResourceLocation id) {
        return attachmentDisplay == null ? null : attachmentDisplay.getData(id);
    }

    @Nullable
    public AttachmentDisplay getAttachmentVariantDisplay(ResourceLocation id) {
        return attachmentVariantDisplay == null ? null : attachmentVariantDisplay.getData(id);
    }

    @Nullable
    public AttachmentDisplay getSlotAdapterDisplay(ResourceLocation id) {
        return slotAdapterDisplay == null ? null : slotAdapterDisplay.getData(id);
    }

    @Nullable
    public AmmoDisplay getAmmoDisplay(ResourceLocation id) {
        return ammoDisplay == null ? null : ammoDisplay.getData(id);
    }

    @Nullable
    public BlockDisplay getBlockDisplay(ResourceLocation id) {
        return blockDisplay == null ? null : blockDisplay.getData(id);
    }

    @Nullable
    public BedrockModelPOJO getBedrockModelPOJO(ResourceLocation id) {
        return bedrockModel == null ? null : bedrockModel.getData(id);
    }

    @Nullable
    public BedrockAnimationFile getBedrockAnimations(ResourceLocation id) {
        return bedrockAnimation == null ? null : bedrockAnimation.getData(id);
    }

    @Nullable
    public LuaTable getScript(ResourceLocation id) {
        return scriptManager == null ? null : scriptManager.getScript(id);
    }

    @Nullable
    public AnimationStructure getGltfAnimation(ResourceLocation id) {
        return gltfAnimation == null ? null : gltfAnimation.getGltfAnimation(id);
    }

    @Nullable
    public PackInfo getPackInfo(String namespace) {
        // 1.7.10 安全网：管理器由 ClientGunPackReloader 在客户端 postInit 建立；
        // 任何在建立之前就打开 GUI / 读取展示数据的路径都必须容忍「尚未装载」而不是 NPE
        // （实测崩溃：GuiContainerCreative.initGui → CreativePackFilters.updateTab → 这里抛 NullPointerException）。
        if (packInfo == null) {
            return null;
        }
        return packInfo.getData(namespace);
    }

    public long getPackInfoReloadVersion() {
        return packInfo == null ? 0 : packInfo.getReloadVersion();
    }

    @Nullable
    public PackInfo getPackInfo(@Nullable ResourceLocation namespace) {
        if (namespace == null || packInfo == null) {
            return null;
        }
        return packInfo.getData(namespace.getResourceDomain());
    }

    /**
     * 对应上游 {@code Minecraft#reloadResourcePacks().get()} + 集成服存在时的 {@code CommonAssetsManager#reloadAllPack()}。
     *
     * <p>
     * 1.7.10 与 1.20 的平台差异在本方法内补齐：
     * <ol>
     * <li>1.20 的 {@code reloadResourcePacks()} 先 {@code PackRepository#reload()} 再重载，前者会回调
     * {@code AddPackFindersEvent} → {@code GunPackLoader#loadPacks} 重新发现枪包；1.7.10 没有资源包自动发现，
     * 必须显式 {@link GunPackLoader#reloadPackList()}，否则新增/删除的客户端枪包不会进入本轮重载。
     * 枪包 assets 的索引 {@code GunPackResourcePack} 也要跟着作废、语言要重新注入（新 namespace 的
     * {@code .lang} 不在本轮 {@code LanguageManager} 重载的 domain 集合里）。</li>
     * <li>1.20 的 {@code reloadResourcePacks()} 把真正的重载提交给 client 线程的 gameExecutor，调用线程只等结果；
     * 1.7.10 的 {@code Minecraft#refreshResources()} 必须在 client 线程执行（{@code TextureManager} 重载会做
     * GL 上传、{@code renderGlobal} 也会重建）。单人游戏里 {@code /tacz reload} 由集成服 Server thread 执行
     * ⇒ 投递到 client 线程并等待（见 {@link #runOnClientThreadAndWait}）。</li>
     * </ol>
     * 服务端数据重载后，上游靠 {@code OnDatapackSyncEvent} 把同步包回传给玩家、由客户端 {@code syncGunPack}
     * 重建索引；1.7.10 的 {@code CommonAssetsManager#reloadAllPack} 目前不重发同步包（跨模块），
     * 因此单人游戏里由本方法在 client 线程显式补一次 {@link ClientIndexManager#reload()}。
     */
    @SideOnly(Side.CLIENT)
    public static void reloadAllPack() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        // ① 客户端资源重载：枪包重扫 → 内置资源包重挂/索引作废 → 语言注入 → 原版重载（数据管理器 + 客户端索引）
        runOnClientThreadAndWait(minecraft, () -> {
            GunPackLoader.INSTANCE.reloadPackList();
            GunPackResourcePack.invalidateCache();
            GunPackLoader.injectAllGunPackLanguages();
            minecraft.refreshResources();
        });
        // ② 集成服存在时一并重载服务端数据（对应上游 ServerLifecycleHooks.getCurrentServer() != null 分支）
        if (MinecraftServer.getServer() != null) {
            CommonAssetsManager.reloadAllPack();
            // 上游随后由 OnDatapackSyncEvent → syncGunPack 用新数据重建索引；1.7.10 暂无该回传，这里补一次。
            runOnClientThreadAndWait(minecraft, ClientIndexManager::reload);
        }
    }

    /**
     * 在客户端线程执行并等待结果；已在客户端线程时直接执行。
     *
     * <p>
     * 1.20 的 {@code Minecraft#reloadResourcePacks()} 内部把重载提交给 {@code gameExecutor}（= client 线程），
     * 调用线程 {@code .get()} 等待 —— 这正是「集成服命令线程调用客户端重载」能工作的原因。
     * 1.7.10 的 {@code Minecraft#refreshResources()} 是同步且非线程安全的，只能这样等价地换到 client 线程：
     * {@code func_152344_a(Runnable)} = {@code addScheduledTask}（{@code Minecraft.java:2978-3015}：
     * 已在客户端线程时立即执行并返回 immediateFuture，否则入队、由客户端线程每 tick 开头执行队列，
     * 见 {@code Minecraft.java:1658-1669}），{@code .get()} 即上游的阻塞等待。
     */
    private static void runOnClientThreadAndWait(Minecraft minecraft, Runnable action) {
        try {
            minecraft.func_152344_a(action)
                .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 对应 1.20 {@code ResourceLocation.Serializer}（{@code ResourceLocation#CODEC} 背后的 gson 适配器）。
     *
     * <p>
     * 1.7.10 的 {@link ResourceLocation} 是一个纯 {@code (namespace, path)} 二元组，没有自带 gson 适配器，
     * 需要自己提供：序列化成 {@code "namespace:path"} 字符串；反序列化同时接受字符串与
     * {@code {"namespace":…,"path":…}} 对象两种写法。
     */
    public static final class ResourceLocationAdapter
        implements JsonSerializer<ResourceLocation>, JsonDeserializer<ResourceLocation> {

        @Override
        public JsonElement serialize(ResourceLocation src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public ResourceLocation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                String namespace = obj.has("namespace") ? obj.get("namespace")
                    .getAsString() : GunMod.MOD_ID;
                String path = obj.has("path") ? obj.get("path")
                    .getAsString() : "";
                return new ResourceLocation(namespace, path);
            }
            String text = json.getAsString();
            if (text.indexOf(':') < 0) {
                return new ResourceLocation(GunMod.MOD_ID, text);
            }
            return new ResourceLocation(text);
        }
    }
}
