package com.tacz.guns.block.entity;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.block.BlockMetadata;
import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.PegboardShape;
import com.tacz.guns.config.client.RenderConfig;

/**
 * 枪架/展示板的方块实体：最多 2 个展示面 x 16 格，另存方块自身的染色。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/entity/GunPegboardBlockEntity.java}
 *
 * <h3>NBT 键（与上游完全一致，存档兼容）</h3>
 * 
 * <pre>
 * Items          : list&lt;compound&gt;（Slot byte(0-31)、Item compound）
 * Rotations      : byte[32]
 * SlotShape      : string（straight / corner_left / corner_right，用于结构镜像时换算槽位）
 * Color          : int（无染色时不写）
 * InheritedColor : boolean（从相邻方块继承来的颜色时为 true）
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntity} -> {@link TileEntity}，public 无参构造；坐标走 xCoord/yCoord/zCoord。</li>
 * <li>方块状态（FACING/SHAPE）走 metadata，见 {@link GunPegboardBlock}；一律
 * {@code worldObj.getBlockMetadata(...)} 现读（{@link #getBlockMetadata()} 是脏的）。
 * 上游的 {@code DYED} 由本实体的 {@code color} 派生，不进 metadata（见 {@code GunPegboardBlock} 类注释）。</li>
 * <li>上游 {@code setBlockState(BlockState)} 钩子不存在（1.7.10 改 metadata 不通知方块实体），
 * 由 {@code GunPegboardBlock#onNeighborBlockChange} 显式调用 {@link #onBlockStateChanged(int, int)}。</li>
 * <li>{@code saveAdditional}/{@code load} -> {@link #writeToNBT}/{@link #readFromNBT}；
 * {@code getUpdateTag}/{@code getUpdatePacket} -> {@link #getDescriptionPacket}/{@link #onDataPacket}。
 * 上游 {@code getUpdateTag} 会去掉 {@code SlotShape}（客户端可能还没收到方块状态），这里同样去掉。</li>
 * <li>{@code ItemStack.EMPTY} -> {@code null}；{@code Block.popResource} -> 直接生成
 * {@link EntityItem}。</li>
 * <li>{@code shouldRefresh} 重写为“只有方块类型变化才重建实体”。</li>
 * </ul>
 */
public class GunPegboardBlockEntity extends TileEntity implements GunDisplayBlockEntity {

    /** 上游 {@code GRID_SIZE} / {@code SLOTS_PER_FACE} / {@code SLOT_COUNT}。 */
    public static final int GRID_SIZE = 4;
    public static final int SLOTS_PER_FACE = GRID_SIZE * GRID_SIZE;
    /** 原有 0..15 槽位保持不变，16..31 属于折面；转角时每面仅启用三列。 */
    public static final int SLOT_COUNT = SLOTS_PER_FACE * 2;

    private static final String ITEMS_TAG = "Items";
    private static final String SLOT_TAG = "Slot";
    private static final String ITEM_TAG = "Item";
    private static final String ROTATIONS_TAG = "Rotations";
    private static final String SLOT_SHAPE_TAG = "SlotShape";
    private static final String COLOR_TAG = "Color";
    private static final String INHERITED_COLOR_TAG = "InheritedColor";

    private final ItemStack[] items = new ItemStack[SLOT_COUNT];
    private final byte[] rotations = new byte[SLOT_COUNT];
    private final DisplayGunSlot[] displaySlots = new DisplayGunSlot[SLOT_COUNT];
    private int color = -1;
    private boolean inheritedColor;

    public GunPegboardBlockEntity() {}

    @Override
    @Nullable
    public ItemStack getItem(int slot) {
        return GunPegboardSlotHelper.isSlotActive(metadata(), slot) ? getItemUnchecked(slot) : null;
    }

    @Nullable
    private ItemStack getItemUnchecked(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? items[slot] : null;
    }

    public int getColor() {
        return color;
    }

    public boolean hasInheritedColor() {
        return inheritedColor;
    }

    /**
     * 上游 {@code setColor(int, boolean)}（GunPegboardBlockEntity.java:64-74）：同时更新
     * {@code DYED} 状态位与方块实体的颜色。
     *
     * <p>
     * 1.7.10：{@code DYED} 不占 metadata（4 bit 已被 FACING/SHAPE 占满，见 {@code GunPegboardBlock}
     * 类注释），它完全由本字段派生；{@link #update()} 的 {@code markBlockForUpdate} 既把颜色同步给客户端
     * （{@code getDescriptionPacket}），也让该方块重绘以套用 grayscale 图标 + 染色。
     */
    public void setColor(int color, boolean inherited) {
        int normalized = color < 0 ? -1 : color & 0xFFFFFF;
        boolean inheritedColor = normalized >= 0 && inherited;
        if (this.color == normalized && this.inheritedColor == inheritedColor) {
            return;
        }
        this.color = normalized;
        this.inheritedColor = inheritedColor;
        update();
    }

    @Override
    public int getRotation(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? rotations[slot] & 7 : 0;
    }

    @Override
    public DisplayGunSlot getDisplaySlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IndexOutOfBoundsException(slot);
        }
        DisplayGunSlot handle = displaySlots[slot];
        if (handle == null) {
            displaySlots[slot] = handle = new DisplayGunSlot(this, slot);
        }
        return handle;
    }

    @Override
    public int getGridSize() {
        return GRID_SIZE;
    }

    @Override
    public ForgeDirection getSlotFacing(int slot) {
        return GunPegboardSlotHelper.getSlotFacing(metadata(), slot);
    }

    @Override
    public boolean isDisplayGlowing() {
        return getBlockType() instanceof GunPegboardBlock pegboard && pegboard.isGlowing();
    }

    @Override
    public TileEntity getBlockEntity() {
        return this;
    }

    private void displaySlotChanged(int slot) {
        if (displaySlots[slot] != null) {
            displaySlots[slot].changed();
        }
    }

    public boolean setItem(int slot, @Nullable ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT
            || !GunPegboardSlotHelper.isSlotActive(metadata(), slot)
            || items[slot] != null
            || stack == null) {
            return false;
        }
        ItemStack stored = stack.copy();
        stored.stackSize = 1;
        items[slot] = stored;
        rotations[slot] = 0;
        displaySlotChanged(slot);
        update();
        return true;
    }

    /** 上游 {@code setItems(Map<Integer, ItemStack>)}（GunPegboardBlockEntity.java:127-147）。 */
    public boolean setItems(Map<Integer, ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty() || stacks.size() > SLOT_COUNT) {
            return false;
        }
        int metadata = metadata();
        for (Map.Entry<Integer, ItemStack> entry : stacks.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= SLOT_COUNT
                || !GunPegboardSlotHelper.isSlotActive(metadata, slot)
                || entry.getValue() == null
                || items[slot] != null) {
                return false;
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : stacks.entrySet()) {
            int slot = entry.getKey();
            ItemStack stored = entry.getValue()
                .copy();
            stored.stackSize = 1;
            items[slot] = stored;
            rotations[slot] = 0;
            displaySlotChanged(slot);
        }
        update();
        return true;
    }

    public boolean rotateItem(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT
            || !GunPegboardSlotHelper.isSlotActive(metadata(), slot)
            || items[slot] == null) {
            return false;
        }
        rotations[slot] = (byte) ((rotations[slot] + 1) & 7);
        if (displaySlots[slot] != null) {
            displaySlots[slot].transformed();
        }
        update();
        return true;
    }

    public boolean dropItem(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT || items[slot] == null || this.worldObj == null) {
            return false;
        }
        ForgeDirection facing = GunPegboardSlotHelper.getSlotFacing(metadata(), slot);
        dropStack(items[slot], facing.offsetX, facing.offsetZ);
        items[slot] = null;
        rotations[slot] = 0;
        displaySlotChanged(slot);
        update();
        return true;
    }

    public boolean removeItem(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT || items[slot] == null) {
            return false;
        }
        items[slot] = null;
        rotations[slot] = 0;
        displaySlotChanged(slot);
        update();
        return true;
    }

    public void dropAllItems() {
        if (this.worldObj == null) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (items[i] != null) {
                ForgeDirection facing = GunPegboardSlotHelper.getSlotFacing(metadata(), i);
                dropStack(items[i], facing.offsetX, facing.offsetZ);
                items[i] = null;
                rotations[i] = 0;
                displaySlotChanged(i);
            }
        }
        update();
    }

    /**
     * 上游 {@code setBlockState(BlockState)}（GunPegboardBlockEntity.java:211-232）的 1.7.10 替身：
     * 朝向/折面形状变化后，把新状态下失效槽位上的物品丢出来。
     *
     * <p>
     * 1.7.10 改 metadata 不会通知方块实体，所以由 {@code GunPegboardBlock#onNeighborBlockChange} 显式调用。
     */
    public void onBlockStateChanged(int previousMetadata, int newMetadata) {
        if (GunPegboardBlock.getFacing(previousMetadata) == GunPegboardBlock.getFacing(newMetadata)
            && GunPegboardBlock.getShape(previousMetadata) == GunPegboardBlock.getShape(newMetadata)) {
            return;
        }
        boolean changed = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (displaySlots[slot] != null) {
                displaySlots[slot].transformed();
            }
            boolean removedFace = slot >= SLOTS_PER_FACE
                && GunPegboardBlock.getShape(previousMetadata) != GunPegboardBlock.getShape(newMetadata);
            if (this.worldObj != null && !this.worldObj.isRemote
                && items[slot] != null
                && (!GunPegboardSlotHelper.isSlotActive(newMetadata, slot) || removedFace)) {
                ForgeDirection facing = GunPegboardSlotHelper.getSlotFacing(previousMetadata, slot);
                dropStack(items[slot], facing.offsetX, facing.offsetZ);
                items[slot] = null;
                rotations[slot] = 0;
                displaySlotChanged(slot);
                changed = true;
            }
        }
        if (changed) {
            update();
        }
    }

    private int metadata() {
        return this.worldObj != null ? this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord)
            : this.blockMetadata;
    }

    private void dropStack(ItemStack stack, int offsetX, int offsetZ) {
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        EntityItem entityItem = new EntityItem(
            this.worldObj,
            this.xCoord + 0.5D + offsetX,
            this.yCoord + 0.5D,
            this.zCoord + 0.5D + offsetZ,
            stack);
        entityItem.delayBeforeCanPickup = 10;
        this.worldObj.spawnEntityInWorld(entityItem);
    }

    /** 上游 {@code update()}（GunPegboardBlockEntity.java:198-204）。 */
    private void update() {
        markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readPayload(tag, true);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writePayload(tag);
    }

    private void writePayload(NBTTagCompound tag) {
        tag.setString(
            SLOT_SHAPE_TAG,
            GunPegboardBlock.getShape(metadata())
                .getSerializedName());
        if (color >= 0) {
            tag.setInteger(COLOR_TAG, color);
        }
        if (inheritedColor) {
            tag.setBoolean(INHERITED_COLOR_TAG, true);
        }
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte(SLOT_TAG, (byte) i);
                entry.setTag(ITEM_TAG, items[i].writeToNBT(new NBTTagCompound()));
                list.appendTag(entry);
            }
        }
        tag.setTag(ITEMS_TAG, list);
        tag.setByteArray(ROTATIONS_TAG, rotations);
    }

    /**
     * @param applyShapeMirror 是否按 {@code SlotShape} 做左右镜像换算（存档要，网络更新不要——上游
     *                         {@code getUpdateTag} 会去掉 SlotShape，理由见类注释）
     */
    private void readPayload(NBTTagCompound tag, boolean applyShapeMirror) {
        color = tag.hasKey(COLOR_TAG, Constants.NBT.TAG_INT) ? tag.getInteger(COLOR_TAG) & 0xFFFFFF : -1;
        inheritedColor = color >= 0 && tag.getBoolean(INHERITED_COLOR_TAG);
        ItemStack[] previousItems = items.clone();
        byte[] previousRotations = rotations.clone();
        for (int i = 0; i < items.length; i++) {
            items[i] = null;
            rotations[i] = 0;
        }
        if (tag.hasKey(ITEMS_TAG, Constants.NBT.TAG_LIST)) {
            NBTTagList list = tag.getTagList(ITEMS_TAG, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                int slot = entry.getByte(SLOT_TAG) & 255;
                if (slot >= 0 && slot < SLOT_COUNT && entry.hasKey(ITEM_TAG, Constants.NBT.TAG_COMPOUND)) {
                    items[slot] = ItemStack.loadItemStackFromNBT(entry.getCompoundTag(ITEM_TAG));
                }
            }
        }
        if (tag.hasKey(ROTATIONS_TAG, Constants.NBT.TAG_BYTE_ARRAY)) {
            byte[] saved = tag.getByteArray(ROTATIONS_TAG);
            System.arraycopy(saved, 0, rotations, 0, Math.min(saved.length, rotations.length));
        }
        if (applyShapeMirror) {
            PegboardShape shape = GunPegboardBlock.getShape(metadata());
            String savedShape = tag.getString(SLOT_SHAPE_TAG);
            // 结构镜像只转换方块状态；左右转角互换时，同步镜像每面的槽位列和旋转角度
            boolean mirrored = shape == PegboardShape.CORNER_LEFT
                && savedShape.equals(PegboardShape.CORNER_RIGHT.getSerializedName())
                || shape == PegboardShape.CORNER_RIGHT
                    && savedShape.equals(PegboardShape.CORNER_LEFT.getSerializedName());
            if (mirrored) {
                ItemStack[] savedItems = items.clone();
                byte[] savedRotations = rotations.clone();
                for (int slot = 0; slot < SLOT_COUNT; slot++) {
                    int mirroredSlot = slot - slot % GRID_SIZE + GRID_SIZE - 1 - slot % GRID_SIZE;
                    items[mirroredSlot] = savedItems[slot];
                    rotations[mirroredSlot] = (byte) (-savedRotations[slot] & 7);
                }
            }
        }
        for (int i = 0; i < displaySlots.length; i++) {
            DisplayGunSlot handle = displaySlots[i];
            if (handle == null) {
                continue;
            }
            if (!ItemStack.areItemStacksEqual(previousItems[i], items[i])) {
                handle.changed();
            } else if (previousRotations[i] != rotations[i]) {
                handle.transformed();
            }
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writePayload(tag);
        // 网络更新不能按结构镜像解释，客户端可能尚未收到对应的方块状态（与上游 getUpdateTag 一致）
        tag.removeTag(SLOT_SHAPE_TAG);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readPayload(pkt.func_148857_g(), false);
    }

    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y,
        int z) {
        return oldBlock != newBlock;
    }

    /** 上游 {@code getRenderBoundingBox}（GunPegboardBlockEntity.java:316-319）。 */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(
            this.xCoord - 2,
            this.yCoord - 2,
            this.zCoord - 2,
            this.xCoord + 3,
            this.yCoord + 3,
            this.zCoord + 3);
    }

    /**
     * 上游 {@code GunPegboardRenderer#getViewDistance()}（{@code RenderConfig.TARGET_RENDER_DISTANCE}，默认 128）：
     * 1.7.10 的距离剔除由本方法承担
     * （{@code build/rfg/minecraft-src/java/net/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher.java:119}）。
     * 只在客户端被调用，专用服务器上 {@code RenderConfig} 未初始化时保留默认值。
     */
    @Override
    public double getMaxRenderDistanceSquared() {
        if (RenderConfig.TARGET_RENDER_DISTANCE == null) {
            return super.getMaxRenderDistanceSquared();
        }
        double distance = RenderConfig.TARGET_RENDER_DISTANCE.get();
        return distance * distance;
    }

    /** 1.7.10 便捷：转 metadata 之外的地方偶尔要用朝向。 */
    public ForgeDirection getFacing() {
        return BlockMetadata.facing(metadata());
    }
}
