package com.tacz.guns.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.nbt.AmmoBoxItemDataAccessor;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.index.CommonAmmoIndex;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 搬运自上游 1.20.1 {@code item/AmmoBoxItem.java}（extends Item implements DyeableLeatherItem, AmmoBoxItemDataAccessor）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code DyeableLeatherItem} 接口在 1.7.10 不存在：颜色读写直接操作 1.20.1 同结构的
 * {@code display.color} NBT（保持存档格式），通过 {@link #getColorFromItemStack} 提供染色；</li>
 * <li>{@code getName} → {@link #getItemStackDisplayName}（输出 {@link EnumChatFormatting} 颜色前缀，
 * 对应上游 Component.withStyle）；{@code appendHoverText} → {@link #addInformation}；</li>
 * <li>耐久条：{@code isBarVisible/getBarWidth/getBarColor} → 1.7.10 的
 * {@link #showDurabilityBar}/{@link #getDurabilityForDisplay}（1.7.10 无法自定义耐久条颜色，
 * {@code getBarColor} 无法表达，记录差异）；</li>
 * <li>物品栏交互：上游 {@code overrideStackedOnOther}（手持弹药盒右键点槽位：空格取弹 / 弹药格存弹）
 * 在 1.7.10 没有对应钩子，改由 {@link #tryStackedOnOther} 承载同一套逻辑，并由
 * {@code mixin/common/ContainerSlotClickMixin} 在 {@code Container#slotClick} 入口调用；NBT 数据层
 * （AmmoId/AmmoCount/Level/Creative/AllTypeCreative）与创造模式条目完整保留；</li>
 * <li>{@code getTooltipImage}（1.20 tooltip 组件）删除（1.7.10 无对应，L2 客户端 tooltip 处理）。</li>
 * </ul>
 */
public class AmmoBoxItem extends Item implements AmmoBoxItemDataAccessor {

    public static final ResourceLocation PROPERTY_NAME = new ResourceLocation(GunMod.MOD_ID, "ammo_statue");

    public static final int IRON_LEVEL = 0;
    public static final int GOLD_LEVEL = 1;
    public static final int DIAMOND_LEVEL = 2;

    private static final String DISPLAY_TAG = "display";
    private static final String COLOR_TAG = "color";

    private static final int OPEN = 0;
    private static final int CLOSE = 1;

    private static final int CREATIVE_INDEX = 6;
    private static final int ALL_TYPE_CREATIVE_INDEX = 8;

    public AmmoBoxItem() {
        this.setMaxStackSize(1);
    }

    /**
     * 1.7.10 必须有 {@link net.minecraft.util.IIcon}（1.20 走 JSON item model + 多 predicate 覆盖）。
     * 贴图由 assets-port 复制到 {@code assets/tacz/textures/items/ammo_box.png}；不同等级/创造模式的外观
     * 在 L2 由 IItemRenderer 按 NBT（Level/Creative）切换（TODO(L2, client-port)），此处用基础图标。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.itemIcon = register.registerIcon("tacz:ammo_box");
    }

    /**
     * 1.7.10 物品染色（对应 1.20 的 {@code DyeableLeatherItem#getColor}）。
     */
    public static int getColor(ItemStack stack, int tintIndex) {
        return tintIndex > 0 ? -1 : getTagColor(stack);
    }

    /**
     * 客户端模型雕像索引（上游 {@code getStatue(ItemStack, ClientLevel, LivingEntity, int)}；
     * 1.7.10 客户端没有 ClientLevel 语义，参数缩减为 stack，客户端渲染器按需调用）。
     */
    public static float getStatue(ItemStack stack) {
        int openStatue = OPEN;
        int ammoLevel = IRON_LEVEL;
        if (stack != null && stack.getItem() instanceof IAmmoBox iAmmoBox) {
            if (iAmmoBox.isAllTypeCreative(stack)) {
                return ALL_TYPE_CREATIVE_INDEX;
            }
            openStatue = getOpenStatue(iAmmoBox, stack);
            if (iAmmoBox.isCreative(stack)) {
                return openStatue + CREATIVE_INDEX;
            }
            ammoLevel = iAmmoBox.getAmmoLevel(stack);
        }
        return openStatue + 2 * ammoLevel;
    }

    private static int getOpenStatue(IAmmoBox iAmmoBox, ItemStack stack) {
        boolean idIsEmpty = iAmmoBox.getAmmoId(stack)
            .equals(DefaultAssets.EMPTY_AMMO_ID);
        boolean countIsZero = iAmmoBox.getAmmoCount(stack) <= 0;
        if (idIsEmpty || countIsZero) {
            return OPEN;
        }
        return CLOSE;
    }

    private static int getTagColor(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag == null || !tag.hasKey(DISPLAY_TAG, 10)) {
            return 0x727d6b;
        }
        NBTTagCompound display = tag.getCompoundTag(DISPLAY_TAG);
        return display.hasKey(COLOR_TAG, Constants.NBT.TAG_ANY_NUMERIC) ? display.getInteger(COLOR_TAG) : 0x727d6b;
    }

    @Override
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        return renderPass > 0 ? -1 : getTagColor(stack);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (isAllTypeCreative(stack)) {
            return EnumChatFormatting.DARK_PURPLE
                + StatCollector.translateToLocal("item.tacz.ammo_box.all_type_creative");
        }
        if (isCreative(stack)) {
            return EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("item.tacz.ammo_box.creative");
        }
        int ammoLevel = getAmmoLevel(stack);
        return switch (ammoLevel) {
            case GOLD_LEVEL -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("item.tacz.ammo_box.gold");
            case DIAMOND_LEVEL -> EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("item.tacz.ammo_box.diamond");
            default -> EnumChatFormatting.RESET + StatCollector.translateToLocal("item.tacz.ammo_box.iron");
        };
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        if (isAllTypeCreative(stack) || isCreative(stack)) {
            return true;
        }
        return super.hasEffect(stack);
    }

    // ---- 1.7.10 耐久条：上游 isBarVisible/getBarWidth/getBarColor 的等价物 ----

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        if (isAllTypeCreative(stack) || isCreative(stack)) {
            return false;
        }
        return !this.getAmmoId(stack)
            .equals(DefaultAssets.EMPTY_AMMO_ID) && this.getAmmoCount(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        ResourceLocation ammoId = this.getAmmoId(stack);
        int ammoCount = this.getAmmoCount(stack);
        int boxLevelMultiplier = this.getAmmoLevel(stack) + 1;
        double widthPercent = TimelessAPI.getCommonAmmoIndex(ammoId)
            .map(index -> {
                double totalCount = index.getStackSize() * SyncConfig.AMMO_BOX_STACK_SIZE.get() * boxLevelMultiplier;
                return ammoCount / totalCount;
            })
            .orElse(0d);
        // 1.7.10 的耐久条语义：0 满、1 空，因此取 1 - 充满比例
        return 1.0D - Math.min(Math.max(widthPercent, 0d), 1d);
    }

    /**
     * 1.7.10 创造模式物品栏填充（替代上游 {@code fillItemCategory(CreativeModeTab.Output)}）。
     */
    public static List<ItemStack> fillItemCategory() {
        List<ItemStack> stacks = new ArrayList<>();
        ItemStack ammoBox = new ItemStack(ModItems.AMMO_BOX);
        if (ModItems.AMMO_BOX instanceof IAmmoBox iAmmoBox) {
            stacks.add(iAmmoBox.setAmmoLevel(ammoBox.copy(), IRON_LEVEL));
            stacks.add(iAmmoBox.setAmmoLevel(ammoBox.copy(), GOLD_LEVEL));
            stacks.add(iAmmoBox.setAmmoLevel(ammoBox.copy(), DIAMOND_LEVEL));
            stacks.add(iAmmoBox.setCreative(ammoBox.copy(), false));
            stacks.add(iAmmoBox.setCreative(ammoBox.copy(), true));
        }
        return stacks;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        if (isAllTypeCreative(stack)) {
            tooltip.add(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("tooltip.tacz.ammo_box.usage.all_type_creative"));
            return;
        }
        if (isCreative(stack)) {
            tooltip.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("tooltip.tacz.ammo_box.usage.creative.1"));
            tooltip.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("tooltip.tacz.ammo_box.usage.creative.2"));
            return;
        }
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.tacz.ammo_box.usage.deposit"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.tacz.ammo_box.usage.remove"));
    }

    /**
     * 1.7.10 播放取弹声音（替代 1.20 的 {@code SoundEvents.BUNDLE_REMOVE_ONE}；1.7.10 无 bundle 音效，
     * 用 {@code random.pop} 近似，记录差异）。由 {@link #tryStackedOnOther} 调用。
     */
    public void playRemoveOneSound(Entity entity) {
        entity.playSound("random.pop", 0.8F, 0.8F + entity.worldObj.rand.nextFloat() * 0.4F);
    }

    /**
     * 1.7.10 播放装弹声音（替代 1.20 的 {@code SoundEvents.BUNDLE_INSERT}，用 {@code random.click} 近似）。
     */
    public void playInsertSound(Entity entity) {
        entity.playSound("random.click", 0.8F, 0.8F + entity.worldObj.rand.nextFloat() * 0.4F);
    }

    // ================================================================================================
    // 物品栏内右键交互（上游 Item#overrideStackedOnOther 的 1.7.10 落地）
    // ================================================================================================

    /**
     * 手持弹药盒右键点槽位：目标槽为空 → 从盒里取弹；目标槽是同类弹药 → 把弹药存进盒。
     *
     * <p>
     * 逐行对应上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/item/AmmoBoxItem.java:102-180} 的
     * {@code overrideStackedOnOther(ItemStack ammoBox, Slot slot, ClickAction action, Player player)}：
     * 「弹药盒」= 光标上的手持栈（上游由 {@code AbstractContainerMenu} 传 {@code getCarried()} 进来）。
     *
     * <p>
     * <b>为什么是「静态方法 + mixin」而不是 GUI</b>：上游该交互没有界面，就是物品栏里的右键点击；
     * 1.7.10 的 {@code Container#slotClick} 也没有任何「物品自定义点击回调」。因此由
     * {@code mixin/common/ContainerSlotClickMixin}（r7-platform）在 {@code Container#slotClick} 的 HEAD 调用本方法：
     * 返回 {@code true} 时 cancel，并 {@code return player.inventory.getItemStack()}（= 修改后的弹药盒）。
     *
     * <p>
     * <b>返回值语义必须与上游一致（false = 交回原版）</b>：上游在这些分支 {@code return false}，意思是
     * 「不处理，走 `doClick` 的普通放置/交换」；1.7.10 里同样要让 {@code slotClick} 继续跑
     * （右键会把盒子放进空格 / 与格内物品交换），所以本方法**不能**把拒绝分支改成 true。
     *
     * <p>
     * <b>两侧同语义</b>：客户端 {@code PlayerControllerMP#windowClick}（{@code PlayerControllerMP.java:475-481}）
     * 会先在本地跑一次 {@code slotClick}，再把返回值当 {@code clickedItem} 发给服务端；
     * 服务端 {@code NetHandlerPlayServer#processClickWindow}（{@code :949} 起）拿自己的返回值与之比较，
     * 相等才 confirm 并 {@code updateHeldItem()}。本方法只依赖「手持栈 + 目标槽」这两份两侧一致的状态，
     * 因此预测/权威结果相同。
     *
     * @param slotId        被点击的槽位号
     * @param clickedButton 0 = 左键，1 = 右键（上游只处理右键 = {@code ClickAction.SECONDARY}）
     * @param mode          0 = 普通点击（上游只在 {@code ClickType.PICKUP} 分支调用该钩子）
     * @return true = 本次点击已处理（调用方应 cancel {@code slotClick}）
     */
    public static boolean tryStackedOnOther(EntityPlayer player, int slotId, int clickedButton, int mode) {
        // 上游：if (action == ClickAction.SECONDARY) —— 只处理右键普通点击
        if (player == null || slotId < 0 || mode != 0 || clickedButton != 1) {
            return false;
        }
        ItemStack ammoBox = player.inventory.getItemStack();
        if (ammoBox == null || !(ammoBox.getItem() instanceof AmmoBoxItem ammoBoxItem)) {
            return false;
        }
        // 1.7.10 无「槽位越界返回空槽」语义（Container#getSlot 直接 List#get），必须先判界
        if (slotId >= player.openContainer.inventorySlots.size()) {
            return false;
        }
        Slot slot = player.openContainer.getSlot(slotId);
        // 只接管玩家物品栏的格子：合成输出/交易结果等特殊槽位的 pickup 回调有副作用
        if (slot == null || !(slot.inventory instanceof InventoryPlayer)) {
            return false;
        }
        ResourceLocation boxAmmoId = ammoBoxItem.getAmmoId(ammoBox);
        ItemStack slotItem = slot.getStack();

        // ── 格子为空 ⇒ 取出弹药 ──
        if (slotItem == null || slotItem.getItem() == null) {
            // 创造模式弹药箱不能取出任何东西
            if (ammoBoxItem.isAllTypeCreative(ammoBox) || ammoBoxItem.isCreative(ammoBox)) {
                return false;
            }
            // 盒内没有弹种 / 数量不对，不能取出
            if (boxAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                return false;
            }
            int boxAmmoCount = ammoBoxItem.getAmmoCount(ammoBox);
            if (boxAmmoCount <= 0) {
                return false;
            }
            Optional<CommonAmmoIndex> ammoIndex = TimelessAPI.getCommonAmmoIndex(boxAmmoId);
            if (ammoIndex.isEmpty()) {
                return false;
            }
            int takeCount = Math.min(
                ammoIndex.get()
                    .getStackSize(),
                boxAmmoCount);
            ItemStack takeAmmo = AmmoItemBuilder.create()
                .setId(boxAmmoId)
                .setCount(takeCount)
                .build();
            // 上游 slot.safeInsert(takeAmmo)：先判 mayPlace，再按 min(槽位上限, 物品上限) 插入，返回剩余
            if (!slot.isItemValid(takeAmmo)) {
                return false;
            }
            // 格子为空，无需减去已有数量（同 1.20 Slot#safeInsert 的 existing.getCount() == 0 分支）
            int insertedCount = Math.min(takeCount, slot.getSlotStackLimit());
            if (insertedCount <= 0) {
                return false;
            }
            ItemStack placed = takeAmmo.copy();
            placed.stackSize = insertedCount;
            slot.putStack(placed);
            slot.onSlotChanged();

            int remainCount = boxAmmoCount - insertedCount;
            ammoBoxItem.setAmmoCount(ammoBox, remainCount);
            if (remainCount <= 0) {
                ammoBoxItem.setAmmoId(ammoBox, DefaultAssets.EMPTY_AMMO_ID);
            }
            ammoBoxItem.playRemoveOneSound(player);
            return true;
        }

        // ── 格子是弹药 ⇒ 存入弹药盒 ──
        if (slotItem.getItem() instanceof IAmmo slotAmmo) {
            // 全类型创造弹药箱不能存入
            if (ammoBoxItem.isAllTypeCreative(ammoBox)) {
                return false;
            }
            ResourceLocation slotAmmoId = slotAmmo.getAmmoId(slotItem);
            // 格子里的子弹 ID 不对，不能放
            if (slotAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                return false;
            }
            // 盒子的子弹 ID 为空 → 变成当前点击的类型；否则必须同种
            if (boxAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                ammoBoxItem.setAmmoId(ammoBox, slotAmmoId);
            } else if (!slotAmmoId.equals(boxAmmoId)) {
                return false;
            }
            // 上游此处是 ifPresent：索引缺失时什么都不搬，但仍会 playInsertSound 并 return true（逐行保留）
            Optional<CommonAmmoIndex> ammoIndex = TimelessAPI.getCommonAmmoIndex(slotAmmoId);
            if (ammoIndex.isPresent()) {
                if (ammoBoxItem.isCreative(ammoBox)) {
                    // 创造模式弹药箱直接存满
                    ammoBoxItem.setAmmoCount(ammoBox, Integer.MAX_VALUE);
                } else {
                    int boxAmmoCount = ammoBoxItem.getAmmoCount(ammoBox);
                    int boxLevelMultiplier = ammoBoxItem.getAmmoLevel(ammoBox) + 1;
                    int maxSize = ammoIndex.get()
                        .getStackSize() * SyncConfig.AMMO_BOX_STACK_SIZE.get()
                        * boxLevelMultiplier;
                    int needCount = maxSize - boxAmmoCount;
                    // 上游 slot.safeTake(slotItem.getCount(), needCount, player)：
                    // 1.7.10 等价 = canTakeStack 判定 → min(格内数量, needCount) → decrStackSize → onPickupFromSlot
                    if (needCount > 0 && slot.canTakeStack(player)) {
                        ItemStack taken = slot.decrStackSize(Math.min(slotItem.stackSize, needCount));
                        if (taken != null && taken.getItem() != null && taken.stackSize > 0) {
                            slot.onPickupFromSlot(player, taken);
                            ammoBoxItem.setAmmoCount(ammoBox, boxAmmoCount + taken.stackSize);
                        }
                    }
                }
            }
            ammoBoxItem.playInsertSound(player);
            return true;
        }
        // 格子是其它物品 ⇒ 交回原版
        return false;
    }

    // TODO(port L2, client-port): getTooltipImage/appendHoverText 的客户端 tooltip 组件由 L2 客户端 tooltip 渲染接管。
}
