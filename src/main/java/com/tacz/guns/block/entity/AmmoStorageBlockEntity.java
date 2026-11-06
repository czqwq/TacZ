package com.tacz.guns.block.entity;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.tacz.guns.block.AmmoStorageBlock;
import com.tacz.guns.inventory.AmmoStorageInventory;
import com.tacz.guns.util.AmmoStorageHelper;

/**
 * 弹药库的方块实体：用一个 {@link AmmoStorageInventory} 按“弹药类型”聚合存放（一种弹药一格，数量 long）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/entity/AmmoStorageBlockEntity.java}
 *
 * <h3>NBT 键（与上游完全一致，存档兼容）</h3>
 * 
 * <pre>
 * Entries : list&lt;compound&gt;（Slot byte、Stack compound、Count long）
 *           —— 由 AmmoStorageInventory#save()/load(NBTTagList) 负责
 * </pre>
 * 
 * 掉落物把整份 NBT 放在物品的 {@code BlockEntityTag} 里（键同 1.20 的 {@code BlockItem#setBlockEntityData}），
 * 放置时由 {@code item/AmmoStorageBlockItem#placeBlockAt} 还原。
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntityType} 静态字段删除：方块实体注册名在 {@code init/ModBlocks}
 * （{@code GameRegistry.registerTileEntity(AmmoStorageBlockEntity.class, "tacz:ammo_storage")}）。</li>
 * <li>{@code MenuProvider}（{@code createMenu}/{@code getDisplayName}）-> 1.7.10 的
 * {@code inventory/GuiHandler#getServerGuiElement} 直接构造 {@code AmmoStorageMenu}；
 * {@code getDisplayName()} 保留但返回 {@link IChatComponent}。</li>
 * <li>{@code Container}（1.20 的库存抽象）-> {@link IInventory}；{@code moveAmmoToInventory} 的参数类型随之改变。</li>
 * <li>{@code Capability}/{@code ForgeCapabilities.ITEM_HANDLER}/{@code LazyOptional} -> 1.7.10 没有 Capability
 * 体系，库存本身就是 {@link IInventory}（漏斗等可直接访问），见 {@link #getInventory()}。</li>
 * <li>{@code level.updateNeighbourForOutputSignal(pos, block)} -> {@code World#func_147453_f}。</li>
 * <li>{@code setChanged()} -> {@link #markDirty()}（1.7.10 会同时标记区块已修改）。</li>
 * </ul>
 */
public class AmmoStorageBlockEntity extends TileEntity {

    /** 上游 {@code MAX_AMMO_TYPES}：最多 27 种弹药（3 行 x 9 列）。 */
    public static final int MAX_AMMO_TYPES = 27;

    private static final String ENTRIES_TAG = "Entries";

    private final AmmoStorageInventory inventory = new AmmoStorageInventory(
        MAX_AMMO_TYPES,
        this::getCapacityStacks,
        AmmoStorageHelper::isValidAmmo,
        AmmoStorageHelper::getStackSize,
        this::onInventoryChanged);

    public AmmoStorageBlockEntity() {}

    public int insertAmmo(@Nullable ItemStack stack, boolean simulate) {
        return inventory.insert(stack, simulate);
    }

    public ItemStack extractAmmo(@Nullable ItemStack sample, int count, boolean simulate) {
        return inventory.extract(sample, count, simulate);
    }

    /** 上游 {@code extractAmmo(ResourceLocation, int, boolean)}（AmmoStorageBlockEntity.java:53-61）。 */
    public ItemStack extractAmmo(ResourceLocation ammoId, int count, boolean simulate) {
        for (AmmoStorageInventory.Content content : inventory.getContents()) {
            if (ammoId.equals(AmmoStorageHelper.getAmmoId(content.sample()))) {
                // Never combine differently tagged ammo into one returned ItemStack.
                return inventory.extract(content.sample(), count, simulate);
            }
        }
        return null;
    }

    public int moveAmmoToInventory(IInventory target, int targetSlots, @Nullable ItemStack sample, int count) {
        return inventory.moveToInventory(target, targetSlots, sample, count);
    }

    public List<AmmoStorageInventory.Content> getContents() {
        return inventory.getContents();
    }

    public long getInventoryRevision() {
        return inventory.getRevision();
    }

    public long getUsedStacks() {
        return inventory.getUsedStacks();
    }

    public long getRemainingCapacity(@Nullable ItemStack sample) {
        return inventory.getRemainingCapacity(sample);
    }

    public int getComparatorSignal() {
        return inventory.getComparatorSignal();
    }

    /** 上游 {@code stillValid(Player)}（AmmoStorageBlockEntity.java:87-90），供 Container 校验距离。 */
    public boolean stillValid(EntityPlayer player) {
        return this.worldObj != null && this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
            && player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
    }

    /** 1.7.10 无 Capability，直接暴露 {@link IInventory} 供漏斗/其它模组访问。 */
    public IInventory getInventory() {
        return inventory;
    }

    /**
     * 上游 {@code MenuProvider#getDisplayName()} 的 1.7.10 形态（返回 {@link IChatComponent}）。
     * 界面标题实际由 {@code AmmoStorageInventory#getInventoryName()} 提供。
     */
    public IChatComponent getDisplayName() {
        return new ChatComponentTranslation("gui.tacz.ammo_storage.title");
    }

    /** 上游 {@code getCapacityStacks()}（AmmoStorageBlockEntity.java:142-145）：容量由方块类型决定。 */
    public int getCapacityStacks() {
        return getBlockType() instanceof AmmoStorageBlock storage ? storage.getCapacityStacks()
            : AmmoStorageBlock.IRON_CAPACITY_STACKS;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        inventory.load(
            tag.hasKey(ENTRIES_TAG, Constants.NBT.TAG_LIST) ? tag.getTagList(ENTRIES_TAG, Constants.NBT.TAG_COMPOUND)
                : null);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag(ENTRIES_TAG, inventory.save());
    }

    /**
     * 上游注释：「Contents are sent only to open menus, never through block-entity/chunk synchronization.」
     * -> 1.7.10 同样不重写 {@code getDescriptionPacket}，内容只经容器界面同步。
     */
    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y,
        int z) {
        return oldBlock != newBlock;
    }

    /** 上游 {@code onInventoryChanged()}（AmmoStorageBlockEntity.java:134-140）。 */
    private void onInventoryChanged() {
        markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            // 1.20: level.updateNeighbourForOutputSignal(pos, block) -> 1.7.10: World#func_147453_f
            this.worldObj.func_147453_f(this.xCoord, this.yCoord, this.zCoord, getBlockType());
        }
    }
}
