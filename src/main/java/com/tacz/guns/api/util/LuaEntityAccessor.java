package com.tacz.guns.api.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;

/**
 * Lua 侧实体访问器（暴露给枪械逻辑脚本）。
 * <p>
 * 1.7.10 移植说明：
 * <ul>
 * <li>{@code Component}（1.20.1）→ {@link IChatComponent}；</li>
 * <li>{@code Component.literal} → {@link ChatComponentText}，{@code Component.translatable} →
 * {@link ChatComponentTranslation}；</li>
 * <li>{@code entity.level().damageSources().generic()} → {@link DamageSource#generic}；</li>
 * <li>{@code Player#displayClientMessage(msg, true)}（action bar）降级为聊天栏消息：1.7.10 的网络包
 * {@code S02PacketChat} 没有 1.8+ 的 position 字段，客户端也无 action bar 渲染入口，平台无等价能力。</li>
 * </ul>
 */
@SuppressWarnings("unused")
public record LuaEntityAccessor(EntityLivingBase entity) {

    public void sendSystemMessage(IChatComponent message) {
        if (entity instanceof EntityPlayer player) {
            player.addChatComponentMessage(message);
        }
    }

    public void sendActionBar(IChatComponent message) {
        // 平台差异：1.7.10 无 action bar（见类注释），降级为聊天消息。
        if (entity instanceof EntityPlayer player) {
            player.addChatComponentMessage(message);
        }
    }

    public float getHealth() {
        return entity.getHealth();
    }

    public boolean hurt(float amount) {
        return entity.attackEntityFrom(DamageSource.generic, amount);
    }

    public IChatComponent literal(String text) {
        return new ChatComponentText(text);
    }

    public IChatComponent translatable(String key) {
        return new ChatComponentTranslation(key);
    }

    public IChatComponent translatable(String key, IChatComponent... components) {
        return new ChatComponentTranslation(key, (Object[]) components);
    }
}
