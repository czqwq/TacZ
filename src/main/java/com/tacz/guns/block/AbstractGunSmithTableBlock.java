package com.tacz.guns.block;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.inventory.GuiHandler;
import com.tacz.guns.inventory.GuiId;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械工作台基类（单方块 / 2x1x1 / 1x2x1 三种形态共用）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/AbstractGunSmithTableBlock.java}
 *
 * <h3>1.20.1 BlockState 属性 -> 1.7.10 metadata 编码</h3>
 * 
 * <pre>
 * 属性（1.20.1）              位置      取值
 * FACING（HORIZONTAL_FACING） meta 0-1  0=NORTH 1=SOUTH 2=WEST 3=EAST
 *                                       （顺序与 1.20.1 Direction#get2DDataValue() 一致，
 *                                        见 BlockMetadata#facing(int)）
 * </pre>
 * 
 * 子类再占用更高位（{@link GunSmithTableBlockB} 的 PART、{@link GunSmithTableBlockC} 的 HALF），
 * 见各自类注释。
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BaseEntityBlock} -> {@link Block} + {@code hasTileEntity(int)} /
 * {@code createTileEntity(World,int)}；方块实体注册名由 {@code init/ModBlocks} 的
 * {@code GameRegistry.registerTileEntity} 决定，所以上游 {@code BlockEntityType} 静态字段在 1.7.10 不存在。</li>
 * <li>{@code NetworkHooks.openScreen(serverPlayer, provider, writer)} -> 1.7.10 的 {@code IGuiHandler}，
 * 统一走 {@code inventory.GuiHandler#openGui}。上游通过 {@code FriendlyByteBuf} 额外下发 {@code BlockId}，
 * 1.7.10 由 Container 在服务端直接读方块实体（{@link GunSmithTableBlockEntity#getId()}），
 * 不需要额外数据包。</li>
 * <li>{@code getCloneItemStack(...)} ->
 * {@link #getPickBlock(MovingObjectPosition, World, int, int, int, EntityPlayer)}（创造模式中键取方块）。</li>
 * <li>{@code BlockPos} -> {@link ChunkCoordinates}（1.7.10 唯一的三元组坐标类型）。</li>
 * <li>{@code RenderShape.ENTITYBLOCK_ANIMATED} -> {@link #getRenderType()} 返回 -1（渲染交给 TESR；
 * 具体渲染是 L2 client-port 的工作）。</li>
 * <li>{@code PushReaction.DESTROY} -> {@link #getMobilityFlag()} 返回 1。</li>
 * </ul>
 */
public abstract class AbstractGunSmithTableBlock extends Block {

    /**
     * 一次性掉落抑制位：创造模式破坏、以及“另一半被拆”的连锁移除（详见 {@link #breakBlock} 的注释）。
     * 服务端单线程使用，读后即清。
     */
    private boolean suppressNextDrop;

    public AbstractGunSmithTableBlock() {
        super(Material.wood);
        this.setStepSound(Block.soundTypeWood);
        this.setHardness(2.0F);
        this.setResistance(3.0F);
        // L1 占位贴图；1.20.1 的每面模型由 L2 用 ISBRH 接回
        this.setBlockTextureName("tacz:block/gun_smith_table");
    }

    /**
     * 1.7.10 方块必须有 IIcon（1.20.1 走 JSON block model）：{@code Block#registerBlockIcons} 默认空实现，
     * 不注册时 {@code blockIcon} 恒为 null → TextureMap 会产出 MISSING_ICON 缺失贴图。
     * A/B/C 三种工作台共用父类图标（上游三种工作台使用同一套 gun_smith_table 贴图，见交付说明 B1）。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.blockIcon = register.registerIcon("tacz:gun_smith_table");
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new GunSmithTableBlockEntity();
    }

    /**
     * 上游 {@code use(...)}（AbstractGunSmithTableBlock.java:42-55）：服务端打开工作台 GUI。
     *
     * <p>
     * 1.7.10 注意：{@code onBlockActivated} 客户端也会被调用，且返回值决定手臂摆动，
     * 所以两端都返回 true，只有服务端真正开界面。
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float subX,
        float subY, float subZ) {
        int metadata = world.getBlockMetadata(x, y, z);
        ChunkCoordinates root = getRootPos(x, y, z, metadata);
        if (world.isRemote) {
            return true;
        }
        TileEntity tileEntity = world.getTileEntity(root.posX, root.posY, root.posZ);
        if (tileEntity instanceof GunSmithTableBlockEntity) {
            GuiHandler.openGui(player, world, root.posX, root.posY, root.posZ, GuiId.GUN_SMITH_TABLE);
        }
        return true;
    }

    /**
     * 上游 {@code setPlacedBy(...)}（AbstractGunSmithTableBlock.java:80-91）：把物品上的 {@code BlockId}
     * 写进方块实体。
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (world.isRemote || stack == null) {
            return;
        }
        if (stack.getItem() instanceof BlockItemDataAccessor accessor) {
            ResourceLocation id = accessor.getBlockId(stack);
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof GunSmithTableBlockEntity table) {
                table.setId(id);
            }
        }
    }

    /** 上游 {@code getCloneItemStack(...)}（AbstractGunSmithTableBlock.java:94-104）。 */
    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z, EntityPlayer player) {
        ChunkCoordinates root = getRootPos(x, y, z, world.getBlockMetadata(x, y, z));
        TileEntity tileEntity = world.getTileEntity(root.posX, root.posY, root.posZ);
        if (tileEntity instanceof GunSmithTableBlockEntity table && table.getId() != null) {
            return BlockItemBuilder.create(this)
                .setId(table.getId())
                .build();
        }
        return new ItemStack(Item.getItemFromBlock(this));
    }

    /** 上游 {@code getCloneItemStack(...)} 的无玩家重载（1.7.10 的 {@code getPickBlock} 有该重载）。 */
    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return getPickBlock(target, world, x, y, z, null);
    }

    /**
     * 上游掉落表（{@code data/tacz/loot_tables/blocks/workbench_*.json} 与 {@code gun_smith_table.json}）：
     * <ul>
     * <li>{@code workbench_a}：任意状态掉落，并把方块实体的 {@code BlockId} 复制到物品上（{@code copy_nbt}）；</li>
     * <li>{@code workbench_b}：只有 {@code part=foot} 掉落，且复制 {@code BlockId}；</li>
     * <li>{@code gun_smith_table}：只有 {@code part=foot} 掉落（上游 loot 表<b>不</b>复制 {@code BlockId}，
     * 但两者是同一个类的两个注册实例，1.7.10 无法区分，这里统一复制，属记录在案的差异）；</li>
     * <li>{@code workbench_c}：只有 {@code half=lower} 掉落，且复制 {@code BlockId}；</li>
     * <li>都带 {@code survives_explosion} 条件。</li>
     * </ul>
     *
     * <p>
     * <b>1.7.10 差异（重要）</b>：1.7.10 的 {@code getDrops} 在 {@code breakBlock} <b>之后</b>才被调用
     * （{@code ItemInWorldManager#tryHarvestBlock} -> {@code removeBlock()} 先移除方块与方块实体，
     * 之后才 {@code harvestBlock()}），那时读不到 {@code BlockId}。因此掉落统一在 {@code breakBlock} 里做
     * （与原版 {@code BlockBed}/{@code BlockChest} 等带方块实体的方块同一惯例），{@code getDrops} 返回空表
     * 避免二次掉落。副作用：{@code BlockEvent.HarvestDropsEvent} 看不到这份掉落；爆炸破坏按 100% 掉落
     * （上游 {@code survives_explosion} 是按爆炸半径的概率）。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        boolean suppressed = this.suppressNextDrop;
        this.suppressNextDrop = false;
        if (!suppressed && !world.isRemote && isRoot(metadata)) {
            ItemStack drop = createDropStack(world, x, y, z);
            if (drop != null) {
                dropBlockAsItem(world, x, y, z, drop);
            }
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    /** 上游 1.20 创造模式破坏不产生掉落；1.7.10 的掉落走 {@code breakBlock}，在这里置抑制位。 */
    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int metadata, EntityPlayer player) {
        if (player != null && player.capabilities.isCreativeMode) {
            this.suppressNextDrop = true;
        }
    }

    /** 见 {@code breakBlock}：掉落已在其中处理。 */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        return new ArrayList<>();
    }

    /** 组装掉落物：方块物品 + 上游 loot 表 {@code copy_nbt} 的 {@code BlockId}。 */
    protected ItemStack createDropStack(World world, int x, int y, int z) {
        ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof GunSmithTableBlockEntity table && table.getId() != null
            && stack.getItem() instanceof IBlock blockItem) {
            blockItem.setBlockId(stack, table.getId());
        }
        return stack;
    }

    /**
     * 移除本方块且不产生掉落，供“另一半被拆”的连锁移除使用
     * （对应上游 {@code updateShape} 返回 {@code Blocks.AIR}：没有任何掉落）。
     */
    protected void removeWithoutDrop(World world, int x, int y, int z) {
        this.suppressNextDrop = true;
        world.setBlockToAir(x, y, z);
        this.suppressNextDrop = false;
    }

    /**
     * 两格/双层方块的第二格是否放得下（上游 {@code getStateForPlacement} 里
     * {@code level.getBlockState(above).canBeReplaced(context)} 的前置条件）。
     *
     * <p>
     * 1.7.10 的放置判定必须由 ItemBlock 在 {@code onItemUse} 里先做（参考原版 {@code ItemBed}），
     * 因此把判定暴露成公开静态方法供 {@code item/} 侧预检。
     */
    public static boolean isReplaceable(World world, int x, int y, int z) {
        return world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z);
    }

    /**
     * 第二格放不下时的兜底：1.7.10 无法在 {@code onBlockPlacedBy} 阶段阻止放置（方块已放、物品已扣），
     * 这里撤销方块并把物品退回玩家。
     *
     * <p>
     * TODO(port): 上游在 {@code getStateForPlacement} 返回 null，放置直接取消、物品不消耗。
     * 1.7.10 的正确做法是 ItemBlock 侧先调用 {@link #isReplaceable} 预检
     * （{@code item/GunSmithTableItem#onItemUse}），本兜底只保证“不会白扣物品、也不会留下半截方块”。
     */
    protected void refundAndRemove(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        this.suppressNextDrop = true;
        world.setBlockToAir(x, y, z);
        this.suppressNextDrop = false;
        if (placer instanceof EntityPlayer player && !player.capabilities.isCreativeMode) {
            ItemStack refund = new ItemStack(Item.getItemFromBlock(this));
            if (stack != null && stack.hasTagCompound()) {
                refund.setTagCompound(
                    (NBTTagCompound) stack.getTagCompound()
                        .copy());
            }
            if (!player.inventory.addItemStackToInventory(refund)) {
                player.dropPlayerItemWithRandomChoice(refund, false);
            }
        }
    }

    /** 上游 {@code RenderShape.ENTITYBLOCK_ANIMATED}：由 TESR 渲染，方块自身不渲染。 */
    @Override
    public int getRenderType() {
        return -1;
    }

    /** 上游 {@code noOcclusion()}。 */
    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /** 上游 {@code PushReaction.DESTROY}：活塞推动即破坏（1.7.10 的 mobilityFlag 1）。 */
    @Override
    public int getMobilityFlag() {
        return 1;
    }

    /** 上游 {@code isRoot(BlockState)}。1.7.10 用 metadata 判断（编码见子类注释）。 */
    public abstract boolean isRoot(int metadata);

    /** 上游 {@code getRootPos(BlockPos, BlockState)}：多方块工作台的“主方块”坐标。 */
    public abstract ChunkCoordinates getRootPos(int x, int y, int z, int metadata);

    /**
     * 上游 {@code parseRotation(Direction)}（AbstractGunSmithTableBlock.java:108-110）：
     * TESR 用的 Y 轴旋转角（度）。
     *
     * <p>
     * 因为 metadata 的朝向位沿用 1.20.1 {@code Direction#get2DDataValue()} 的编码，
     * 这里换算出的角度与上游逐位一致。
     */
    public float parseRotation(ForgeDirection direction) {
        return 90.0F * (3 - BlockMetadata.facing2D(direction)) - 90;
    }

    /** 1.20.1 {@code Direction} 的 1.7.10 等价物，供客户端渲染读取朝向。 */
    public ForgeDirection getFacing(int metadata) {
        return BlockMetadata.facing(metadata);
    }

    /**
     * 上游 {@code use(...)} 在 {@code getId() == null} 时用 {@link DefaultAssets#DEFAULT_BLOCK_ID}
     * （AbstractGunSmithTableBlock.java:49）。1.7.10 的 Container 也按同样规则兜底。
     */
    public static ResourceLocation idOrDefault(ResourceLocation id) {
        return id == null ? DefaultAssets.DEFAULT_BLOCK_ID : id;
    }
}
