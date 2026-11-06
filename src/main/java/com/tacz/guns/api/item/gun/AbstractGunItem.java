package com.tacz.guns.api.item.gun;

import java.util.*;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.*;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械物品公共父类。
 * <p>
 * 1.7.10 移植说明（与上游 1.20.1 的差异，均因平台能力缺失）：
 * <ul>
 * <li>上游构造器收 {@code Item.Properties}；1.7.10 没有 Properties，改为无参构造并在此设置
 * {@code setMaxStackSize(1)}（上游 {@code ModernKineticGunItem} 里 {@code new Item.Properties().stacksTo(1)} 的等价物）。</li>
 * <li>上游 {@code getName(ItemStack)} → 1.7.10 {@link #getItemStackDisplayName(ItemStack)}（返回 String）。</li>
 * <li>上游 {@code initializeClient(Consumer<IClientItemExtensions>)} / {@code getTooltipImage(...)} 删除：
 * 1.7.10 没有 {@code IClientItemExtensions}（物品渲染由 {@code MinecraftForgeClient.registerItemRenderer}
 * 在 client 侧注册，L2 负责）也没有图片式 tooltip（{@code TooltipComponent}）。</li>
 * <li>上游通过 {@code getCapability(ForgeCapabilities.ITEM_HANDLER)} 遍历实体背包；1.7.10 无 capability，
 * 改为直接遍历 {@link IInventory}（目前只有 {@link EntityPlayer} 的背包可获取，其它实体返回空，见
 * {@link #getEntityInventory(EntityLivingBase)}）。</li>
 * <li>上游 {@code Projectile#shootFromRotation} → 1.7.10 无 Projectile 基类，默认实现在
 * {@link #doBulletSpread} 内用 {@link Entity#motionX} 等字段逐行复刻 1.20.1 的数学。</li>
 * </ul>
 */
public abstract class AbstractGunItem extends Item implements IGun, IAnimationItem {

    protected AbstractGunItem() {
        // 上游 AbstractGunItem 的 Properties 由子类给出（stacksTo(1)），1.7.10 在基类统一设置。
        this.setMaxStackSize(1);
    }

    private static Comparator<Map.Entry<ResourceLocation, CommonGunIndex>> idNameSort() {
        return ContentPackOrder.<CommonGunIndex>byNamespace()
            .thenComparingInt(
                m -> m.getValue()
                    .getSort());
    }

    /**
     * 开始拉栓时调用，返回 bolt 状态
     * 
     * @return bolt 状态。ture 代表开始 bolt，false 则代表不开始。
     */
    public abstract boolean startBolt(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter);

    /**
     * 拉栓 tick 时调用，返回是否仍在 bolt 状态
     * 
     * @return 是否仍在 bolt 状态
     */
    public abstract boolean tickBolt(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter);

    /**
     * 射击时触发
     */
    public abstract void shoot(ShooterDataHolder dataHolder, ItemStack gunItem, Supplier<Float> pitch,
        Supplier<Float> yaw, EntityLivingBase shooter);

    /**
     * 开始换弹时调用
     */
    public abstract boolean startReload(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter);

    /**
     * 换弹时每个 tick 调用
     * 
     * @return 如果返回的类型是 NOT_RELOADING 则下一个 tick 不再继续调用
     */
    public abstract ReloadState tickReload(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter);

    /**
     * 尝试打断换弹时调用
     */
    public abstract void interruptReload(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter);

    /**
     * 主动卸弹的行为入口，由操作者完成空闲检查后调用。
     * 默认清空弹匣和膛内弹；枪械实现可覆写此方法或分发到逻辑脚本。
     */
    public void unloadAmmo(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {
        unloadAmmo(gunItem, shooter, true, -1);
    }

    /**
     * 按数量结算卸弹，不触发主动卸弹回调，供默认行为和脚本 API 复用。
     *
     * @param unloadBarrel 是否同时卸出闭膛枪的膛内一发
     * @param amount       弹匣卸出数量；0 保留弹匣，-1 清空弹匣
     * @return 实际卸出总数（含膛内）；无效取值、缺少枪械定义、背包直读或客户端调用返回 0
     */
    public int unloadAmmo(ItemStack gunItem, EntityLivingBase shooter, boolean unloadBarrel, int amount) {
        return TimelessAPI.getCommonGunIndex(getGunId(gunItem))
            .map(index -> GunAmmoUnloadHelper.unloadAmmo(shooter, gunItem, this, index, unloadBarrel, amount))
            .orElse(0);
    }

    /**
     * 切换开火模式时调用
     */
    public abstract void fireSelect(ShooterDataHolder dataHolder, ItemStack gunItem);

    /**
     * 近战时调用
     */
    public abstract void melee(ShooterDataHolder dataHolder, EntityLivingBase user, ItemStack gunItem);

    /**
     * 过热 tick 处理<br/>
     * 默认不做任何事情
     */
    public void tickHeat(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {};

    /**
     * 初始化子弹角度和速度
     * 
     * @param dataHolder     状态数据
     * @param gunItem        枪械物品
     * @param shooter        射击者
     * @param projectile     子弹
     * @param bulletCnt      多弹丸的子弹序数
     * @param processedSpeed 修正后的子弹初速
     * @param inaccuracy     修正后的子弹不准确度
     * @param pitch          射击方向
     * @param yaw            射击方向
     */
    public void doBulletSpread(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter,
        Entity projectile, int bulletCnt, float processedSpeed, float inaccuracy, float pitch, float yaw) {
        // 1.7.10 无 Projectile 基类，按上游调用的 1.20.1 Projectile#shootFromRotation/#shoot 逐行复刻：
        // 方向 -> Vec3.normalize -> 每轴加 random.triangle(0, 0.0172275 * inaccuracy) -> 乘初速 -> 写 rotation。
        float f = -Mth.sin(yaw * (float) (Math.PI / 180.0)) * Mth.cos(pitch * (float) (Math.PI / 180.0));
        float f1 = -Mth.sin(pitch * (float) (Math.PI / 180.0));
        float f2 = Mth.cos(yaw * (float) (Math.PI / 180.0)) * Mth.cos(pitch * (float) (Math.PI / 180.0));
        double len = Math.sqrt((double) f * f + (double) f1 * f1 + (double) f2 * f2);
        // 对应 Vec3#normalize：长度 < 1e-4 时归零
        double vx;
        double vy;
        double vz;
        if (len < 1.0E-4D) {
            vx = 0.0D;
            vy = 0.0D;
            vz = 0.0D;
        } else {
            vx = f / len;
            vy = f1 / len;
            vz = f2 / len;
        }
        // RandomSource#triangle(0, deviation) = (nextDouble - nextDouble) * deviation；1.7.10 用世界随机替代。
        double deviation = 0.0172275D * inaccuracy;
        Random random = projectile.worldObj.rand;
        vx += (random.nextDouble() - random.nextDouble()) * deviation;
        vy += (random.nextDouble() - random.nextDouble()) * deviation;
        vz += (random.nextDouble() - random.nextDouble()) * deviation;
        projectile.motionX = vx * processedSpeed;
        projectile.motionY = vy * processedSpeed;
        projectile.motionZ = vz * processedSpeed;
        double horizontal = Math
            .sqrt(projectile.motionX * projectile.motionX + projectile.motionZ * projectile.motionZ);
        projectile.rotationYaw = (float) (Math.atan2(projectile.motionX, projectile.motionZ) * (180D / Math.PI));
        projectile.rotationPitch = (float) (Math.atan2(projectile.motionY, horizontal) * (180D / Math.PI));
        projectile.prevRotationYaw = projectile.rotationYaw;
        projectile.prevRotationPitch = projectile.rotationPitch;
        // 继承射击者的速度（与 1.20 Projectile#shootFromRotation 末尾一致）
        projectile.motionX += shooter.motionX;
        projectile.motionY += shooter.onGround ? 0.0D : shooter.motionY;
        projectile.motionZ += shooter.motionZ;
    }

    /**
     * 换弹前的检查，完成如下检查：枪内弹药是否已经填满？玩家背包是否有可用弹药？是否为背包直读？
     * 
     * @param shooter 准备换弹的实体
     * @param gunItem 枪械物品
     * @return 是否满足换弹条件
     */
    public boolean canReload(EntityLivingBase shooter, ItemStack gunItem) {
        ResourceLocation gunId = this.getGunId(gunItem);
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gunId)
            .orElse(null);
        if (gunIndex == null) {
            return false;
        }

        int currentAmmoCount = getCurrentAmmoCount(gunItem);
        int maxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, gunIndex.getGunData());
        if (currentAmmoCount >= maxAmmoCount) {
            return false;
        }
        // 背包直读不进行换弹
        if (useInventoryAmmo(gunItem)) {
            return false;
        }
        // 无限备弹不需要消耗实际子弹
        if (gunIndex.getGunData()
            .getReloadData()
            .isInfinite()) {
            return true;
        }
        // 虚拟备弹处理
        if (useDummyAmmo(gunItem)) {
            return getDummyAmmoAmount(gunItem) > 0;
        }
        // 检查背包内的弹药数量
        IInventory inventory = getEntityInventory(shooter);
        if (inventory == null) {
            return false;
        }
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack checkAmmoStack = inventory.getStackInSlot(i);
            if (ItemStackNbtHelper.isEmpty(checkAmmoStack)) {
                continue;
            }
            if (checkAmmoStack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gunItem, checkAmmoStack)) {
                return true;
            }
            if (checkAmmoStack.getItem() instanceof IAmmoBox iAmmoBox
                && iAmmoBox.isAmmoBoxOfGun(gunItem, checkAmmoStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将枪内的弹药全部退至背包（如果背包满了会丢到地上）。不会退枪膛内的弹药。
     * 目前，仅更换弹匣配件时调用。
     * 
     * @param player  玩家
     * @param gunItem 枪械物品
     */
    @Override
    public void dropAllAmmo(EntityPlayer player, ItemStack gunItem) {
        // 背包直读时不调用退弹
        if (useInventoryAmmo(gunItem)) {
            return;
        }
        // TODO 这里操作的对象不应该是 Player 而是 EntityLivingBase。此外枪膛内的子弹也要退
        int ammoCount = getCurrentAmmoCount(gunItem);
        if (ammoCount <= 0) {
            return;
        }
        ResourceLocation gunId = getGunId(gunItem);
        TimelessAPI.getCommonGunIndex(gunId)
            .ifPresent(index -> {
                // 如果使用的是虚拟备弹，返还至虚拟备弹
                if (useDummyAmmo(gunItem)) {
                    setCurrentAmmoCount(gunItem, 0);
                    // 燃料罐类型的换弹不返还
                    if (index.getGunData()
                        .getReloadData()
                        .getType()
                        .equals(FeedType.FUEL)) {
                        return;
                    }
                    addDummyAmmoAmount(gunItem, ammoCount);
                    return;
                }

                ResourceLocation ammoId = index.getGunData()
                    .getAmmoId();
                // 创造模式类型的换弹，只填满子弹总数，不进行任何卸载弹药逻辑
                if (player.capabilities.isCreativeMode) {
                    int maxAmmCount = AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, index.getGunData());
                    setCurrentAmmoCount(gunItem, maxAmmCount);
                    return;
                }
                // 燃料罐类型的只清空不返还
                if (index.getGunData()
                    .getReloadData()
                    .getType()
                    .equals(FeedType.FUEL)) {
                    setCurrentAmmoCount(gunItem, 0);
                    return;
                }
                TimelessAPI.getCommonAmmoIndex(ammoId)
                    .ifPresent(ammoIndex -> {
                        int stackSize = ammoIndex.getStackSize();
                        int tmpAmmoCount = ammoCount;
                        int roundCount = tmpAmmoCount / (stackSize + 1);
                        for (int i = 0; i <= roundCount; i++) {
                            int count = Math.min(tmpAmmoCount, stackSize);
                            ItemStack ammoItem = AmmoItemBuilder.create()
                                .setId(ammoId)
                                .setCount(count)
                                .build();
                            giveItemToPlayer(player, ammoItem);
                            tmpAmmoCount -= stackSize;
                        }
                        setCurrentAmmoCount(gunItem, 0);
                    });
            });
    }

    /**
     * 1.7.10 移植辅助：{@code ItemHandlerHelper.giveItemToPlayer} 的等价实现
     * （先塞背包，塞不下就丢到玩家脚下）。
     */
    private static void giveItemToPlayer(EntityPlayer player, ItemStack stack) {
        if (ItemStackNbtHelper.isEmpty(stack)) {
            return;
        }
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropPlayerItemWithRandomChoice(stack, false);
        }
    }

    /**
     * 1.7.10 移植辅助：获取实体可用的物品栏。
     * 上游 1.20.1 通过 capability {@code ForgeCapabilities.ITEM_HANDLER} 获取任意实体的背包；
     * 1.7.10 没有 capability，且只有玩家的背包是可访问的（生物背包没有统一抽象），
     * 因此这里只返回 {@link EntityPlayer#inventory}，其它实体返回 null（记录为移植差异）。
     */
    @Nullable
    protected static IInventory getEntityInventory(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer player) {
            return player.inventory;
        }
        return null;
    }

    /**
     * 枪械寻弹和扣除背包弹药逻辑
     * 
     * @param itemHandler   目标实体的背包
     * @param gunItem       枪械物品
     * @param needAmmoCount 需要的弹药 (物品) 数量
     * @return 寻找到的弹药 (物品) 数量
     */
    @Deprecated
    public int findAndExtractInventoryAmmos(IInventory itemHandler, ItemStack gunItem, int needAmmoCount) {
        return findAndExtractInventoryAmmo(itemHandler, gunItem, needAmmoCount);
    }

    /**
     * 枪械寻弹和扣除背包弹药逻辑
     * 
     * @param inventory     目标实体的物品栏（1.7.10 无 {@code IItemHandler}，改用 {@link IInventory}）
     * @param gunItem       枪械物品
     * @param needAmmoCount 需要的弹药 (物品) 数量
     * @return 寻找到的弹药 (物品) 数量
     */
    public int findAndExtractInventoryAmmo(IInventory inventory, ItemStack gunItem, int needAmmoCount) {
        int cnt = needAmmoCount;
        // 背包检查
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack checkAmmoStack = inventory.getStackInSlot(i);
            if (ItemStackNbtHelper.isEmpty(checkAmmoStack)) {
                continue;
            }
            if (checkAmmoStack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gunItem, checkAmmoStack)) {
                ItemStack extractItem = inventory.decrStackSize(i, cnt);
                cnt = cnt - (ItemStackNbtHelper.isEmpty(extractItem) ? 0 : extractItem.stackSize);
                if (cnt <= 0) {
                    break;
                }
            }
            if (checkAmmoStack.getItem() instanceof IAmmoBox iAmmoBox
                && iAmmoBox.isAmmoBoxOfGun(gunItem, checkAmmoStack)) {
                int boxAmmoCount = iAmmoBox.getAmmoCount(checkAmmoStack);
                int extractCount = Math.min(boxAmmoCount, cnt);
                int remainCount = boxAmmoCount - extractCount;
                iAmmoBox.setAmmoCount(checkAmmoStack, remainCount);
                if (remainCount <= 0) {
                    iAmmoBox.setAmmoId(checkAmmoStack, DefaultAssets.EMPTY_AMMO_ID);
                }
                cnt = cnt - extractCount;
                if (cnt <= 0) {
                    break;
                }
            }
        }
        return needAmmoCount - cnt;
    }

    /**
     * 扣除虚拟弹药逻辑，该方法具有通用的实现，放在此处
     * 
     * @param gunItem       枪械物品
     * @param needAmmoCount 需要的弹药(物品)数量
     * @return 找到的弹药(物品)数量
     */
    public int findAndExtractDummyAmmo(ItemStack gunItem, int needAmmoCount) {
        int dummyAmmoCount = getDummyAmmoAmount(gunItem);
        int extractCount = Math.min(dummyAmmoCount, needAmmoCount);
        addDummyAmmoAmount(gunItem, -extractCount);
        return extractCount;
    }

    /**
     * 检查枪械是否允许安装指定的物品作为配件
     */
    @Override
    public boolean allowAttachment(ItemStack gun, ItemStack attachmentItem) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun != null && iAttachment != null) {
            AttachmentType attachmentType = iAttachment.getType(attachmentItem);
            ResourceLocation attachmentId = iAttachment.getAttachmentId(attachmentItem);
            return SlotAdapterHelper.allowsAttachment(gun, attachmentType, attachmentId);
        }
        return false;
    }

    /**
     * 检查枪械是否允许安装某种类型的配件
     */
    @Override
    public boolean allowAttachmentType(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun != null) {
            return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(gunIndex -> {
                    List<AttachmentType> allowAttachments = gunIndex.getGunData()
                        .getAllowAttachments();
                    if (allowAttachments == null) {
                        return false;
                    }
                    return allowAttachments.contains(type);
                })
                .orElse(false);
        } else {
            return false;
        }
    }

    /**
     * 获取枪械的显示名称。
     * 1.7.10 移植：上游 {@code getName(ItemStack)} 返回 {@code Component}，1.7.10 对应
     * {@link Item#getItemStackDisplayName(ItemStack)} 返回 String。
     */
    @Override
    @Nonnull
    @SideOnly(Side.CLIENT)
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        ResourceLocation gunId = this.getGunId(stack);
        Optional<ClientGunIndex> gunIndex = TimelessAPI.getClientGunIndex(gunId);
        if (gunIndex.isPresent()) {
            return StatCollector.translateToLocal(
                gunIndex.get()
                    .getName());
        }
        return super.getItemStackDisplayName(stack);
    }

    /**
     * 获取某一类 TabType 的所有枪械物品的实例。用于填充创造物品栏和枪械制造台。
     * <p>
     * 1.7.10 移植：上游返回 {@code NonNullList<ItemStack>}，1.7.10 无 NonNullList，改回普通 {@link List}。
     */
    public static List<ItemStack> fillItemCategory(GunTabType type) {
        List<ItemStack> stacks = new ArrayList<>();
        TimelessAPI.getAllCommonGunIndex()
            .stream()
            .sorted(idNameSort())
            .forEach(entry -> {
                CommonGunIndex index = entry.getValue();
                GunData gunData = index.getGunData();
                String key = type.name()
                    .toLowerCase(Locale.US);
                String indexType = index.getType();
                if (key.equals(indexType)) {
                    ItemStack itemStack = GunItemBuilder.create()
                        .setId(entry.getKey())
                        .setFireMode(
                            gunData.getFireModeSet()
                                .get(0))
                        .setAmmoCount(gunData.getAmmoAmount())
                        .setHeatData(gunData.hasHeatData())
                        .setAmmoInBarrel(true)
                        .putAllVirtualAttachment(DefaultGunAttachments.defaults(entry.getKey()))
                        .build();
                    if (!ItemStackNbtHelper.isEmpty(itemStack)) {
                        stacks.add(itemStack);
                    }
                }
            });
        return stacks;
    }

    /**
     * 阻止玩家手臂挥动。
     * 1.7.10 移植：上游 {@code onEntitySwing(ItemStack, LivingEntity)}，1.7.10 的参数顺序是
     * {@code onEntitySwing(EntityLivingBase, ItemStack)}。
     */
    @Override
    public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack) {
        return true;
    }

    @Override
    public boolean useInventoryAmmo(ItemStack gun) {
        // 1.20 空栈是 ItemStack.EMPTY（getItem() 返回 AIR，instanceof 为 false）；
        // 1.7.10 空栈是 null，必须先判空，否则空手/空槽位调用即 NPE。
        if (!ItemStackNbtHelper.isEmpty(gun) && gun.getItem() instanceof IGun) {
            Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(this.getGunId(gun));
            if (gunIndexOptional.isEmpty()) {
                return false;
            }
            CommonGunIndex gunIndex = gunIndexOptional.get();
            // 是否为弹药直读
            return gunIndex.getGunData()
                .getReloadData()
                .getType()
                .equals(FeedType.INVENTORY);
        }
        return false;
    }

    /**
     * 获取是否有供给弹药直读的弹药
     * 
     * @param gun 枪械
     * @return 是否有供给弹药直读的弹药
     */
    @Override
    public boolean hasInventoryAmmo(EntityLivingBase shooter, ItemStack gun, boolean needCheckAmmo) {
        // 如果不是背包直读，则直接返回 false
        if (!useInventoryAmmo(gun)) {
            return false;
        }
        // 如果不需要检查子弹，则直接返回 true
        if (!needCheckAmmo) {
            return true;
        }
        // 虚拟备弹处理
        if (useDummyAmmo(gun)) {
            return getDummyAmmoAmount(gun) > 0;
        }
        // 检查背包内的弹药数量
        IInventory inventory = getEntityInventory(shooter);
        if (inventory == null) {
            return false;
        }
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack checkAmmoStack = inventory.getStackInSlot(i);
            if (ItemStackNbtHelper.isEmpty(checkAmmoStack)) {
                continue;
            }
            if (checkAmmoStack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gun, checkAmmoStack)) {
                return true;
            }
            if (checkAmmoStack.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(gun, checkAmmoStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取 RPM
     * 
     * @param gun 枪械
     * @return RPM 数值
     */
    @Override
    public int getRPM(ItemStack gun) {
        if (!ItemStackNbtHelper.isEmpty(gun) && gun.getItem() instanceof IGun iGun) {
            return TimelessAPI.getCommonGunIndex(this.getGunId(gun))
                .map(CommonGunIndex::getGunData)
                .map(gunData -> {
                    FireMode fireMode = getFireMode(gun);
                    int rpm = gunData.getRoundsPerMinute(fireMode);
                    if (iGun.hasHeatData(gun)) {
                        rpm *= (int) iGun.lerpRPM(gun);
                    }
                    return rpm;
                })
                .orElse(300);
        }
        return 300;
    }

    /**
     * 获取是否可以趴下射击
     * 
     * @param gun 枪械
     * @return 是否可以趴下射击
     */
    @Override
    public boolean isCanCrawl(ItemStack gun) {
        if (!ItemStackNbtHelper.isEmpty(gun) && gun.getItem() instanceof IGun) {
            return TimelessAPI.getCommonGunIndex(this.getGunId(gun))
                .map(CommonGunIndex::getGunData)
                .map(GunData::isCanCrawl)
                .orElse(false);
        }
        return false;
    }

    @Override
    public boolean isSame(ItemStack i, ItemStack j) {
        IGun iGun1 = IGun.getIGunOrNull(i);
        IGun iGun2 = IGun.getIGunOrNull(j);
        if (iGun1 != null && iGun2 != null) {
            return iGun1.getGunId(i)
                .equals(iGun2.getGunId(j))
                && iGun1.getGunDisplayId(i)
                    .equals(iGun2.getGunDisplayId(j));
        }
        if (ItemStackNbtHelper.isEmpty(i) || ItemStackNbtHelper.isEmpty(j)) {
            return ItemStackNbtHelper.isEmpty(i) && ItemStackNbtHelper.isEmpty(j);
        }
        // 1.7.10 无 ItemStack.matches，等价物是 areItemStacksEqual（同样比较 StackSize/item/damage/NBT）
        return ItemStack.areItemStacksEqual(i, j);
    }
}
