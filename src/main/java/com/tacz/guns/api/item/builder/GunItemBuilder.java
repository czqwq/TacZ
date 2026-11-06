package com.tacz.guns.api.item.builder;

import java.util.EnumMap;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.google.common.collect.Maps;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.util.VirtualOemAttachment;

public final class GunItemBuilder {

    private int count = 1;
    private int ammoCount = 0;
    private boolean heatData = false;
    private ResourceLocation gunId;
    private FireMode fireMode = FireMode.UNKNOWN;
    private boolean bulletInBarrel = false;
    private EnumMap<AttachmentType, ResourceLocation> attachments = Maps.newEnumMap(AttachmentType.class);
    private EnumMap<AttachmentType, ResourceLocation> virtualAttachments = Maps.newEnumMap(AttachmentType.class);

    private GunItemBuilder() {}

    public static GunItemBuilder create() {
        return new GunItemBuilder();
    }

    public GunItemBuilder setCount(int count) {
        this.count = Math.max(count, 1);
        return this;
    }

    public GunItemBuilder setAmmoCount(int count) {
        this.ammoCount = Math.max(count, 0);
        return this;
    }

    public GunItemBuilder setId(ResourceLocation id) {
        this.gunId = id;
        return this;
    }

    public GunItemBuilder setFireMode(FireMode fireMode) {
        this.fireMode = fireMode;
        return this;
    }

    public GunItemBuilder setAmmoInBarrel(boolean ammoInBarrel) {
        this.bulletInBarrel = ammoInBarrel;
        return this;
    }

    public GunItemBuilder putAttachment(AttachmentType type, ResourceLocation attachmentId) {
        this.attachments.put(type, attachmentId);
        return this;
    }

    public GunItemBuilder putAllAttachment(EnumMap<AttachmentType, ResourceLocation> attachments) {
        this.attachments = attachments;
        return this;
    }

    public GunItemBuilder putAllVirtualAttachment(EnumMap<AttachmentType, ResourceLocation> virtualAttachments) {
        this.virtualAttachments = virtualAttachments;
        return this;
    }

    public GunItemBuilder setHeatData(boolean heatData) {
        this.heatData = heatData;
        return this;
    }

    /**
     * 强行以默认的枪支Item构建一个物品，不进行index检查<br/>
     * 可能会返回功能不完整的物品
     */
    public ItemStack forceBuild() {
        ItemStack gun = new ItemStack(ModItems.MODERN_KINETIC_GUN, this.count);
        if (gun.getItem() instanceof IGun iGun) {
            iGun.setGunId(gun, this.gunId);
            iGun.setFireMode(gun, this.fireMode);
            iGun.setCurrentAmmoCount(gun, this.ammoCount);
            iGun.setBulletInBarrel(gun, this.bulletInBarrel);
            if (heatData) iGun.setHeatAmount(gun, 0f);
            installAttachments(gun, iGun);
        }
        return gun;
    }

    /**
     * @return 构建的枪械物品；枪械索引/变种物品不可用时返回 {@code null}
     *         （1.7.10 没有 {@code ItemStack.EMPTY}，以 null 表示空栈）
     */
    @Nullable
    public ItemStack build() {
        String itemType = TimelessAPI.getCommonGunIndex(gunId)
            .map(
                index -> index.getPojo()
                    .getItemType())
            .orElse(null);
        if (itemType == null) {
            return null;
        }

        AbstractGunItem gunItem = GunItemManager.getGunItem(itemType);
        if (gunItem == null) {
            return null;
        }

        ItemStack gun = new ItemStack(gunItem, this.count);
        if (gun.getItem() instanceof IGun iGun) {
            iGun.setGunId(gun, this.gunId);
            iGun.setFireMode(gun, this.fireMode);
            iGun.setCurrentAmmoCount(gun, this.ammoCount);
            iGun.setBulletInBarrel(gun, this.bulletInBarrel);
            installAttachments(gun, iGun);
        }
        return gun;
    }

    private void installAttachments(ItemStack gun, IGun iGun) {
        this.virtualAttachments.forEach((type, id) -> {
            if (this.attachments.containsKey(type)) {
                return;
            }
            ItemStack attachmentStack = AttachmentItemBuilder.create()
                .setId(id)
                .build();
            VirtualOemAttachment.mark(attachmentStack);
            iGun.installAttachment(gun, attachmentStack);
        });
        this.attachments.forEach((type, id) -> {
            ItemStack attachmentStack = AttachmentItemBuilder.create()
                .setId(id)
                .build();
            iGun.installAttachment(gun, attachmentStack);
        });
    }
}
