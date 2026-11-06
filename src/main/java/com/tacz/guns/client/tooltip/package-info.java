@ParametersAreNonnullByDefault
package com.tacz.guns.client.tooltip;

import javax.annotation.ParametersAreNonnullByDefault;

/*
 * 1.7.10 差异：上游 1.20.1 的 package-info 还带
 * {@code @MethodsReturnNonnullByDefault} / {@code @FieldsAreNonnullByDefault}（来自 Minecraft 自带的
 * {@code net.minecraft.MethodsReturnNonnullByDefault} / {@code net.minecraft.FieldsAreNonnullByDefault}），
 * 1.7.10 里没有这两个注解类（那是 1.13+ 才加进原版的），
 * 因此这里只保留 JSR305 的 {@link ParametersAreNonnullByDefault}；
 * 方法/字段的可空性一律用 {@code javax.annotation.Nullable} 逐个标注。
 */
