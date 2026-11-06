package com.tacz.guns.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.BlockColored;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.item.GunPegboardItem;

/**
 * 枪械挂板染色配方（1 个挂板 + N 个染料 → 染色后的挂板）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/crafting/GunPegboardDyeRecipe.java}
 * 
 * <pre>
 * {@code public class GunPegboardDyeRecipe extends CustomRecipe}
 * </pre>
 * 
 * 上游用 {@code DyeItem} + {@code DyeableLeatherItem#dyeArmor(pegboard, dyes)} 完成调色。
 *
 * <p>
 * <b>1.7.10 差异</b>：
 * <ul>
 * <li>{@code CustomRecipe} → 直接实现 {@link IRecipe}（1.7.10 无该基类）；
 * {@code assemble(CraftingContainer, RegistryAccess)} → {@link #getCraftingResult(InventoryCrafting)}；</li>
 * <li>1.7.10 没有 {@code DyeItem} / {@code DyeableLeatherItem}：染料就是 {@code Items.dye}
 * （damage 0..15），调色算法照抄原版 {@code item/crafting/RecipesArmorDyes}
 * （{@code EntitySheep.fleeceColorTable} + {@code BlockColored.func_150032_b}，
 * 见 {@code build/rfg/minecraft-src/java/net/minecraft/item/crafting/RecipesArmorDyes.java}）；</li>
 * <li>颜色写入的 NBT 键与 1.20 的 {@code DyeableLeatherItem} 完全一致（{@code display.color}），
 * 并与 {@code item/GunPegboardItem#hasCustomColor}/{@code getColor} 的读法对接
 * （该类是 {@code ItemBlock}，不带 {@code setColor}，所以这里直接写 NBT）。</li>
 * </ul>
 */
public class GunPegboardDyeRecipe implements IRecipe {

    private static final String DISPLAY_TAG = "display";
    private static final String COLOR_TAG = "color";

    @Override
    public boolean matches(InventoryCrafting container, World world) {
        if (container == null) {
            return false;
        }
        boolean pegboard = false;
        boolean dye = false;
        for (int slot = 0; slot < container.getSizeInventory(); slot++) {
            ItemStack stack = container.getStackInSlot(slot);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            if (stack.getItem() instanceof GunPegboardItem) {
                if (pegboard) {
                    return false;
                }
                pegboard = true;
            } else if (stack.getItem() == Items.dye) {
                dye = true;
            } else {
                return false;
            }
        }
        return pegboard && dye;
    }

    @Override
    @Nullable
    public ItemStack getCraftingResult(InventoryCrafting container) {
        if (container == null) {
            return null;
        }
        ItemStack pegboard = null;
        List<ItemStack> dyes = new ArrayList<>();
        for (int slot = 0; slot < container.getSizeInventory(); slot++) {
            ItemStack stack = container.getStackInSlot(slot);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            if (stack.getItem() instanceof GunPegboardItem && pegboard == null) {
                pegboard = stack;
            } else if (stack.getItem() == Items.dye) {
                dyes.add(stack);
            } else {
                return null;
            }
        }
        if (pegboard == null || dyes.isEmpty()) {
            return null;
        }
        ItemStack result = pegboard.copy();
        result.stackSize = 1;
        // 原版 RecipesArmorDyes 的调色算法（RGB 累加 + 按最大分量归一）
        int[] colorSum = new int[3];
        int maxSum = 0;
        int components = 0;
        if (pegboard.getItem() instanceof GunPegboardItem item && item.hasCustomColor(pegboard)) {
            int color = item.getColor(pegboard);
            float red = (float) (color >> 16 & 255) / 255.0F;
            float green = (float) (color >> 8 & 255) / 255.0F;
            float blue = (float) (color & 255) / 255.0F;
            maxSum += (int) (Math.max(red, Math.max(green, blue)) * 255.0F);
            colorSum[0] += (int) (red * 255.0F);
            colorSum[1] += (int) (green * 255.0F);
            colorSum[2] += (int) (blue * 255.0F);
            components++;
        }
        for (ItemStack dye : dyes) {
            float[] rgb = EntitySheep.fleeceColorTable[BlockColored.func_150032_b(dye.getItemDamage() & 15)];
            int red = (int) (rgb[0] * 255.0F);
            int green = (int) (rgb[1] * 255.0F);
            int blue = (int) (rgb[2] * 255.0F);
            maxSum += Math.max(red, Math.max(green, blue));
            colorSum[0] += red;
            colorSum[1] += green;
            colorSum[2] += blue;
            components++;
        }
        int red = colorSum[0] / components;
        int green = colorSum[1] / components;
        int blue = colorSum[2] / components;
        float average = (float) maxSum / (float) components;
        float max = (float) Math.max(red, Math.max(green, blue));
        if (max > 0.0F) {
            red = (int) ((float) red * average / max);
            green = (int) ((float) green * average / max);
            blue = (int) ((float) blue * average / max);
        }
        int rgb = (red << 8) + green;
        rgb = (rgb << 8) + blue;
        setColor(result, rgb);
        return result;
    }

    /** 等价于 1.20 {@code DyeableLeatherItem#setColor}：写 {@code display.color}。 */
    private static void setColor(ItemStack stack, int color) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        NBTTagCompound display = tag.getCompoundTag(DISPLAY_TAG);
        tag.setTag(DISPLAY_TAG, display);
        display.setInteger(COLOR_TAG, color);
    }

    @Override
    public int getRecipeSize() {
        return 9;
    }

    @Override
    @Nullable
    public ItemStack getRecipeOutput() {
        // 无固定产物（颜色取决于染料），上游 CustomRecipe 同样没有固定产物
        return null;
    }
}
