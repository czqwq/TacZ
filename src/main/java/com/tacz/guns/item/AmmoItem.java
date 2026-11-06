package com.tacz.guns.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.util.ContentPackOrder;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 搬运自上游 1.20.1 {@code item/AmmoItem.java}（extends Item implements AmmoItemDataAccessor）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code getName} → {@link #getItemStackDisplayName}；客户端枪包索引名走 {@link ItemClientHooks} 钩子，
 * common 侧不 import client 类（L1 编译边界）；</li>
 * <li>{@code getMaxStackSize(ItemStack)} → 1.7.10 Forge 的 {@link Item#getItemStackLimit(ItemStack)}；</li>
 * <li>{@code NonNullList} → {@link ArrayList}；{@code initializeClient} 删除（L2 客户端注册）；</li>
 * <li>{@code appendHoverText}/{@code getTooltipImage}（客户端索引 tooltip）删除并记录差异，L2 由客户端 tooltip 接管。</li>
 * </ul>
 */
public class AmmoItem extends Item implements AmmoItemDataAccessor {

    public AmmoItem() {
        this.setMaxStackSize(1);
    }

    /**
     * 1.7.10 必须有 {@link net.minecraft.util.IIcon}：上游 1.20.1 的 ammo item model 是 {@code builtin/entity}
     * （3D 渲染，无 2D 图标）。base 模组无弹药 2D png，用最接近的原版图标兜底，避免 MISSING_ICON_ITEM。
     * TODO(L2, client-port): 弹药按 NBT 显示不同外观，需 IItemRenderer 接管；此图标仅背包/JEI 兜底。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.itemIcon = register.registerIcon("minecraft:arrow");
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof IAmmo iAmmo) {
            return TimelessAPI.getCommonAmmoIndex(iAmmo.getAmmoId(stack))
                .map(CommonAmmoIndex::getStackSize)
                .orElse(1);
        }
        return 1;
    }

    @Override
    @Nonnull
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return ItemClientHooks.getItemName(stack, super.getItemStackDisplayName(stack));
    }

    private static Comparator<Map.Entry<ResourceLocation, CommonAmmoIndex>> idNameSort() {
        return ContentPackOrder.<CommonAmmoIndex>byNamespace()
            .thenComparingInt(
                m -> m.getValue()
                    .getSort());
    }

    /**
     * 1.7.10 创造模式物品栏填充（1.20.1 的 {@code fillItemCategory(CreativeModeTab.Output)} 替代）。
     */
    public static List<ItemStack> fillItemCategory() {
        List<ItemStack> stacks = new ArrayList<>();
        TimelessAPI.getAllCommonAmmoIndex()
            .stream()
            .sorted(idNameSort())
            .forEach(entry -> {
                ItemStack itemStack = AmmoItemBuilder.create()
                    .setId(entry.getKey())
                    .build();
                stacks.add(itemStack);
            });
        return stacks;
    }

    // TODO(port L2, client-port): 上游 AmmoItem 的 appendHoverText（读 ClientAmmoIndex tooltipKey 与 ClientAssetsManager
    // PackInfo）
    // 属客户端 tooltip 渲染，1.7.10 L1 编译边界禁止 import client；由客户端在 L2 通过 IItemRenderer/tooltip 事件接回。
    // 上游依据：tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/item/AmmoItem.java:90-106
}
