package com.tacz.guns.inventory;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

/**
 * 弹药库界面的「一条库存」快照（不是可写的槽位）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/AmmoStorageEntry.java:6}
 * 
 * <pre>
 * {@code public record AmmoStorageEntry(int id, ItemStack sample, long count, long remainingCapacity)}
 * </pre>
 *
 * <p>
 * 1.7.10 差异（只有一处）：上游紧凑构造器用 {@code sample.copyWithCount(1)}，
 * 1.7.10 的 {@link ItemStack} 没有 {@code copyWithCount(int)}，且<b>允许 null 表示空栈</b>
 * （上游 {@code ItemStack.EMPTY}）。因此这里：
 * <ul>
 * <li>{@code sample == null} 时保持 null（调用方按「空」处理）；</li>
 * <li>非 null 时 {@code copy()} + {@code stackSize = 1}，语义与上游一致：只作为「样品」，不带数量。</li>
 * </ul>
 * 其余字段（{@code id} = 界面内自增标识，{@code count} = 存量，{@code remainingCapacity} = 还能装多少）
 * 的名字、类型与含义完全保持上游（getter 由 record 生成，名字与上游一致）。
 */
public record AmmoStorageEntry(int id, @Nullable ItemStack sample, long count, long remainingCapacity) {

    public AmmoStorageEntry {
        sample = sampleWithCountOne(sample);
    }

    /** 1.7.10 版 {@code ItemStack#copyWithCount(1)}：null 安全。 */
    @Nullable
    private static ItemStack sampleWithCountOne(@Nullable ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }
}
