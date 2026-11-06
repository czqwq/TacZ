package com.tacz.guns.block;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械展示柜：4x4 网格挂枪，可多格连成一面墙（连在一起时锁定/解锁一起切换）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/GunDisplayCaseBlock.java}
 *
 * <h3>1.20.1 BlockState 属性 -> 1.7.10 metadata 编码</h3>
 * 
 * <pre>
 * 属性（1.20.1）                      位置     取值
 * FACING（HORIZONTAL_FACING）         meta 0-1 0=NORTH 1=SOUTH 2=WEST 3=EAST
 * LOCKED                              meta 2   0=开 1=锁定（默认锁定，与上游 defaultState 一致）
 * LEFT / RIGHT / UP / DOWN            不进 metadata，按需从邻居现算（见下）
 * （移植新增）ISOLATED                meta 3   1=放置时潜行，不参与连接/锁定联动
 * </pre>
 * 
 * 1.7.10 的方块 metadata 只有 <b>4 bit</b>（{@code NibbleArray#set} 会掩掉高位，
 * {@code build/rfg/minecraft-src/java/net/minecraft/world/chunk/NibbleArray.java:44-58}），
 * FACING(2)+LOCKED(1)+四个连接标记 = 7 bit 放不下，因此四个连接标记改成
 * <b>由邻居现算</b>（{@link #isConnected(IBlockAccess, int, int, int, ForgeDirection)}）：
 * 上游它们本就是「相邻方块是同朝向展示柜」的缓存，{@code updateShape} 只负责在邻居变化时清位、
 * {@code onPlace}/放置时补位，故现算与缓存值等价（同朝向下互为邻居的关系天然对称，
 * 上游 {@code shouldAutoConnectTo} 里「邻居也认我」的互相确认条件因此恒成立）。
 * 连接标记只影响锁定联动与自动连接，几何仍只由 FACING 决定（见 {@code GunDisplayCaseSlotHelper}）。
 *
 * <p>
 * 唯一的例外是上游 {@code context.isSecondaryUseActive()}（潜行放置）：那种情况下上游既不给自己补标记、
 * 也不给邻居补标记，于是新柜子<b>永远孤立</b>（之后邻居放置时 {@code shouldAutoConnectTo} 也因对侧标记为 false
 * 而拒绝连接）。用一个 {@code ISOLATED} 位复刻这个「永久孤立」语义。
 *
 * <h3>形状（VoxelShape -> 多 AABB 近似）</h3>
 * 上游用 5 个 {@code Block.box} 拼出碰撞箱（背板 + 四边框），1.7.10 用 {@link #addCollisionBoxesToList}
 * 逐盒判定、{@link #getCollisionBoundingBoxFromPool} 返回外包盒；数值逐位沿用上游（1/16 单位，见
 * {@link BlockMetadata#box}）。锁定时是一块厚板；打开时选取箱只剩背板，碰撞箱是“背板 + 四边框”，
 * 与上游 {@code getShape}/{@code getCollisionShape} 的分工一致。
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code getStateForPlacement(BlockPlaceContext)} 没有对应钩子：放置面（{@code side}）在
 * {@link #onBlockPlaced} 里记下（{@link #pendingPlaceSide}），朝向在 {@link #onBlockPlacedBy} 里用
 * 放置者朝向 + 点击到的柜子朝向计算（与原版 {@code BlockBed} 同一套做法）。</li>
 * <li>{@code updateShape} / {@code onPlace} 的「清/补连接标记」不再需要（见上，标记按需现算），
 * 因此本类不重写 {@code onNeighborBlockChange}。</li>
 * <li>{@code onRemove} -> {@link #breakBlock}（1.7.10 的 {@code breakBlock} 里还能读到方块实体）。</li>
 * <li>{@code Player#getAbilities().instabuild} -> {@code player.capabilities.isCreativeMode}。</li>
 * <li>音效：{@code ITEM_FRAME_ADD_ITEM}/{@code ITEM_FRAME_ROTATE_ITEM}/{@code IRON_DOOR_OPEN} 在 1.7.10
 * 没有同名资源，改用 {@code random.pop}/{@code random.click}/{@code random.door_open}（见各调用点）。</li>
 * </ul>
 */
public class GunDisplayCaseBlock extends Block {

    /** 上游 {@code LOCKED} 占用的 metadata 位（LEFT/RIGHT/UP/DOWN 见类注释）。 */
    private static final int LOCKED_BIT = 2;
    /** 移植新增：潜行放置的孤立柜子（上游 {@code isSecondaryUseActive()} 放置路径，见类注释）。 */
    private static final int ISOLATED_BIT = 3;

    private static final int MAX_GROUP_TOGGLE = 512;
    /** 上游 {@code LOCK_UPDATE_FLAGS}（{@code UPDATE_KNOWN_SHAPE | UPDATE_CLIENTS}）-> 1.7.10 只需发客户端。 */
    private static final int LOCK_UPDATE_FLAGS = 2;

    /** 放置时 {@code onBlockPlaced} 记下的点击面，供 {@code onBlockPlacedBy} 使用（移植 glue，见类注释）。 */
    private int pendingPlaceSide = -1;

    public GunDisplayCaseBlock() {
        super(Material.glass);
        this.setStepSound(Block.soundTypeGlass);
        this.setHardness(0.5F);
        this.setResistance(1200.0F);
        this.setBlockName("gun_display_case");
        this.setBlockTextureName("tacz:block/display_case_all");
    }

    /**
     * 1.7.10 方块必须有 IIcon（1.20.1 走 JSON block model）：{@code Block#registerBlockIcons} 默认空实现，
     * 不注册时 {@code blockIcon} 恒为 null → TextureMap 会产出 MISSING_ICON 缺失贴图。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.blockIcon = register.registerIcon("tacz:display_case_all");
    }

    public static ForgeDirection getFacing(int metadata) {
        return BlockMetadata.facing(metadata);
    }

    public static int withFacing(int metadata, ForgeDirection facing) {
        return BlockMetadata.withFacing(metadata, facing);
    }

    public static boolean isLocked(int metadata) {
        return BlockMetadata.flag(metadata, LOCKED_BIT);
    }

    public static int withLocked(int metadata, boolean locked) {
        return BlockMetadata.withFlag(metadata, LOCKED_BIT, locked);
    }

    /**
     * 1.20.1 {@code getValue(LEFT/RIGHT/UP/DOWN)} 的统一入口：1.7.10 按需现算
     * （= 该方向上的邻居是同朝向展示柜，见类注释）。
     *
     * <p>
     * 只有 LEFT/RIGHT/UP/DOWN 四个方向有意义（水平朝向的对侧与 UP/DOWN），其余方向恒为 false，
     * 与上游 {@code propertyForDirection(...) == null} 时返回 false 一致。
     */
    public static boolean isConnected(IBlockAccess world, int x, int y, int z, ForgeDirection direction) {
        int metadata = world.getBlockMetadata(x, y, z);
        ForgeDirection facing = getFacing(metadata);
        if (direction != ForgeDirection.UP && direction != ForgeDirection.DOWN
            && direction != leftOf(facing)
            && direction != rightOf(facing)) {
            return false;
        }
        if (isIsolated(metadata)) {
            return false;
        }
        int nx = x + direction.offsetX;
        int ny = y + direction.offsetY;
        int nz = z + direction.offsetZ;
        int neighborMetadata = world.getBlockMetadata(nx, ny, nz);
        return !isIsolated(neighborMetadata) && connectsTo(metadata, world.getBlock(nx, ny, nz), neighborMetadata);
    }

    /** 上游 {@code context.isSecondaryUseActive()} 放置的孤立柜子（见类注释）。 */
    public static boolean isIsolated(int metadata) {
        return BlockMetadata.flag(metadata, ISOLATED_BIT);
    }

    /** 上游 {@code leftOf(facing)} = {@code getCounterClockWise()}。 */
    private static ForgeDirection leftOf(ForgeDirection facing) {
        return BlockMetadata.counterClockwise(facing);
    }

    /** 上游 {@code rightOf(facing)} = {@code getClockWise()}。 */
    private static ForgeDirection rightOf(ForgeDirection facing) {
        return BlockMetadata.clockwise(facing);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new GunDisplayCaseBlockEntity();
    }

    /** 记下点击面（1.7.10 只有这个钩子能拿到 {@code side}）。 */
    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float subX, float subY, float subZ,
        int metadata) {
        this.pendingPlaceSide = side;
        return metadata;
    }

    /**
     * 上游 {@code getStateForPlacement(...)}（GunDisplayCaseBlock.java:109-125）+ {@code onPlace(...)}
     * （GunDisplayCaseBlock.java:136-142）：只算朝向；连接标记按需现算，无需在此补位/清位。
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        int side = this.pendingPlaceSide;
        this.pendingPlaceSide = -1;
        if (world.isRemote) {
            return;
        }
        ForgeDirection clickedFace = ForgeDirection.getOrientation(side < 0 ? 1 : side);
        int clickedX = x - clickedFace.offsetX;
        int clickedY = y - clickedFace.offsetY;
        int clickedZ = z - clickedFace.offsetZ;
        Block clickedBlock = world.getBlock(clickedX, clickedY, clickedZ);
        int clickedMetadata = world.getBlockMetadata(clickedX, clickedY, clickedZ);
        boolean clickedDisplayCase = clickedBlock instanceof GunDisplayCaseBlock;

        ForgeDirection facing;
        if (clickedDisplayCase) {
            facing = getFacing(clickedMetadata);
        } else if (clickedFace == ForgeDirection.UP || clickedFace == ForgeDirection.DOWN) {
            // 1.20: context.getHorizontalDirection().getOpposite()；没有放置者时退回 NORTH
            facing = placer == null ? ForgeDirection.NORTH
                : BlockMetadata.horizontalDirection(placer)
                    .getOpposite();
        } else {
            facing = clickedFace;
        }
        // 上游 context.isSecondaryUseActive()（潜行放置）：不补连接标记 ⇒ 该柜子永久孤立。
        // 连接标记现算后用一个 ISOLATED 位复刻，见类注释。
        boolean isolated = placer instanceof EntityPlayer player && player.isSneaking();
        int metadata = withFacing(world.getBlockMetadata(x, y, z), facing);
        if (isolated) {
            metadata = BlockMetadata.withFlag(metadata, ISOLATED_BIT, true);
        }
        world.setBlockMetadataWithNotify(x, y, z, metadata, 2);
    }

    // 上游 updateConnections / shouldAutoConnectTo / collectConnectedCases / syncConnectedNeighbors
    // （GunDisplayCaseBlock.java:245-316）都是在维护 LEFT/RIGHT/UP/DOWN 这层缓存；1.7.10 改为
    // isConnected(...) 现算后不再需要，见类注释的 metadata 编码一节。

    /** 上游 {@code connectsTo(state, neighbor)}（GunDisplayCaseBlock.java:318-320）。 */
    private static boolean connectsTo(int metadata, Block neighborBlock, int neighborMetadata) {
        return neighborBlock instanceof GunDisplayCaseBlock && getFacing(neighborMetadata) == getFacing(metadata);
    }

    /** 上游 {@code queueConnectedCases(...)}（GunDisplayCaseBlock.java:379-392）：四个方向上的连接邻居入队。 */
    private static void queueConnectedCases(World world, ArrayDeque<ChunkCoordinates> queue, ChunkCoordinates pos) {
        ForgeDirection facing = getFacing(world.getBlockMetadata(pos.posX, pos.posY, pos.posZ));
        ForgeDirection[] directions = { leftOf(facing), rightOf(facing), ForgeDirection.UP, ForgeDirection.DOWN };
        for (ForgeDirection direction : directions) {
            if (isConnected(world, pos.posX, pos.posY, pos.posZ, direction)) {
                queue.add(shift(pos, direction));
            }
        }
    }

    private static ChunkCoordinates shift(ChunkCoordinates pos, ForgeDirection direction) {
        return new ChunkCoordinates(
            pos.posX + direction.offsetX,
            pos.posY + direction.offsetY,
            pos.posZ + direction.offsetZ);
    }

    /**
     * 上游 {@code use(...)}（GunDisplayCaseBlock.java:145-188）。
     *
     * <p>
     * 1.7.10 的命中点由 {@code onBlockActivated} 的 {@code subX/subY/subZ}（方块内 0..1 偏移）还原，
     * 不再需要 {@code BlockHitResult}。
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float subX,
        float subY, float subZ) {
        ItemStack held = player.getCurrentEquippedItem();
        boolean holdingGun = held != null && held.getItem() instanceof IGun;
        int metadata = world.getBlockMetadata(x, y, z);
        if (player.isSneaking()) {
            if (holdingGun) {
                return false;
            }
            toggleLockFromInteraction(world, x, y, z);
            return true;
        }
        if (isLocked(metadata)) {
            return held == null || holdingGun;
        }
        int slot = GunDisplayCaseSlotHelper.getSlotFromRelative(metadata, subX, subY, subZ);
        if (slot < 0) {
            return false;
        }
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof GunDisplayCaseBlockEntity displayCase)) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        ItemStack stored = displayCase.getItem(slot);
        if (stored == null) {
            if (held != null && GunPegboardItemConfigRead.canPlace(held) && displayCase.setItem(slot, held)) {
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
            return held == null;
        }
        if (displayCase.rotateItem(slot)) {
            // 上游 SoundEvents.ITEM_FRAME_ROTATE_ITEM
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.click", 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    /** 上游 {@code toggleLockFromInteraction(...)}（GunDisplayCaseBlock.java:346-352）。 */
    public void toggleLockFromInteraction(World world, int x, int y, int z) {
        if (world.isRemote) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        toggleLinkedCases(world, x, y, z, !isLocked(metadata));
        // 上游 SoundEvents.IRON_DOOR_OPEN
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.door_open", 0.8F, isLocked(metadata) ? 1.1F : 0.9F);
    }

    /** 上游 {@code toggleLinkedCases(...)}（GunDisplayCaseBlock.java:354-377）。 */
    private void toggleLinkedCases(World world, int startX, int startY, int startZ, boolean locked) {
        if (!(world.getBlock(startX, startY, startZ) instanceof GunDisplayCaseBlock)) {
            return;
        }
        int startMetadata = world.getBlockMetadata(startX, startY, startZ);
        ForgeDirection facing = getFacing(startMetadata);
        ArrayDeque<ChunkCoordinates> queue = new ArrayDeque<>();
        Set<ChunkCoordinates> visited = new HashSet<>();
        queue.add(new ChunkCoordinates(startX, startY, startZ));
        while (!queue.isEmpty() && visited.size() < MAX_GROUP_TOGGLE) {
            ChunkCoordinates pos = queue.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }
            Block block = world.getBlock(pos.posX, pos.posY, pos.posZ);
            int metadata = world.getBlockMetadata(pos.posX, pos.posY, pos.posZ);
            if (!(block instanceof GunDisplayCaseBlock) || getFacing(metadata) != facing) {
                continue;
            }
            if (isLocked(metadata) != locked) {
                world.setBlockMetadataWithNotify(
                    pos.posX,
                    pos.posY,
                    pos.posZ,
                    withLocked(metadata, locked),
                    LOCK_UPDATE_FLAGS);
            }
            queueConnectedCases(world, queue, pos);
        }
    }

    /** 锁定时是一块厚板（上游 {@code *_LOCKED_SHAPE}，GunDisplayCaseBlock.java:50-53）。 */
    private static AxisAlignedBB lockedShape(ForgeDirection facing) {
        switch (facing) {
            case SOUTH:
                return BlockMetadata.box(0, 0, 0, 16, 16, 12);
            case WEST:
                return BlockMetadata.box(4, 0, 0, 16, 16, 16);
            case EAST:
                return BlockMetadata.box(0, 0, 0, 12, 16, 16);
            case NORTH:
            default:
                return BlockMetadata.box(0, 0, 4, 16, 16, 16);
        }
    }

    /** 打开时的背板（上游 {@code *_BACK_PANEL_SHAPE}，GunDisplayCaseBlock.java:54-57）。 */
    private static AxisAlignedBB backPanelShape(ForgeDirection facing) {
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

    /**
     * 打开时的碰撞箱：背板 + 四边框（上游 {@code *_OPEN_SHAPE}，GunDisplayCaseBlock.java:58-85，
     * 用 {@code Shapes.or} 组合 5 个盒子，这里同样返回 5 个盒子）。
     */
    private static AxisAlignedBB[] openShape(ForgeDirection facing) {
        switch (facing) {
            case SOUTH:
                return new AxisAlignedBB[] { BlockMetadata.box(0, 0, 0, 16, 16, 3),
                    BlockMetadata.box(0, 0, 0, 1, 16, 12), BlockMetadata.box(15, 0, 0, 16, 16, 12),
                    BlockMetadata.box(0, 0, 0, 16, 1, 12), BlockMetadata.box(0, 15, 0, 16, 16, 12) };
            case WEST:
                return new AxisAlignedBB[] { BlockMetadata.box(13, 0, 0, 16, 16, 16),
                    BlockMetadata.box(4, 0, 0, 16, 16, 1), BlockMetadata.box(4, 0, 15, 16, 16, 16),
                    BlockMetadata.box(4, 0, 0, 16, 1, 16), BlockMetadata.box(4, 15, 0, 16, 16, 16) };
            case EAST:
                return new AxisAlignedBB[] { BlockMetadata.box(0, 0, 0, 3, 16, 16),
                    BlockMetadata.box(0, 0, 0, 12, 16, 1), BlockMetadata.box(0, 0, 15, 12, 16, 16),
                    BlockMetadata.box(0, 0, 0, 12, 1, 16), BlockMetadata.box(0, 15, 0, 12, 16, 16) };
            case NORTH:
            default:
                return new AxisAlignedBB[] { BlockMetadata.box(0, 0, 13, 16, 16, 16),
                    BlockMetadata.box(0, 0, 4, 1, 16, 16), BlockMetadata.box(15, 0, 4, 16, 16, 16),
                    BlockMetadata.box(0, 0, 4, 16, 1, 16), BlockMetadata.box(0, 15, 4, 16, 16, 16) };
        }
    }

    /** 上游 {@code getShape(...)}（GunDisplayCaseBlock.java:190-208）。 */
    private static AxisAlignedBB[] shapeBoxes(int metadata) {
        return new AxisAlignedBB[] {
            isLocked(metadata) ? lockedShape(getFacing(metadata)) : backPanelShape(getFacing(metadata)) };
    }

    /** 上游 {@code getCollisionShape(...)}（GunDisplayCaseBlock.java:210-222）。 */
    private static AxisAlignedBB[] collisionBoxes(int metadata) {
        if (isLocked(metadata)) {
            return shapeBoxes(metadata);
        }
        return openShape(getFacing(metadata));
    }

    /**
     * 把方块包围盒设成选取箱（外包盒），供 1.7.10 的默认碰撞/高亮逻辑使用；真正的细化碰撞走
     * {@link #addCollisionBoxesToList}。
     */
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

    /** 上游 {@code getShape(...)}：选取箱（外包盒）。 */
    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        AxisAlignedBB union = BlockMetadata.union(shapeBoxes(world.getBlockMetadata(x, y, z)));
        return BlockMetadata.at(union, x, y, z);
    }

    /** 上游 {@code getCollisionShape(...)} 的 1.7.10 形态：逐个 AABB 与掩码求交。 */
    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List<AxisAlignedBB> list,
        Entity collider) {
        for (AxisAlignedBB local : collisionBoxes(world.getBlockMetadata(x, y, z))) {
            AxisAlignedBB box = BlockMetadata.at(local, x, y, z);
            if (mask == null || mask.intersectsWith(box)) {
                list.add(box);
            }
        }
    }

    /** 单个外包盒版本（供 {@code getCollisionBoundingBoxFromPool} 等使用）。 */
    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        AxisAlignedBB union = BlockMetadata.union(collisionBoxes(world.getBlockMetadata(x, y, z)));
        return BlockMetadata.at(union, x, y, z);
    }

    /** 上游 {@code onRemove(...)}（GunDisplayCaseBlock.java:224-233）：掉落柜内物品。 */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        // 1.7.10 的 breakBlock 在“已换成新方块”之后调用，用当前方块判断是否被替换
        // （对应上游 oldState.is(newState.getBlock())）
        if (world.getBlock(x, y, z) != this) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof GunDisplayCaseBlockEntity displayCase) {
                displayCase.dropAllItems();
            }
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    /** 上游 {@code RenderShape.MODEL}。 */
    @Override
    public int getRenderType() {
        return 0;
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
