package com.tacz.guns.api.entity;

import net.minecraft.entity.EntityLivingBase;

/**
 * 用于修改实体被子弹击中后的击退效果的设计
 * 默认给所有 EntityLivingBase 添加了此接口
 */
public interface KnockBackModifier {

    /**
     * EntityLivingBase 通过 Mixin 的方式实现了这个接口
     */
    static KnockBackModifier fromLivingEntity(EntityLivingBase entity) {
        return (KnockBackModifier) entity;
    }

    /**
     * 重置击退效果，实体此时恢复正常原版击退逻辑
     */
    void resetKnockBackStrength();

    /**
     * 获取击退强度
     */
    double getKnockBackStrength();

    /**
     * 设置击退强度
     */
    void setKnockBackStrength(double strength);
}
