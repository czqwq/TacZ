package com.tacz.guns.init;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.item.AmmoBoxItem;
import com.tacz.guns.item.AmmoItem;
import com.tacz.guns.item.AttachmentItem;
import com.tacz.guns.item.GunSmithTableItem;

/**
 * 搬运自上游 1.20.1 {@code init/ModCreativeTabs.java}（16 个创造模式标签页）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code DeferredRegister<CreativeModeTab>}/{@code CreativeModeTab.builder()} → 1.7.10
 * {@link CreativeTabs} 子类；</li>
 * <li>{@code displayItems(...)}（1.20 的懒执行 lambda）→ {@link CreativeTabs#displayAllReleventItems(List)}，
 * 同样是「打开标签页时才枚举」，因此里面的 {@code fillItemCategory()} 能在枪包加载完成后正确取到索引；</li>
 * <li>{@code Component.translatable} 标题 → {@code CreativeTabs} 的 label（翻译 key 前缀 {@code itemGroup.}，
 * 与 assets 里 {@code itemGroup.tab.tacz.*} 对齐）；</li>
 * <li>1.7.10 的标签页槽位有限（{@code creativeTabArray} 是固定容量数组），但自定义 tab 走
 * {@link CreativeTabs#CreativeTabs(String)} 构造器，按需扩容，行为与上游一致。</li>
 * </ul>
 */
@SuppressWarnings("all")
public class ModCreativeTabs {

    public static CreativeTabs OTHER_TAB;
    public static CreativeTabs AMMO_TAB;
    public static CreativeTabs ATTACHMENT_SCOPE_TAB;
    public static CreativeTabs ATTACHMENT_MUZZLE_TAB;
    public static CreativeTabs ATTACHMENT_STOCK_TAB;
    public static CreativeTabs ATTACHMENT_GRIP_TAB;
    public static CreativeTabs ATTACHMENT_EXTENDED_MAG_TAB;
    public static CreativeTabs ATTACHMENT_LASER_TAB;
    public static CreativeTabs GUN_PISTOL_TAB;
    public static CreativeTabs GUN_SNIPER_TAB;
    public static CreativeTabs GUN_DMR_TAB;
    public static CreativeTabs GUN_RIFLE_TAB;
    public static CreativeTabs GUN_SHOTGUN_TAB;
    public static CreativeTabs GUN_SMG_TAB;
    public static CreativeTabs GUN_RPG_TAB;
    public static CreativeTabs GUN_MG_TAB;

    public static void register() {
        if (OTHER_TAB != null) {
            return;
        }
        OTHER_TAB = new TaczTab("tab.tacz.other", () -> new ItemStack(ModItems.GUN_SMITH_TABLE), () -> {
            List<ItemStack> list = new ArrayList<>(GunSmithTableItem.fillItemCategory());
            list.add(new ItemStack(ModItems.TARGET));
            list.add(new ItemStack(ModItems.STATUE));
            list.add(new ItemStack(ModItems.GUN_PEGBOARD));
            list.add(new ItemStack(ModItems.GLOW_GUN_PEGBOARD));
            list.add(new ItemStack(ModItems.AMMO_STORAGE));
            list.add(new ItemStack(ModItems.AMMO_STORAGE_GOLD));
            list.add(new ItemStack(ModItems.AMMO_STORAGE_DIAMOND));
            list.add(new ItemStack(ModItems.GUN_DISPLAY_CASE));
            list.add(new ItemStack(ModItems.TARGET_MINECART));
            list.addAll(AmmoBoxItem.fillItemCategory());
            return list;
        });
        AMMO_TAB = new TaczTab(
            "tab.tacz.ammo",
            () -> AmmoItemBuilder.create()
                .setId(DefaultAssets.DEFAULT_AMMO_ID)
                .build(),
            AmmoItem::fillItemCategory);
        ATTACHMENT_SCOPE_TAB = new TaczTab(
            "tacz.type.scope.name",
            () -> AttachmentItemBuilder.create()
                .setId(new ResourceLocation("tacz", "scope_acog_ta31"))
                .build(),
            () -> AttachmentItem.fillItemCategory(AttachmentType.SCOPE));
        ATTACHMENT_MUZZLE_TAB = new TaczTab(
            "tacz.type.muzzle.name",
            () -> AttachmentItemBuilder.create()
                .setId(new ResourceLocation("tacz", "muzzle_compensator_trident"))
                .build(),
            () -> AttachmentItem.fillItemCategory(AttachmentType.MUZZLE));
        ATTACHMENT_STOCK_TAB = new TaczTab(
            "tacz.type.stock.name",
            () -> AttachmentItemBuilder.create()
                .setId(new ResourceLocation("tacz", "stock_militech_b5"))
                .build(),
            () -> AttachmentItem.fillItemCategory(AttachmentType.STOCK));
        ATTACHMENT_GRIP_TAB = new TaczTab(
            "tacz.type.grip.name",
            () -> AttachmentItemBuilder.create()
                .setId(new ResourceLocation("tacz", "grip_magpul_afg_2"))
                .build(),
            () -> AttachmentItem.fillItemCategory(AttachmentType.GRIP));
        ATTACHMENT_EXTENDED_MAG_TAB = new TaczTab(
            "tacz.type.extended_mag.name",
            () -> AttachmentItemBuilder.create()
                .setId(new ResourceLocation("tacz", "extended_mag_3"))
                .build(),
            () -> AttachmentItem.fillItemCategory(AttachmentType.EXTENDED_MAG));
        ATTACHMENT_LASER_TAB = new TaczTab(
            "tacz.type.laser.name",
            () -> AttachmentItemBuilder.create()
                .setId(new ResourceLocation("tacz", "laser_compact"))
                .build(),
            () -> AttachmentItem.fillItemCategory(AttachmentType.LASER));
        GUN_PISTOL_TAB = gunTab("tacz.type.pistol.name", "glock_17", GunTabType.PISTOL);
        GUN_SNIPER_TAB = gunTab("tacz.type.sniper.name", "ai_awp", GunTabType.SNIPER);
        GUN_DMR_TAB = gunTab("tacz.type.dmr.name", "mk14", GunTabType.DMR);
        GUN_RIFLE_TAB = gunTab("tacz.type.rifle.name", "ak47", GunTabType.RIFLE);
        GUN_SHOTGUN_TAB = gunTab("tacz.type.shotgun.name", "db_short", GunTabType.SHOTGUN);
        GUN_SMG_TAB = gunTab("tacz.type.smg.name", "hk_mp5a5", GunTabType.SMG);
        GUN_RPG_TAB = gunTab("tacz.type.rpg.name", "rpg7", GunTabType.RPG);
        GUN_MG_TAB = gunTab("tacz.type.mg.name", "m249", GunTabType.MG);
    }

    private static CreativeTabs gunTab(String label, String iconGunId, GunTabType type) {
        return new TaczTab(
            label,
            () -> GunItemBuilder.create()
                .setId(new ResourceLocation("tacz", iconGunId))
                .build(),
            () -> AbstractGunItem.fillItemCategory(type));
    }

    /**
     * 1.7.10 自定义创造标签页：图标与内容都用 lambdas 懒求值（等价 1.20 CreativeModeTab.builder 的 icon/displayItems）。
     */
    private static final class TaczTab extends CreativeTabs {

        private final String translationKey;
        private final Supplier<ItemStack> icon;
        private final Supplier<List<ItemStack>> items;

        private TaczTab(String label, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items) {
            super(label);
            // 上游 label 就是翻译 key 的剩余部分：1.7.10 的 getTranslatedTabLabel() = "itemGroup." + label
            this.translationKey = label;
            this.icon = icon;
            this.items = items;
        }

        /**
         * 上游 1.20.1 的 tab 标题是 {@code Component.translatable(label)}（<b>不加前缀</b>）。
         *
         * <p>
         * 1.7.10 依据：{@code CreativeTabs.java:186-189} 默认实现返回 {@code "itemGroup." + getTabLabel()}，
         * 而 {@code GuiContainerCreative.java:433/750/913} 用 {@code I18n.format(getTranslatedTabLabel())} 取标题。
         * 本模组的两类 key 前缀不同：
         * <ul>
         * <li>{@code tab.tacz.other} / {@code tab.tacz.ammo} → lang 里是 {@code itemGroup.tab.tacz.*}
         * （{@code assets/tacz/lang/en_US.lang:71-72}），走默认前缀；</li>
         * <li>{@code tacz.type.<type>.name} → 在枪包 lang 里就是该 key 本身
         * （{@code custom/tacz_default_gun/assets/tacz/lang/en_US.lang:12-20}），不能再加 {@code itemGroup.}，
         * 否则 9 个枪械/配件标签页会显示成 {@code itemGroup.tacz.type.rifle.name} 原文。</li>
         * </ul>
         */
        @Override
        public String getTranslatedTabLabel() {
            return translationKey.startsWith("tab.") ? "itemGroup." + translationKey : translationKey;
        }

        @Override
        public Item getTabIconItem() {
            ItemStack stack = this.icon.get();
            return stack == null ? null : stack.getItem();
        }

        @Override
        public ItemStack getIconItemStack() {
            return this.icon.get();
        }

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public void displayAllReleventItems(List list) {
            List<ItemStack> stacks = this.items.get();
            if (stacks != null) {
                list.addAll(stacks);
            }
        }
    }
}
