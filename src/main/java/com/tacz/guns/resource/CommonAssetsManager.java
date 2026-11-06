package com.tacz.guns.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.vmlib.LuaGunLogicConstant;
import com.tacz.guns.api.vmlib.LuaLibrary;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonAttachmentVariantIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;
import com.tacz.guns.resource.manager.AttachmentDataManager;
import com.tacz.guns.resource.manager.AttachmentsTagManager;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.manager.GunDataManager;
import com.tacz.guns.resource.manager.GunIndexManager;
import com.tacz.guns.resource.manager.GunSmithTableRecipeManager;
import com.tacz.guns.resource.manager.INetworkCacheReloadListener;
import com.tacz.guns.resource.manager.LootInjectionManager;
import com.tacz.guns.resource.manager.RecipeFilterManager;
import com.tacz.guns.resource.manager.ScriptManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.PackDirectory;
import com.tacz.guns.resource.pojo.AttachmentVariantIndexPOJO;
import com.tacz.guns.resource.pojo.SlotAdapterIndexPOJO;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.attachment.SlotAdapterData;
import com.tacz.guns.resource.pojo.data.attachment.VariantData;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.gun.BuiltInAttachment;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.Ignite;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;
import com.tacz.guns.resource.serialize.BuiltInAttachmentSerializer;
import com.tacz.guns.resource.serialize.CommonAmmoIndexSerializer;
import com.tacz.guns.resource.serialize.CommonAttachmentIndexSerializer;
import com.tacz.guns.resource.serialize.CommonBlockIndexSerializer;
import com.tacz.guns.resource.serialize.CommonGunIndexSerializer;
import com.tacz.guns.resource.serialize.DistanceDamagePairSerializer;
import com.tacz.guns.resource.serialize.EnumMapTypeAdapterFactory;
import com.tacz.guns.resource.serialize.GunSmithTableIngredientSerializer;
import com.tacz.guns.resource.serialize.GunSmithTableResultSerializer;
import com.tacz.guns.resource.serialize.IgniteSerializer;
import com.tacz.guns.resource.serialize.ModifierTypeAdapterFactory;
import com.tacz.guns.resource.serialize.PairSerializer;
import com.tacz.guns.resource.serialize.ResourceLocationSerializer;
import com.tacz.guns.resource.serialize.Vec3Serializer;
import com.tacz.guns.util.AllowAttachmentTagMatcher;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 枪包资源总入口：持有全部数据/索引管理器，并给全仓提供统一查询面。
 *
 * <p>
 * 上游：tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/CommonAssetsManager.java。
 *
 * <p>
 * 1.7.10 移植形态（差异集中在这里）：① 上游是 {@code @Mod.EventBusSubscriber}，靠
 * {@code AddReloadListenerEvent} 把每个管理器挂进原版资源重载管线；1.7.10 没有 datapack /
 * {@code PreparableReloadListener} / {@code ProfilerFiller} ⇒ 改为 {@link #reload()} 一次性<b>按上游注册顺序
 * 串行</b>触发（{@code gunData → attachmentData → … → index/*}，索引依赖数据，顺序不能变）。
 * ② 上游 {@code TagsUpdatedEvent} 里补调 {@code GunSmithTableRecipe#init()}；1.7.10 无 datapack 与
 * {@code RecipeManager} ⇒ 配方由 {@link GunSmithTableRecipeManager} 从 {@code data/<ns>/recipes/**} 读入静态表，
 * 再由 {@link #initLazyRecipes()} 在管线末尾展开产物。③ 上游 {@code OnDatapackSyncEvent} → 1.7.10 用
 * {@code PlayerEvent.PlayerLoggedInEvent}。④ 上游 {@code ServerStoppedEvent} → {@code WorldEvent.Unload}
 * （主世界卸载）兜底清理。⑤ {@code ServerLifecycleHooks}/{@code PackRepository} 重载 → {@link #reloadAllPack()}
 * 直接重扫枪包并重载。
 *
 * <p>
 * 调用时机（由 {@code CommonProxy} 保证）：preInit 里先 {@code GunPackLoader.INSTANCE.reloadPackList()}，服务端启动
 * 时 {@link #reload()}，并把本类注册到事件总线（{@link #registerEventHandlers()}）。
 */
public class CommonAssetsManager implements ICommonResourceProvider {

    private static CommonAssetsManager INSTANCE;

    /**
     * 事件总线注册载体（见 {@link #registerEventHandlers()} 的说明）。
     * 1.7.10 的 {@code EventBus} 只认实例，传 {@code Class} 等于不注册任何处理器。
     */
    private static CommonAssetsManager eventHandler;

    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ResourceLocation.class, new ResourceLocationSerializer())
        .registerTypeAdapter(Pair.class, new PairSerializer())
        .registerTypeAdapter(GunSmithTableIngredient.class, new GunSmithTableIngredientSerializer())
        .registerTypeAdapter(GunSmithTableResult.class, new GunSmithTableResultSerializer())
        .registerTypeAdapter(BuiltInAttachment.class, new BuiltInAttachmentSerializer())
        .registerTypeAdapter(ExtraDamage.DistanceDamagePair.class, new DistanceDamagePairSerializer())
        .registerTypeAdapter(Vec3.class, new Vec3Serializer())
        .registerTypeAdapter(Ignite.class, new IgniteSerializer())
        .registerTypeAdapter(RecipeFilter.class, new RecipeFilter.Deserializer())
        .registerTypeAdapter(CommonGunIndex.class, new CommonGunIndexSerializer())
        .registerTypeAdapter(CommonAmmoIndex.class, new CommonAmmoIndexSerializer())
        .registerTypeAdapter(CommonAttachmentIndex.class, new CommonAttachmentIndexSerializer())
        .registerTypeAdapter(CommonBlockIndex.class, new CommonBlockIndexSerializer())
        .registerTypeAdapter(TabConfig.class, new TabConfig.Deserializer())
        // 1.7.10 差异：gson 2.2.4 无 @JsonAdapter 注解，改用显式注册 ModifierTypeAdapterFactory
        .registerTypeAdapterFactory(new ModifierTypeAdapterFactory())
        // 1.7.10 差异：gson 2.2.4 不会为 EnumMap 字段构造 EnumMap（构造 LinkedHashMap 后 set 字段失败），
        // 实测 14 个枪械 data/*_data.json 的 fireModeAdjust 装载失败；上游 gson 2.10 原生支持。
        .registerTypeAdapterFactory(new EnumMapTypeAdapterFactory())
        .create();

    /** 全部监听器，按上游注册顺序；重载严格按该顺序串行执行（index 依赖 data）。 */
    private final List<IGunPackReloadListener> reloadOrder = new ArrayList<>();
    /** 其中需要把原始 json 缓存下发给客户端的那些。 */
    private final List<INetworkCacheReloadListener> listeners = new ArrayList<>();
    private List<ServerMessageSyncGunPack> networkSyncMessages = List.of();
    private CommonDataManager<GunData> gunData;
    private CommonDataManager<AttachmentData> attachmentData;
    private CommonDataManager<VariantData> attachmentVariantData;
    private CommonDataManager<SlotAdapterData> slotAdapterData;
    private CommonDataManager<BlockData> blockData;
    private CommonDataManager<CommonAmmoIndex> ammoIndex;
    private CommonDataManager<CommonGunIndex> gunIndex;
    private CommonDataManager<CommonAttachmentIndex> attachmentIndex;
    private CommonDataManager<CommonAttachmentVariantIndex> attachmentVariantIndex;
    private CommonDataManager<CommonSlotAdapterIndex> slotAdapterIndex;
    private CommonDataManager<CommonBlockIndex> blockIndex;
    private RecipeFilterManager recipeFilterManager;
    private LootInjectionManager lootInjectionManager;
    /** 枪械工作台配方（{@code data/<ns>/recipes/**}）；同时也是 {@link DataType#RECIPES} 下发的生产者。 */
    private GunSmithTableRecipeManager gunSmithTableRecipeManager;

    private AttachmentsTagManager attachmentsTagManager;
    List<LuaLibrary> libList = List.of(new LuaGunLogicConstant());
    private final ScriptManager scriptManager = new ScriptManager(
        PackDirectory.of("scripts", ".lua"),
        libList,
        GunPackResources.DATA_ROOT);

    /**
     * 建好全部管理器并立刻串行重载。
     *
     * <p>
     * 1.7.10 差异：上游 {@code reloadAndRegister(Consumer<PreparableReloadListener>)} 只做注册、
     * 真正的装载由原版重载管线异步执行；这里注册与装载合并成一个同步方法。
     */
    public void reloadAndRegister() {
        // 这里会顺序重载，所以需要把index这种依赖data的放在后面
        gunData = register(new GunDataManager());
        attachmentData = register(new AttachmentDataManager());
        attachmentVariantData = register(
            new CommonDataManager<>(
                DataType.ATTACHMENT_VARIANT_DATA,
                VariantData.class,
                GSON,
                "data/attachment_variants",
                "AttachmentVariantDataLoader"));
        slotAdapterData = register(
            new CommonDataManager<>(
                DataType.SLOT_ADAPTER_DATA,
                SlotAdapterData.class,
                GSON,
                "data/slot_adapters",
                "SlotAdapterDataLoader"));
        attachmentsTagManager = register(new AttachmentsTagManager());
        recipeFilterManager = register(new RecipeFilterManager());
        lootInjectionManager = register(new LootInjectionManager());
        blockData = register(
            new CommonDataManager<>(DataType.BLOCK_DATA, BlockData.class, GSON, "data/blocks", "BlockDataLoader"));
        register(scriptManager);

        ammoIndex = register(
            new CommonDataManager<>(DataType.AMMO_INDEX, CommonAmmoIndex.class, GSON, "index/ammo", "AmmoIndexLoader"));
        gunIndex = register(new GunIndexManager());
        attachmentIndex = register(
            new CommonDataManager<>(
                DataType.ATTACHMENT_INDEX,
                CommonAttachmentIndex.class,
                GSON,
                "index/attachments",
                "AttachmentIndexLoader"));
        attachmentVariantIndex = register(
            new CommonDataManager<>(
                DataType.ATTACHMENT_VARIANT_INDEX,
                CommonAttachmentVariantIndex.class,
                GSON,
                "index/attachment_variants",
                "AttachmentVariantIndexLoader") {

                @Override
                protected CommonAttachmentVariantIndex parseJson(JsonElement element) {
                    return CommonAttachmentVariantIndex
                        .getInstance(getGson().fromJson(element, AttachmentVariantIndexPOJO.class));
                }
            });
        slotAdapterIndex = register(
            new CommonDataManager<>(
                DataType.SLOT_ADAPTER_INDEX,
                CommonSlotAdapterIndex.class,
                GSON,
                "index/slot_adapters",
                "SlotAdapterIndexLoader") {

                @Override
                protected CommonSlotAdapterIndex parseJson(JsonElement element) {
                    return CommonSlotAdapterIndex.getInstance(getGson().fromJson(element, SlotAdapterIndexPOJO.class));
                }
            });
        blockIndex = register(
            new CommonDataManager<>(
                DataType.BLOCK_INDEX,
                CommonBlockIndex.class,
                GSON,
                "index/blocks",
                "BlockIndexLoader"));
        // 工作台配方（data/<ns>/recipes/**）。放在最后：它的「产物展开」要等 gun/attachment index 就绪，
        // 而展开动作由管线末尾的 initLazyRecipes() 统一做；这里只要保证注册顺序稳定、且进入网络缓存。
        gunSmithTableRecipeManager = register(new GunSmithTableRecipeManager());

        // 上游把这段放在重载管线末尾（barrier.thenRunAsync），保证「全部管理器都装完」后再执行
        for (IGunPackReloadListener listener : reloadOrder) {
            listener.reload();
        }
        AllowAttachmentTagMatcher.resetCache();
        networkSyncMessages = ServerMessageSyncGunPack.createMessages(getNetworkCache());
        initLazyRecipes();
    }

    private <T extends IGunPackReloadListener> T register(T listener) {
        reloadOrder.add(listener);
        if (listener instanceof INetworkCacheReloadListener networkListener) {
            listeners.add(networkListener);
        }
        return listener;
    }

    public Map<DataType, Map<ResourceLocation, String>> getNetworkCache() {
        ImmutableMap.Builder<DataType, Map<ResourceLocation, String>> builder = ImmutableMap.builder();
        for (INetworkCacheReloadListener listener : listeners) {
            builder.put(listener.getType(), listener.getNetworkCache());
        }
        return builder.build();
    }

    @Nullable
    @Override
    public GunData getGunData(ResourceLocation id) {
        return gunData.getData(id);
    }

    @Nullable
    @Override
    public AttachmentData getAttachmentData(ResourceLocation id) {
        return attachmentData.getData(id);
    }

    @Nullable
    @Override
    public VariantData getAttachmentVariantData(ResourceLocation id) {
        return attachmentVariantData.getData(id);
    }

    @Nullable
    @Override
    public SlotAdapterData getSlotAdapterData(ResourceLocation id) {
        return slotAdapterData.getData(id);
    }

    @Nullable
    @Override
    public BlockData getBlockData(ResourceLocation id) {
        return blockData.getData(id);
    }

    @Override
    @Nullable
    public RecipeFilter getRecipeFilter(ResourceLocation id) {
        return recipeFilterManager.getFilter(id);
    }

    public List<LootTableInjection> getLootTableInjections(ResourceLocation lootTable) {
        if (lootInjectionManager == null) {
            return List.of();
        }
        return lootInjectionManager.getInjections(lootTable);
    }

    @Nullable
    @Override
    public CommonGunIndex getGunIndex(ResourceLocation gunId) {
        return gunIndex.getData(gunId);
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonGunIndex>> getAllGuns() {
        return gunIndex.getAllData()
            .entrySet();
    }

    @Nullable
    @Override
    public CommonAmmoIndex getAmmoIndex(ResourceLocation ammoId) {
        return ammoIndex.getData(ammoId);
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonAmmoIndex>> getAllAmmos() {
        return ammoIndex.getAllData()
            .entrySet();
    }

    @Nullable
    @Override
    public CommonAttachmentIndex getAttachmentIndex(ResourceLocation attachmentId) {
        return attachmentIndex.getData(attachmentId);
    }

    @Nullable
    @Override
    public CommonAttachmentVariantIndex getAttachmentVariantIndex(ResourceLocation variantId) {
        return attachmentVariantIndex.getData(variantId);
    }

    @Nullable
    @Override
    public CommonSlotAdapterIndex getSlotAdapterIndex(ResourceLocation adapterId) {
        return slotAdapterIndex.getData(adapterId);
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonAttachmentIndex>> getAllAttachments() {
        return attachmentIndex.getAllData()
            .entrySet();
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonAttachmentVariantIndex>> getAllAttachmentVariants() {
        return attachmentVariantIndex.getAllData()
            .entrySet();
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>> getAllSlotAdapters() {
        return slotAdapterIndex.getAllData()
            .entrySet();
    }

    @Override
    public LuaTable getScript(ResourceLocation scriptId) {
        return scriptManager.getScript(scriptId);
    }

    @Nullable
    @Override
    public CommonBlockIndex getBlockIndex(ResourceLocation blockId) {
        return blockIndex.getData(blockId);
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonBlockIndex>> getAllBlocks() {
        return blockIndex.getAllData()
            .entrySet();
    }

    @Override
    public Set<String> getAttachmentTags(ResourceLocation registryName) {
        return attachmentsTagManager.getAttachmentTags(registryName);
    }

    @Override
    public Set<String> getAllowAttachmentTags(ResourceLocation registryName) {
        return attachmentsTagManager.getAllowAttachmentTags(registryName);
    }

    /** 当前是否已经装载了数据（1.7.10 新增，供 client 侧判断要不要等同步）。 */
    public boolean isLoaded() {
        return gunIndex != null;
    }

    /**
     * 获取实例<br/>
     * 实例仅当内置服务器/专用服务器启动时才会被创建<br/>
     * 当客户端正连接到多人游戏时，该方法将返回 null
     *
     * @return CommonAssetsManger实例
     */
    @Nullable
    public static CommonAssetsManager getInstance() {
        return INSTANCE;
    }

    public static void clearInstance() {
        INSTANCE = null;
    }

    /**
     * 根据当前环境选择合适的缓存<br/>
     * 当前环境为单人游戏或多人游戏的服务端时，返回CommonAssetsManger实例<br/>
     * 当前环境为多人游戏的客户端时，返回CommonNetworkCache实例
     *
     * @return ICommonResourceProvider实例
     */
    public static ICommonResourceProvider get() {
        return INSTANCE == null ? CommonNetworkCache.INSTANCE : INSTANCE;
    }

    /**
     * 重载全部枪包数据。
     *
     * <p>
     * 取代上游的 {@code AddReloadListenerEvent#onReload}：1.7.10 没有资源重载事件，
     * 由 {@code CommonProxy} 在服务端启动时调用本方法。
     * 注意 {@link #INSTANCE} 必须在<b>装载之前</b>赋值，否则索引类管理器
     * （{@code CommonGunIndex#checkData} 等）通过 {@link #get()} 拿不到刚加载的数据。
     */
    public static void reload() {
        GunPackLoader.INSTANCE.reloadPackList();
        CommonAssetsManager manager = new CommonAssetsManager();
        INSTANCE = manager;
        try {
            manager.reloadAndRegister();
        } catch (RuntimeException exception) {
            GunMod.LOGGER.error("Failed to reload gun pack data", exception);
            clearInstance();
            throw exception;
        }
    }

    /**
     * 延迟初始化的枪械工作台配方。
     *
     * <p>
     * 上游：{@code TagsUpdatedEvent} 里调 {@code GunSmithTableRecipe#init()}，把
     * {@code RawGunTableResult} 展开成真正的 {@code ItemStack}（这一步要查 gun/attachment index，
     * 所以必须等本轮重载的全部管理器都跑完才能做）。
     *
     * <p>
     * <b>1.7.10 差异 + 已修的第二层断裂</b>：早先这里遍历
     * {@code CraftingManager#getRecipeList()} 找 {@link GunSmithTableRecipe} 实例，
     * 但 {@link GunSmithTableRecipe} <b>根本不实现 {@code IRecipe}</b>、也<b>从不进
     * {@code CraftingManager}</b>（1.7.10 的 {@code CraftingManager#findMatchingRecipe} 会把表里每一项强转成
     * {@code IRecipe} 再调 {@code matches}，塞进去会 {@code ClassCastException}；见
     * {@code GunSmithTableRecipe} 类注释）。于是那个循环恒为 0 次，
     * {@code init()} 永不执行 → {@code getOutput()} 恒 {@code null} →
     * {@code GunSmithTableMenu#doCraft} 在「产物为空」分支直接 return（配方列表就算显示出来也做不出东西）。
     * 现在改为直接对 {@link GunSmithTableRecipe} 自己的静态登记表调用 {@link GunSmithTableRecipe#initAll()}。
     */
    public static void initLazyRecipes() {
        try {
            GunSmithTableRecipe.initAll();
        } catch (RuntimeException exception) {
            GunMod.LOGGER.error("Failed to init gun smith table recipes", exception);
        }
    }

    /**
     * 把本类挂到 1.7.10 的事件总线（1.20 由 {@code @Mod.EventBusSubscriber} 自动完成）。
     *
     * <p>
     * <b>必须传实例，不能传 {@code Class}</b>：1.7.10 的 {@code EventBus#register(Object)} 取的是
     * {@code object.getClass()} 上的 {@code @SubscribeEvent} 方法，传 {@code CommonAssetsManager.class}
     * 等于注册 {@code java.lang.Class}，一个处理器都找不到（同一个坑见 {@code docs/PORT_STATUS.md}
     * 「{@code MinecraftForge.EVENT_BUS.register(XxxClass.class)} 什么都不会注册」）。
     * 传 Class 的后果（玩家可见）：{@link #onPlayerLoggedIn} 永不触发 → 多人服客户端收不到
     * {@code ServerMessageSyncGunPack} → {@code CommonNetworkCache} 恒空 →
     * {@code TimelessAPI.getClientGunIndex} 全部落空 → 枪械物品名回退到
     * {@code AbstractGunItem#getItemStackDisplayName} 的 {@code super}（原始 lang key）、
     * 且 {@code ClientIndexManager} 没有任何 display/model。
     * 处理器本身是 {@code static}，注册载体只是「让 EventBus 扫到方法」的实例，
     * 与 {@link #INSTANCE}（真正装载好的数据）无关。
     */
    public static void registerEventHandlers() {
        if (eventHandler != null) {
            return;
        }
        eventHandler = new CommonAssetsManager();
        MinecraftForge.EVENT_BUS.register(eventHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(eventHandler);
    }

    /**
     * 玩家登录时把枪包数据同步给他。
     *
     * <p>
     * 上游对应 {@code OnDatapackSyncEvent}（{@code event.getPlayer() != null} 分支）。
     *
     * <p>
     * <b>必须是实例方法</b>：{@link #registerEventHandlers()} 注册的是**实例**
     * （{@code MinecraftForge.EVENT_BUS.register(eventHandler)}），而 FML 1.7.10 的
     * {@code ASMEventHandler} 生成的字节码用 {@code INVOKEVIRTUAL} 回调 ——
     * {@code public static} 的处理器会在派发时抛
     * {@code IncompatibleClassChangeError: Expecting non-static method}，
     * 被 {@code EventBus#post} 吞掉只打日志 ⇒ 「注册成功了但永远不执行」。
     * 实测后果：玩家进服时抛该异常并被踢出（{@code HandshakeCompletionHandler exception}）。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        CommonAssetsManager manager = getInstance();
        if (manager == null || !(event.player instanceof EntityPlayerMP player)) {
            return;
        }
        manager.networkSyncMessages.forEach(message -> NetworkHandler.sendToClientPlayer(message, player));
    }

    /**
     * 主世界卸载（= 退出存档 / 服务端停止）时清理实例。
     *
     * <p>
     * 上游对应 {@code ServerStoppedEvent}。
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world != null && event.world.provider != null && event.world.provider.dimensionId == 0) {
            clearInstance();
        }
    }

    /**
     * 重扫枪包并重载全部数据（{@code /tacz reload} 命令的专用服务端分支用）。
     *
     * <p>
     * 1.7.10 差异：上游走 {@code PackRepository#reload()} + {@code server.reloadResources(...)}，
     * 1.7.10 没有这套管线，直接重扫目录后再跑一遍 {@link #reload()}（重扫由 {@link #reload()} 内部完成）。
     *
     * <p>
     * 重载后必须补一次广播：上游 {@code server.reloadResources(...)} 完成后，原版会触发
     * {@code OnDatapackSyncEvent}（{@code event.getPlayer() == null} 分支 → 给<b>所有在线玩家</b>重发快照），
     * 本类 {@link #onPlayerLoggedIn} 只覆盖「玩家进服」。少了这一步，{@code /tacz reload} 之后
     * 已连接的客户端仍用旧枪包数据（新枪看不见、改过的数值不生效），直到重新进服。
     */
    public static void reloadAllPack() {
        reload();
        broadcastNetworkSync();
    }

    /** 把当前同步快照重发给所有在线玩家（对应上游 {@code OnDatapackSyncEvent} 的广播分支）。 */
    private static void broadcastNetworkSync() {
        CommonAssetsManager manager = getInstance();
        MinecraftServer server = MinecraftServer.getServer();
        if (manager == null || server == null) {
            return;
        }
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        if (players.isEmpty()) {
            return;
        }
        for (EntityPlayerMP player : players) {
            manager.networkSyncMessages.forEach(message -> NetworkHandler.sendToClientPlayer(message, player));
        }
        GunMod.LOGGER.info("Re-synced gun pack data to {} player(s) after reload", players.size());
    }

    /** 供命令层/同步层读取当前待下发的消息（1.7.10 新增；上游是私有字段）。 */
    public Collection<ServerMessageSyncGunPack> getNetworkSyncMessages() {
        return networkSyncMessages;
    }
}
