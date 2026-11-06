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

import org.jetbrains.annotations.NotNull;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.util.ContentPackOrder;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 搬运自上游 1.20.1 {@code item/AttachmentItem.java}（extends Item implements AttachmentItemDataAccessor）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code getName} → {@link #getItemStackDisplayName}，客户端枪包索引名走 {@link ItemClientHooks} 钩子；</li>
 * <li>{@code initializeClient}/{@code getTooltipImage} 删除（1.7.10 无 IClientItemExtensions/tooltip 组件，L2 客户端接管）；</li>
 * <li>{@code verifyTagAfterLoad(CompoundTag)}（1.20 的数据修复钩子）1.7.10 无对应：
 * 数据修复逻辑保留在 {@code util/datafixer/AttachmentIdFix}，改由读取时懒修复（TODO 记录）；</li>
 * <li>{@code NonNullList} → {@link ArrayList}。</li>
 * </ul>
 */
public class AttachmentItem extends Item implements AttachmentItemDataAccessor {

    public AttachmentItem() {
        this.setMaxStackSize(1);
    }

    /**
     * 1.7.10 必须有 {@link net.minecraft.util.IIcon}：上游 1.20.1 的 attachment item model 是 {@code builtin/entity}
     * （3D 渲染，无 2D 图标）。base 模组无配件 2D png，用最接近的原版图标兜底，避免 MISSING_ICON_ITEM。
     * TODO(L2, client-port): 配件按 NBT 显示不同外观，需 IItemRenderer 接管；此图标仅背包/JEI 兜底。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.itemIcon = register.registerIcon("minecraft:compass");
    }

    @Override
    @Nonnull
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return ItemClientHooks.getItemName(stack, super.getItemStackDisplayName(stack));
    }

    private static Comparator<Map.Entry<ResourceLocation, CommonAttachmentIndex>> idNameSort() {
        return ContentPackOrder.<CommonAttachmentIndex>byNamespace()
            .thenComparingInt(
                m -> m.getValue()
                    .getSort());
    }

    /**
     * 1.7.10 创造模式物品栏填充（替代上游 {@code fillItemCategory(CreativeModeTab.Output)}）。
     */
    public static List<ItemStack> fillItemCategory(AttachmentType type) {
        List<ItemStack> stacks = new ArrayList<>();
        TimelessAPI.getAllCommonAttachmentIndex()
            .stream()
            .sorted(idNameSort())
            .forEach(entry -> {
                if (entry.getValue()
                    .getPojo()
                    .isHidden()) {
                    return;
                }
                if (type.equals(
                    entry.getValue()
                        .getType())) {
                    ItemStack itemStack = AttachmentItemBuilder.create()
                        .setId(entry.getKey())
                        .build();
                    stacks.add(itemStack);
                }
            });
        return stacks;
    }

    @Override
    @NotNull
    public AttachmentType getType(ItemStack attachmentStack) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (iAttachment != null) {
            ResourceLocation id = iAttachment.getAttachmentId(attachmentStack);
            return TimelessAPI.getCommonAttachmentIndex(id)
                .map(CommonAttachmentIndex::getType)
                .orElse(AttachmentType.NONE);
        } else {
            return AttachmentType.NONE;
        }
    }

    // TODO(port): 上游 verifyTagAfterLoad(CompoundTag) 在物品 NBT 加载后调用
    // util/datafixer/AttachmentIdFix#updateAttachmentIdInTag
    // 做旧存档的配件 ID 修复。1.7.10 没有该钩子，改为在 api 的 AttachmentItemDataAccessor 读取路径里懒修复（见 api 实现），
    // 或由 L2 的客户端/服务端加载监听补充。上游依据 AttachmentItem.java:103-106。
}
