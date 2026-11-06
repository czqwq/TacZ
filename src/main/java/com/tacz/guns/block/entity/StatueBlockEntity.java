package com.tacz.guns.block.entity;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.block.BlockMetadata;
import com.tacz.guns.config.client.RenderConfig;

/**
 * 雕像方块实体：只存展示的枪（1 件），客户端每 5 秒在枪上方冒一次末地烛粒子。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/entity/StatueBlockEntity.java}
 *
 * <h3>NBT 键（与上游完全一致，存档兼容）</h3>
 * 
 * <pre>
 * Item : compound（ItemStack NBT，无枪时是空 compound）
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntity} -> {@link TileEntity}，public 无参构造。</li>
 * <li>{@code clientTick(...)}（{@code BlockEntityTicker}）-> {@link #updateEntity()} 里的
 * {@code worldObj.isRemote} 分支（上游这个 ticker 本身就是客户端专用，见该方法注释）；
 * 1.7.10 的粒子 API 是 {@code World#spawnParticle(String, ...)}，
 * 名称 {@code endRod} 在 1.7.10 不存在（末地烛是 1.9 加入的），改用 {@code flame}
 * （TODO(port L2, client-port)：如需精确还原需 client 侧自定义粒子）。</li>
 * <li>{@code level.sendBlockUpdated(...)} -> {@link World#markBlockForUpdate}；
 * {@code setChanged()} -> {@link #markDirty()}。</li>
 * <li>{@code getUpdateTag}/{@code getUpdatePacket} -> {@link #getDescriptionPacket}/{@link #onDataPacket}。</li>
 * <li>{@code Block.popResource(level, pos.relative(facing).above(), stack)} -> 直接生成 {@link EntityItem}。</li>
 * <li>{@code shouldRefresh} 重写为“只有方块类型变化才重建实体”。</li>
 * </ul>
 */
public class StatueBlockEntity extends TileEntity {

    private static final String ITEM_TAG = "Item";

    @Nullable
    private ItemStack gunItem = null;

    public StatueBlockEntity() {}

    /**
     * 上游 {@code clientTick(...)}（StatueBlockEntity.java:31-44）：每 100 tick 在枪的位置冒粒子。
     *
     * <p>
     * 客户端专用：上游 {@code getTicker} 只在 {@code level.isClientSide()} 挂它（StatueBlock.java:45-47），
     * 而 1.7.10 {@code World#updateEntities} 两端都 tick 方块实体（World.java:2148-2160）⇒ 这条早退不能删。
     * 也不要改成 {@code WorldServer#func_147487_a}（{@code ServerLevel#sendParticles} 等价物）：它只发给
     * 16 格内玩家（WorldServer.java:1134），比「每个客户端各自生成、可见距离 = 渲染距离」更差。
     */
    @Override
    public void updateEntity() {
        if (this.worldObj == null || !this.worldObj.isRemote || gunItem == null) {
            return;
        }
        if (this.worldObj.getTotalWorldTime() % 100 != 0) {
            return;
        }
        ForgeDirection direction = BlockMetadata.facing(metadata());
        double x = this.xCoord + direction.offsetX * 0.75 + 0.5;
        double z = this.zCoord + direction.offsetZ * 0.75 + 0.5;
        double dx = -0.02 + this.worldObj.rand.nextDouble() * 0.04;
        double dy = -0.02 + this.worldObj.rand.nextDouble() * 0.04;
        double dz = -0.02 + this.worldObj.rand.nextDouble() * 0.04;
        // TODO(port L2, client-port): 上游 ParticleTypes.END_ROD；1.7.10 无末地烛粒子，降级为 flame
        this.worldObj.spawnParticle("flame", x, this.yCoord + 2.25, z, dx, dy, dz);
    }

    @Nullable
    public ItemStack getGunItem() {
        return gunItem;
    }

    /** 上游 {@code setGun(ItemStack)}（StatueBlockEntity.java:50-58）。 */
    public void setGun(@Nullable ItemStack stack) {
        this.dropItem();
        if (stack == null) {
            return;
        }
        this.gunItem = stack.copy();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
        this.markDirty();
    }

    /** 上游 {@code dropItem()}（StatueBlockEntity.java:60-71）。 */
    public void dropItem() {
        if (gunItem == null || this.worldObj == null) {
            return;
        }
        ForgeDirection direction = BlockMetadata.facing(metadata());
        EntityItem entityItem = new EntityItem(
            this.worldObj,
            this.xCoord + 0.5D + direction.offsetX,
            this.yCoord + 1.5D,
            this.zCoord + 0.5D + direction.offsetZ,
            gunItem);
        entityItem.delayBeforeCanPickup = 10;
        this.worldObj.spawnEntityInWorld(entityItem);
        this.gunItem = null;
        this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        this.markDirty();
    }

    private int metadata() {
        return this.worldObj != null ? this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord)
            : this.blockMetadata;
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
        tag.setTag(ITEM_TAG, gunItem == null ? new NBTTagCompound() : gunItem.writeToNBT(new NBTTagCompound()));
    }

    private void readPayload(NBTTagCompound tag) {
        if (tag.hasKey(ITEM_TAG, Constants.NBT.TAG_COMPOUND)) {
            this.gunItem = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(ITEM_TAG));
        } else {
            this.gunItem = null;
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

    /** 上游 {@code getRenderBoundingBox}（StatueBlockEntity.java:94-97）。 */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(
            this.xCoord - 2,
            this.yCoord,
            this.zCoord - 2,
            this.xCoord + 2,
            this.yCoord + 2,
            this.zCoord + 2);
    }

    /**
     * 上游 {@code StatueRenderer#getViewDistance()} / {@code shouldRender(...)}（默认 128 格）：
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

    /** 1.7.10 便捷：给渲染读朝向（上游用 {@code StatueBlock.FACING}）。 */
    public ForgeDirection getFacing() {
        return BlockMetadata.facing(metadata());
    }
}
