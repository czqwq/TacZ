package com.tacz.guns.init;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.tacz.guns.block.AmmoStorageBlock;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunSmithTableBlockA;
import com.tacz.guns.block.GunSmithTableBlockB;
import com.tacz.guns.block.GunSmithTableBlockC;
import com.tacz.guns.block.StatueBlock;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.item.AmmoStorageBlockItem;
import com.tacz.guns.item.BlockItemWithItemIcon;
import com.tacz.guns.item.DefaultTableItem;
import com.tacz.guns.item.GunPegboardItem;
import com.tacz.guns.item.GunSmithTableItem;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 搬运自上游 1.20.1 {@code init/ModBlocks.java}。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code DeferredRegister<Block>} → 普通 public static 字段 + {@link GameRegistry#registerBlock}；</li>
 * <li>{@code BlockEntityType}（1.20 的方块实体类型注册表）→ {@link GameRegistry#registerTileEntity}，
 * 注册名与上游保持同一语义（{@code tacz:<名字>}）；</li>
 * <li>方块物品（BlockItem）→ 1.7.10 的 {@link ItemBlock} 子类，由 {@code registerBlock} 的
 * {@code itemclass} 参数创建：工作台用 {@link DefaultTableItem}/{@link GunSmithTableItem}、
 * 挂板用 {@link GunPegboardItem}、弹药储存用 {@link AmmoStorageBlockItem}、标靶/雕像用
 * {@link BlockItemWithItemIcon}（2D 物品图标），其余用原版 ItemBlock；</li>
 * <li>上游 {@code TagKey<Block> BULLET_IGNORE_BLOCKS}（1.20 方块标签 {@code tacz:bullet_ignore}）→
 * 1.7.10 无 tag 系统，字段删除（{@code util/block/BlockRayTrace} 已按无 tag 降级，见其类注释）。</li>
 * </ul>
 */
public class ModBlocks {

    public static Block GUN_SMITH_TABLE;
    public static Block WORKBENCH_111;
    public static Block WORKBENCH_211;
    public static Block WORKBENCH_121;
    public static Block TARGET;
    public static Block STATUE;
    public static Block GUN_PEGBOARD;
    public static Block GLOW_GUN_PEGBOARD;
    public static Block AMMO_STORAGE;
    public static Block AMMO_STORAGE_GOLD;
    public static Block AMMO_STORAGE_DIAMOND;
    public static Block GUN_DISPLAY_CASE;

    public static void register() {
        if (GUN_SMITH_TABLE != null) {
            return;
        }
        ModCreativeTabs.register();
        // 上游 DeferredRegister 先注册方块再注册方块实体类型，这里保持顺序
        GUN_SMITH_TABLE = registerBlock("gun_smith_table", new GunSmithTableBlockB(), DefaultTableItem.class);
        WORKBENCH_111 = registerBlock("workbench_a", new GunSmithTableBlockA(), GunSmithTableItem.class);
        WORKBENCH_211 = registerBlock("workbench_b", new GunSmithTableBlockB(), GunSmithTableItem.class);
        WORKBENCH_121 = registerBlock("workbench_c", new GunSmithTableBlockC(), GunSmithTableItem.class);

        TARGET = registerBlock("target", new TargetBlock(), BlockItemWithItemIcon.TargetBlockItem.class);
        STATUE = registerBlock("statue", new StatueBlock(), BlockItemWithItemIcon.StatueBlockItem.class);
        GUN_PEGBOARD = registerBlock("gun_pegboard", new GunPegboardBlock(), GunPegboardItem.class);
        GLOW_GUN_PEGBOARD = registerBlock("glow_gun_pegboard", new GunPegboardBlock(true), GunPegboardItem.class);
        AMMO_STORAGE = registerBlock(
            "ammo_storage",
            new AmmoStorageBlock(AmmoStorageBlock.IRON_CAPACITY_STACKS),
            AmmoStorageBlockItem.class);
        AMMO_STORAGE_GOLD = registerBlock(
            "ammo_storage_gold",
            new AmmoStorageBlock(AmmoStorageBlock.GOLD_CAPACITY_STACKS),
            AmmoStorageBlockItem.class);
        AMMO_STORAGE_DIAMOND = registerBlock(
            "ammo_storage_diamond",
            new AmmoStorageBlock(AmmoStorageBlock.DIAMOND_CAPACITY_STACKS),
            AmmoStorageBlockItem.class);
        GUN_DISPLAY_CASE = registerBlock("gun_display_case", new GunDisplayCaseBlock(), GunDisplayCaseBlockItem.class);

        GameRegistry.registerTileEntity(GunSmithTableBlockEntity.class, "tacz:gun_smith_table");
        GameRegistry.registerTileEntity(TargetBlockEntity.class, "tacz:target");
        GameRegistry.registerTileEntity(StatueBlockEntity.class, "tacz:statue");
        GameRegistry.registerTileEntity(GunPegboardBlockEntity.class, "tacz:gun_pegboard");
        GameRegistry.registerTileEntity(AmmoStorageBlockEntity.class, "tacz:ammo_storage");
        GameRegistry.registerTileEntity(GunDisplayCaseBlockEntity.class, "tacz:gun_display_case");
    }

    private static Block registerBlock(String name, Block block, Class<? extends ItemBlock> itemClass) {
        block.setBlockName("tacz." + name);
        block.setCreativeTab(ModCreativeTabs.OTHER_TAB);
        GameRegistry.registerBlock(block, itemClass, name);
        return block;
    }

    /**
     * 枪械展示柜的方块物品。
     *
     * <p>
     * 上游在 {@code ModItems} 注册时用匿名 {@code BlockItem} 覆写 {@code appendHoverText} 追加
     * {@code tooltip.tacz.gun_display_case}
     * （{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModItems.java:47-52}）；
     * 1.7.10 的等价钩子是 {@link ItemBlock#addInformation}。
     */
    public static class GunDisplayCaseBlockItem extends ItemBlock {

        public GunDisplayCaseBlockItem(Block block) {
            super(block);
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings("unchecked")
        public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
            super.addInformation(stack, player, tooltip, advanced);
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.tacz.gun_display_case"));
        }
    }
}
