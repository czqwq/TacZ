package com.tacz.guns.block.entity;

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
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.config.client.RenderConfig;

/**
 * 枪械展示柜的方块实体：4x4 网格共 16 个展示槽，每格存 1 件物品 + 0..7 的旋转档位。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/entity/GunDisplayCaseBlockEntity.java}
 *
 * <h3>NBT 键（与上游完全一致，存档兼容）</h3>
 * 
 * <pre>
 * Items     : list&lt;compound&gt;（Slot byte(0-15)、Item compound = ItemStack NBT）
 * Rotations : byte[16]
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntity} -> {@link TileEntity}，public 无参构造；坐标走
 * {@link #xCoord}/{@link #yCoord}/{@link #zCoord}。</li>
 * <li>方块状态：1.7.10 没有 {@code getBlockState()}，统一从 {@code worldObj.getBlockMetadata(...)} 读
 * （<b>不能</b>用 {@link #getBlockMetadata()}：它只在实体创建/校验时刷新，metadata 改动后是脏的），
 * 朝向编码见 {@link GunDisplayCaseBlock}。</li>
 * <li>{@code saveAdditional}/{@code load} -> {@link #writeToNBT}/{@link #readFromNBT}；
 * {@code getUpdateTag}/{@code getUpdatePacket} -> {@link #getDescriptionPacket}/{@link #onDataPacket}。</li>
 * <li>{@code ItemStack.EMPTY} -> {@code null}；{@code ItemStack.of(...)} ->
 * {@link ItemStack#loadItemStackFromNBT}。</li>
 * <li>{@code Block.popResource(level, pos, stack)} -> 直接生成 {@link EntityItem}
 * （方块类的 {@code dropBlockAsItem} 是 protected，方块实体里不可用）。</li>
 * <li>{@code setChanged()} -> {@link #markDirty()}；{@code level.sendBlockUpdated(...)} ->
 * {@link World#markBlockForUpdate}。</li>
 * <li>{@code shouldRefresh} 重写为“只有方块类型变化才重建实体”，否则 1.7.10 默认行为会在任何
 * {@code setBlock} 上清空内容。</li>
 * </ul>
 */
public class GunDisplayCaseBlockEntity extends TileEntity implements GunDisplayBlockEntity {

    /** 上游 {@code GRID_SIZE}：每面 4x4。 */
    public static final int GRID_SIZE = 4;
    public static final int SLOT_COUNT = GRID_SIZE * GRID_SIZE;

    private static final String ITEMS_TAG = "Items";
    private static final String SLOT_TAG = "Slot";
    private static final String ITEM_TAG = "Item";
    private static final String ROTATIONS_TAG = "Rotations";

    private final ItemStack[] items = new ItemStack[SLOT_COUNT];
    private final byte[] rotations = new byte[SLOT_COUNT];
    private final DisplayGunSlot[] displaySlots = new DisplayGunSlot[SLOT_COUNT];

    public GunDisplayCaseBlockEntity() {}

    @Override
    @Nullable
    public ItemStack getItem(int slot) {
        return isValidSlot(slot) ? items[slot] : null;
    }

    @Override
    public int getRotation(int slot) {
        return isValidSlot(slot) ? rotations[slot] & 7 : 0;
    }

    @Override
    public DisplayGunSlot getDisplaySlot(int slot) {
        if (!isValidSlot(slot)) {
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
        return BlockMetadata.facing(metadata());
    }

    @Override
    public boolean isDisplayGlowing() {
        return true;
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
        if (!isValidSlot(slot) || items[slot] != null || stack == null) {
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

    public boolean rotateItem(int slot) {
        if (!isValidSlot(slot) || items[slot] == null) {
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
        if (!isValidSlot(slot) || items[slot] == null || this.worldObj == null) {
            return false;
        }
        ForgeDirection facing = BlockMetadata.facing(metadata());
        dropStack(items[slot], facing.offsetX, facing.offsetZ);
        items[slot] = null;
        rotations[slot] = 0;
        displaySlotChanged(slot);
        update();
        return true;
    }

    public boolean removeItem(int slot) {
        if (!isValidSlot(slot) || items[slot] == null) {
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
        ForgeDirection facing = BlockMetadata.facing(metadata());
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (items[i] != null) {
                dropStack(items[i], facing.offsetX, facing.offsetZ);
                items[i] = null;
                rotations[i] = 0;
                displaySlotChanged(i);
            }
        }
        update();
    }

    /** 读当前方块 metadata（1.7.10 的 “BlockState” 替身）。 */
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

    /** 上游 {@code update()}（GunDisplayCaseBlockEntity.java:145-151）。 */
    private void update() {
        markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readPayload(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writePayload(tag);
    }

    private void writePayload(NBTTagCompound tag) {
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

    private void readPayload(NBTTagCompound tag) {
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
                if (isValidSlot(slot) && entry.hasKey(ITEM_TAG, Constants.NBT.TAG_COMPOUND)) {
                    items[slot] = ItemStack.loadItemStackFromNBT(entry.getCompoundTag(ITEM_TAG));
                }
            }
        }
        if (tag.hasKey(ROTATIONS_TAG, Constants.NBT.TAG_BYTE_ARRAY)) {
            byte[] saved = tag.getByteArray(ROTATIONS_TAG);
            System.arraycopy(saved, 0, rotations, 0, Math.min(saved.length, rotations.length));
        }
        // 只通知已存在的渲染句柄（对应上游逐槽比较 previousItems/previousRotations）
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
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readPayload(pkt.func_148857_g());
    }

    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y,
        int z) {
        return oldBlock != newBlock;
    }

    /** 上游 {@code getRenderBoundingBox}（GunDisplayCaseBlockEntity.java:216-219）。 */
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
     * 上游 {@code GunDisplayCaseRenderer#getViewDistance()}（{@code RenderConfig.TARGET_RENDER_DISTANCE}，默认 128）：
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
}
