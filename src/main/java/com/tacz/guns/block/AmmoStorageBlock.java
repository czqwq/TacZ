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
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.inventory.GuiHandler;
import com.tacz.guns.inventory.GuiId;
import com.tacz.guns.util.AmmoStorageHelper;
import com.tacz.guns.util.AmmoStorageInteractionHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 弹药库（铁 / 金 / 钻石三种容量）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/AmmoStorageBlock.java}
 *
 * <h3>1.20.1 BlockState 属性 -> 1.7.10 metadata 编码</h3>
 * 
 * <pre>
 * 属性（1.20.1）              位置      取值
 * FACING（HORIZONTAL_FACING） meta 0-1  0=NORTH 1=SOUTH 2=WEST 3=EAST
 * </pre>
 * 
 * 容量不是方块状态，而是构造器参数（上游同样是构造器参数），见 {@link #getCapacityStacks()}。
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BaseEntityBlock#newBlockEntity} -> {@code hasTileEntity(int)} /
 * {@code createTileEntity(World,int)}。</li>
 * <li>{@code getDrops(...)} 依赖 {@code LootContextParams.BLOCK_ENTITY} 读方块实体；1.7.10 的
 * {@code getDrops} 在方块实体移除之后才调用，所以改为在 {@code breakBlock} 里把整个方块实体的 NBT
 * 写进掉落物（键 {@link #BLOCK_ENTITY_TAG}，与 1.20 {@code BlockItem#setBlockEntityData} 一致），
 * {@code getDrops} 返回空表避免二次掉落。放置时的还原由 {@code item/AmmoStorageBlockItem} 的
 * {@code placeBlockAt} 负责（common-game Lead 已实现）。</li>
 * <li>{@code IAmmoBox}/{@code InteractionResult} -> {@code boolean}（1.7.10
 * {@code onBlockActivated} 的返回值）。</li>
 * <li>「弹药盒倒进仓储」与上游一样<b>委托</b> {@link AmmoStorageInteractionHelper#unloadAmmoBox}
 * （上游 {@code AmmoStorageBlock.java:95}），本类不保留私有副本；
 * 音效降级（{@code BUNDLE_REMOVE_ONE} -> {@code random.pop}）在 util 侧注释说明。</li>
 * <li>{@code NetworkHooks.openScreen} -> {@code inventory.GuiHandler#openGui}（{@code GuiId#AMMO_STORAGE}）。</li>
 * <li>{@code player.displayClientMessage(Component, true)}（动作栏）-> 1.7.10 没有动作栏，
 * 降级为聊天栏 {@link ChatComponentTranslation}。</li>
 * <li>{@code SoundEvents.ARMOR_EQUIP_IRON}（{@code fillAmmoBox}）-> 1.7.10 没有同名音效资源，
 * 用最接近的原版音效名，见 {@link #SOUND_ARMOR_EQUIP}。</li>
 * </ul>
 */
public class AmmoStorageBlock extends Block {

    /** 上游 {@code BlockItem.setBlockEntityData(...)} 使用的物品 NBT 键，掉落物/放置时读写它。 */
    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    public static final int IRON_CAPACITY_STACKS = 216;
    public static final int GOLD_CAPACITY_STACKS = 432;
    public static final int DIAMOND_CAPACITY_STACKS = 864;

    /**
     * 音效降级：1.7.10 的音效是字符串名，且没有 1.20 的 {@code armor.equip_iron}，用
     * {@code random.bowhit}（1.7.10 里装备/金属碰撞类最近的原版音效）。
     * TODO(port L2): 若要精确还原，应在 {@code init/ModSounds} 注册自定义音效后替换。
     */
    private static final String SOUND_ARMOR_EQUIP = "random.bowhit";

    private final int capacityStacks;

    /** 三档容量各自的图标（见 {@link #registerBlockIcons(IIconRegister)}）。 */
    private IIcon[] ammoStorageIcons;

    /** 一次性掉落抑制位（创造模式破坏不产生掉落）。服务端单线程使用、读后即清。 */
    private boolean suppressNextDrop;

    public AmmoStorageBlock() {
        this(IRON_CAPACITY_STACKS);
    }

    public AmmoStorageBlock(int capacityStacks) {
        super(Material.iron);
        this.capacityStacks = capacityStacks;
        this.setStepSound(Block.soundTypeMetal);
        this.setHardness(2.0F);
        this.setResistance(2.0F);
        // 上游按注册名区分三种贴图/名称；1.7.10 没有 blockstate，这里按容量推出默认值，
        // init/ModBlocks 可用 setBlockName / setBlockTextureName 覆盖（见交付说明）。
        if (capacityStacks == GOLD_CAPACITY_STACKS) {
            this.setBlockName("ammo_storage_gold");
            this.setBlockTextureName("tacz:block/ammo_storage_gold");
        } else if (capacityStacks == DIAMOND_CAPACITY_STACKS) {
            this.setBlockName("ammo_storage_diamond");
            this.setBlockTextureName("tacz:block/ammo_storage_diamond");
        } else {
            this.setBlockName("ammo_storage");
            this.setBlockTextureName("tacz:block/ammo_storage");
        }
    }

    /**
     * 1.7.10 方块必须有 IIcon（1.20.1 走 JSON block model）：{@code Block#registerBlockIcons} 默认空实现，
     * 不注册时 {@code blockIcon} 恒为 null → TextureMap 会产出 MISSING_ICON 缺失贴图。
     *
     * <p>
     * 三档容量在 1.7.10 是**构造器参数**（不是 metadata，也不是 BlockState）：三个注册实例
     * （ammo_storage / ammo_storage_gold / ammo_storage_diamond）各自是一个 {@code AmmoStorageBlock} 对象，
     * 因此这里注册全部三档图标到 {@link #ammoStorageIcons}，由 {@link #getIcon(int, int)} 按实例容量返回。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.ammoStorageIcons = new IIcon[] { register.registerIcon("tacz:ammo_storage"),
            register.registerIcon("tacz:ammo_storage_gold"), register.registerIcon("tacz:ammo_storage_diamond") };
        this.blockIcon = this.ammoStorageIcons[iconIndex()];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        if (this.ammoStorageIcons == null) {
            return this.blockIcon;
        }
        return this.ammoStorageIcons[iconIndex()];
    }

    private int iconIndex() {
        if (this.capacityStacks == GOLD_CAPACITY_STACKS) {
            return 1;
        }
        return this.capacityStacks == DIAMOND_CAPACITY_STACKS ? 2 : 0;
    }

    public int getCapacityStacks() {
        return capacityStacks;
    }

    /** 上游 {@code FACING} 属性。 */
    public static ForgeDirection getFacing(int metadata) {
        return BlockMetadata.facing(metadata);
    }

    /** 写入 {@code FACING} 位，返回新 metadata。 */
    public static int withFacing(int metadata, ForgeDirection facing) {
        return BlockMetadata.withFacing(metadata, facing);
    }

    /** 1.7.10 便捷：给渲染/其它包读朝向。 */
    public ForgeDirection getFacing(IBlockAccess world, int x, int y, int z) {
        return BlockMetadata.facing(world.getBlockMetadata(x, y, z));
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new AmmoStorageBlockEntity();
    }

    /** 上游 {@code getStateForPlacement(...)}（AmmoStorageBlock.java:78-81）。 */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (world.isRemote || placer == null) {
            return;
        }
        int metadata = BlockMetadata.withFacing(
            world.getBlockMetadata(x, y, z),
            BlockMetadata.horizontalDirection(placer)
                .getOpposite());
        world.setBlockMetadataWithNotify(x, y, z, metadata, 2);
    }

    /**
     * 上游 {@code getDrops(...)}（AmmoStorageBlock.java:132-143）的 1.7.10 形态。
     * 见类注释：掉落改在 {@code breakBlock} 里做。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        boolean suppressed = this.suppressNextDrop;
        this.suppressNextDrop = false;
        if (!suppressed && !world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            ItemStack drop = new ItemStack(Item.getItemFromBlock(this));
            if (tileEntity instanceof AmmoStorageBlockEntity storage) {
                NBTTagCompound tag = new NBTTagCompound();
                storage.writeToNBT(tag);
                // 等同上游 saveWithoutMetadata()：去掉坐标与注册名
                tag.removeTag("id");
                tag.removeTag("x");
                tag.removeTag("y");
                tag.removeTag("z");
                if (!tag.hasNoTags()) {
                    drop.setTagCompound(new NBTTagCompound());
                    drop.getTagCompound()
                        .setTag(BLOCK_ENTITY_TAG, tag);
                }
            }
            dropBlockAsItem(world, x, y, z, drop);
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    /** 见 {@code breakBlock}：掉落已在其中处理，这里返回空表避免重复掉落。 */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        return new ArrayList<>();
    }

    /** 上游 1.20 创造模式破坏不产生掉落；1.7.10 的掉落走 {@code breakBlock}，在这里置抑制位。 */
    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int metadata, EntityPlayer player) {
        if (player != null && player.capabilities.isCreativeMode) {
            this.suppressNextDrop = true;
        }
    }

    /**
     * 上游 {@code use(...)}（AmmoStorageBlock.java:84-106）：
     * 手持子弹盒 -> 装弹/卸弹；否则打开弹药库界面。
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float subX,
        float subY, float subZ) {
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof AmmoStorageBlockEntity storage)) {
            return false;
        }
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() instanceof IAmmoBox ammoBox) {
            if (world.isRemote) {
                return true;
            }
            if (player.isSneaking()) {
                // 上游 block→util 的唯一实现（AmmoStorageBlock.java:95）；本类不再保留私有副本
                return AmmoStorageInteractionHelper.unloadAmmoBox(storage, held, ammoBox, player);
            }
            return fillAmmoBox(storage, held, ammoBox, player, world, x, y, z);
        }
        if (world.isRemote) {
            return true;
        }
        GuiHandler.openGui(player, world, x, y, z, GuiId.AMMO_STORAGE);
        return true;
    }

    /** 上游 {@code fillAmmoBox(...)}（AmmoStorageBlock.java:108-130）。 */
    private boolean fillAmmoBox(AmmoStorageBlockEntity storage, ItemStack boxStack, IAmmoBox ammoBox,
        EntityPlayer player, World world, int x, int y, int z) {
        ResourceLocation ammoId = ammoBox.getAmmoId(boxStack);
        if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            // 上游：displayClientMessage(..., true) 是动作栏；1.7.10 无动作栏 -> 聊天栏
            player.addChatComponentMessage(new ChatComponentTranslation("message.tacz.ammo_storage.ammo_box_empty"));
            return true;
        }
        int current = ammoBox.getAmmoCount(boxStack);
        int levelMultiplier = ammoBox.getAmmoLevel(boxStack) + 1;
        int max = AmmoStorageHelper.getStackSize(AmmoStorageHelper.createAmmoStack(ammoId, 1))
            * SyncConfig.AMMO_BOX_STACK_SIZE.get()
            * levelMultiplier;
        int need = Math.max(0, max - current);
        if (need <= 0) {
            return true;
        }
        ItemStack extracted = storage.extractAmmo(ammoId, need, false);
        if (extracted == null || extracted.stackSize <= 0) {
            player.addChatComponentMessage(new ChatComponentTranslation("message.tacz.ammo_storage.no_ammo_box_stock"));
            return true;
        }
        ammoBox.setAmmoCount(boxStack, current + extracted.stackSize);
        player.addChatComponentMessage(
            new ChatComponentTranslation(
                "message.tacz.ammo_storage.picked",
                extracted.getDisplayName(),
                extracted.stackSize));
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, SOUND_ARMOR_EQUIP, 0.8F, 1.0F);
        return true;
    }

    /**
     * 上游 {@code hasAnalogOutputSignal}（AmmoStorageBlock.java:146-148）。
     */
    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    /** 上游 {@code getAnalogOutputSignal}（AmmoStorageBlock.java:151-157）。 */
    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof AmmoStorageBlockEntity storage) {
            return storage.getComparatorSignal();
        }
        return 0;
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
}
