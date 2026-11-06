package com.tacz.guns.crafting;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 弹药库升级配方（铁 → 金 → 钻石）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/crafting/AmmoStorageUpgradeRecipe.java}
 * 
 * <pre>
 * {@code public class AmmoStorageUpgradeRecipe extends CustomRecipe implements IShapedRecipe<CraftingContainer>}
 * </pre>
 * 
 * 形状固定 3×3：正中是要升级的弹药库，其余八格是材料（{@code tacz:ammo_storage} + 8×金锭 →
 * {@code tacz:ammo_storage_gold}；{@code tacz:ammo_storage_gold} + 8×钻石 → {@code tacz:ammo_storage_diamond}），
 * 并且要把来源方块的 {@code BlockEntityTag}（库存 NBT）复制到产物上。
 *
 * <p>
 * <b>1.7.10 差异</b>：
 * <ul>
 * <li>{@code CustomRecipe}（1.13+ 才有的「代码写死形状的配方」基类）→ 直接实现
 * {@link IRecipe}：{@code CustomRecipe#matches} → {@link #matches}，
 * {@code assemble} → {@link #getCraftingResult}（上游 {@code getResultItem} → {@link #getRecipeOutput}）；</li>
 * <li>上游按配方 id 里是否含 {@code "diamond"} 选变体（{@code ammo_storage_gold.json} /
 * {@code ammo_storage_diamond.json} 都是 {@code "type":"tacz:ammo_storage_upgrade"}，靠 id 区分）。
 * 1.7.10 没有「配方 id」，因此改由静态工厂 {@link #createRecipe(String)} 显式传路径；</li>
 * <li>上游用 {@code BlockItem#getBlockEntityData}/{@code setBlockEntityData}（1.20 的
 * {@code BlockEntityTag} 读写 API）；1.7.10 直接读写同名 NBT 键
 * {@code BlockEntityTag}，与 {@code item/AmmoStorageBlockItem#placeBlockAt}
 * 的放置逻辑对接（契约见 docs/PORT_GAME.md）；</li>
 * <li>上游用 {@code CraftingContainer#getWidth()/getHeight()}；1.7.10 的
 * {@link InventoryCrafting} 不暴露宽高，只用 {@code getSizeInventory() == 9} 判定 3×3 网格。</li>
 * </ul>
 */
public class AmmoStorageUpgradeRecipe implements IRecipe {

    /** 与 {@code item/AmmoStorageBlockItem} 约定的库存 NBT 键（1.20 的 {@code BlockEntityTag}）。 */
    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    private final Item from;
    private final Item material;
    private final Item to;

    public AmmoStorageUpgradeRecipe(@Nullable Item from, @Nullable Item material, @Nullable Item to) {
        this.from = from;
        this.material = material;
        this.to = to;
    }

    /**
     * 上游「按配方 id 选变体」的等价物。
     *
     * @param recipePath {@code data/tacz/recipes} 下的文件名（{@code ammo_storage_gold} /
     *                   {@code ammo_storage_diamond}），含 {@code diamond} 时走钻石升级
     * @return 物品未注册时返回 {@code null}（必须在 {@code ModItems#register()} 之后调用）
     */
    @Nullable
    public static IRecipe createRecipe(String recipePath) {
        Item ammoStorage = findItem("ammo_storage");
        Item ammoStorageGold = findItem("ammo_storage_gold");
        Item ammoStorageDiamond = findItem("ammo_storage_diamond");
        if (ammoStorage == null || ammoStorageGold == null || ammoStorageDiamond == null) {
            return null;
        }
        if (recipePath != null && recipePath.contains("diamond")) {
            return new AmmoStorageUpgradeRecipe(ammoStorageGold, Items.diamond, ammoStorageDiamond);
        }
        return new AmmoStorageUpgradeRecipe(ammoStorage, Items.gold_ingot, ammoStorageGold);
    }

    @Nullable
    private static Item findItem(String path) {
        return GameRegistry.findItem("tacz", path);
    }

    @Override
    public boolean matches(InventoryCrafting container, World world) {
        if (from == null || material == null || container == null || container.getSizeInventory() != 9) {
            return false;
        }
        for (int slot = 0; slot < container.getSizeInventory(); slot++) {
            ItemStack stack = container.getStackInSlot(slot);
            if (stack == null || stack.getItem() == null) {
                return false;
            }
            if (slot == 4) {
                if (stack.getItem() != from) {
                    return false;
                }
            } else if (stack.getItem() != material) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Nullable
    public ItemStack getCraftingResult(InventoryCrafting container) {
        ItemStack result = getRecipeOutput();
        if (result == null || container == null) {
            return result;
        }
        ItemStack source = container.getStackInSlot(4);
        if (source == null || source.getItem() == null) {
            return result;
        }
        NBTTagCompound sourceTag = source.getTagCompound();
        if (sourceTag == null || !sourceTag.hasKey(BLOCK_ENTITY_TAG, 10)) {
            return result;
        }
        NBTTagCompound resultTag = result.getTagCompound();
        if (resultTag == null) {
            resultTag = new NBTTagCompound();
            result.setTagCompound(resultTag);
        }
        resultTag.setTag(
            BLOCK_ENTITY_TAG,
            sourceTag.getCompoundTag(BLOCK_ENTITY_TAG)
                .copy());
        return result;
    }

    @Override
    public int getRecipeSize() {
        return 9;
    }

    @Override
    @Nullable
    public ItemStack getRecipeOutput() {
        return to == null ? null : new ItemStack(to);
    }
}
