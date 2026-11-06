package com.tacz.guns.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.api.BlockModelInfo;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.advancement.TargetHitTrigger;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.config.common.OtherConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 标靶方块：1x2x1，被打中会“倒下”（STAND=false）并在 5 秒后复位，同时输出红石信号（强度按命中点离靶心远近）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/TargetBlock.java}
 *
 * <h3>1.20.1 BlockState 属性 -> 1.7.10 metadata 编码</h3>
 * 
 * <pre>
 * 属性（1.20.1）              位置      取值
 * FACING（HORIZONTAL_FACING） meta 0-1  0=SOUTH 1=WEST 2=NORTH 3=EAST
 * HALF（DoubleBlockHalf）     meta 2    0=LOWER 1=UPPER
 * STAND                       meta 3    0=倒下 1=立着
 * OUTPUT_POWER（0-15）        不是在 metadata 里，见下
 * </pre>
 * 
 * <b>OUTPUT_POWER 降级</b>：facing(2bit)+half(1)+stand(1) 已占满 4 bit，1.7.10 的 metadata 放不下
 * 4 bit 的红石强度，因此改存到方块实体 {@link TargetBlockEntity#getOutputPower()}（随方块实体 NBT 存档），
 * 供 {@link #isProvidingWeakPower} 与渲染读取。这是本包最主要的一处“能力降级”，语义等价、存储位置不同。
 *
 * <h3>形状（VoxelShape -> 多 AABB 近似）</h3>
 * 上游 5 个 {@code Block.box} 常量（TargetBlock.java:45-49）逐个换算为 AABB；立柱 + 靶面是两个盒子，
 * {@link #addCollisionBoxesToList} 返回它们，{@link #getCollisionBoundingBoxFromPool} 返回外包盒。
 * 上半块且倒下（{@code !STAND}）时上游返回 {@code Shapes.empty()}，这里返回空数组 / null。
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code onProjectileHit(Level, BlockState, BlockHitResult, Projectile)} 没有对应钩子（1.7.10 没有
 * {@code Block#onProjectileHit}，TACZ 的子弹实体也不是原版 {@code Projectile}）：改由
 * {@link #onBulletHit(World, MovingObjectPosition, EntityPlayer, float)} 显式调用，
 * 由 {@code entity/EntityKineticBullet} 在命中方块时转调（见交付说明 D 节）。</li>
 * <li>{@code scheduleTick} -> {@link #updateTick} + {@code World#scheduleBlockUpdate}。</li>
 * <li>{@code getSignal} -> {@link #isProvidingWeakPower}；红石强度取自方块实体（见上）。</li>
 * <li>{@code canSurvive} -> {@link #canBlockStay}。</li>
 * <li>{@code Component.translatable} -> {@link ChatComponentTranslation}；{@code displayClientMessage(..., true)}
 * 的动作栏在 1.7.10 无对应，降级为聊天栏。</li>
 * <li>{@code SkullBlockEntity.updateGameprofile(...)}（1.20 异步填充皮肤）在 1.7.10 无等价物，
 * 只保存名字、不填皮肤（TODO(port L2)）。</li>
 * <li>{@code getTicker} 的客户端动画 tick 由 {@code TargetBlockEntity#updateEntity()} 执行。</li>
 * <li>{@code PushReaction.DESTROY} -> {@link #getMobilityFlag()}。</li>
 * </ul>
 */
public class TargetBlock extends Block implements BlockModelInfo {

    /** 上游 {@code HALF} / {@code STAND} 占用的 metadata 位。 */
    private static final int HALF_UPPER_BIT = 2;
    private static final int STAND_BIT = 3;

    /** 上游 {@code RESET_TIME}：5 秒复位（在方块实体里）。 */
    private static final int RESET_TIME = 5 * 20;

    /**
     * 一次性掉落抑制位：创造模式破坏、以及「另一半被拆」的连锁移除（见 {@link #breakBlock}）。
     * 服务端单线程使用、读后即清（与同包 {@code AbstractGunSmithTableBlock} 同一套约定）。
     */
    private boolean suppressNextDrop;

    public TargetBlock() {
        super(Material.wood);
        this.setStepSound(Block.soundTypeWood);
        this.setHardness(2.0F);
        this.setResistance(3.0F);
        this.setBlockName("target");
        this.setBlockTextureName("tacz:block/target");
    }

    /**
     * 1.7.10 方块必须有 IIcon（1.20.1 走 JSON block model）：{@code Block#registerBlockIcons} 默认空实现，
     * 不注册时 {@code blockIcon} 恒为 null → TextureMap 会产出 MISSING_ICON 缺失贴图。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.blockIcon = register.registerIcon("tacz:target");
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

    public static boolean isUpper(int metadata) {
        return BlockMetadata.flag(metadata, HALF_UPPER_BIT);
    }

    public static int withUpper(int metadata, boolean upper) {
        return BlockMetadata.withFlag(metadata, HALF_UPPER_BIT, upper);
    }

    public static boolean isStand(int metadata) {
        return BlockMetadata.flag(metadata, STAND_BIT);
    }

    public static int withStand(int metadata, boolean stand) {
        return BlockMetadata.withFlag(metadata, STAND_BIT, stand);
    }

    // ------------------------------------------------------------------------------------------
    // 红石强度
    // ------------------------------------------------------------------------------------------

    /**
     * 上游 {@code getRedstoneStrength(BlockHitResult, boolean)}（TargetBlock.java:56-79）。
     *
     * <p>
     * 1.7.10 用 {@code side}（{@code MovingObjectPosition#sideHit}）+ 命中点绝对坐标表达
     * {@code BlockHitResult}。命中下方恒为 1。
     */
    public static int getRedstoneStrength(int side, double hitX, double hitY, double hitZ, boolean isUpperBlock) {
        if (!isUpperBlock) {
            return 1;
        }
        // 标靶中心为 (0.5, 0.32, 0.5)
        double x = Math.abs(MathHelper.clamp_double(hitX - MathHelper.floor_double(hitX), 0.0D, 1.0D) - 0.5D);
        double y = Math.abs(MathHelper.clamp_double(hitY - MathHelper.floor_double(hitY), 0.0D, 1.0D) - 0.32D);
        double z = Math.abs(MathHelper.clamp_double(hitZ - MathHelper.floor_double(hitZ), 0.0D, 1.0D) - 0.5D);
        int axis = sideAxis(side);
        double distance;
        if (axis == 0) {
            distance = Math.max(x, z);
        } else if (axis == 2) {
            distance = Math.max(x, y);
        } else {
            distance = Math.max(y, z);
        }
        // 离开中心 0.25 单位就是最低分
        double percent = MathHelper.clamp_double((0.25D - distance) / 0.25D, 0.0D, 1.0D);
        return Math.max(1, MathHelper.ceiling_double_int(15 * percent));
    }

    /** 1.20.1 {@code Direction#getAxis()} 的 1.7.10 等价：0=Y、1=X、2=Z（与枚举顺序一致）。 */
    private static int sideAxis(int side) {
        ForgeDirection direction = ForgeDirection.getOrientation(side);
        if (direction.offsetY != 0) {
            return 0;
        }
        return direction.offsetX != 0 ? 1 : 2;
    }

    // ------------------------------------------------------------------------------------------
    // 方块实体
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code newBlockEntity} 只对 LOWER 返回实体（TargetBlock.java:92-100）。 */
    @Override
    public boolean hasTileEntity(int metadata) {
        return !isUpper(metadata);
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return isUpper(metadata) ? null : new TargetBlockEntity();
    }

    // ------------------------------------------------------------------------------------------
    // 放置 / 更新
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code getStateForPlacement(...)}（TargetBlock.java:169-179）+ {@code setPlacedBy(...)}（181-199）。 */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (world.isRemote || placer == null) {
            return;
        }
        if (!world.getBlock(x, y + 1, z)
            .isReplaceable(world, x, y + 1, z)) {
            world.setBlockToAir(x, y, z);
            return;
        }
        // 上游用 getHorizontalDirection()（不取反）
        int lowerMetadata = BlockMetadata
            .withFacing(world.getBlockMetadata(x, y, z), BlockMetadata.horizontalDirection(placer));
        lowerMetadata = withStand(lowerMetadata, true);
        world.setBlockMetadataWithNotify(x, y, z, lowerMetadata, 2);
        world.setBlock(x, y + 1, z, this, withUpper(lowerMetadata, true), 3);
        if (stack != null && stack.hasDisplayName()) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TargetBlockEntity target) {
                target.setOwner(new GameProfile(null, stack.getDisplayName()));
                target.setCustomName(stack.getDisplayName());
                target.refresh();
            }
        }
    }

    /** 上游 {@code updateShape(...)}（TargetBlock.java:143-167）。 */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        boolean upper = isUpper(metadata);
        boolean stand = isStand(metadata);
        int neighbourY = y + (upper ? -1 : 1);
        boolean paired = world.getBlock(x, neighbourY, z) == this
            && isUpper(world.getBlockMetadata(x, neighbourY, z)) != upper;
        if (!paired) {
            // 拆一半另外一半跟着没（上游返回 Blocks.AIR）：连带移除不产生掉落
            removeWithoutDrop(world, x, y, z);
            return;
        }
        // 同步击倒状态（上游 updateShape 的 STAND/OUTPUT_POWER 同步）
        int neighbourMetadata = world.getBlockMetadata(x, neighbourY, z);
        if (isStand(neighbourMetadata) != stand) {
            world.setBlockMetadataWithNotify(x, y, z, withStand(metadata, isStand(neighbourMetadata)), 2);
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            TileEntity neighbourTile = world.getTileEntity(x, neighbourY, z);
            if (!upper && tileEntity instanceof TargetBlockEntity target
                && neighbourTile instanceof TargetBlockEntity neighbourTarget) {
                target.setOutputPower(neighbourTarget.getOutputPower());
            }
        }
        // 底下方块没了也拆掉
        if (!upper && !canBlockStay(world, x, y, z)) {
            removeWithoutDrop(world, x, y, z);
        }
    }

    /** 连带移除（不产生掉落）：对应上游 {@code updateShape} 返回 {@code Blocks.AIR} 的情形。 */
    private void removeWithoutDrop(World world, int x, int y, int z) {
        this.suppressNextDrop = true;
        world.setBlockToAir(x, y, z);
        this.suppressNextDrop = false;
    }

    /** 上游 {@code canSurvive(...)}（TargetBlock.java:211-219）。 */
    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        int metadata = world.getBlockMetadata(x, y, z);
        if (!isUpper(metadata)) {
            return true;
        }
        return world.getBlock(x, y - 1, z) == this;
    }

    /** 上游 {@code tick(...)}（TargetBlock.java:112-118）：计划刻复位。 */
    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        if (world.isRemote) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        if (!isStand(metadata)) {
            world.setBlockMetadataWithNotify(x, y, z, withStand(metadata, true), 3);
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TargetBlockEntity target) {
                target.setOutputPower(0);
            }
            updateNeighbourPower(world, x, y, z);
        }
    }

    /**
     * 上游 {@code onProjectileHit(Level, BlockState, BlockHitResult, Projectile)}（TargetBlock.java:120-141）。
     *
     * <p>
     * 1.7.10 没有方块级的弹射物命中回调，由子弹实体在命中时调用本方法（见类注释与交付说明 D 节）。
     *
     * @param hit    子弹命中的 {@code MovingObjectPosition}（{@code blockX/Y/Z}、{@code sideHit}、{@code hitVec}）
     * @param owner  枪手（可为 null）
     * @param damage 子弹在命中点的伤害（用于提示文本，相当于上游 {@code bullet.getDamage(hit.getLocation())}）
     * @return 是否命中本标靶的有效靶面
     */
    public boolean onBulletHit(World world, MovingObjectPosition hit, EntityPlayer owner, float damage) {
        int x = hit.blockX;
        int y = hit.blockY;
        int z = hit.blockZ;
        int metadata = world.getBlockMetadata(x, y, z);
        ForgeDirection hitFace = ForgeDirection.getOrientation(hit.sideHit);
        if (hitFace.getOpposite() != BlockMetadata.facing(metadata)) {
            return false;
        }
        boolean upper = isUpper(metadata);
        int lowerY = upper ? y - 1 : y;
        TileEntity tileEntity = world.getTileEntity(x, lowerY, z);
        if (tileEntity instanceof TargetBlockEntity target) {
            target.hit(world, x, lowerY, z, hit, upper);
        }
        if (!world.isRemote && owner != null && isStand(metadata)) {
            String formattedDamage = String.format("%.1f", damage);
            double distance = owner.getDistance(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
            String formattedDistance = String.format("%.2f", distance);
            // 上游：displayClientMessage(Component, true)（动作栏）；1.7.10 无动作栏 -> 聊天栏
            owner.addChatComponentMessage(
                new ChatComponentTranslation("message.tacz.target_minecart.hit", formattedDamage, formattedDistance));
            if (owner instanceof EntityPlayerMP serverPlayer) {
                TargetHitTrigger.INSTANCE.trigger(serverPlayer, distance);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 红石输出
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code isSignalSource(BlockState)}。 */
    @Override
    public boolean canProvidePower() {
        return true;
    }

    /** 上游 {@code getSignal(state, ..., side)}：强度存在方块实体里。 */
    @Override
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int side) {
        int metadata = world.getBlockMetadata(x, y, z);
        int lowerY = isUpper(metadata) ? y - 1 : y;
        TileEntity tileEntity = world.getTileEntity(x, lowerY, z);
        if (tileEntity instanceof TargetBlockEntity target) {
            return target.getOutputPower();
        }
        return 0;
    }

    /** 上游 {@code onPlace(...)}（TargetBlock.java:231-238）：放置时清掉残留强度。 */
    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (world.isRemote) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        int lowerY = isUpper(metadata) ? y - 1 : y;
        TileEntity tileEntity = world.getTileEntity(x, lowerY, z);
        if (tileEntity instanceof TargetBlockEntity target && target.getOutputPower() > 0) {
            target.setOutputPower(0);
            updateNeighbourPower(world, x, y, z);
        }
    }

    /** 1.7.10 的 {@code level.updateNeighbourForOutputSignal} 等价物。 */
    public static void updateNeighbourPower(World world, int x, int y, int z) {
        world.func_147453_f(x, y, z, world.getBlock(x, y, z));
    }

    /** 上游 {@code getCloneItemStack(...)}（TargetBlock.java:201-209）：带自定义名称。 */
    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z, EntityPlayer player) {
        int metadata = world.getBlockMetadata(x, y, z);
        int lowerY = isUpper(metadata) ? y - 1 : y;
        TileEntity tileEntity = world.getTileEntity(x, lowerY, z);
        if (tileEntity instanceof TargetBlockEntity targetEntity && targetEntity.getCustomName() != null) {
            ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
            stack.setStackDisplayName(targetEntity.getCustomName());
            return stack;
        }
        return new ItemStack(Item.getItemFromBlock(this));
    }

    // ------------------------------------------------------------------------------------------
    // 掉落 / 形状 / 渲染
    // ------------------------------------------------------------------------------------------

    /**
     * 上游 loot 表 {@code target.json}（只有 {@code half=lower} 掉落，并复制自定义名称）与
     * {@code onRemove} 的 1.7.10 形态。
     *
     * <p>
     * 必须放在 {@code breakBlock} 里：1.7.10 的 `ItemInWorldManager#tryHarvestBlock` 先
     * {@code removeBlock}（触发 {@code breakBlock} 并清掉方块实体）再 {@code harvestBlock}
     * （{@code build/rfg/minecraft-src/java/net/minecraft/server/management/ItemInWorldManager.java:322-326}），
     * 原来写在 {@code getDrops} 里读 {@code TargetBlockEntity} 必然拿到 null，
     * 「自定义名称」「Owner」写入方块实体后永远回不到掉落物上。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        boolean suppressed = this.suppressNextDrop;
        this.suppressNextDrop = false;
        if (!suppressed && !world.isRemote && !isUpper(metadata)) {
            ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TargetBlockEntity target && target.getCustomName() != null) {
                stack.setStackDisplayName(target.getCustomName());
            }
            dropBlockAsItem(world, x, y, z, stack);
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

    /** 见 {@link #breakBlock}：掉落已在其中处理，这里返回空表避免重复掉落。 */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        return new ArrayList<>();
    }

    /** 上游 {@code getShape(...)}（TargetBlock.java:102-110）。 */
    private static AxisAlignedBB[] shapeBoxes(int metadata) {
        boolean stand = isStand(metadata);
        boolean xAxis = BlockMetadata.facing(metadata).offsetX != 0;
        if (isUpper(metadata)) {
            if (!stand) {
                return new AxisAlignedBB[0];
            }
            return xAxis ? new AxisAlignedBB[] { BlockMetadata.box(6, 0, 2, 10, 16, 14) }
                : new AxisAlignedBB[] { BlockMetadata.box(2, 0, 6, 14, 16, 10) };
        }
        if (stand) {
            return xAxis
                ? new AxisAlignedBB[] { BlockMetadata.box(6, 0, 6, 10, 16, 10),
                    BlockMetadata.box(6, 13, 2, 10, 16, 14) }
                : new AxisAlignedBB[] { BlockMetadata.box(6, 0, 6, 10, 16, 10),
                    BlockMetadata.box(2, 13, 6, 14, 16, 10) };
        }
        return new AxisAlignedBB[] { BlockMetadata.box(6, 0, 6, 10, 4, 10) };
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        AxisAlignedBB[] boxes = shapeBoxes(world.getBlockMetadata(x, y, z));
        if (boxes.length == 0) {
            this.setBlockBounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
            return;
        }
        AxisAlignedBB union = BlockMetadata.union(boxes);
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
        AxisAlignedBB[] boxes = shapeBoxes(world.getBlockMetadata(x, y, z));
        if (boxes.length == 0) {
            return AxisAlignedBB.getBoundingBox(x, y, z, x, y, z);
        }
        return BlockMetadata.at(BlockMetadata.union(boxes), x, y, z);
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
        AxisAlignedBB[] boxes = shapeBoxes(world.getBlockMetadata(x, y, z));
        if (boxes.length == 0) {
            return null;
        }
        return BlockMetadata.at(BlockMetadata.union(boxes), x, y, z);
    }

    /** 上游 {@code RenderShape.ENTITYBLOCK_ANIMATED}（由 TESR 渲染）。 */
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

    /** 上游 {@code PushReaction.DESTROY}。 */
    @Override
    public int getMobilityFlag() {
        return 1;
    }

    /** 复位延迟（方块实体命中后调用 {@code World#scheduleBlockUpdate} 用）。 */
    public static int getResetTime() {
        return RESET_TIME;
    }

    /** 1.7.10 便捷：把 {@code onBlockActivated}/{@code MovingObjectPosition} 换算成命中点。 */
    public static Vec3 hitVector(int x, int y, int z, float subX, float subY, float subZ) {
        return Vec3.createVectorHelper(x + subX, y + subY, z + subZ);
    }

    /** 伤害/距离提示用的音效名（上游 {@code ModSounds.TARGET_HIT}，1.7.10 用字符串音效名）。 */
    public static final String TARGET_HIT_SOUND = "tacz:target_block_hit";

    /** 上游 {@code OtherConfig.TARGET_SOUND_DISTANCE} 的音量换算（16 格 -> 1.0）。 */
    public static float getTargetSoundVolume() {
        Float configured = OtherConfig.TARGET_SOUND_DISTANCE.get()
            .floatValue();
        return Math.max(configured / 16.0F, 0.0F);
    }

    /**
     * GTNHLib JSON 模型系统的 <b>opt-out</b>（1.7.10 平台适配）。
     *
     * <p>
     * 上游 1.20 里本方块是 {@code RenderShape.ENTITYBLOCK_ANIMATED}（{@code -1} + BER），
     * 1.7.10 同样用 {@code TileEntitySpecialRenderer} 渲染。但 GTNHLib 的模型扫描器会按
     * {@code assets/tacz/blockstates/<name>.json} 是否存在把方块标记为「有 JSON 模型」，
     * 进而覆盖其物品渲染器/方块图标（本方块在 1.7.10 的 blockstate JSON 只是上游遗留资产，
     * 模型 JSON 也没有 elements）。这里显式声明为「非模型方块」，让 GTNHLib 保留我们的 BER/原版渲染。
     */
    @Override
    public boolean nhlib$isModeled() {
        return false;
    }

    @Override
    public void nhlib$setModeled(boolean modeled) {
        // 显式 opt-out：保持 false
    }
}
