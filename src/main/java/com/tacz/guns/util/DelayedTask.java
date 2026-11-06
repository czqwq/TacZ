package com.tacz.guns.util;

import java.util.LinkedList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.google.common.collect.Lists;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 延迟执行的工具类
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游用 {@code net.minecraftforge.api.distmarker.@OnlyIn(Dist.CLIENT)}；
 * 1.7.10 对应物是 {@link SideOnly} + {@link Side#CLIENT}，语义相同（编译期不裁剪，运行期仅客户端）。</li>
 * <li>{@code com.google.common.collect.Lists} 在 1.7.10 自带的 Guava 里存在，保留。</li>
 * <li>逻辑逐行一致，<b>无语义差异</b>。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class DelayedTask {

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    public static LinkedList<BooleanSupplier> SUPPLIERS = Lists.newLinkedList();

    private DelayedTask() {}

    public static void add(final Runnable runnable, final int delayedTick) {
        final int[] tickArray = { delayedTick };
        SUPPLIERS.add(() -> {
            if (--tickArray[0] < 0) {
                runnable.run();
                return true;
            }
            return false;
        });
    }

    public static void add(final Consumer<Integer> consumer, final int delayedTick, final int times) {
        final int[] tickArray = { delayedTick };
        SUPPLIERS.add(() -> {
            if (--tickArray[0] < 0) {
                consumer.accept(times);
                return true;
            }
            return false;
        });
    }
}
