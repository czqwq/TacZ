package com.tacz.guns.init;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 搬运自上游 1.20.1 {@code init/ModAttributes.java}。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code DeferredRegister<Attribute>}/{@code RegistryObject} → 普通 public static 字段 +
 * {@link RangedAttribute}（1.7.10 属性没有全局注册表）；</li>
 * <li>上游 {@code EntityAttributeModificationEvent}（按 EntityType 批量挂属性）1.7.10 没有：
 * 改为监听 {@link EntityEvent.EntityConstructing}，在实体属性表构造时
 * {@link BaseAttributeMap#registerAttribute} 挂到**每个** {@link EntityLivingBase}
 * （等价上游「所有实体类型都加 BULLET_RESISTANCE」）；</li>
 * <li>{@code setSyncable(true)}（1.20 的属性同步开关）1.7.10 无对应：1.7.10 的属性同步由
 * {@code ServersideAttributeMap#getWatchedAttributes} + 原版包机制自动处理，无需额外设置。</li>
 * </ul>
 */
public class ModAttributes {

    /**
     * 子弹抗性，范围 0~1。字段形态是普通 static Attribute（不是 1.20 的 RegistryObject），
     * 其它包按上游语义直接 {@code living.getEntityAttribute(ModAttributes.BULLET_RESISTANCE)} 使用。
     *
     * <p>
     * {@code setShouldWatch(true)} 对应上游 {@code RangedAttribute(...).setSyncable(true)}
     * （{@code tmp/TACZ-1.20.1/.../init/ModAttributes.java:14}）：1.7.10 只有该标志为真的属性
     * 才会进 {@code ServersideAttributeMap#getWatchedAttributes} 并随实体属性包同步到客户端；
     * 不加则客户端只能看到默认值（其它模组/客户端逻辑读到的抗性恒 0）。
     */
    public static final IAttribute BULLET_RESISTANCE = new RangedAttribute(
        "attribute.name.tacz.bullet_resistance",
        0.0D,
        0.0D,
        1.0D).setShouldWatch(true);

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    /**
     * 1.7.10 移植要点：注册到事件总线的处理器必须是<b>可访问的具名类</b>。
     * 上游 1.20 用 {@code @Mod.EventBusSubscriber} 的静态方法；若这里改用匿名类
     * （{@code new Object() { @SubscribeEvent ... }}），编译产物是包私有的 {@code ModAttributes$1}，
     * 而 FML 生成的 {@code ASMEventHandler_*_onEntityConstructing_*} 在
     * {@code cpw.mods.fml.common.eventhandler} 包里调用它时会抛
     * {@code IllegalAccessError: tried to access class com.tacz.guns.init.ModAttributes$1}（实测崩溃）。
     */
    public static final class Handler {

        @SubscribeEvent
        public void onEntityConstructing(EntityEvent.EntityConstructing event) {
            if (!(event.entity instanceof EntityLivingBase living)) {
                return;
            }
            BaseAttributeMap attributeMap = living.getAttributeMap();
            if (attributeMap.getAttributeInstance(BULLET_RESISTANCE) == null) {
                attributeMap.registerAttribute(BULLET_RESISTANCE);
            }
        }
    }
}
