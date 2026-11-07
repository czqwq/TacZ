package com.tacz.guns.compat.nei;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;

/**
 * NEI 枪械查询页面的共享数据（枪↔弹药、枪↔配件）。
 *
 * <p>
 * 上游 1.20.1 在 JEI 里做同样的事：{@code compat/jei/entry/GunAmmoQueryEntry} / {@code AmmoQueryEntry} /
 * {@code AttachmentQueryData} / {@code GunAttachmentQueryEntry} / {@code AttachmentQueryEntry}。
 * 1.7.10 没有 JEI 的 category/entry 概念，本类把「数据计算」抽出来供 4 个 NEI handler 复用：
 * <ul>
 * <li>枪↔弹药：{@link GunAmmo}（R 枪 → 弹药页）与 {@link AmmoGuns}（R 弹药 → 适用枪械页）；</li>
 * <li>枪↔配件：{@link AttachmentGuns}（R 配件 → 兼容枪械页）与 {@link GunAttachments}（R 枪 → 兼容配件页）。</li>
 * </ul>
 *
 * <p>
 * <b>缓存与失效</b>：配件兼容性计算是 {@code 枪数 × 配件数} 级别的（默认+示例枪包约 1.7 万次
 * {@code IGun#allowAttachment}），每次按 R/U 重算会明显卡顿，所以按「枪/弹药/配件索引的 id 哈希 + 数量」
 * 做指纹缓存；枪包重载（{@code /tacz reload}）后 id 集合变化会自动重建，同 id 集合下的数据内容变化
 * （例如只改配件兼容性）不会触发重建 —— 重载后重开一次 NEI 查询即可，属可接受限制（记录在
 * {@code docs/review/r8/nei.md}）。
 */
final class NeiQueryData {

    /** 一页最多显示多少个物品（9 列 x 2 行）。 */
    static final int PAGE_SIZE = NeiRender.GRID_COLUMNS * 2;

    private static final Object LOCK = new Object();
    private static volatile Snapshot snapshot;

    private NeiQueryData() {}

    static Snapshot get() {
        long fingerprint = fingerprint();
        Snapshot current = snapshot;
        if (current != null && current.fingerprint == fingerprint) {
            return current;
        }
        synchronized (LOCK) {
            current = snapshot;
            if (current != null && current.fingerprint == fingerprint) {
                return current;
            }
            long start = System.currentTimeMillis();
            Snapshot rebuilt = build(fingerprint);
            snapshot = rebuilt;
            GunMod.LOGGER.info(
                "[TACZ] NEI query data rebuilt in {} ms ({} guns, {} ammo entries, {} attachment entries)",
                System.currentTimeMillis() - start,
                rebuilt.gunAmmo.size(),
                rebuilt.ammoGuns.size(),
                rebuilt.attachmentGuns.size());
            return rebuilt;
        }
    }

    private static long fingerprint() {
        long fingerprint = 17L;
        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : TimelessAPI.getAllCommonGunIndex()) {
            fingerprint = fingerprint * 31L + entry.getKey()
                .hashCode() * 31L
                + entry.getValue()
                    .getSort();
        }
        fingerprint = fingerprint * 31L + TimelessAPI.getAllCommonAmmoIndex()
            .size();
        for (Map.Entry<ResourceLocation, CommonAmmoIndex> entry : TimelessAPI.getAllCommonAmmoIndex()) {
            fingerprint = fingerprint * 31L + entry.getKey()
                .hashCode() * 31L
                + entry.getValue()
                    .getSort();
        }
        fingerprint = fingerprint * 31L + TimelessAPI.getAllCommonAttachmentIndex()
            .size();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            fingerprint = fingerprint * 31L + entry.getKey()
                .hashCode() * 31L
                + entry.getValue()
                    .getSort() * 7L
                + entry.getValue()
                    .getType()
                    .ordinal();
        }
        return fingerprint;
    }

    private static Snapshot build(long fingerprint) {
        List<GunEntry> guns = collectGuns();
        List<AttachmentEntry> attachments = collectAttachments();

        List<GunAmmo> gunAmmo = new ArrayList<>();
        Map<ResourceLocation, ItemStack> ammoStackById = new LinkedHashMap<>();
        for (GunEntry gun : guns) {
            ResourceLocation ammoId = gun.index()
                .getGunData()
                .getAmmoId();
            if (ammoId == null || TimelessAPI.getCommonAmmoIndex(ammoId)
                .isEmpty()) {
                continue;
            }
            ItemStack gunStack = NeiStacks.buildGun(gun.id());
            if (gunStack == null) {
                continue;
            }
            ItemStack ammoStack = ammoStackById.computeIfAbsent(ammoId, NeiStacks::buildAmmo);
            IGun iGun = IGun.getIGunOrNull(gunStack);
            IAmmo iAmmo = IAmmo.getIAmmoOrNull(ammoStack);
            if (iGun == null || iAmmo == null || !iAmmo.isAmmoOfGun(gunStack, ammoStack)) {
                continue;
            }
            gunAmmo.add(new GunAmmo(gunStack, ammoStack));
        }

        // 弹药 → 适用枪械：与上游 AmmoQueryEntry 一致，共用枪↔弹药映射，保证两个方向结果一致。
        Map<ResourceLocation, List<ItemStack>> gunsByAmmo = new LinkedHashMap<>();
        Map<ResourceLocation, ItemStack> ammoStacks = new LinkedHashMap<>();
        for (GunAmmo pair : gunAmmo) {
            IAmmo iAmmo = IAmmo.getIAmmoOrNull(pair.ammo());
            if (iAmmo == null) {
                continue;
            }
            ResourceLocation ammoId = iAmmo.getAmmoId(pair.ammo());
            gunsByAmmo.computeIfAbsent(ammoId, id -> new ArrayList<>())
                .add(pair.gun());
            ammoStacks.put(ammoId, pair.ammo());
        }
        List<AmmoGuns> ammoGuns = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ItemStack> entry : ammoStacks.entrySet()) {
            ammoGuns.add(new AmmoGuns(entry.getValue(), List.copyOf(gunsByAmmo.get(entry.getKey()))));
        }

        // 配件 ↔ 枪械：与上游 AttachmentQueryData#create 的兼容性判定一致
        // （attachment 侧只列 GunTabType 已知的枪械分组；gun 侧只列 allowAttachmentType 允许的槽位类型）。
        Map<AttachmentEntry, Map<GunTabType, List<ItemStack>>> gunsByAttachment = new LinkedHashMap<>();
        Map<GunEntry, Map<AttachmentType, List<ItemStack>>> attachmentsByGun = new LinkedHashMap<>();
        Set<AttachmentType> presentTypes = EnumSet.noneOf(AttachmentType.class);
        for (AttachmentEntry attachment : attachments) {
            if (attachment.type() != AttachmentType.NONE) {
                presentTypes.add(attachment.type());
            }
        }
        for (GunEntry gun : guns) {
            ItemStack gunStack = NeiStacks.buildGun(gun.id());
            IGun iGun = IGun.getIGunOrNull(gunStack);
            if (iGun == null) {
                continue;
            }
            GunTabType gunTab = toTabType(
                gun.index()
                    .getType());
            Set<AttachmentType> allowedTypes = EnumSet.noneOf(AttachmentType.class);
            for (AttachmentType type : presentTypes) {
                if (iGun.allowAttachmentType(gunStack, type)) {
                    allowedTypes.add(type);
                }
            }
            for (AttachmentEntry attachment : attachments) {
                ItemStack attachmentStack = NeiStacks.buildAttachment(attachment.id());
                if (attachmentStack == null) {
                    continue;
                }
                boolean reverseEligible = allowedTypes.contains(attachment.type());
                if (gunTab == null && !reverseEligible) {
                    continue;
                }
                if (!iGun.allowAttachment(gunStack, attachmentStack)) {
                    continue;
                }
                // 配件视角（R 配件 → 可用枪械）不要求 allowAttachmentType，与上游注释一致。
                if (gunTab != null) {
                    gunsByAttachment.computeIfAbsent(attachment, key -> new EnumMap<>(GunTabType.class))
                        .computeIfAbsent(gunTab, key -> new ArrayList<>())
                        .add(gunStack);
                }
                if (reverseEligible) {
                    attachmentsByGun.computeIfAbsent(gun, key -> new EnumMap<>(AttachmentType.class))
                        .computeIfAbsent(attachment.type(), key -> new ArrayList<>())
                        .add(attachmentStack);
                }
            }
        }

        List<AttachmentGuns> attachmentGuns = new ArrayList<>();
        for (AttachmentEntry attachment : attachments) {
            Map<GunTabType, List<ItemStack>> groups = gunsByAttachment.get(attachment);
            if (groups == null) {
                continue;
            }
            for (GunTabType tab : GunTabType.values()) {
                List<ItemStack> matches = groups.get(tab);
                if (matches != null) {
                    ItemStack attachmentStack = NeiStacks.buildAttachment(attachment.id());
                    if (attachmentStack != null) {
                        attachmentGuns.add(new AttachmentGuns(attachmentStack, tab, List.copyOf(matches)));
                    }
                }
            }
        }

        List<GunAttachments> gunAttachments = new ArrayList<>();
        for (GunEntry gun : guns) {
            Map<AttachmentType, List<ItemStack>> groups = attachmentsByGun.get(gun);
            if (groups == null) {
                continue;
            }
            for (AttachmentType type : AttachmentType.values()) {
                List<ItemStack> matches = groups.get(type);
                if (matches == null) {
                    continue;
                }
                ItemStack gunStack = NeiStacks.buildGun(gun.id());
                if (gunStack != null) {
                    gunAttachments.add(new GunAttachments(gunStack, type, List.copyOf(matches)));
                }
            }
        }

        return new Snapshot(
            fingerprint,
            List.copyOf(gunAmmo),
            List.copyOf(ammoGuns),
            List.copyOf(attachmentGuns),
            List.copyOf(gunAttachments));
    }

    private static List<GunEntry> collectGuns() {
        List<GunEntry> guns = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonGunIndex> entry : TimelessAPI.getAllCommonGunIndex()) {
            guns.add(new GunEntry(entry.getKey(), entry.getValue()));
        }
        guns.sort(
            (a, b) -> a.index()
                .getSort()
                != b.index()
                    .getSort() ? Integer.compare(
                        a.index()
                            .getSort(),
                        b.index()
                            .getSort())
                        : a.id()
                            .toString()
                            .compareTo(
                                b.id()
                                    .toString()));
        return guns;
    }

    private static List<AttachmentEntry> collectAttachments() {
        List<AttachmentEntry> attachments = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            if (entry.getValue()
                .getPojo()
                .isHidden()) {
                continue;
            }
            attachments.add(new AttachmentEntry(entry.getKey(), entry.getValue()));
        }
        attachments.sort(
            (a, b) -> a.index()
                .getSort()
                != b.index()
                    .getSort() ? Integer.compare(
                        a.index()
                            .getSort(),
                        b.index()
                            .getSort())
                        : a.id()
                            .toString()
                            .compareTo(
                                b.id()
                                    .toString()));
        return attachments;
    }

    @Nullable
    private static GunTabType toTabType(@Nullable String type) {
        if (type == null) {
            return null;
        }
        try {
            return GunTabType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ================================================================================================
    // 数据结构（只读快照）
    // ================================================================================================

    static final class Snapshot {

        final long fingerprint;
        final List<GunAmmo> gunAmmo;
        final List<AmmoGuns> ammoGuns;
        final List<AttachmentGuns> attachmentGuns;
        final List<GunAttachments> gunAttachments;

        private Snapshot(long fingerprint, List<GunAmmo> gunAmmo, List<AmmoGuns> ammoGuns,
            List<AttachmentGuns> attachmentGuns, List<GunAttachments> gunAttachments) {
            this.fingerprint = fingerprint;
            this.gunAmmo = gunAmmo;
            this.ammoGuns = ammoGuns;
            this.attachmentGuns = attachmentGuns;
            this.gunAttachments = gunAttachments;
        }
    }

    record GunEntry(ResourceLocation id, CommonGunIndex index) {}

    record AttachmentEntry(ResourceLocation id, CommonAttachmentIndex index) {

        AttachmentType type() {
            return index.getType();
        }
    }

    /** 一把枪与其使用的弹药物品。 */
    record GunAmmo(ItemStack gun, ItemStack ammo) {}

    /** 一种弹药与适用它的全部枪械。 */
    record AmmoGuns(ItemStack ammo, List<ItemStack> guns) {}

    /** 一个配件在某个枪械分类下兼容的枪械列表。 */
    record AttachmentGuns(ItemStack attachment, GunTabType tab, List<ItemStack> guns) {}

    /** 一把枪在某个配件槽位类型下兼容的配件列表。 */
    record GunAttachments(ItemStack gun, AttachmentType type, List<ItemStack> attachments) {}

    /** 供调试/日志用：当前是否已有缓存。 */
    static boolean isCached() {
        return snapshot != null;
    }

    /** 按 {@link #PAGE_SIZE} 把一页物品切成多页（列表类页面用）。 */
    static List<List<ItemStack>> splitPages(List<ItemStack> stacks) {
        List<List<ItemStack>> pages = new ArrayList<>();
        if (stacks == null || stacks.isEmpty()) {
            return pages;
        }
        for (int start = 0; start < stacks.size(); start += PAGE_SIZE) {
            pages.add(stacks.subList(start, Math.min(start + PAGE_SIZE, stacks.size())));
        }
        return pages;
    }

    /** 清空缓存（枪包重载钩子可调用）。 */
    static void invalidate() {
        snapshot = null;
    }

    /** {@link CommonAssetsManager} 是否已经加载出数据（NEI 插件加载时机可能早于枪包同步）。 */
    static boolean hasData() {
        return !TimelessAPI.getAllCommonGunIndex()
            .isEmpty()
            || !TimelessAPI.getAllCommonAttachmentIndex()
                .isEmpty();
    }
}
