package com.tacz.guns.api;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.client.other.IThirdPersonAnimation;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.index.ClientSlotAdapterIndex;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonAttachmentVariantIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;
import com.tacz.guns.util.VariantHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class TimelessAPI {

    @SideOnly(Side.CLIENT)
    public static Optional<GunDisplayInstance> getGunDisplay(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return Optional.empty();
        }
        if (stack.getItem() instanceof IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(stack);
            if (getCommonGunIndex(gunId).isEmpty()) {
                return Optional.empty();
            }
            ResourceLocation displayId = iGun.getGunDisplayId(stack);
            if (displayId.equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID)) {
                return getClientGunIndex(gunId).map(ClientGunIndex::getDefaultDisplay);
            } else {
                return getGunDisplay(displayId, gunId);
            }
        }
        return Optional.empty();
    }

    @SideOnly(Side.CLIENT)
    public static Optional<ClientGunIndex> getClientGunIndex(ResourceLocation gunId) {
        return Optional.ofNullable(ClientIndexManager.GUN_INDEX.get(gunId));
    }

    @SideOnly(Side.CLIENT)
    public static Optional<GunDisplayInstance> getGunDisplay(ResourceLocation displayId,
        ResourceLocation fallbackGunId) {
        if (displayId == null || displayId.equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID)) {
            return getClientGunIndex(fallbackGunId).map(ClientGunIndex::getDefaultDisplay);
        }

        GunDisplayInstance instance = ClientIndexManager.getOrCreateGunDisplay(displayId);
        if (instance == null) {
            return getClientGunIndex(fallbackGunId).map(ClientGunIndex::getDefaultDisplay);
        }
        return Optional.of(instance);
    }

    @SideOnly(Side.CLIENT)
    public static Optional<ClientAttachmentIndex> getClientAttachmentIndex(ResourceLocation attachmentId) {
        return Optional.ofNullable(ClientIndexManager.ATTACHMENT_INDEX.get(attachmentId));
    }

    @SideOnly(Side.CLIENT)
    public static Optional<ClientAttachmentVariantIndex> getClientAttachmentVariantIndex(ItemStack attachment) {
        if (attachment == null || attachment.getItem() == null) {
            return Optional.empty();
        }
        ResourceLocation variantId = VariantHelper.getVariant(attachment);
        if (variantId == null) {
            return Optional.empty();
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        if (iAttachment == null) {
            return Optional.empty();
        }
        ResourceLocation attachmentId = iAttachment.getAttachmentId(attachment);
        boolean valid = getCommonAttachmentVariantIndex(variantId).map(index -> index.appliesTo(attachmentId))
            .orElse(false);
        if (!valid) {
            return Optional.empty();
        }
        return Optional.ofNullable(ClientIndexManager.ATTACHMENT_VARIANT_INDEX.get(variantId));
    }

    @SideOnly(Side.CLIENT)
    public static Optional<ClientAttachmentVariantIndex> getClientAttachmentVariantIndex(ResourceLocation variantId) {
        return Optional.ofNullable(ClientIndexManager.ATTACHMENT_VARIANT_INDEX.get(variantId));
    }

    @SideOnly(Side.CLIENT)
    public static Optional<ClientSlotAdapterIndex> getClientSlotAdapterIndex(ResourceLocation adapterId) {
        return Optional.ofNullable(ClientIndexManager.SLOT_ADAPTER_INDEX.get(adapterId));
    }

    @SideOnly(Side.CLIENT)
    public static Optional<ClientAmmoIndex> getClientAmmoIndex(ResourceLocation ammoId) {
        return Optional.ofNullable(ClientIndexManager.AMMO_INDEX.get(ammoId));
    }

    @SideOnly(Side.CLIENT)
    public static Optional<ClientBlockIndex> getClientBlockIndex(ResourceLocation blockId) {
        return Optional.ofNullable(ClientIndexManager.BLOCK_INDEX.get(blockId));
    }

    @SideOnly(Side.CLIENT)
    public static Set<Map.Entry<ResourceLocation, ClientGunIndex>> getAllClientGunIndex() {
        return ClientIndexManager.getAllGuns();
    }

    @SideOnly(Side.CLIENT)
    public static Set<Map.Entry<ResourceLocation, ClientAmmoIndex>> getAllClientAmmoIndex() {
        return ClientIndexManager.getAllAmmo();
    }

    @SideOnly(Side.CLIENT)
    public static Set<Map.Entry<ResourceLocation, ClientAttachmentIndex>> getAllClientAttachmentIndex() {
        return ClientIndexManager.getAllAttachments();
    }

    @SideOnly(Side.CLIENT)
    public static Set<Map.Entry<ResourceLocation, ClientAttachmentVariantIndex>> getAllClientAttachmentVariantIndex() {
        return ClientIndexManager.getAllAttachmentVariants();
    }

    @SideOnly(Side.CLIENT)
    public static Set<Map.Entry<ResourceLocation, ClientSlotAdapterIndex>> getAllClientSlotAdapterIndex() {
        return ClientIndexManager.getAllSlotAdapters();
    }

    public static Optional<CommonBlockIndex> getCommonBlockIndex(ResourceLocation blockId) {
        return Optional.ofNullable(
            CommonAssetsManager.get()
                .getBlockIndex(blockId));
    }

    /**
     * YSM 等外部模组依赖的入口：取枪械公共索引。
     * 1.7.10 移植说明：方法名与签名与上游一致，仅 {@link ResourceLocation} 换成 1.7.10 同名类。
     */
    public static Optional<CommonGunIndex> getCommonGunIndex(ResourceLocation gunId) {
        return Optional.ofNullable(
            CommonAssetsManager.get()
                .getGunIndex(gunId));
    }

    public static Optional<CommonAttachmentIndex> getCommonAttachmentIndex(ResourceLocation attachmentId) {
        return Optional.ofNullable(
            CommonAssetsManager.get()
                .getAttachmentIndex(attachmentId));
    }

    public static Optional<CommonAttachmentVariantIndex> getCommonAttachmentVariantIndex(ResourceLocation variantId) {
        return Optional.ofNullable(
            CommonAssetsManager.get()
                .getAttachmentVariantIndex(variantId));
    }

    public static Optional<CommonSlotAdapterIndex> getCommonSlotAdapterIndex(ResourceLocation adapterId) {
        return Optional.ofNullable(
            CommonAssetsManager.get()
                .getSlotAdapterIndex(adapterId));
    }

    public static Optional<CommonAmmoIndex> getCommonAmmoIndex(ResourceLocation ammoId) {
        return Optional.ofNullable(
            CommonAssetsManager.get()
                .getAmmoIndex(ammoId));
    }

    /**
     * @deprecated 1.7.10 无 {@code RecipeManager}；上游此方法已废弃并返回空。
     *             1.7.10 的配方请走 {@code CraftingManager.getInstance().getRecipeList()} / {@code GameRegistry.addRecipe}。
     */
    @Deprecated
    public static Optional<GunSmithTableRecipe> getRecipe(ResourceLocation recipeId) {
        return Optional.empty();
    }

    public static Set<Map.Entry<ResourceLocation, CommonBlockIndex>> getAllCommonBlockIndex() {
        return CommonAssetsManager.get()
            .getAllBlocks();
    }

    public static Set<Map.Entry<ResourceLocation, CommonGunIndex>> getAllCommonGunIndex() {
        return CommonAssetsManager.get()
            .getAllGuns();
    }

    public static Set<Map.Entry<ResourceLocation, CommonAmmoIndex>> getAllCommonAmmoIndex() {
        return CommonAssetsManager.get()
            .getAllAmmos();
    }

    public static Set<Map.Entry<ResourceLocation, CommonAttachmentIndex>> getAllCommonAttachmentIndex() {
        return CommonAssetsManager.get()
            .getAllAttachments();
    }

    public static Set<Map.Entry<ResourceLocation, CommonAttachmentVariantIndex>> getAllCommonAttachmentVariantIndex() {
        return CommonAssetsManager.get()
            .getAllAttachmentVariants();
    }

    public static Set<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>> getAllCommonSlotAdapterIndex() {
        return CommonAssetsManager.get()
            .getAllSlotAdapters();
    }

    /**
     * @deprecated 1.7.10 无 {@code RecipeManager}，配方统一走原版 {@code CraftingManager}。
     */
    @Deprecated
    public static Map<ResourceLocation, GunSmithTableRecipe> getAllRecipes() {
        return Collections.emptyMap();
    }

    public static void registerThirdPersonAnimation(String name, IThirdPersonAnimation animation) {
        ThirdPersonManager.register(name, animation);
    }
}
