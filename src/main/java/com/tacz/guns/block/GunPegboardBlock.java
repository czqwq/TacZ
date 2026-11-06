package com.tacz.guns.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import com.tacz.guns.item.GunPegboardItem;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪架（普通 / 发光两种）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/GunPegboardBlock.java}
 *
 * <h3>1.20.1 BlockState 属性 -> 1.7.10 metadata 编码</h3>
 * 
 * <pre>
 * 属性（1.20.1）              位置      取值
 * FACING（HORIZONTAL_FACING） meta 0-1  0=SOUTH 1=WEST 2=NORTH 3=EAST
 * SHAPE（PegboardShape）      meta 2-3  0=STRAIGHT 1=CORNER_LEFT 2=CORNER_RIGHT
 * DYED                        不进 metadata，见下
 * </pre>
 * 
 * 1.7.10 的方块 metadata 只有 <b>4 bit</b>（{@code NibbleArray#set} 会掩掉高位，
 * {@code build/rfg/minecraft-src/java/net/minecraft/world/chunk/NibbleArray.java:44-58}），
 * 放不下 FACING(2)+SHAPE(2)+DYED(1)=5 bit，因此：
 * <ul>
 * <li>FACING + SHAPE 进 metadata（最大 0b1011 = 11）；</li>
 * <li>{@code DYED} 只留在方块实体（{@code GunPegboardBlockEntity#getColor()}，上游本就是同一份数据的派生量），
 * 渲染时由 {@link #getIcon(IBlockAccess, int, int, int, int)} 换 grayscale 图标 +
 * {@link #colorMultiplier(IBlockAccess, int, int, int)} 染色，等价上游
 * {@code blockstates/tacz:gun_pegboard.json} 的 {@code dyed=true -> gun_pegboard_dyed}
 * （{@code models/block/gun_pegboard_dyed.json} = {@code block/pegboard_grayscale} + {@code tintindex:0}）。</li>
 * </ul>
 * 
 * 发光是<b>方块实例</b>属性（构造器参数 {@code glowing}），不是状态，用 {@link #isGlowing()} 读，
 * 亮度走 {@link #getLightValue(IBlockAccess, int, int, int)}。
 *
 * <h3>形状（VoxelShape -> 多 AABB 近似）</h3>
 * 直板是一个 3/16 厚的薄板；折面是两块薄板的并集（上游 {@code Shapes.or(plate, cornerPlate)}），
 * 因此 {@link #addCollisionBoxesToList} 在折面时返回 2 个 AABB，
 * {@link #getCollisionBoundingBoxFromPool} 返回它们的外包盒。数值与上游逐位一致
 * （{@code GunPegboardBlock.java:50-53}）。
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code getStateForPlacement(BlockPlaceContext)} 没有对应钩子：放置面在 {@link #onBlockPlaced} 记录
 * （{@link #pendingPlaceSide}），朝向 + 折面形状在 {@link #onBlockPlacedBy} 里算。
 * 上游的 {@code !context.replacingClickedOnBlock()} 在 1.7.10 等价于「点击的方块就是枪架」——
 * {@code BlockPlaceContext#replaceClicked} = 点击方块 {@code canBeReplaced(this)}，而枪架的
 * {@code Properties} 没有 {@code replaceable()}（{@code GunPegboardBlock.java:61}），故恒为 false。</li>
 * <li>{@code updateShape} -> {@link #onNeighborBlockChange}（只重算 SHAPE 位）。</li>
 * <li>{@code rotate(BlockState, Rotation)} / {@code mirror(...)}（结构方块用）-> 1.7.10 的
 * {@link #rotateBlock} / {@link #getValidRotations}（Forge 的旋转钩子）。</li>
 * <li>{@code Items.DEBUG_STICK} 在 1.7.10 不存在，相关判断删除（记录差异）。</li>
 * <li>染色：上游 {@code DyeableLeatherItem} 的 {@code display.color} NBT 在这里直接读写，
 * 键与 {@code item/GunPegboardItem} 一致（{@code display.color}）。</li>
 * <li>{@code PushReaction.DESTROY} -> {@link #getMobilityFlag()} 返回 1。</li>
 * </ul>
 */
public class GunPegboardBlock extends Block {

    /** 上游 {@code SHAPE} 占用的 metadata 位（DYED 见类注释，不占位）。 */
    private static final int SHAPE_SHIFT = 2;
    private static final int SHAPE_MASK = 0x3;

    private final boolean glowing;

    /** 染色用 grayscale 图标（上游 {@code block/pegboard_grayscale}，配 {@code tintindex:0}）。 */
    private IIcon grayscaleIcon;

    /** 放置时 {@code onBlockPlaced} 记下的点击面，供 {@code onBlockPlacedBy} 使用（移植 glue）。 */
    private int pendingPlaceSide = -1;
    /** 一次性掉落抑制位（创造模式破坏不产生掉落）。 */
    private boolean suppressNextDrop;

    public GunPegboardBlock() {
        this(false);
    }

    /**
     * 1.7.10 方块必须有 IIcon（1.20.1 走 JSON block model）：{@code Block#registerBlockIcons} 默认空实现，
     * 不注册时 {@code blockIcon} 恒为 null → TextureMap 会产出 MISSING_ICON 缺失贴图。
     * 发光/普通是同一个类的两个实例（构造参数 {@link #glowing} 区分），这里按实例字段选图标。
     *
     * <p>
     * 同时注册 grayscale 图标：上游染色走 {@code gun_pegboard_dyed} 模型
     * （{@code block/pegboard_grayscale} + {@code tintindex:0}），1.7.10 的等价物是
     * {@link #getIcon(IBlockAccess, int, int, int, int)} 换图标 + {@link #colorMultiplier}。
     *
     * <p>
     * 折面模型（{@code models/block/gun_pegboard_corner*.json} / {@code glow_gun_pegboard_corner*.json}）
     * 用的是另外 4 张贴图（{@code peg_board_cornor} 系列），也必须进方块图集，否则
     * GTNHLib JSON 模型系统（其自动贴图注册）取到 missingno。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.blockIcon = register.registerIcon(this.glowing ? "tacz:glow_pegboard" : "tacz:pegboard");
        this.grayscaleIcon = register
            .registerIcon(this.glowing ? "tacz:glow_pegboard_grayscale" : "tacz:pegboard_grayscale");
        // 折面模型用的 4 张贴图（见方法注释）：只注册、不保存引用，GTNHLib 模型系统按名字取
        register.registerIcon(this.glowing ? "tacz:glow_pegboard_cornor" : "tacz:peg_board_cornor");
        register.registerIcon(this.glowing ? "tacz:glow_pegboard_cornor_grayscale" : "tacz:peg_board_cornor_grayscale");
    }

    /** 上游 {@code blockstates/gun_pegboard.json}：{@code dyed} 决定用普通还是 grayscale（可染色）贴图。 */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        return pegboardColor(world, x, y, z) >= 0 && this.grayscaleIcon != null ? this.grayscaleIcon : this.blockIcon;
    }

    /**
     * 上游染色模型的 {@code tintindex:0}（{@code models/block/gun_pegboard_dyed.json}）在 1.7.10 的等价物：
     * {@code RenderBlocks#renderStandardBlock} 会读 {@code Block#colorMultiplier} 作为顶点颜色
     * （{@code build/rfg/minecraft-src/java/net/minecraft/client/renderer/RenderBlocks.java:4410-4413}）。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        int color = pegboardColor(world, x, y, z);
        return color >= 0 ? color : 0xFFFFFF;
    }

    /**
     * 物品/3D 图标路径（{@code RenderBlocks#renderBlockAsItem} 等）没有坐标，无法取方块实体：
     * 退回未染色（与上游未染色物品模型 {@code gun_pegboard} 一致；染色物品图标见
     * {@code item/GunPegboardItem#getColorFromItemStack}）。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int metadata) {
        return 0xFFFFFF;
    }

    /** 方块实体的染色（上游 {@code GunPegboardBlockEntity#getColor()}，{@code -1} = 未染色）。 */
    public static int pegboardColor(IBlockAccess world, int x, int y, int z) {
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        return tileEntity instanceof GunPegboardBlockEntity pegboard ? pegboard.getColor() : -1;
    }

    public GunPegboardBlock(boolean glowing) {
        super(Material.iron);
        this.glowing = glowing;
        this.setStepSound(Block.soundTypeMetal);
        this.setHardness(5.0F);
        this.setResistance(6.0F);
        if (glowing) {
            this.setBlockName("glow_gun_pegboard");
            this.setBlockTextureName("tacz:block/glow_pegboard");
        } else {
            this.setBlockName("gun_pegboard");
            this.setBlockTextureName("tacz:block/pegboard");
        }
    }

    /** 上游 {@code isGlowing()}。 */
    public boolean isGlowing() {
        return glowing;
    }

    // ------------------------------------------------------------------------------------------
    // metadata 编解码
    // ------------------------------------------------------------------------------------------

    public static ForgeDirection getFacing(int metadata) {
        return BlockMetadata.facing(metadata);
    }

    public static int withFacing(int metadata, ForgeDirection facing) {
        return BlockMetadata.withFacing(metadata, facing);
    }

    public static PegboardShape getShape(int metadata) {
        return PegboardShape.byMeta(BlockMetadata.bits(metadata, SHAPE_SHIFT, SHAPE_MASK));
    }

    public static int withShape(int metadata, PegboardShape shape) {
        return BlockMetadata.withBits(metadata, SHAPE_SHIFT, SHAPE_MASK, shape.toMeta());
    }

    /** 上游 {@code hasCorner(...)}：折面时两块板都算本方块。 */
    public static boolean hasCorner(int metadata) {
        return getShape(metadata) != PegboardShape.STRAIGHT;
    }

    // ------------------------------------------------------------------------------------------
    // 方块实体 / 亮度
    // ------------------------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new GunPegboardBlockEntity();
    }

    /** 上游 {@code lightLevel(state -> glowing ? 10 : 0)}。 */
    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return glowing ? 10 : 0;
    }

    // ------------------------------------------------------------------------------------------
    // 放置
    // ------------------------------------------------------------------------------------------

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float subX, float subY, float subZ,
        int metadata) {
        this.pendingPlaceSide = side;
        return metadata;
    }

    /** 上游 {@code getStateForPlacement(...)}（GunPegboardBlock.java:83-105）。 */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        int side = this.pendingPlaceSide;
        this.pendingPlaceSide = -1;
        if (world.isRemote) {
            return;
        }
        ForgeDirection clickedFace = ForgeDirection.getOrientation(side < 0 ? 1 : side);
        int targetX = x - clickedFace.offsetX;
        int targetY = y - clickedFace.offsetY;
        int targetZ = z - clickedFace.offsetZ;
        Block targetBlock = world.getBlock(targetX, targetY, targetZ);
        int targetMetadata = world.getBlockMetadata(targetX, targetY, targetZ);

        int metadata;
        if (targetBlock instanceof GunPegboardBlock) {
            ForgeDirection targetFacing = getFacing(targetMetadata);
            if (GunPegboardSlotHelper.hasCorner(targetMetadata)
                && clickedFace == getShape(targetMetadata).getCornerFacing(targetFacing)) {
                targetFacing = clickedFace;
            }
            ForgeDirection playerFacing = placer == null ? ForgeDirection.NORTH
                : BlockMetadata.horizontalDirection(placer);
            // 水平视线正对板面时延续朝向，沿板面看时按玩家朝向放置垂直板
            ForgeDirection placedFacing = sameAxis(playerFacing, targetFacing) ? targetFacing
                : playerFacing.getOpposite();
            metadata = withFacing(world.getBlockMetadata(x, y, z), placedFacing);
        } else if (clickedFace == ForgeDirection.UP || clickedFace == ForgeDirection.DOWN) {
            ForgeDirection facing = placer == null ? ForgeDirection.NORTH
                : BlockMetadata.horizontalDirection(placer)
                    .getOpposite();
            metadata = withFacing(world.getBlockMetadata(x, y, z), facing);
        } else {
            metadata = withFacing(world.getBlockMetadata(x, y, z), clickedFace);
        }
        metadata = withShape(metadata, getConnectedShape(world, x, y, z, metadata));
        world.setBlockMetadataWithNotify(x, y, z, metadata, 2);
    }

    /** 1.20.1 {@code Direction#getAxis()} 的水平等价判断。 */
    private static boolean sameAxis(ForgeDirection left, ForgeDirection right) {
        return axis(left) == axis(right);
    }

    /** 0 = Y 轴，1 = X 轴，2 = Z 轴（与 1.20.1 {@code Direction.Axis} 的枚举顺序一致）。 */
    private static int axis(ForgeDirection direction) {
        if (direction.offsetY != 0) {
            return 0;
        }
        return direction.offsetX != 0 ? 1 : 2;
    }

    // ------------------------------------------------------------------------------------------
    // 形状重算
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code getConnectedShape(...)}（GunPegboardBlock.java:107-123）。 */
    private static PegboardShape getConnectedShape(World world, int x, int y, int z, int metadata) {
        ForgeDirection facing = getFacing(metadata);
        int frontX = x + facing.offsetX;
        int frontZ = z + facing.offsetZ;
        Block frontBlock = world.getBlock(frontX, y, frontZ);
        if (frontBlock instanceof GunPegboardBlock) {
            ForgeDirection otherFacing = getFacing(world.getBlockMetadata(frontX, y, frontZ));
            // 折面位于其朝向的反侧；该边已有同朝向直板时不能转角
            if (otherFacing == BlockMetadata.counterClockwise(facing)) {
                ForgeDirection clockwise = BlockMetadata.clockwise(facing);
                if (!hasStraightConnection(world, x + clockwise.offsetX, y, z + clockwise.offsetZ, facing)) {
                    return PegboardShape.CORNER_LEFT;
                }
            }
            if (otherFacing == BlockMetadata.clockwise(facing)) {
                ForgeDirection counterClockwise = BlockMetadata.counterClockwise(facing);
                if (!hasStraightConnection(
                    world,
                    x + counterClockwise.offsetX,
                    y,
                    z + counterClockwise.offsetZ,
                    facing)) {
                    return PegboardShape.CORNER_RIGHT;
                }
            }
        }
        return PegboardShape.STRAIGHT;
    }

    /** 上游 {@code hasStraightConnection(...)}（GunPegboardBlock.java:125-128）。 */
    private static boolean hasStraightConnection(World world, int x, int y, int z, ForgeDirection facing) {
        Block neighbor = world.getBlock(x, y, z);
        return neighbor instanceof GunPegboardBlock && getFacing(world.getBlockMetadata(x, y, z)) == facing;
    }

    /**
     * 上游 {@code updateShape(...)}（GunPegboardBlock.java:130-133）：邻居变化时重算 SHAPE 位。
     */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        int updated = withShape(metadata, getConnectedShape(world, x, y, z, metadata));
        if (updated != metadata) {
            world.setBlockMetadataWithNotify(x, y, z, updated, 2);
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof GunPegboardBlockEntity pegboard) {
                // 上游靠 GunPegboardBlockEntity#setBlockState 钩子处理失效面的物品，1.7.10 改由方块显式通知
                pegboard.onBlockStateChanged(metadata, updated);
            }
        }
    }

    /** 上游 {@code rotate(BlockState, Rotation)}：只转朝向，折面形状由 {@code onNeighborBlockChange} 重算。 */
    @Override
    public boolean rotateBlock(World world, int x, int y, int z, ForgeDirection axis) {
        if (world.isRemote || (axis != ForgeDirection.UP && axis != ForgeDirection.DOWN)) {
            return false;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        ForgeDirection rotated = BlockMetadata.counterClockwise(getFacing(metadata));
        int updated = withFacing(metadata, rotated);
        // 朝向变了要重算折面形状，并把失效面上的物品交给方块实体处理
        updated = withShape(updated, getConnectedShape(world, x, y, z, updated));
        world.setBlockMetadataWithNotify(x, y, z, updated, 3);
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof GunPegboardBlockEntity pegboard) {
            pegboard.onBlockStateChanged(metadata, updated);
        }
        return true;
    }

    /** 上游 {@code Mirror} 的 1.7.10 等价物只有“有效性”声明：水平朝向都可旋转。 */
    @Override
    public ForgeDirection[] getValidRotations(World world, int x, int y, int z) {
        return new ForgeDirection[] { ForgeDirection.UP, ForgeDirection.DOWN };
    }

    // ------------------------------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code use(...)}（GunPegboardBlock.java:152-190）。 */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float subX,
        float subY, float subZ) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() instanceof GunPegboardItem) {
            return false;
        }
        if (player.isSneaking()) {
            return false;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        int slot = GunPegboardSlotHelper.getSlotFromRelative(metadata, subX, subY, subZ);
        if (slot < 0) {
            return false;
        }
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof GunPegboardBlockEntity pegboard)) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        ItemStack stored = pegboard.getItem(slot);
        if (stored == null) {
            if (held != null && GunPegboardItemConfigRead.canPlace(held) && pegboard.setItem(slot, held)) {
                if (!player.capabilities.isCreativeMode) {
                    --held.stackSize;
                    if (held.stackSize <= 0) {
                        player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                    }
                }
                // 上游 SoundEvents.ITEM_FRAME_ADD_ITEM
                world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.pop", 1.0F, 1.0F);
                return true;
            }
            return false;
        }
        if (pegboard.rotateItem(slot)) {
            // 上游 SoundEvents.ITEM_FRAME_ROTATE_ITEM
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.click", 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // 形状
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code getPlateShape(Direction)}（GunPegboardBlock.java:199-207）。 */
    private static AxisAlignedBB plateShape(ForgeDirection facing) {
        switch (facing) {
            case SOUTH:
                return BlockMetadata.box(0, 0, 0, 16, 16, 3);
            case WEST:
                return BlockMetadata.box(13, 0, 0, 16, 16, 16);
            case EAST:
                return BlockMetadata.box(0, 0, 0, 3, 16, 16);
            case NORTH:
            default:
                return BlockMetadata.box(0, 0, 13, 16, 16, 16);
        }
    }

    /** 上游 {@code getShape(...)}（GunPegboardBlock.java:192-197）：直板 1 个盒，折面 2 个盒。 */
    private static AxisAlignedBB[] shapeBoxes(int metadata) {
        AxisAlignedBB plate = plateShape(getFacing(metadata));
        if (getShape(metadata) == PegboardShape.STRAIGHT) {
            return new AxisAlignedBB[] { plate };
        }
        return new AxisAlignedBB[] { plate, plateShape(getShape(metadata).getCornerFacing(getFacing(metadata))) };
    }

    /** 上游 {@code getCollisionShape(...)}（GunPegboardBlock.java:209-212）：与选取箱相同。 */
    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        AxisAlignedBB union = BlockMetadata.union(shapeBoxes(world.getBlockMetadata(x, y, z)));
        this.setBlockBounds(
            (float) union.minX,
            (float) union.minY,
            (float) union.minZ,
            (float) union.maxX,
            (float) union.maxY,
            (float) union.maxZ);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        AxisAlignedBB union = BlockMetadata.union(shapeBoxes(world.getBlockMetadata(x, y, z)));
        return BlockMetadata.at(union, x, y, z);
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List<AxisAlignedBB> list,
        Entity collider) {
        for (AxisAlignedBB local : shapeBoxes(world.getBlockMetadata(x, y, z))) {
            AxisAlignedBB box = BlockMetadata.at(local, x, y, z);
            if (mask == null || mask.intersectsWith(box)) {
                list.add(box);
            }
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        AxisAlignedBB union = BlockMetadata.union(shapeBoxes(world.getBlockMetadata(x, y, z)));
        return BlockMetadata.at(union, x, y, z);
    }

    // ------------------------------------------------------------------------------------------
    // 掉落
    // ------------------------------------------------------------------------------------------

    /**
     * 上游 {@code getDrops(...)}（GunPegboardBlock.java:230-241）与 {@code getCloneItemStack(...)}
     * （GunPegboardBlock.java:243-250）：掉落物带上方块实体的染色（{@code display.color}）。
     *
     * <p>
     * 1.7.10 差异：{@code getDrops} 运行时方块实体已被移除，所以掉落改在 {@link #breakBlock} 里做，
     * {@code getDrops} 返回空表；颜色直接写 {@code display.color} NBT（键与
     * {@code item/GunPegboardItem} 一致）。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        boolean suppressed = this.suppressNextDrop;
        this.suppressNextDrop = false;
        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            int color = -1;
            boolean inherited = false;
            if (tileEntity instanceof GunPegboardBlockEntity pegboard) {
                color = pegboard.getColor();
                inherited = pegboard.hasInheritedColor();
                // 上游 onRemove -> dropAllItems()（挂着的枪/配件掉出来）
                pegboard.dropAllItems();
            }
            if (!suppressed) {
                ItemStack drop = new ItemStack(Item.getItemFromBlock(this));
                if (color >= 0 && !inherited) {
                    NBTTagCompound tag = new NBTTagCompound();
                    NBTTagCompound display = new NBTTagCompound();
                    display.setInteger("color", color);
                    tag.setTag("display", display);
                    drop.setTagCompound(tag);
                }
                dropBlockAsItem(world, x, y, z, drop);
            }
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        return new ArrayList<>();
    }

    /** 上游 1.20 创造模式破坏不产生掉落；1.7.10 的掉落走 {@code breakBlock}。 */
    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int metadata, EntityPlayer player) {
        if (player != null && player.capabilities.isCreativeMode) {
            this.suppressNextDrop = true;
        }
    }

    /** 上游 {@code getCloneItemStack(...)}（GunPegboardBlock.java:243-250）。 */
    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z, EntityPlayer player) {
        ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof GunPegboardBlockEntity pegboard && pegboard.getColor() >= 0) {
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagCompound display = new NBTTagCompound();
            display.setInteger("color", pegboard.getColor());
            tag.setTag("display", display);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    // ------------------------------------------------------------------------------------------
    // 渲染 / 活塞
    // ------------------------------------------------------------------------------------------

    /**
     * 上游 {@code RenderShape.MODEL}（{@code GunPegboardBlock.java:226-229}）：模型由
     * {@code blockstates/gun_pegboard.json} / {@code glow_gun_pegboard.json}（按 facing+dyed+shape 选
     * 直板/折面模型）描述，1.7.10 的等价物是
     * GTNHLib JSON 模型系统（{@code ModelISBRH}），blockstate 属性注册见 {@link TaczBlockStateProperties}。
     *
     * <p>
     * 不能用 0（原版立方体）：那会把整张 64×64 贴图铺满每个面，枪架会变成一个实心方块。
     */
    @Override
    public int getRenderType() {
        return BlockRenderIds.jsonModel();
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

    /** 上游 {@code PushReaction.DESTROY}。 */
    @Override
    public int getMobilityFlag() {
        return 1;
    }

    /** 1.7.10 便捷：把 {@code onBlockActivated} 的 subX/Y/Z 还原成命中点绝对坐标。 */
    public static Vec3 hitVector(int x, int y, int z, float subX, float subY, float subZ) {
        return Vec3.createVectorHelper(x + subX, y + subY, z + subZ);
    }
}
