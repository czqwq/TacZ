package com.tacz.guns.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.tacz.guns.init.ModAttributes;
import com.tacz.guns.init.ModDamageTypes;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 子弹伤害的「防弹属性」修正。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/EntityDamageEvent.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}；
 * {@code @SubscribeEvent}/{@code EventPriority} 换到 {@code cpw.mods.fml.common.eventhandler} 包；</li>
 * <li>{@code LivingEntity} → {@link EntityLivingBase}；</li>
 * <li>1.7.10 的 {@link LivingHurtEvent} 字段为 {@code source} / {@code ammount}（上游即此拼写）/
 * {@code entityLiving}，<b>没有</b> {@code getAmount()/setAmount()}；</li>
 * <li>上游 {@code event.getSource().is(ModDamageTypes.BULLETS_TAG)}（1.20 伤害类型 tag）→ 1.7.10 没有
 * 伤害类型注册表与 tag，改由 {@code init/ModDamageTypes} 提供
 * {@code static boolean isBulletDamage(net.minecraft.util.DamageSource)}（依赖见交付清单）；</li>
 * <li>{@code AttributeInstance} → {@link IAttributeInstance}（{@code getAttributeValue()} 取代
 * {@code getValue()}）；{@code ModAttributes.BULLET_RESISTANCE.get()} → 1.7.10 为普通静态
 * {@code Attribute} 字段，直接引用（不带 {@code .get()}）。</li>
 * </ul>
 */
public class EntityDamageEvent {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new EntityDamageEvent());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!ModDamageTypes.isBulletDamage(event.source)) {
            return;
        }
        EntityLivingBase living = event.entityLiving;
        IAttributeInstance resistance = living.getEntityAttribute(ModAttributes.BULLET_RESISTANCE);
        if (resistance != null) {
            float modifiedDamage = event.ammount * (float) (1 - resistance.getAttributeValue());
            event.ammount = modifiedDamage;
        }
    }
}
