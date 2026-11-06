package com.tacz.guns.init;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;

import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.advancement.TargetHitTrigger;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.network.NetworkHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 通用侧注册 / 加载完成的标志位。
 *
 * <p>
 * 上游形态（tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/CommonRegistry.java）是
 * {@code @Mod.EventBusSubscriber(bus = MOD_BUS)} 的四个 {@code @SubscribeEvent}：{@code FMLCommonSetupEvent}
 * （enqueueWork 依次 NetworkHandler::init、ModSyncedEntityData::init、TargetHitTrigger::register、
 * GunRefitTrigger::register、两处坩埚交互）、{@code FMLLoadCompleteEvent}（置 LOAD_COMPLETE）、
 * {@code EntityAttributeModificationEvent}（给所有实体类型加 {@code ModAttributes.BULLET_RESISTANCE}）、
 * {@code AddPackFindersEvent}。
 *
 * <p>
 * 1.7.10 形态：{@code FMLCommonSetupEvent} → {@link #commonSetup()}（{@code CommonProxy#init} 调；
 * {@code enqueueWork} 的「丢回主线程」语义不需要，{@code FMLInitializationEvent} 本身就在主线程同步执行）；
 * {@code FMLLoadCompleteEvent} → {@link #markLoadComplete()}（{@code CommonProxy#postInit} 调）；
 * 坩埚交互 {@code CauldronInteraction.WATER} <b>没有对应物</b> —— 1.7.10 原版
 * {@code BlockCauldron#onBlockActivated} 把支持的物品写死（装水/染色皮革/洗旗帜），无法给挂板物品追加交互，
 * 见 {@link #commonSetup()} 的注释；{@code EntityAttributeModificationEvent} 不存在 ⇒ 改在
 * {@link EntityEvent.EntityConstructing} 里逐实体挂属性（{@link #onEntityConstructing}）；
 * {@code AddPackFindersEvent} 也没有，枪包扫描由 {@code resource/GunPackLoader} 在 preInit 自己完成。
 *
 * <p>
 * 注册方式：上游自动注册，1.7.10 必须显式 {@code MinecraftForge.EVENT_BUS.register(实例)}，
 * <b>不能传 {@code Class} 对象</b>（见 {@link #register()}）。属性挂载已由 {@code init/ModAttributes.Handler}
 * 实例承担，{@link #register()} 只保留调用点形状。
 *
 * <p>
 * 幂等：{@code ModAttributes} 与 {@link #onEntityConstructing} 两处挂载点都用
 * {@code getAttributeInstance(...) == null} 判断，<b>即使两处都注册也不会重复创建属性实例</b>
 * （{@code ModAttributes.java:52-64}）。{@code advancement/TargetHitTrigger}、{@code advancement/GunRefitTrigger}
 * 依赖的 1.20 criterion trigger 体系在 1.7.10 不存在，其 {@code register()} 已是空实现，这里照上游顺序调用。
 */
public final class CommonRegistry {

    private static boolean LOAD_COMPLETE = false;

    /**
     * 注册到 {@code MinecraftForge.EVENT_BUS} 的处理器实例（1.7.10 必须按实例注册，见 {@link #register()}）。
     */
    private static CommonRegistry eventHandler;

    private CommonRegistry() {}

    /**
     * 等价上游 {@code FMLCommonSetupEvent} 里的 {@code enqueueWork} 序列，顺序与上游一致。
     * <p>
     * 由 {@code CommonProxy#init(FMLInitializationEvent)} 调用。
     */
    public static void commonSetup() {
        NetworkHandler.init();
        ModSyncedEntityData.init();
        TargetHitTrigger.register();
        GunRefitTrigger.register();

        // 上游（1.20.1）：
        // CauldronInteraction.WATER.put(ModItems.GUN_PEGBOARD.get(), CauldronInteraction.DYED_ITEM);
        // CauldronInteraction.WATER.put(ModItems.GLOW_GUN_PEGBOARD.get(), CauldronInteraction.DYED_ITEM);
        // 1.7.10 没有 CauldronInteraction 注册表：BlockCauldron#onBlockActivated 把支持的物品写死了
        // （水桶/药水瓶/染色皮革/旗帜），无法给挂板物品追加"用水染色"交互。
        // 丢掉的行为：1.20.1 里手持颜料/水桶对炼药锅交互可以给挂板染色；1.7.10 只能走物品自身的染色逻辑。
        // TODO(port, 待评估): 若必须保留，只能 @SideOnly(Side.CLIENT)/ 服务端 mixin 进 BlockCauldron 或
        // 用 Forge 的 PlayerInteractEvent 自己模拟，属于新增机制，需另行决策。
    }

    /**
     * 等价上游 {@code FMLLoadCompleteEvent} 里的 {@code LOAD_COMPLETE = true}。
     * <p>
     * 由 {@code CommonProxy#postInit(FMLPostInitializationEvent)} 调用。
     */
    public static void markLoadComplete() {
        LOAD_COMPLETE = true;
    }

    /**
     * 上游 {@code CommonRegistry#isLoadComplete}，签名与语义保持一致（其他包用它判断加载是否结束）。
     */
    public static boolean isLoadComplete() {
        return LOAD_COMPLETE;
    }

    /**
     * 等价上游 {@code EntityAttributeModificationEvent} 处理器：给所有实体类型加
     * {@code ModAttributes.BULLET_RESISTANCE}。
     *
     * <p>
     * 1.7.10 的属性没有「全局注册表 + 按 EntityType 批量挂载」这一层：
     * {@code EntityLivingBase#getAttributeMap()} 是每个实体实例自己的 {@link BaseAttributeMap}，只能在实体构造时
     * （{@link EntityEvent.EntityConstructing}）逐个 {@code registerAttribute(...)}，见
     * {@link #onEntityConstructing(EntityEvent.EntityConstructing)}。
     * 本方法本身无事可做（{@code init/ModAttributes#register()} 已挂了等价处理器），保留只是为了让
     * {@code CommonProxy} 的调用顺序与上游事件顺序一一对应。
     */
    public static void registerAttributes() {
        // no-op：真正挂载在 onEntityConstructing（以及 ModAttributes.register() 里的等价处理器），
        // 两处都以 getAttributeInstance(...) == null 做幂等保护，见类注释。
    }

    /**
     * 1.7.10 的「给所有活体实体挂属性」入口（上游 {@code EntityAttributeModificationEvent} 的对应物）。
     *
     * <p>
     * 与 {@code ModAttributes#register()} 里的同名处理器幂等共存：先到的一方把属性注册进
     * {@link BaseAttributeMap}，后到的因 {@code getAttributeInstance(...) != null} 直接返回，不会覆盖已有的
     * {@code AttributeInstance}（否则会丢掉其他模组加的 modifier）。
     */
    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (!(event.entity instanceof EntityLivingBase)) {
            return;
        }
        BaseAttributeMap attributeMap = ((EntityLivingBase) event.entity).getAttributeMap();
        // 1.7.10 的属性接口是 IAttribute（RangedAttribute → BaseAttribute → IAttribute），
        // 这里显式取 IAttribute，避免依赖 ModAttributes 字段的声明类型。
        IAttribute attribute = ModAttributes.BULLET_RESISTANCE;
        if (attributeMap.getAttributeInstance(attribute) == null) {
            attributeMap.registerAttribute(attribute);
        }
    }

    /**
     * 上游 {@code @Mod.EventBusSubscriber} 的等价注册点（{@code CommonProxy#preInit} 会调它）。
     *
     * <p>
     * <b>永远不要</b>写 {@code EVENT_BUS.register(XxxClass.class)}：1.7.10 的 {@code EventBus#register(Object)}
     * 用 {@code target.getClass()} 反射扫描处理器（cpw/mods/fml/common/eventhandler/EventBus.java:45,59-60），
     * 传 {@code Class} 对象时扫的是 {@code java.lang.Class} 自己的方法 ⇒ 一个 {@code @SubscribeEvent} 都注册不到。
     * 且 {@code @SubscribeEvent} 方法<b>必须是实例方法</b>：FML 的 {@code ASMEventHandler} 用
     * {@code INVOKEVIRTUAL} 回调，静态处理器会抛 {@code IncompatibleClassChangeError}，并被
     * {@code EventBus#post} 吞成一条 error 日志。
     *
     * <p>
     * 与 {@code init/ModAttributes#register()} 的 {@code Handler} 幂等共存（两处都用
     * {@code getAttributeInstance(...) == null} 判重，{@code ModAttributes.java:40-42,52-64}），
     * 不会覆盖其它模组加的 modifier。
     */
    public static void register() {
        if (eventHandler != null) {
            return;
        }
        eventHandler = new CommonRegistry();
        MinecraftForge.EVENT_BUS.register(eventHandler);
    }
}
