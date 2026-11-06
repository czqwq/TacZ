package com.tacz.guns.util;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;

/**
 * 弹药堆叠相关的查询与构造工具，供弹药仓储（{@code AmmoStorage*}）与网络包共用。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}（1.7.10 同名类）。</li>
 * <li>{@code net.minecraft.world.item.ItemStack} → {@link ItemStack}；{@code ItemStack.EMPTY} → {@code null}，
 * {@code stack.isEmpty()} → {@link ItemStackNbtHelper#isEmpty(ItemStack)}。</li>
 * <li><b>1.7.10 新增判空</b>：上游 ItemStack 永不为 null，1.20.1→1.7.10 后调用方可能传 null
 * （例如空的物品栏槽位、聚合列表里的空条目），因此 {@link #isValidAmmo} / {@link #getAmmoId} /
 * {@link #getSort} / {@link #getStackSize} 全部先判空；判空后的返回值与上游
 * 「不是弹药」时的返回值一致，不改变语义。</li>
 * <li>{@link #createAmmoStack(ResourceLocation, int)} 依旧返回 {@link ItemStack}；
 * 1.7.10 里它的 {@code getDisplayName()} 返回 {@link String} 而不是 1.20 的 {@code Component}，
 * 网络包 {@code ClientMessageAmmoStorageInteract} 已按 String 使用。</li>
 * </ul>
 */
public final class AmmoStorageHelper {

    private AmmoStorageHelper() {}

    public static boolean isValidAmmo(ItemStack stack) {
        if (ItemStackNbtHelper.isEmpty(stack) || !(stack.getItem() instanceof IAmmo)) {
            return false;
        }
        IAmmo ammo = (IAmmo) stack.getItem();
        ResourceLocation ammoId = ammo.getAmmoId(stack);
        return ammoId != null && TimelessAPI.getCommonAmmoIndex(ammoId)
            .isPresent();
    }

    @Nullable
    public static ResourceLocation getAmmoId(ItemStack stack) {
        if (ItemStackNbtHelper.isEmpty(stack)) {
            return null;
        }
        if (stack.getItem() instanceof IAmmo) {
            IAmmo ammo = (IAmmo) stack.getItem();
            return ammo.getAmmoId(stack);
        }
        return null;
    }

    public static int getSort(ItemStack stack) {
        ResourceLocation id = getAmmoId(stack);
        if (id == null) {
            return Integer.MAX_VALUE;
        }
        return TimelessAPI.getCommonAmmoIndex(id)
            .map(index -> index.getSort())
            .orElse(Integer.MAX_VALUE);
    }

    public static int getStackSize(ItemStack stack) {
        ResourceLocation id = getAmmoId(stack);
        if (id == null) {
            // 1.7.10 里 stack 可能为 null，此时 getMaxStackSize 会 NPE，退回 64（原版上限）。
            return ItemStackNbtHelper.isEmpty(stack) ? 64 : stack.getMaxStackSize();
        }
        return TimelessAPI.getCommonAmmoIndex(id)
            .map(index -> index.getStackSize())
            .orElse(ItemStackNbtHelper.isEmpty(stack) ? 64 : stack.getMaxStackSize());
    }

    public static ItemStack createAmmoStack(ResourceLocation ammoId, int count) {
        return AmmoItemBuilder.create()
            .setId(ammoId)
            .setCount(count)
            .build();
    }
}
