package com.tacz.guns.block.entity;

import java.util.UUID;

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
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.block.BlockMetadata;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.config.client.RenderConfig;

/**
 * 标靶方块实体（只有下半块有）：保存击倒动画角度、红石强度、自定义名称与“主人”档案。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/entity/TargetBlockEntity.java}
 *
 * <h3>NBT 键（与上游完全一致，存档兼容）</h3>
 * 
 * <pre>
 * Owner      : compound（GameProfile 的 NBT，见 NbtUtils.writeGameProfile）
 * CustomName : string（JSON 文本，上游用 Component.Serializer）
 * </pre>
 * 
 * 另外 {@code OutputPower} 是本移植<b>新增</b>的键：上游把红石强度存在 BlockState 的
 * {@code OUTPUT_POWER} 属性里，1.7.10 的 4 bit metadata 放不下（facing 2 + half 1 + stand 1 已占满），
 * 改存方块实体（见 {@code TargetBlock} 类注释）。
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntity} -> {@link TileEntity}，public 无参构造。</li>
 * <li>{@code Standalone ticker}（{@code clientTick}）-> {@link #updateEntity()} 的客户端分支。</li>
 * <li>{@code BlockHitResult} -> {@link MovingObjectPosition}（{@code hit} 的入参）。</li>
 * <li>{@code Component} -> String（存档里存文本；{@code Nameable} 接口在 1.7.10 用
 * {@link #getCustomName()} 表达，返回 String 而不是 {@code Component}）。</li>
 * <li>{@code NbtUtils.readGameProfile}/{@code writeGameProfile} -> 1.7.10 同名
 * {@code net.minecraft.nbt.NbtUtils} 不存在，这里手写 profile 的 name/uuid 两个字段。</li>
 * <li>{@code SkullBlockEntity.updateGameprofile(...)}（异步填皮肤）在 1.7.10 无等价物，省略。</li>
 * <li>{@code level.scheduleTick(...)} -> {@code World#scheduleBlockUpdate}；
 * {@code level.setBlock(...)} -> {@code setBlockMetadataWithNotify} + 方块实体强度字段。</li>
 * <li>{@code level.playSound(...)} -> {@code World#playSoundEffect(x, y, z, name, volume, pitch)}。</li>
 * </ul>
 */
public class TargetBlockEntity extends TileEntity {

    /** 标靶复位时间，暂定为 5 秒。 */
    private static final int RESET_TIME = 5 * 20;
    private static final String OWNER_TAG = "Owner";
    private static final String CUSTOM_NAME_TAG = "CustomName";
    private static final String OUTPUT_POWER_TAG = "OutputPower";

    /** 渲染用的击倒角度（0 = 立着，90 = 完全倒下）。 */
    public float rot = 0;
    public float oRot = 0;

    @Nullable
    private GameProfile owner;
    /** 上游把自定义名称存成 JSON 文本组件（{@code Component.Serializer.toJson}），这里保持同样的存档格式。 */
    @Nullable
    private String nameJson;
    /** 解析出的纯文本（1.7.10 的物品显示名是 String）。 */
    @Nullable
    private String nameText;
    private int outputPower;

    public TargetBlockEntity() {}

    /**
     * 上游 {@code clientTick(...)}（TargetBlockEntity.java:49-56）：击倒/复位的插值角度。
     */
    @Override
    public void updateEntity() {
        if (this.worldObj == null || !this.worldObj.isRemote) {
            return;
        }
        this.oRot = this.rot;
        if (TargetBlock.isStand(metadata())) {
            this.rot = Math.max(this.rot - 18, 0);
        } else {
            this.rot = Math.min(this.rot + 45, 90);
        }
    }

    @Nullable
    public GameProfile getOwner() {
        return owner;
    }

    public void setOwner(@Nullable GameProfile owner) {
        this.owner = owner;
        // TODO(port L2): 上游 SkullBlockEntity.updateGameprofile(this.owner, ...) 会异步补全皮肤属性；
        // 1.7.10 没有对应工具，这里只保存名字/UUID，皮肤由客户端渲染器自行处理。
        this.refresh();
    }

    /** 上游 {@code getCustomName()} 返回 {@code Component}；1.7.10 的物品显示名是 String，这里返回纯文本。 */
    @Nullable
    public String getCustomName() {
        return nameText;
    }

    public void setCustomName(@Nullable String name) {
        this.nameText = name;
        this.nameJson = name == null ? null : IChatComponent.Serializer.func_150696_a(new ChatComponentText(name));
    }

    /** 上游 {@code getOutputPower()} 的等价物（移植新增，见类注释）。 */
    public int getOutputPower() {
        return outputPower;
    }

    public void setOutputPower(int outputPower) {
        this.outputPower = Math.max(0, Math.min(15, outputPower));
        this.markDirty();
    }

    /** 上游 {@code refresh()}（TargetBlockEntity.java:118-124）。 */
    public void refresh() {
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    /**
     * 上游 {@code hit(Level, BlockState, BlockHitResult, boolean)}（TargetBlockEntity.java:131-148）：
     * 击倒 + 输出红石 + 排定复位 + 播放音效。
     */
    public void hit(World world, int x, int y, int z, MovingObjectPosition hit, boolean isUpperBlock) {
        if (this.worldObj == null || world == null) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        if (!TargetBlock.isStand(metadata)) {
            return;
        }
        int blockY = y;
        int redstoneStrength = TargetBlock
            .getRedstoneStrength(hit.sideHit, hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord, isUpperBlock);
        // 上游：把 STAND=false、OUTPUT_POWER=strength 写进方块状态；1.7.10 前者进 metadata、后者进方块实体
        world.setBlockMetadataWithNotify(x, blockY, z, TargetBlock.withStand(metadata, false), 3);
        TileEntity tileEntity = world.getTileEntity(x, blockY, z);
        if (tileEntity instanceof TargetBlockEntity target) {
            target.setOutputPower(redstoneStrength);
        }
        TargetBlock.updateNeighbourPower(world, x, blockY, z);
        world.scheduleBlockUpdate(x, blockY, z, world.getBlock(x, blockY, z), RESET_TIME);
        // 原版的声音传播距离由 volume 决定：大于 1 时距离 = 16 * volume
        float volume = TargetBlock.getTargetSoundVolume();
        world.playSoundEffect(
            x + 0.5D,
            blockY + 0.5D,
            z + 0.5D,
            TargetBlock.TARGET_HIT_SOUND,
            volume,
            world.rand.nextFloat() * 0.1F + 0.9F);
    }

    /** JSON 文本组件 -> 纯文本；坏数据回退为原始字符串（与上游宽松解析一致）。 */
    @Nullable
    private static String decodeName(@Nullable String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        if (json.charAt(0) != '{') {
            // 纯文本（旧存档 / 手写 NBT）
            return json;
        }
        try {
            return IChatComponent.Serializer.func_150699_a(json)
                .getUnformattedText();
        } catch (RuntimeException e) {
            return json;
        }
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
        if (owner != null) {
            NBTTagCompound ownerTag = new NBTTagCompound();
            if (owner.getName() != null) {
                ownerTag.setString("Name", owner.getName());
            }
            if (owner.getId() != null) {
                ownerTag.setString(
                    "Id",
                    owner.getId()
                        .toString());
            }
            tag.setTag(OWNER_TAG, ownerTag);
        }
        if (this.nameJson != null) {
            tag.setString(CUSTOM_NAME_TAG, this.nameJson);
        }
        if (this.outputPower > 0) {
            tag.setInteger(OUTPUT_POWER_TAG, this.outputPower);
        }
    }

    private void readPayload(NBTTagCompound tag) {
        if (tag.hasKey(OWNER_TAG, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound ownerTag = tag.getCompoundTag(OWNER_TAG);
            String ownerName = ownerTag.hasKey("Name") ? ownerTag.getString("Name") : null;
            UUID uuid = null;
            if (ownerTag.hasKey("Id")) {
                try {
                    uuid = UUID.fromString(ownerTag.getString("Id"));
                } catch (IllegalArgumentException ignored) {
                    // 与上游一致：坏数据不阻塞存档加载
                }
            }
            this.owner = ownerName == null && uuid == null ? null : new GameProfile(uuid, ownerName);
        } else {
            this.owner = null;
        }
        this.nameJson = tag.hasKey(CUSTOM_NAME_TAG, Constants.NBT.TAG_STRING) ? tag.getString(CUSTOM_NAME_TAG) : null;
        this.nameText = decodeName(this.nameJson);
        this.outputPower = Math.max(0, Math.min(15, tag.getInteger(OUTPUT_POWER_TAG)));
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

    /** 上游 {@code getRenderBoundingBox}（TargetBlockEntity.java:127-129）。 */
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
     * 上游 {@code TargetRenderer#getViewDistance()}（{@code RenderConfig.TARGET_RENDER_DISTANCE}，默认 128）：
     * 1.7.10 的 TESR 没有 {@code getViewDistance()}，距离剔除由本方法承担
     * （{@code build/rfg/minecraft-src/java/net/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher.java:119}，
     * 默认值 {@code TileEntity.java:193-196} 只有 64 格）。
     *
     * <p>
     * 只在客户端渲染时被调用；专用服务器上 {@code RenderConfig} 未初始化，故保留默认值兜底。
     */
    @Override
    public double getMaxRenderDistanceSquared() {
        if (RenderConfig.TARGET_RENDER_DISTANCE == null) {
            return super.getMaxRenderDistanceSquared();
        }
        double distance = RenderConfig.TARGET_RENDER_DISTANCE.get();
        return distance * distance;
    }

    /** 上游用 {@code Direction} 读朝向，这里给渲染一个入口。 */
    public ForgeDirection getFacing() {
        return BlockMetadata.facing(metadata());
    }
}
