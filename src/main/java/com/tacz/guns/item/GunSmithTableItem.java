package com.tacz.guns.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.BlockMetadata;
import com.tacz.guns.block.GunSmithTableBlockB;
import com.tacz.guns.block.GunSmithTableBlockC;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.util.ContentPackOrder;

/**
 * 搬运自上游 1.20.1 {@code item/GunSmithTableItem.java}（extends BlockItem implements BlockItemDataAccessor）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code BlockItem} → {@link ItemBlock}；</li>
 * <li>{@code initializeClient}（1.20 的 BEWLR 注册）删除 —— 1.7.10 在 client 侧
 * {@code MinecraftForgeClient.registerItemRenderer} 注册，属 L2 client-port 工作；</li>
 * <li>{@code getName} → {@link #getItemStackDisplayName}；客户端索引名通过
 * {@link ItemClientHooks#getItemName} 钩子获取（client 侧由 ClientProxy 注入，L1 服务端无依赖）；</li>
 * <li>{@code NonNullList} → {@link ArrayList}；{@code getTooltipImage} 无 1.7.10 对应，删除（记录差异）。</li>
 * </ul>
 */
public class GunSmithTableItem extends ItemBlock implements BlockItemDataAccessor {

    public GunSmithTableItem(Block block) {
        super(block);
        this.setMaxStackSize(1);
    }

    public static List<ItemStack> fillItemCategory() {
        List<ItemStack> stacks = new ArrayList<>();
        TimelessAPI.getAllCommonBlockIndex()
            .stream()
            .sorted(ContentPackOrder.<CommonBlockIndex>byNamespace())
            .forEach(entry -> {
                // 1.7.10：CommonBlockIndex#getBlock 返回 Item（ItemBlock）；1.20.1 是 BlockItem
                Item blockItem = entry.getValue()
                    .getBlock();
                if (!(blockItem instanceof GunSmithTableItem)) {
                    return;
                }
                ItemStack stack = BlockItemBuilder.create(blockItem)
                    .setId(entry.getKey())
                    .build();
                stacks.add(stack);
            });
        return stacks;
    }

    @Override
    @NotNull
    public String getItemStackDisplayName(@NotNull ItemStack stack) {
        return ItemClientHooks.getItemName(stack, super.getItemStackDisplayName(stack));
    }

    /**
     * 上游 {@code getStateForPlacement(...)} 在「第二格不可替换」时返回 null ⇒ 放置取消、物品不消耗
     * （{@code GunSmithTableBlockB.java:43-52}、{@code GunSmithTableBlockC.java:40-49}）。
     *
     * <p>
     * 1.7.10 的等价落点：{@code ItemBlock#onItemUse} 用 {@code placeBlockAt} 的返回值决定是否扣物品
     * （{@code build/rfg/minecraft-src/java/net/minecraft/item/ItemBlock.java:116-119}），此处坐标已是最终
     * 放置坐标。返回 false 时方块与物品都不动，{@code onBlockPlacedBy} 里的兜底退款分支因此不再触发。
     */
    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        // player == null（非玩家放置路径）时无法算朝向，退回旧路径：由 onBlockPlacedBy 兜底退款
        if (player != null && !secondPartReplaceable(world, player, x, y, z)) {
            return false;
        }
        return super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
    }

    /** 第二格坐标与朝向的计算必须与 {@link GunSmithTableBlockB}/{@link GunSmithTableBlockC} 的放置逻辑一致。 */
    private boolean secondPartReplaceable(World world, EntityPlayer player, int x, int y, int z) {
        Block block = this.field_150939_a;
        if (block instanceof GunSmithTableBlockC) {
            return world.getBlock(x, y + 1, z)
                .isReplaceable(world, x, y + 1, z);
        }
        if (block instanceof GunSmithTableBlockB) {
            ForgeDirection facing = BlockMetadata.clockwise(BlockMetadata.horizontalDirection(player));
            int headX = x + facing.offsetX;
            int headZ = z + facing.offsetZ;
            return world.getBlock(headX, y, headZ)
                .isReplaceable(world, headX, y, headZ);
        }
        return true;
    }
}
