package com.tacz.guns.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.util.ResourceLocationHelper;
import com.tacz.guns.loot.LootTableInjectorModifier;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.LootInjectionManager;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;
import com.tacz.guns.util.EntityIdHelper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 枪包战利品注入的 1.7.10 接线（r3 修复：此前 {@link LootTableInjectorModifier#inject} 全仓 0 调用者）。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1}）：{@code loot/LootTableInjectorModifier} 是 Forge 的
 * <b>global loot modifier</b>（{@code IGlobalLootModifier} + {@code LootModifierSerializer}，注册于
 * {@code init/ModLootModifiers}，由 {@code data/forge/loot_modifiers/global_loot_modifiers.json} 挂到
 * 所有战利品表上）；原版每次结算 {@code LootTable}（箱子/实体/方块/钓鱼…）都会回调 {@code doApply}，
 * 用 {@code LootContext#getQueriedLootTableId()} 找到注入项，把枪包声明的物品<b>追加</b>到产出里。
 *
 * <p>
 * <b>1.7.10 没有这套体系</b>（无 {@code LootTable}/{@code LootContext}/{@code LootModifier}），
 * 因此本类把「表 id」映射到 1.7.10 实际存在的钩子：
 * <ul>
 * <li><b>箱子类</b>（{@code minecraft:chests/*}）→ {@link ChestGenHooks} 分类注册表。1.7.10 的箱子内容
 * 就是「从 {@code ChestGenHooks} 的候选池里按权重抽 N 个」（{@code WorldServer#createBonusChest}、
 * {@code WorldGenDungeons}、各结构 piece 都调 {@code ChestGenHooks.getItems/getCount}），
 * 因此把注入物品加进对应分类的候选池 + 把抽取数量 {@code countMin/countMax} 增加注入个数，
 * 就能实现「期望数量与上游追加语义一致」的注入。权重按
 * {@code 现有总权重 / max(1, 原抽取数 - 注入数)} 计算，使期望抽取次数 ≈ 1 次/注入项（见
 * {@link #refreshChestInjections()} 注释）。这是 1.7.10 平台能力内最接近上游的降级，不是逐字等价。</li>
 * <li><b>实体类</b>（{@code minecraft:entities/*}）→ {@link LivingDropsEvent}（服务端）：
 * 用 {@code EntityList#getEntityString} 反推 1.20 表 id，把注入物品包装成 {@link EntityItem}
 * 追加到 {@code event.drops}（真·追加语义，与上游一致）。</li>
 * <li><b>方块类</b>（{@code minecraft:blocks/*}）→ {@link BlockEvent.HarvestDropsEvent}（服务端）：
 * 用方块注册名反推表 id，把注入物品追加到 {@code event.drops}。</li>
 * <li><b>新世界奖励箱</b>：{@link WorldEvent.CreateSpawnPosition} 在
 * {@code WorldServer#createSpawnPosition}（{@code WorldServer.java:772}）触发，**早于**
 * {@code createBonusChest}（{@code WorldServer.java:809}）；本类在这里刷新箱子注入池，
 * 保证默认枪包的「奖励箱送 Taurus 943」在新世界生成前生效。</li>
 * </ul>
 *
 * <p>
 * <b>数据来源的时序问题（重要）</b>：{@code CommonAssetsManager}（含 {@code LootInjectionManager}）
 * 只在 {@code FMLServerStartingEvent}（{@code CommonProxy#serverStarting}）才装载，而新世界的
 * {@code CreateSpawnPosition} 发生在其<b>之前</b>（{@code IntegratedServer#startServer} 先
 * {@code loadAllWorlds} 再 {@code handleServerStarting}，{@code IntegratedServer.java:96-98}）。
 * 因此这里在 {@code CommonAssetsManager.getInstance() == null} 时用一份独立的
 * {@link LootInjectionManager} 兜底（只扫枪包里的 {@code tacz_loot_injectors}，不依赖完整资源管理器），
 * 等正式实例装载后再以正式实例为准（见 {@link #getInjections}）。
 *
 * <p>
 * <b>刷新时机</b>：{@code CommonAssetsManager.reload()} 每次都会新建实例（{@code /tacz reload}、
 * 服务端启动各一次），本类在 {@link TickEvent.ServerTickEvent}（END）里比较实例身份，
 * 变化时重建注入池 → 重载枪包后下一 tick 生效；退出世界时 {@code CommonAssetsManager} 清空实例，
 * 同样会被检测到并从兜底数据重建。全程<b>无日志噪声</b>（无注入时 debug 级、异常才 warn）。
 *
 * <p>
 * <b>已知不支持</b>（1.7.10 无对应钩子，属平台差异）：钓鱼战利品（{@code gameplay/fishing}）、
 * 掠夺/时运等 {@code conditions}、{@code empty}/{@code loot_table}/{@code tag} 类型 entry（见
 * {@link LootTableInjectorModifier} 类注释的子集说明）。
 */
public final class LootInjectionEvent {

    /** 反查：{@link ChestGenHooks} 分类 → 该分类对应的 1.20 表 id 列表（一个分类可能对应多个历史 id）。 */
    private static final Map<String, List<ResourceLocation>> CATEGORY_TO_CHEST_TABLES = new LinkedHashMap<>();

    /** 已注入到 {@link ChestGenHooks} 的条目（用于重载时撤回，避免重复累积）。 */
    private static final Map<String, List<ItemStack>> APPLIED_CHEST_STACKS = new HashMap<>();
    /** 已加到 {@link ChestGenHooks} 分类上的抽取数量增量（用于重载时恢复）。 */
    private static final Map<String, Integer> APPLIED_CHEST_COUNT_DELTA = new HashMap<>();
    /** 上一次刷新所用的 {@code CommonAssetsManager} 实例（身份比较，检测 reload）。 */
    private static CommonAssetsManager lastAppliedManager;
    /** 正式资源管理器尚未装载时的兜底数据源（只含战利品注入）。 */
    private static LootInjectionManager fallbackManager;
    /** 兜底数据源加载失败后不再反复重试（避免每 tick 刷日志）。 */
    private static boolean fallbackFailed;
    /** 已 debug 过「拿不到 1.20 实体 id」的 1.7.10 注册名（每次掉落都会走到，故按名字去重）。 */
    private static final Set<String> UNRESOLVED_ENTITY_NAMES_LOGGED = new HashSet<>();

    static {
        mapChest("chests/spawn_bonus_chest", ChestGenHooks.BONUS_CHEST);
        mapChest("chests/simple_dungeon", ChestGenHooks.DUNGEON_CHEST);
        mapChest("chests/abandoned_mineshaft", ChestGenHooks.MINESHAFT_CORRIDOR);
        mapChest("chests/desert_pyramid", ChestGenHooks.PYRAMID_DESERT_CHEST);
        mapChest("chests/jungle_temple", ChestGenHooks.PYRAMID_JUNGLE_CHEST);
        mapChest("chests/jungle_temple_dispenser", ChestGenHooks.PYRAMID_JUNGLE_DISPENSER);
        mapChest("chests/stronghold_corridor", ChestGenHooks.STRONGHOLD_CORRIDOR);
        mapChest("chests/stronghold_library", ChestGenHooks.STRONGHOLD_LIBRARY);
        mapChest("chests/stronghold_crossing", ChestGenHooks.STRONGHOLD_CROSSING);
        // 1.20 的村民铁匠箱；1.16 之前的旧 id 是 village_blacksmith，两个都接受
        mapChest("chests/village/village_weaponsmith", ChestGenHooks.VILLAGE_BLACKSMITH);
        mapChest("chests/village_blacksmith", ChestGenHooks.VILLAGE_BLACKSMITH);
    }

    private LootInjectionEvent() {}

    private static void mapChest(String lootTablePath, String category) {
        ResourceLocation id = new ResourceLocation("minecraft", lootTablePath);
        CATEGORY_TO_CHEST_TABLES.computeIfAbsent(category, key -> new ArrayList<>())
            .add(id);
    }

    /** 由 {@link EventHandlers#registerAll()} 调用（对应上游 {@code @Mod.EventBusSubscriber}）。 */
    public static void register() {
        LootInjectionEvent handler = new LootInjectionEvent();
        // 箱子/实体/方块钩子都在 Forge 总线上；ServerTickEvent 在 FML 私有总线上
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    // ———————————————————————————— 箱子：ChestGenHooks 注入池 ————————————————————————————

    /**
     * 新世界创建出生点时刷新箱子注入池（在 {@code createBonusChest} 之前）。
     *
     * <p>
     * 只在服务端/无客户端世界执行；退出世界后再进已有世界不会触发本事件（{@code World#initialize}
     * 只在 {@code worldInfo} 未初始化时调用），已有世界由 server tick 监听到 reload 后刷新。
     */
    @SubscribeEvent
    public void onCreateSpawnPosition(WorldEvent.CreateSpawnPosition event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        refreshChestInjections();
    }

    /** 检 {@code CommonAssetsManager} 实例变化（服务端启动装载、{@code /tacz reload}、退出世界清空）。 */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (CommonAssetsManager.getInstance() != lastAppliedManager) {
            refreshChestInjections();
        }
    }

    /**
     * 重建 {@link ChestGenHooks} 注入池（幂等：先撤回上一轮再按当前数据重加）。
     *
     * <p>
     * <b>权重推导</b>：1.7.10 箱子是「从候选池加权抽 N 个」，无法像上游那样「在原产出后追加」。
     * 设某分类原有总权重 V、原抽取数 C、本次要注入 K 个物品栈；给每个注入条目权重
     * {@code w = V / C} 并把抽取数增加到 {@code C + K}，则注入条目期望被抽中次数
     * = {@code (C + K) * K*w / (V + K*w)} = {@code K}（代入 {@code w = V/C}），
     * 即「期望上等于上游的追加 K 个」。这是 1.7.10 平台能力内的近似，不是逐字等价
     * （已在类注释与报告登记）。
     */
    public static synchronized void refreshChestInjections() {
        // 1) 撤回上一轮注入（条目 + 抽取数量增量）
        for (Map.Entry<String, List<ItemStack>> entry : APPLIED_CHEST_STACKS.entrySet()) {
            ChestGenHooks info = ChestGenHooks.getInfo(entry.getKey());
            for (ItemStack stack : entry.getValue()) {
                info.removeItem(stack);
            }
        }
        for (Map.Entry<String, Integer> entry : APPLIED_CHEST_COUNT_DELTA.entrySet()) {
            ChestGenHooks info = ChestGenHooks.getInfo(entry.getKey());
            info.setMin(Math.max(0, info.getMin() - entry.getValue()));
            info.setMax(Math.max(0, info.getMax() - entry.getValue()));
        }
        APPLIED_CHEST_STACKS.clear();
        APPLIED_CHEST_COUNT_DELTA.clear();

        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        lastAppliedManager = manager;
        if (manager != null) {
            // 正式资源已装载：丢弃可能过期的兜底数据源
            fallbackManager = null;
            fallbackFailed = false;
        }

        // 2) 按分类收集注入物品
        Map<String, List<ItemStack>> perCategory = new LinkedHashMap<>();
        for (Map.Entry<String, List<ResourceLocation>> entry : CATEGORY_TO_CHEST_TABLES.entrySet()) {
            List<ItemStack> stacks = new ArrayList<>();
            for (ResourceLocation tableId : entry.getValue()) {
                injectInto(tableId, stacks);
            }
            if (!stacks.isEmpty()) {
                perCategory.put(entry.getKey(), stacks);
            }
        }
        if (perCategory.isEmpty()) {
            return;
        }

        // 3) 计算权重并写入候选池
        Random probe = new Random(0L);
        for (Map.Entry<String, List<ItemStack>> entry : perCategory.entrySet()) {
            String category = entry.getKey();
            List<ItemStack> stacks = entry.getValue();
            ChestGenHooks info = ChestGenHooks.getInfo(category);
            int existingWeight = 0;
            for (WeightedRandomChestContent content : info.getItems(probe)) {
                existingWeight += content.itemWeight;
            }
            int baseCount = Math.max(1, (info.getMin() + info.getMax()) / 2);
            int extra = stacks.size();
            // w = V / C：使「抽取 C+K 次」时注入条目的期望命中次数 = K（推导见方法注释）
            int weight = Math.max(1, existingWeight / baseCount);
            for (ItemStack stack : stacks) {
                info.addItem(new WeightedRandomChestContent(stack, stack.stackSize, stack.stackSize, weight));
            }
            info.setMin(info.getMin() + extra);
            info.setMax(info.getMax() + extra);
            APPLIED_CHEST_STACKS.put(category, stacks);
            APPLIED_CHEST_COUNT_DELTA.put(category, extra);
        }
        if (GunMod.LOGGER.isDebugEnabled()) {
            GunMod.LOGGER.debug("[TACZ] loot injection applied to {} chest categories", perCategory.size());
        }
    }

    // ———————————————————————————— 实体 / 方块：掉落事件追加 ————————————————————————————

    /** {@code minecraft:entities/<name>} → 掉落时追加（真·追加，与上游 loot modifier 语义一致）。 */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if (entity == null) {
            return;
        }
        World world = entity.worldObj;
        if (world == null || world.isRemote) {
            return;
        }
        ResourceLocation tableId = entityLootTableId(entity);
        if (tableId == null) {
            return;
        }
        List<ItemStack> injected = new ArrayList<>();
        if (!injectInto(tableId, injected)) {
            return;
        }
        for (ItemStack stack : injected) {
            event.drops.add(new EntityItem(world, entity.posX, entity.posY + 0.5D, entity.posZ, stack));
        }
    }

    /** {@code minecraft:blocks/<name>} → 挖掘掉落时追加。 */
    @SubscribeEvent
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        World world = event.world;
        if (world == null || world.isRemote || event.block == null) {
            return;
        }
        // 1.7.10 的 Block.blockRegistry 是「名字 → Block」的注册表，getNameForObject 返回字符串 id
        String blockName = Block.blockRegistry.getNameForObject(event.block);
        if (blockName == null || blockName.isEmpty()) {
            return;
        }
        ResourceLocation blockId = ResourceLocationHelper.tryParse(blockName);
        if (blockId == null) {
            return;
        }
        ResourceLocation tableId = new ResourceLocation(
            blockId.getResourceDomain(),
            "blocks/" + blockId.getResourcePath());
        List<ItemStack> injected = new ArrayList<>();
        if (!injectInto(tableId, injected)) {
            return;
        }
        // 与上游「在结算结果后追加」一致：直接进 drops；不修改 dropChance（避免改变其它掉落的概率）
        event.drops.addAll(injected);
    }

    // ———————————————————————————— 数据访问（正式实例 / 兜底） ————————————————————————————

    /**
     * 把某 1.20 战利品表 id 的注入项追加到 {@code out}。
     *
     * @return 是否追加了至少一个物品
     */
    private static boolean injectInto(ResourceLocation lootTableId, List<ItemStack> out) {
        List<LootTableInjection> injections = getInjections(lootTableId);
        if (injections.isEmpty()) {
            return false;
        }
        return LootTableInjectorModifier.inject(injections, out) > 0;
    }

    private static List<LootTableInjection> getInjections(ResourceLocation lootTableId) {
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (manager != null) {
            return manager.getLootTableInjections(lootTableId);
        }
        LootInjectionManager fallback = getFallbackManager();
        return fallback == null ? Collections.emptyList() : fallback.getInjections(lootTableId);
    }

    /**
     * 正式资源管理器未装载时的兜底数据源（只扫 {@code tacz_loot_injectors}，与
     * {@code CommonAssetsManager} 中的同名管理器同一实现、同一份枪包来源）。
     *
     * <p>
     * 时序原因：新世界 {@code CreateSpawnPosition} 早于 {@code FMLServerStartingEvent}
     * （{@code IntegratedServer.java:96-98}），而 {@code CommonAssetsManager} 在后者才装载。
     * 兜底实例在正式实例可用后立即丢弃（见 {@link #refreshChestInjections()}），不会出现两份数据并存。
     */
    private static LootInjectionManager getFallbackManager() {
        if (fallbackManager != null) {
            return fallbackManager;
        }
        if (fallbackFailed) {
            return null;
        }
        try {
            LootInjectionManager manager = new LootInjectionManager();
            manager.reload();
            fallbackManager = manager;
            return manager;
        } catch (Throwable throwable) {
            fallbackFailed = true;
            GunMod.LOGGER.warn("[TACZ] failed to load loot injections before CommonAssetsManager is ready", throwable);
            return null;
        }
    }

    // ———————————————————————————— 1.20 表 id 推导 ————————————————————————————

    /**
     * 实体 → 1.20 战利品表 id（{@code minecraft:entities/zombie}）。
     *
     * <p>
     * 1.7.10 {@link EntityList#getEntityString(Entity)} 的三种形态**都不是** 1.20 的实体 id，
     * 直接 {@code new ResourceLocation(name.toLowerCase())} 会让注入永远匹配不到：
     * <ul>
     * <li><b>FML 模组实体</b>是 {@code "<modid>.<entityName>"}（点号，
     * {@code cpw/mods/fml/common/registry/EntityRegistry.java:164}
     * {@code String.format("%s.%s", mc.getModId(), entityName)}）。本模组的子弹因此是
     * {@code "tacz.bullet"}，按老写法会得到 {@code minecraft:entities/tacz.bullet} ——
     * 模组实体（含 TACZ 自己的实体、其它模组实体）的注入全部失效；</li>
     * <li><b>原版实体</b>是 1.7 时代的驼峰名（{@code "Zombie"} / {@code "PigZombie"} /
     * {@code "EntityHorse"}），1.20 是 snake_case，且部分**改了名字**
     * （{@code zombified_piglin} / {@code horse} / {@code magma_cube} …）。</li>
     * </ul>
     * 两种形态（以及凋灵骷髅与马的变体）已由 {@link EntityIdHelper#getModernId(Entity)} 统一处理，
     * 它是本工程「1.7.10 注册名 → 1.20 实体 id」的<b>单一来源</b>
     * （{@code util/EntityIdHelper.java:43-80} 的差异表；配置匹配侧 {@code config/util/InteractKeyConfigRead}
     * 同样用它）。本类不再复制第二份映射表，避免两处漂移。
     */
    private static ResourceLocation entityLootTableId(Entity entity) {
        ResourceLocation entityId = EntityIdHelper.getModernId(entity);
        if (entityId == null) {
            String name = EntityList.getEntityString(entity);
            if (name != null && !name.isEmpty() && UNRESOLVED_ENTITY_NAMES_LOGGED.add(name)) {
                GunMod.LOGGER.debug("[TACZ] cannot resolve a 1.20 entity id, skip loot injection for: {}", name);
            }
            return null;
        }
        return new ResourceLocation(entityId.getResourceDomain(), "entities/" + entityId.getResourcePath());
    }
}
