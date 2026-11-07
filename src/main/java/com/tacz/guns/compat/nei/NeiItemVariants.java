package com.tacz.guns.compat.nei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.GunSmithTableItem;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;

import codechicken.nei.api.API;

/**
 * 把枪包里的 NBT 变体（枪械 / 弹药 / 配件 / 工作台 / 弹药盒）注入 NEI 物品面板。
 *
 * <p>
 * 上游 1.20.1 用 JEI 的 {@code ISubtypeRegistration#registerSubtypeInterpreter}（{@code GunModSubtype}）
 * 让 JEI 把「同一物品 + 不同 NBT」识别为不同子类型——否则 JEI 面板里只有 {@code modern_kinetic_gun}
 * 一个无 NBT 的空物品，按 R 查不到任何配方。1.7.10 的 NEI 等价物是
 * {@code API#setItemListEntries(Item, Iterable&lt;ItemStack&gt;)}：直接替换该物品在面板中的变体列表。
 *
 * <p>
 * 只注入「有实际变体」的物品；枪包数据尚未同步（列表为空）时保持 NEI 默认行为，绝不用空列表覆盖
 * （那会让物品从面板里消失）。
 */
final class NeiItemVariants {

    private NeiItemVariants() {}

    static void register() {
        registerGuns();
        registerAmmo();
        registerAttachments();
        registerAmmoBoxes();
        registerTables();
    }

    private static void registerGuns() {
        Item gunItem = ModItems.MODERN_KINETIC_GUN;
        if (gunItem == null) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : TimelessAPI.getAllCommonGunIndex()) {
            ItemStack stack = NeiStacks.buildGun(entry.getKey());
            if (stack != null) {
                stacks.add(stack);
            }
        }
        setItemListEntries("gun", gunItem, stacks);
    }

    private static void registerAmmo() {
        Item ammoItem = ModItems.AMMO;
        if (ammoItem == null) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAmmoIndex> entry : TimelessAPI.getAllCommonAmmoIndex()) {
            ItemStack stack = NeiStacks.buildAmmo(entry.getKey());
            if (stack != null) {
                stacks.add(stack);
            }
        }
        setItemListEntries("ammo", ammoItem, stacks);
    }

    private static void registerAttachments() {
        Item attachmentItem = ModItems.ATTACHMENT;
        if (attachmentItem == null) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            CommonAttachmentIndex index = entry.getValue();
            // 与上游配件查询页一致：hidden 配件不展示（属于内部/原厂隐藏件）
            if (index.getPojo()
                .isHidden()) {
                continue;
            }
            ItemStack stack = NeiStacks.buildAttachment(entry.getKey());
            if (stack != null) {
                stacks.add(stack);
            }
        }
        setItemListEntries("attachment", attachmentItem, stacks);
    }

    /** 铁/金/钻石弹药盒：同一个 {@code tacz:ammo_box} 物品 + {@code Level} NBT（0/1/2）。 */
    private static void registerAmmoBoxes() {
        Item ammoBoxItem = ModItems.AMMO_BOX;
        if (ammoBoxItem == null) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int level = 0; level < 3; level++) {
            ItemStack stack = new ItemStack(ammoBoxItem);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Level", level);
            stack.setTagCompound(tag);
            stacks.add(stack);
        }
        setItemListEntries("ammo_box", ammoBoxItem, stacks);
    }

    /** 三个枪械工作台：{@code tacz:gun_smith_table} / {@code tacz:workbench_a} / {@code tacz:workbench_c}。 */
    private static void registerTables() {
        Map<Item, List<ItemStack>> stacksByItem = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, CommonBlockIndex> entry : TimelessAPI.getAllCommonBlockIndex()) {
            CommonBlockIndex index = entry.getValue();
            if (!(index.getBlock() instanceof GunSmithTableItem)) {
                continue;
            }
            ItemStack stack = BlockItemBuilder.create(index.getBlock())
                .setId(entry.getKey())
                .build();
            if (stack == null) {
                continue;
            }
            stacksByItem.computeIfAbsent(index.getBlock(), item -> new ArrayList<>())
                .add(stack);
        }
        for (Map.Entry<Item, List<ItemStack>> entry : stacksByItem.entrySet()) {
            setItemListEntries("gun smith table", entry.getKey(), entry.getValue());
        }
    }

    private static void setItemListEntries(String what, @Nullable Item item, List<ItemStack> stacks) {
        if (item == null || stacks.isEmpty()) {
            return;
        }
        try {
            API.setItemListEntries(item, stacks);
            GunMod.LOGGER.info("[TACZ] NEI item variants: {} x{}", what, stacks.size());
        } catch (RuntimeException exception) {
            GunMod.LOGGER.warn("[TACZ] failed to register NEI item variants for {}", what, exception);
        }
    }
}
