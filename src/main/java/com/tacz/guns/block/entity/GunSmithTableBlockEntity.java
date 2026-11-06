package com.tacz.guns.block.entity;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.util.ResourceLocationHelper;

/**
 * 枪械工作台的方块实体：只存 {@code BlockId}（资源包里的工作台定义 id）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/entity/GunSmithTableBlockEntity.java}
 *
 * <h3>NBT 键（与上游完全一致，存档兼容）</h3>
 * 
 * <pre>
 * BlockId : string（ResourceLocation 文本）
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntity(BlockEntityType, BlockPos, BlockState)} -> public 无参构造
 * （{@code GameRegistry#registerTileEntity} 通过反射 new），坐标/元数据由 {@link #xCoord}/{@code blockMetadata}
 * 提供。</li>
 * <li>{@code saveAdditional}/{@code load} -> {@link #writeToNBT}/{@link #readFromNBT}
 * （1.7.10 没有“附加数据”分段）。</li>
 * <li>{@code getUpdatePacket}/{@code getUpdateTag}（{@code ClientboundBlockEntityDataPacket}） ->
 * {@link #getDescriptionPacket()} + {@link #onDataPacket}（{@code S35PacketUpdateTileEntity}）。</li>
 * <li>{@code MenuProvider#createMenu} 取消：1.7.10 由 {@code inventory/GuiHandler} 直接读本实体并构造
 * Container。{@code getDisplayName()} 的返回类型随之从 {@code Component} 变为 {@link IChatComponent}。</li>
 * <li>{@code shouldRefresh} 必须重写：1.7.10 默认对模组方块实体总是返回 true（任何 {@code setBlock}
 * 都会重建实体），这里改成“方块类型变了才重建”，保证改朝向等 metadata 操作不丢数据。</li>
 * </ul>
 */
public class GunSmithTableBlockEntity extends TileEntity {

    private static final String ID_TAG = "BlockId";

    @Nullable
    private ResourceLocation id = null;

    public GunSmithTableBlockEntity() {}

    public void setId(@Nullable ResourceLocation id) {
        this.id = id;
        this.markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Nullable
    public ResourceLocation getId() {
        return id;
    }

    /**
     * 上游 {@code MenuProvider#getDisplayName()}（GunSmithTableBlockEntity.java:65-67）返回
     * {@code Component.literal("Gun Smith Table")}；1.7.10 没有 {@code Component}，这里返回等价文本组件，
     * 界面标题实际由 {@code inventory/GuiHandler} 构造 Container 时给出（本方法保留以对齐公开 API）。
     */
    public IChatComponent getDisplayName() {
        return new ChatComponentText("Gun Smith Table");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writePayload(tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readPayload(tag);
    }

    private void writePayload(NBTTagCompound tag) {
        if (id != null) {
            tag.setString(ID_TAG, id.toString());
        }
    }

    private void readPayload(NBTTagCompound tag) {
        if (tag.hasKey(ID_TAG, Constants.NBT.TAG_STRING)) {
            this.id = ResourceLocationHelper.tryParse(tag.getString(ID_TAG));
        } else {
            // 与上游一致：没有 BlockId 的旧存档回退到默认工作台
            this.id = DefaultAssets.DEFAULT_BLOCK_ID;
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

    /** 上游 {@code getRenderBoundingBox}（GunSmithTableBlockEntity.java:58-62）。 */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(
            this.xCoord - 2,
            this.yCoord,
            this.zCoord - 2,
            this.xCoord + 2,
            this.yCoord + 1,
            this.zCoord + 2);
    }
}
