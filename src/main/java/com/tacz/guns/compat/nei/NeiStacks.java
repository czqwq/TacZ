package com.tacz.guns.compat.nei;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.crafting.GunSmithTableIngredient;

/**
 * NEI 配方页面用到的物品栈操作。
 *
 * <p>
 * 上游 JEI 通过 {@code GunModSubtype}（{@code ISubtypeRegistration#registerSubtypeInterpreter}）把
 * 「同一物品 + 不同 NBT」的枪械/弹药/配件区分为不同子类型；NEI 没有子类型注册表，本类因此显式做
 * <b>语义比较</b>：枪械按 gun id、弹药按 ammo id、配件按 attachment id 比较，其余物品退回
 * item + damage（+ NBT）。
 */
final class NeiStacks {

    private NeiStacks() {}

    /**
     * 判断「页面产物」与「玩家 R 键点到的物品」是否是同一个产物。
     *
     * <p>
     * 枪械/弹药/配件的 NBT 里带有大量运行期状态（当前弹药数、开火模式、已装配件…），不能用 NBT 全等比较；
     * 上游 JEI 的 subtype interpreter 也是只按 id 判定，故这里同样只比 id。
     */
    static boolean sameResult(@Nullable ItemStack pageResult, @Nullable ItemStack focused) {
        if (pageResult == null || pageResult.getItem() == null || focused == null || focused.getItem() == null) {
            return false;
        }
        if (pageResult.getItem() != focused.getItem()) {
            return false;
        }
        IGun pageGun = IGun.getIGunOrNull(pageResult);
        IGun focusGun = IGun.getIGunOrNull(focused);
        if (pageGun != null && focusGun != null) {
            return pageGun.getGunId(pageResult)
                .equals(focusGun.getGunId(focused));
        }
        IAmmo pageAmmo = IAmmo.getIAmmoOrNull(pageResult);
        IAmmo focusAmmo = IAmmo.getIAmmoOrNull(focused);
        if (pageAmmo != null && focusAmmo != null) {
            return pageAmmo.getAmmoId(pageResult)
                .equals(focusAmmo.getAmmoId(focused));
        }
        IAttachment pageAttachment = IAttachment.getIAttachmentOrNull(pageResult);
        IAttachment focusAttachment = IAttachment.getIAttachmentOrNull(focused);
        if (pageAttachment != null && focusAttachment != null) {
            return pageAttachment.getAttachmentId(pageResult)
                .equals(focusAttachment.getAttachmentId(focused));
        }
        if (pageResult.getItemDamage() != focused.getItemDamage()) {
            return false;
        }
        if (pageResult.getTagCompound() == null || focused.getTagCompound() == null) {
            return pageResult.getTagCompound() == focused.getTagCompound();
        }
        return ItemStack.areItemStackTagsEqual(pageResult, focused);
    }

    /**
     * 把配方材料展开成 NEI 可显示的 {@link ItemStack} 列表（每个候选一个格子物品）。
     *
     * <p>
     * {@link GunSmithTableIngredient#getMatchingStacks()} 返回的是「可接受的候选物品」；
     * 这里逐项 copy 并把数量改成配方需求数量，避免 NEI 渲染时改到配方数据本身。
     */
    static List<ItemStack> ingredientStacks(GunSmithTableIngredient ingredient) {
        List<ItemStack> stacks = new ArrayList<>();
        int count = Math.max(ingredient.getCount(), 1);
        for (ItemStack candidate : ingredient.getMatchingStacks()) {
            if (candidate == null || candidate.getItem() == null) {
                continue;
            }
            ItemStack copy = candidate.copy();
            copy.stackSize = count;
            stacks.add(copy);
        }
        return stacks;
    }

    /** 构造一把枪的物品栈（索引不存在时返回 null）。 */
    @Nullable
    static ItemStack buildGun(net.minecraft.util.ResourceLocation gunId) {
        return com.tacz.guns.api.item.builder.GunItemBuilder.create()
            .setId(gunId)
            .forceBuild();
    }

    @Nullable
    static ItemStack buildAmmo(net.minecraft.util.ResourceLocation ammoId) {
        return AmmoItemBuilder.create()
            .setId(ammoId)
            .build();
    }

    @Nullable
    static ItemStack buildAttachment(net.minecraft.util.ResourceLocation attachmentId) {
        return AttachmentItemBuilder.create()
            .setId(attachmentId)
            .build();
    }
}
