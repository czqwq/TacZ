package com.tacz.guns.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * 文档性质的注解。被注解的枪械属性在配件缓存中的值可以被逻辑脚本修改。
 * 
 * @see com.tacz.guns.resource.modifier.AttachmentPropertyManager#postChangeEvent(EntityLivingBase, ItemStack)
 * @author ChloePrime
 * @since 1.1.7
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface CacheModifiableByScript {}
