@ParametersAreNonnullByDefault
package com.tacz.guns.particles;

import javax.annotation.ParametersAreNonnullByDefault;

/*
 * 1.7.10 差异：上游 1.20.1 的 package-info 还带
 * {@code @MethodsReturnNonnullByDefault} / {@code @FieldsAreNonnullByDefault}（来自 Minecraft 自带的
 * {@code net.minecraft.MethodsReturnNonnullByDefault}），1.7.10 里没有这两个注解类，
 * 因此这里只保留 JSR305 的 {@link ParametersAreNonnullByDefault}。
 */
